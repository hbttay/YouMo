package com.youmo.core.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.common.entity.Book;
import com.youmo.common.entity.ChapterStructure;
import com.youmo.common.entity.Foreshadowing;
import com.youmo.common.enums.ForeshadowingStatus;
import com.youmo.common.enums.ForeshadowingType;
import com.youmo.common.enums.ImportanceLevel;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.repository.ForeshadowingRepository;
import com.youmo.core.service.ForeshadowingService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForeshadowingServiceImpl implements ForeshadowingService {

    private final ForeshadowingRepository foreshadowingRepository;
    private final BookRepository bookRepository;
    private final ChapterStructureRepository chapterStructureRepository;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static final String SCAN_PROMPT = """
        你是一个小说伏笔分析器。分析本章内容，识别：
        1. 新埋下的伏笔（尚未揭示的线索、暗示、悬念）
        2. 已回收的伏笔（之前埋下的线索在本章得到揭示/呼应）

        返回JSON数组，不要输出其他内容：
        [{"type":"new|recycled","description":"伏笔描述","foreshadowing_type":"ITEM|EVENT|CHARACTER|RELATIONSHIP|PLOT_TWIST","importance":"HIGH|MEDIUM|LOW","target_entity":"关联实体名（可选）"}]
        """;

    @Override
    public List<Foreshadowing> listByBook(Long bookId) {
        return foreshadowingRepository.findByBookId(bookId);
    }

    @Override
    @Transactional
    public Foreshadowing create(Foreshadowing foreshadowing) {
        return foreshadowingRepository.save(foreshadowing);
    }

    @Override
    @Transactional
    public Foreshadowing update(Long id, Foreshadowing update) {
        Foreshadowing existing = foreshadowingRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("伏笔不存在"));
        existing.setDescription(update.getDescription());
        existing.setForeshadowingType(update.getForeshadowingType());
        existing.setImportance(update.getImportance());
        existing.setStatus(update.getStatus());
        existing.setTargetEntity(update.getTargetEntity());
        existing.setPlannedRecycleChapter(update.getPlannedRecycleChapter());
        if (update.getRecycledChapter() != null) {
            existing.setRecycledChapter(update.getRecycledChapter());
        }
        return foreshadowingRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        foreshadowingRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Map<String, List<Foreshadowing>> scanChapter(Long bookId, Long chapterStructureId, String chapterContent) {
        Map<String, List<Foreshadowing>> result = new HashMap<>();
        result.put("new", new ArrayList<>());
        result.put("recycled", new ArrayList<>());

        if (chapterContent == null || chapterContent.isBlank()) return result;

        try {
            String truncated = chapterContent.length() > 3000
                ? chapterContent.substring(0, 3000) : chapterContent;

            String userMsg = "本章内容：\n" + truncated;

            Map<String, Object> body = Map.of(
                "model", "deepseek-chat",
                "messages", List.of(
                    Map.of("role", "system", "content", SCAN_PROMPT),
                    Map.of("role", "user", "content", userMsg)
                ),
                "temperature", 0.3,
                "max_tokens", 800,
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
                log.warn("Foreshadowing scan API error {}: {}", resp.statusCode(), resp.body());
                return result;
            }

            var root = objectMapper.readTree(resp.body());
            var choices = root.get("choices");
            if (choices == null || choices.size() == 0) return result;

            String content = choices.get(0).get("message").get("content").asText();
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            var items = objectMapper.readTree(content);
            Book book = bookRepository.getReferenceById(bookId);
            ChapterStructure chapter = chapterStructureRepository.getReferenceById(chapterStructureId);

            for (var item : items) {
                String type = item.has("type") ? item.get("type").asText() : "";
                Foreshadowing f = new Foreshadowing();
                f.setBook(book);
                f.setDescription(item.has("description") ? item.get("description").asText() : "");
                f.setForeshadowingType(parseType(item.has("foreshadowing_type")
                    ? item.get("foreshadowing_type").asText() : "EVENT"));
                f.setImportance(parseImportance(item.has("importance")
                    ? item.get("importance").asText() : "MEDIUM"));
                f.setTargetEntity(item.has("target_entity") ? item.get("target_entity").asText() : null);

                if ("recycled".equals(type)) {
                    f.setStatus(ForeshadowingStatus.RECYCLED);
                    f.setRecycledChapter(chapter);
                    result.get("recycled").add(f);
                } else {
                    f.setStatus(ForeshadowingStatus.ACTIVE);
                    f.setCreatedChapter(chapter);
                    result.get("new").add(f);
                }
                foreshadowingRepository.save(f);
            }
        } catch (Exception e) {
            log.warn("Foreshadowing scan failed: {}", e.getMessage());
        }
        return result;
    }

    private ForeshadowingType parseType(String s) {
        try { return ForeshadowingType.valueOf(s.toUpperCase()); }
        catch (Exception e) { return ForeshadowingType.EVENT; }
    }

    private ImportanceLevel parseImportance(String s) {
        try { return ImportanceLevel.valueOf(s.toUpperCase()); }
        catch (Exception e) { return ImportanceLevel.MEDIUM; }
    }
}
