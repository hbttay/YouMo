package com.youmo.core.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.common.entity.ChapterSummary;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.repository.ChapterSummaryRepository;
import com.youmo.core.service.ChapterAnalysisService;
import com.youmo.core.service.ChapterEmbeddingService;
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
public class ChapterAnalysisServiceImpl implements ChapterAnalysisService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ChapterSummaryRepository chapterSummaryRepository;
    private final ChapterStructureRepository chapterStructureRepository;
    private final ChapterEmbeddingService chapterEmbeddingService;

    public ChapterAnalysisServiceImpl(ChapterSummaryRepository chapterSummaryRepository,
                                      ChapterStructureRepository chapterStructureRepository,
                                      ChapterEmbeddingService chapterEmbeddingService) {
        this.chapterSummaryRepository = chapterSummaryRepository;
        this.chapterStructureRepository = chapterStructureRepository;
        this.chapterEmbeddingService = chapterEmbeddingService;
    }

    private static final String ANALYZE_PROMPT = """
        你是一个专业的小说章节分析器。仔细阅读本章内容，提取以下结构化信息。

        返回JSON，不要输出其他内容：
        {
          "narrative_summary": "用2-3句话自然语言概括本章情节走向和核心冲突，语气专业有洞察力，让作者感觉被理解",
          "core_events": ["事件1", "事件2"],
          "appearing_characters": [{"name":"角色名","role":"本章作用","status_change":"状态变化（可选）"}],
          "character_state_changes": [{"character":"角色名","from":"之前状态","to":"之后状态"}],
          "new_foreshadowings": ["新埋下的伏笔1","新埋下的伏笔2"],
          "recycled_foreshadowings": ["被回收的伏笔1"],
          "emotion_curve_point": {"start":"开篇情绪","middle":"中段情绪","end":"结尾情绪","peak":"情绪高点"},
          "key_scenes": [{"title":"场景名","summary":"场景概要","emotion":"场景情绪"}],
          "world_elements": [{"element":"世界观要素名","detail":"本章涉及的具体内容"}]
        }
        每个数组如果确实没有内容就返回空数组[]，不要虚构。narrative_summary必须有内容。
        """;

    @Override
    @Transactional
    public ChapterSummary analyze(Long bookId, Long structureId, String chapterContent) {
        ChapterSummary summary = chapterSummaryRepository.findByStructureId(structureId)
            .orElseGet(() -> {
                ChapterSummary s = new ChapterSummary();
                s.setStructure(chapterStructureRepository.getReferenceById(structureId));
                s.setSummaryVersion(0);
                s.setSummaryType("SHORT");
                s.setIsPermanent(false);
                return s;
            });

        if (chapterContent == null || chapterContent.isBlank()) {
            log.debug("Empty chapter content for structure {}, skipping analysis", structureId);
            return summary;
        }

        try {
            String truncated = chapterContent.length() > 4000
                ? chapterContent.substring(0, 4000) : chapterContent;

            String userMsg = "请分析以下章节：\n\n" + truncated;

            Map<String, Object> body = Map.of(
                "model", "deepseek-chat",
                "messages", List.of(
                    Map.of("role", "system", "content", ANALYZE_PROMPT),
                    Map.of("role", "user", "content", userMsg)
                ),
                "temperature", 0.3,
                "max_tokens", 1200,
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

            if (resp.statusCode() != 200) {
                log.warn("Chapter analysis API error {}: {}", resp.statusCode(), resp.body());
                return summary;
            }

            var root = objectMapper.readTree(resp.body());
            var choices = root.get("choices");
            if (choices == null || choices.size() == 0) return summary;

            String content = choices.get(0).get("message").get("content").asText();
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            var result = objectMapper.readTree(content);

            if (result.has("narrative_summary") && !result.get("narrative_summary").isNull())
                summary.setNarrativeSummary(result.get("narrative_summary").asText());
            if (result.has("core_events"))
                summary.setCoreEvents(objectMapper.writeValueAsString(result.get("core_events")));
            if (result.has("appearing_characters"))
                summary.setAppearingCharacters(objectMapper.writeValueAsString(result.get("appearing_characters")));
            if (result.has("character_state_changes"))
                summary.setCharacterStateChanges(objectMapper.writeValueAsString(result.get("character_state_changes")));
            if (result.has("new_foreshadowings"))
                summary.setNewForeshadowings(objectMapper.writeValueAsString(result.get("new_foreshadowings")));
            if (result.has("recycled_foreshadowings"))
                summary.setRecycledForeshadowings(objectMapper.writeValueAsString(result.get("recycled_foreshadowings")));
            if (result.has("emotion_curve_point"))
                summary.setEmotionCurvePoint(objectMapper.writeValueAsString(result.get("emotion_curve_point")));
            if (result.has("key_scenes"))
                summary.setKeyScenes(objectMapper.writeValueAsString(result.get("key_scenes")));
            if (result.has("world_elements"))
                summary.setWorldElements(objectMapper.writeValueAsString(result.get("world_elements")));

            summary.setSummaryVersion(summary.getSummaryVersion() + 1);
            summary = chapterSummaryRepository.save(summary);

            // async: generate embedding for vector search
            try {
                chapterEmbeddingService.embedAndSave(summary);
            } catch (Exception e) {
                log.warn("Embedding generation failed for summary {}: {}", summary.getId(), e.getMessage());
            }

            return summary;
        } catch (Exception e) {
            log.warn("Chapter analysis failed for structure {}: {}", structureId, e.getMessage());
            return summary;
        }
    }
}
