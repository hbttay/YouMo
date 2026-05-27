package com.youmo.core.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.common.entity.BookStyleProfile;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.BookStyleProfileRepository;
import com.youmo.core.service.StyleProfileService;
import com.youmo.core.service.TextMetricsService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class StyleProfileServiceImpl implements StyleProfileService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final BookStyleProfileRepository profileRepository;
    private final BookRepository bookRepository;
    private final TextMetricsService textMetricsService;

    public StyleProfileServiceImpl(BookStyleProfileRepository profileRepository,
                                   BookRepository bookRepository,
                                   TextMetricsService textMetricsService) {
        this.profileRepository = profileRepository;
        this.bookRepository = bookRepository;
        this.textMetricsService = textMetricsService;
    }

    private static final String STYLE_PROMPT = """
        你是一个小说风格分析师。分析以下小说的多个章节样本，提取风格特征。

        返回JSON，不要输出其他内容：
        {
          "chapter_opening_pattern": "开篇模式描述（如：场景描写开篇/对话开篇/动作开篇/心理独白开篇）",
          "tone_analysis": {"overall":"整体基调","pace":"节奏特点","density":"信息密度（高/中/低）"},
          "writing_habits": ["写作习惯1","写作习惯2","写作习惯3"],
          "style_label": "简洁/细腻/热血/沉稳/幽默/诗意"
        }
        """;

    @Override
    public BookStyleProfile getOrCreate(Long bookId) {
        return profileRepository.findByBookId(bookId).orElseGet(() -> {
            BookStyleProfile p = new BookStyleProfile();
            p.setBook(bookRepository.getReferenceById(bookId));
            return profileRepository.save(p);
        });
    }

    @Override
    @Transactional
    public BookStyleProfile analyze(Long bookId, List<String> sampleTexts) {
        BookStyleProfile profile = getOrCreate(bookId);

        // Combine all samples for Java metrics
        StringBuilder combined = new StringBuilder();
        for (String t : sampleTexts) {
            if (t != null && !t.isBlank()) combined.append(t).append("\n\n");
        }
        String allText = combined.toString();

        // Step 1: Java-based metrics
        Map<String, Object> metrics = textMetricsService.computeMetrics(allText);
        profile.setAvgSentenceLength(getDouble(metrics, "avg_sentence_length"));
        profile.setDialogueRatio(getDouble(metrics, "dialogue_ratio"));
        profile.setParagraphStyle((String) metrics.get("paragraph_style"));
        profile.setDescriptionActionRatio(getDouble(metrics, "description_action_ratio"));
        profile.setVocabularyRichness(getDouble(metrics, "vocabulary_richness"));
        profile.setSentenceVariety(getDouble(metrics, "sentence_variety"));
        profile.setSampleChapterCount(sampleTexts.size());

        // Step 2: AI-based style analysis (use first 3 samples, truncated)
        if (!sampleTexts.isEmpty()) {
            try {
                StringBuilder samples = new StringBuilder();
                for (int i = 0; i < Math.min(3, sampleTexts.size()); i++) {
                    String s = sampleTexts.get(i);
                    if (s != null && !s.isBlank()) {
                        int maxLen = Math.min(s.length(), 1500);
                        samples.append("--- 样本 ").append(i + 1).append(" ---\n")
                            .append(s.substring(0, maxLen)).append("\n\n");
                    }
                }

                String userMsg = "请分析以下小说样本的风格：\n\n" + samples;

                Map<String, Object> body = Map.of(
                    "model", "deepseek-chat",
                    "messages", List.of(
                        Map.of("role", "system", "content", STYLE_PROMPT),
                        Map.of("role", "user", "content", userMsg)
                    ),
                    "temperature", 0.4,
                    "max_tokens", 500,
                    "stream", false
                );

                String json = objectMapper.writeValueAsString(body);
                HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

                HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    var root = objectMapper.readTree(resp.body());
                    var choices = root.get("choices");
                    if (choices != null && choices.size() > 0) {
                        String content = choices.get(0).get("message").get("content").asText();
                        content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                        var result = objectMapper.readTree(content);

                        if (result.has("chapter_opening_pattern"))
                            profile.setChapterOpeningPattern(
                                objectMapper.writeValueAsString(result.get("chapter_opening_pattern")));
                        if (result.has("tone_analysis"))
                            profile.setToneAnalysis(
                                objectMapper.writeValueAsString(result.get("tone_analysis")));
                        if (result.has("writing_habits"))
                            profile.setWritingHabits(
                                objectMapper.writeValueAsString(result.get("writing_habits")));
                        if (result.has("style_label"))
                            profile.setStyleLabel(result.get("style_label").asText());
                    }
                } else {
                    log.warn("Style analysis API error {}: {}", resp.statusCode(), resp.body());
                }
            } catch (Exception e) {
                log.warn("Style analysis AI call failed: {}", e.getMessage());
            }
        }

        return profileRepository.save(profile);
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
