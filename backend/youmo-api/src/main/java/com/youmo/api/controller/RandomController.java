package com.youmo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.api.config.PromptConfig;
import com.youmo.common.entity.Book;
import com.youmo.core.service.BookService;
import com.youmo.core.service.ChapterStructureService;
import com.youmo.core.service.CharacterService;
import com.youmo.core.service.WorldSettingService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/generation/random")
public class RandomController {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final BookService bookService;
    private final WorldSettingService worldSettingService;
    private final ChapterStructureService chapterStructureService;
    private final CharacterService characterService;
    private final PromptConfig promptConfig;

    // ── Fallback prompts (used when youmo-prompts/ directory is absent) ──

    private static final String FALLBACK_BOOK_IDEA = """
        你是一个小说创意生成器。按 JSON 格式输出：{"title":"书名","core_idea":"核心创意","creation_mode":"LINEAR","target_length":"MEDIUM"}
        """;

    private static final String FALLBACK_CHARACTER = """
        你是一个小说角色生成器。按 JSON 格式输出角色信息，包含 name,gender,age_description,appearance,origin,identity,depth_level 字段。
        """;

    private static final String FALLBACK_WORLD = """
        你是一个小说世界观生成器。按 JSON 格式输出世界观设定，包含 era,geography,history_events,politics,economy,culture,military,core_rule_type,core_rule_summary 字段。
        """;

    private static final String FALLBACK_OUTLINE = """
        你是一个小说大纲生成器。按 JSON 格式输出 volumes 数组，每卷包含 chapters，每章包含 scenes。2-3卷，每卷2-3章，每章1-3节。
        """;

    public RandomController(BookService bookService,
                            WorldSettingService worldSettingService,
                            ChapterStructureService chapterStructureService,
                            CharacterService characterService,
                            PromptConfig promptConfig) {
        this.bookService = bookService;
        this.worldSettingService = worldSettingService;
        this.chapterStructureService = chapterStructureService;
        this.characterService = characterService;
        this.promptConfig = promptConfig;
    }

    // ── Non-streaming DeepSeek call ──
    private String callDeepSeek(String systemPrompt, String userMessage, double temperature, int maxTokens)
            throws Exception {
        Map<String, Object> body = Map.of(
            "model", "deepseek-chat",
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ),
            "temperature", temperature,
            "max_tokens", maxTokens,
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
            log.error("DeepSeek API error {}: {}", resp.statusCode(), resp.body());
            throw new RuntimeException("AI 服务返回错误");
        }

