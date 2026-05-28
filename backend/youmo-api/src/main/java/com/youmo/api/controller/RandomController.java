package com.youmo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.api.config.PromptConfig;
import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.Book;
import com.youmo.core.service.BookService;
import com.youmo.core.service.ChapterStructureService;
import com.youmo.core.service.CharacterService;
import com.youmo.core.service.GenerationLogService;
import com.youmo.core.service.WorldSettingService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final BookService bookService;
    private final WorldSettingService worldSettingService;
    private final ChapterStructureService chapterStructureService;
    private final CharacterService characterService;
    private final PromptConfig promptConfig;
    private final GenerationLogService generationLogService;

    // ── Fallback prompts (used when youmo-prompts/ directory is absent) ──

    private static final String FALLBACK_BOOK_IDEA = """
        你是一个小说创意生成器。按 JSON 格式输出：{"title":"书名","core_idea":"核心创意","creation_mode":"LINEAR","target_length":"MEDIUM"}
        """;

    private static final String FALLBACK_CHARACTER = """
        你是一个小说角色生成器。按 JSON 格式输出角色信息，包含 name,gender,age_description,appearance,origin,identity,depth_level,race 字段。

        depth_level 等级说明：
        - L0：背景板 — 只被提及名字或身份的路人，如店小二、路人甲、报信侍卫。只需 name + identity
        - L1：配角 — 推动单一情节的功能性角色，如送信人、一次性的反派手下。需 name + identity + 简短 appearance
        - L2：重要配角 — 与主角有长期互动，有独立动机和成长弧，如导师、挚友、情敌。需完整字段
        - L3：主角 — 故事核心人物，有完整的人物弧光和深层动机，与主线紧密绑定。需全部字段详实

        生成规则：
        1. 如果未指定等级，均匀分布 L0-L3，其中 L3 约占 15-20%
        2. 角色名要有中文网文风格，避免英文名
        3. 身份、出身要符合书籍世界观和总纲
        4. 只输出 JSON，不要其他内容
        """;

    private static final String FALLBACK_WORLD = """
        你是一个小说世界观生成器。按 JSON 格式输出世界观设定，包含 era,geography,history_events,politics,economy,culture,military,core_rule_type,core_rule_summary 字段。
        """;

    private static final String FALLBACK_OUTLINE = """
        你是一个小说大纲生成器。按 JSON 格式输出 volumes 数组，每卷包含 chapters，每章包含 scenes。2-3卷，每卷2-3章，每章1-3节。
        """;

    private static final String FALLBACK_OUTLINE_EXPAND = """
        你是一个小说大纲生成器。根据一句话梗概，扩展为完整大纲。
        要求：
        - 3-6卷，每卷2-4章，每章1-3节
        - 各卷应有明确的故事阶段划分（开端/发展/转折/高潮/结局）
        - 每节标题应暗示核心冲突或事件
        输出JSON格式：{"volumes":[{"title":"卷名","summary":"卷概要","chapters":[{"title":"章名","scenes":[{"title":"节名"}]}]}]}
        只输出JSON，不要其他内容。
        """;

    public RandomController(BookService bookService,
                            WorldSettingService worldSettingService,
                            ChapterStructureService chapterStructureService,
                            CharacterService characterService,
                            PromptConfig promptConfig,
                            GenerationLogService generationLogService) {
        this.bookService = bookService;
        this.worldSettingService = worldSettingService;
        this.chapterStructureService = chapterStructureService;
        this.characterService = characterService;
        this.promptConfig = promptConfig;
        this.generationLogService = generationLogService;
    }

    // ── Non-streaming DeepSeek call ──
    private String callDeepSeek(String systemPrompt, String userMessage, double temperature, int maxTokens)
            throws Exception {
        return callDeepSeek(systemPrompt, userMessage, temperature, maxTokens, null);
    }

    private String callDeepSeek(String systemPrompt, String userMessage, double temperature, int maxTokens,
                                Long structureId) throws Exception {
        long start = System.currentTimeMillis();
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

        boolean success = true;
        String responseBody = null;
        try {
            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            responseBody = resp.body();
            if (resp.statusCode() != 200) {
                success = false;
                log.error("DeepSeek API error {}: {}", resp.statusCode(), responseBody);
                throw new RuntimeException("AI 服务返回错误");
            }
            var node = objectMapper.readTree(responseBody);
            return node.get("choices").get(0).get("message").get("content").asText().strip();
        } finally {
            long duration = System.currentTimeMillis() - start;
            generationLogService.logNonStreaming(responseBody, "deepseek-chat", duration,
                structureId, systemPrompt + "\n\n" + userMessage, success);
        }
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

    private void assertOwnership(Long bookId) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (!bookService.existsById(bookId)) {
            throw new BusinessException(404, "书籍不存在");
        }
        if (!bookService.isOwner(bookId, userId)) {
            throw new BusinessException(403, "无权访问此书");
        }
    }

    // ── 0. 生成状态检查 ──
    @GetMapping("/status/{bookId}")
    public ResponseEntity<?> generationStatus(@PathVariable Long bookId) {
        assertOwnership(bookId);
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
            assertOwnership(bookId);
            Book book = bookService.getById(bookId).orElse(null);
            String bookTitle = book != null ? book.getTitle() : "";
            String bookIdea = book != null && book.getCoreIdea() != null ? book.getCoreIdea() : "";
            String extra = req != null ? req.getOrDefault("hint", "") : "";
            String depthLevel = req != null ? req.getOrDefault("depth_level", "") : "";

            String systemPrompt = promptConfig.get("random-character", FALLBACK_CHARACTER);

            StringBuilder userMsg = new StringBuilder("请生成一个角色。");
            if (!depthLevel.isBlank()) {
                String levelDesc = switch (depthLevel) {
                    case "L3" -> "主角（L3）— 故事核心人物，有完整人物弧光和深层动机";
                    case "L2" -> "重要配角（L2）— 与主角有长期互动，有独立动机";
                    case "L1" -> "配角（L1）— 功能性角色，推动单一情节";
                    case "L0" -> "背景板（L0）— 路人角色，只需名字和身份";
                    default -> depthLevel;
                };
                userMsg.append("\n必须生成 ").append(levelDesc);
            }
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
            assertOwnership(bookId);
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
            assertOwnership(bookId);
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

    // ── 5. 一句话扩写大纲 ──
    @PostMapping("/outline/expand/{bookId}")
    public ResponseEntity<?> expandOutline(@PathVariable Long bookId,
                                           @RequestBody(required = false) Map<String, String> req) {
        try {
            assertOwnership(bookId);
            Book book = bookService.getById(bookId).orElse(null);
            String bookTitle = book != null ? book.getTitle() : "";
            String oneSentence = req != null ? req.getOrDefault("one_sentence", "") : "";
            if (oneSentence.isBlank() && book != null && book.getOneSentence() != null) {
                oneSentence = book.getOneSentence();
            }
            if (oneSentence.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "请先填写一句话梗概"));
            }

            String systemPrompt = promptConfig.get("random-outline-expand", FALLBACK_OUTLINE_EXPAND);

            StringBuilder userMsg = new StringBuilder("请根据以下梗概生成大纲：\n").append(oneSentence);
            if (!bookTitle.isBlank()) userMsg.append("\n书名：《").append(bookTitle).append("》");
            if (book != null && book.getCoreIdea() != null && !book.getCoreIdea().isBlank()) {
                userMsg.append("\n核心创意：").append(book.getCoreIdea());
            }

            String result = callDeepSeek(systemPrompt, userMsg.toString(), 1.2, 1500);
            String json = extractJson(result);
            return ResponseEntity.ok(objectMapper.readTree(json));
        } catch (Exception e) {
            log.error("Outline expand failed", e);
            return ResponseEntity.status(500).body(Map.of("message", "生成失败: " + e.getMessage()));
        }
    }

    // ── Character fission ──

    private static final String FALLBACK_FISSION = """
        你是一个小说角色生成器。根据一个已有角色，衍生生成一个全新的角色。
        相似度含义：0%=完全不同的新角色，100%=保留核心设定但做细微变化。

        你必须严格按照 JSON 格式输出，不要输出任何其他内容，不要用 markdown 代码块包裹：
        {"name":"角色名（2-4字中文名）","gender":"男或女","age_description":"年龄段描述","appearance":"外貌描写（40-80字）","origin":"出身背景（40-80字）","identity":"当前身份（20-50字）","depth_level":"L2"}

        生成规则：
        1. 新角色不能和原角色同名
        2. 根据相似度保留或变换以下维度：
           - 性别/年龄：相似度高时保持性别，低时可变换
           - 性格/身份：相似度高时身份同类型，低时可完全不同
           - 出身/背景：相似度高时背景相近，低时可来自不同地域/阶层
           - 外貌：相似度高时保留部分特征，低时完全不同
        3. 角色要有特色，避免脸谱化
        4. depth_level 可选值：L0（背景板）、L1（配角）、L2（重要配角）、L3（主角）
        """;

    @PostMapping("/character-fission/{characterId}")
    public ResponseEntity<?> characterFission(@PathVariable Long characterId,
                                               @RequestBody(required = false) Map<String, String> req) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            var character = characterService.getById(characterId).orElse(null);
            if (character == null) {
                return ResponseEntity.status(404).body(Map.of("message", "角色不存在"));
            }

            Long userId = SecurityUtil.getCurrentUserId();
            if (character.getBook() == null || character.getBook().getOwner() == null
                    || !character.getBook().getOwner().getId().equals(userId)) {
                throw new BusinessException(403, "无权访问此角色");
            }

            int similarity = 50;
            String hint = "";
            if (req != null) {
                if (req.containsKey("similarity")) {
                    try { similarity = Integer.parseInt(req.get("similarity")); } catch (NumberFormatException e) { /* use default */ }
                    similarity = Math.max(0, Math.min(100, similarity));
                }
                hint = req.getOrDefault("hint", "");
            }

            String systemPrompt = promptConfig.get("character-fission", FALLBACK_FISSION);

            StringBuilder userMsg = new StringBuilder();
            userMsg.append("请根据以下角色生成一个相似度为 ").append(similarity).append("% 的新角色：\n\n");
            userMsg.append("【原角色信息】\n");
            userMsg.append("姓名：").append(character.getName()).append("\n");
            if (character.getGender() != null) userMsg.append("性别：").append(character.getGender()).append("\n");
            if (character.getAgeDescription() != null) userMsg.append("年龄：").append(character.getAgeDescription()).append("\n");
            if (character.getIdentity() != null) userMsg.append("身份：").append(character.getIdentity()).append("\n");
            if (character.getOrigin() != null) userMsg.append("出身：").append(character.getOrigin()).append("\n");
            if (character.getAppearance() != null) userMsg.append("外貌：").append(character.getAppearance()).append("\n");
            if (character.getRace() != null) userMsg.append("种族：").append(character.getRace()).append("\n");
            if (character.getDepthLevel() != null) userMsg.append("深度等级：").append(character.getDepthLevel().name()).append("\n");

            if (!hint.isBlank()) {
                userMsg.append("\n额外要求：").append(hint);
            }

            String result = callDeepSeek(systemPrompt, userMsg.toString(), 1.0, 600, null);
            String json = extractJson(result);
            success = true;
            return ResponseEntity.ok(objectMapper.readTree(json));
        } catch (Exception e) {
            log.error("Character fission failed", e);
            return ResponseEntity.status(500).body(Map.of("message", "裂变失败: " + e.getMessage()));
        } finally {
            generationLogService.logNonStreaming(null, "deepseek-chat",
                System.currentTimeMillis() - start, null,
                "character-fission: characterId=" + characterId, success);
        }
    }
}