        var node = objectMapper.readTree(resp.body());
        return node.get("choices").get(0).get("message").get("content").asText().strip();
    }

    private String extractJson(String text) {
        text = text.strip();
        if (text.startsWith("```json")) {
            int end = text.lastIndexOf("```");
            if (end > 7) text = text.substring(7, end).strip();
        } else if (text.startsWith("```")) {
            int end = text.lastIndexOf("```");
            if (end > 3) text = text.substring(3, end).strip();
        }
        return text;
    }

    // ── 0. 生成状态检查 ──
    @GetMapping("/status/{bookId}")
    public ResponseEntity<?> generationStatus(@PathVariable Long bookId) {
        Map<String, Boolean> status = new HashMap<>();
        status.put("synopsis", bookService.getById(bookId)
            .map(b -> b.getCoreIdea() != null && !b.getCoreIdea().isBlank()).orElse(false));
        status.put("world_setting", worldSettingService.getByBookId(bookId).isPresent());
        status.put("outline", !chapterStructureService.getTree(bookId).isEmpty());
        status.put("characters", !characterService.listByBook(bookId).isEmpty());
        return ResponseEntity.ok(status);
    }

    // ── 1. 随机书名/创意 ──
    @PostMapping("/book-idea")
    public ResponseEntity<?> generateBookIdea(@RequestBody(required = false) Map<String, String> req) {
        try {
            String genre = req != null ? req.getOrDefault("genre", "") : "";
            String systemPrompt = promptConfig.get("random-book-idea", FALLBACK_BOOK_IDEA);

            String userMsg = genre.isBlank()
                ? "请随机生成一个中文网络小说创意，题材不限"
                : "请生成一个" + genre + "题材的中文网络小说创意";

            String result = callDeepSeek(systemPrompt, userMsg, 1.3, 500);
            String json = extractJson(result);
            return ResponseEntity.ok(objectMapper.readTree(json));
        } catch (Exception e) {
            log.error("Random book idea failed", e);
            return ResponseEntity.status(500).body(Map.of("message", "生成失败: " + e.getMessage()));
        }
    }

    // ── 2. 随机角色 ──
    @PostMapping("/character/{bookId}")
    public ResponseEntity<?> generateCharacter(@PathVariable Long bookId,
                                               @RequestBody(required = false) Map<String, String> req) {
        try {
            Book book = bookService.getById(bookId).orElse(null);
            String bookTitle = book != null ? book.getTitle() : "";
            String bookIdea = book != null && book.getCoreIdea() != null ? book.getCoreIdea() : "";
            String extra = req != null ? req.getOrDefault("hint", "") : "";

            String systemPrompt = promptConfig.get("random-character", FALLBACK_CHARACTER);

            StringBuilder userMsg = new StringBuilder("请生成一个角色。");
            if (!bookTitle.isBlank()) userMsg.append("\n所属书籍：《").append(bookTitle).append("》");
            if (!bookIdea.isBlank()) userMsg.append("\n书籍总纲：").append(bookIdea);
            if (!extra.isBlank()) userMsg.append("\n额外要求：").append(extra);

            String result = callDeepSeek(systemPrompt, userMsg.toString(), 1.2, 600);
            String json = extractJson(result);
            return ResponseEntity.ok(objectMapper.readTree(json));
        } catch (Exception e) {
            log.error("Random character failed", e);
            return ResponseEntity.status(500).body(Map.of("message", "生成失败: " + e.getMessage()));
        }
    }

    // ── 3. 随机世界观 ──
    @PostMapping("/world-setting/{bookId}")
    public ResponseEntity<?> generateWorldSetting(@PathVariable Long bookId,
                                                  @RequestBody(required = false) Map<String, String> req) {
        try {
            Book book = bookService.getById(bookId).orElse(null);
            String bookTitle = book != null ? book.getTitle() : "";
            String bookIdea = book != null && book.getCoreIdea() != null ? book.getCoreIdea() : "";
            String extra = req != null ? req.getOrDefault("hint", "") : "";

            String systemPrompt = promptConfig.get("random-world", FALLBACK_WORLD);

            StringBuilder userMsg = new StringBuilder("请生成世界观设定。");
            if (!bookTitle.isBlank()) userMsg.append("\n书籍：《").append(bookTitle).append("》");
            if (!bookIdea.isBlank()) userMsg.append("\n总纲：").append(bookIdea);
            if (!extra.isBlank()) userMsg.append("\n要求：").append(extra);

            String result = callDeepSeek(systemPrompt, userMsg.toString(), 1.1, 1000);
            String json = extractJson(result);
            return ResponseEntity.ok(objectMapper.readTree(json));
        } catch (Exception e) {
            log.error("Random world setting failed", e);
            return ResponseEntity.status(500).body(Map.of("message", "生成失败: " + e.getMessage()));
        }
    }

    // ── 4. 随机大纲 ──
    @PostMapping("/outline/{bookId}")
    public ResponseEntity<?> generateOutline(@PathVariable Long bookId,
                                             @RequestBody(required = false) Map<String, String> req) {
        try {
            Book book = bookService.getById(bookId).orElse(null);
            String bookTitle = book != null ? book.getTitle() : "";
            String bookIdea = book != null && book.getCoreIdea() != null ? book.getCoreIdea() : "";
            String extra = req != null ? req.getOrDefault("hint", "") : "";

            String systemPrompt = promptConfig.get("random-outline", FALLBACK_OUTLINE);

            StringBuilder userMsg = new StringBuilder("请生成大纲。");
            if (!bookTitle.isBlank()) userMsg.append("\n书籍：《").append(bookTitle).append("》");
            if (!bookIdea.isBlank()) userMsg.append("\n总纲：").append(bookIdea);
            if (!extra.isBlank()) userMsg.append("\n要求：").append(extra);

            String result = callDeepSeek(systemPrompt, userMsg.toString(), 1.2, 1200);
            String json = extractJson(result);
            return ResponseEntity.ok(objectMapper.readTree(json));
        } catch (Exception e) {
            log.error("Random outline failed", e);
            return ResponseEntity.status(500).body(Map.of("message", "生成失败: " + e.getMessage()));
        }
    }
}
