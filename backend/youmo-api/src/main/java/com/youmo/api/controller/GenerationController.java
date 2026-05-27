package com.youmo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.api.config.PromptConfig;
import com.youmo.api.dto.request.ContinueRequest;
import com.youmo.api.dto.request.RewriteRequest;
import com.youmo.api.dto.request.SuggestRequest;
import com.youmo.api.dto.response.SuggestResponse;
import com.youmo.api.security.SecurityUtil;
import com.youmo.common.base.ApiResponse;
import com.youmo.api.service.ConsistencyCheckServiceImpl;
import com.youmo.core.repository.CharacterDetailRepository;
import com.youmo.core.service.ChapterContentService;
import com.youmo.core.service.CharacterService;
import com.youmo.core.service.ConsistencyCheckService;
import com.youmo.core.service.GenerationLogService;
import com.youmo.core.service.PromptAssemblyService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/generation")
public class GenerationController {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final PromptAssemblyService promptAssemblyService;
    private final PromptConfig promptConfig;
    private final ChapterContentService chapterContentService;
    private final ConsistencyCheckService consistencyCheckService;
    private final CharacterService characterService;
    private final CharacterDetailRepository characterDetailRepository;
    private final GenerationLogService generationLogService;
    private final Executor executor;

    private static final String FALLBACK_CONTINUE = """
        你是一个专业的小说续写助手。根据提供的上下文续写下一段内容。
        核心要求：保持文风一致、多写动作和对话、在关键处截断不留收束句。
        只输出续写内容，不输出解释或评价。
        """;

    private static final String FALLBACK_POLISH = """
        你是一个资深小说编辑。润色以下段落，修正语法错误、提升文采。
        严禁改变原意和人设。只输出润色后的全文。
        """;

    private static final String FALLBACK_EXPAND = """
        你是一个专业小说扩写助手。扩写以下段落，丰富场景细节和人物互动。
        篇幅约为原文2-3倍。保持人设和剧情。只输出扩写后的全文。
        """;

    private static final String FALLBACK_SUMMARIZE = """
        你是一个专业小说精炼助手。缩写以下段落，保留核心情节和关键对话。
        篇幅约为原文一半。只输出缩写后的全文。
        """;

    private static final String FALLBACK_FIX = """
        你是一个小说校对助手。修正以下段落中的语法错误、错别字、标点符号问题。
        严禁改变原意和人设。保持原文风格和句式结构。只输出修正后的全文。
        """;

    private static final String PLAN_PROMPT = """
        你是一个专业的小说写作规划师。根据前文内容，规划下一步的写作方向。
        要求：
        - 分析当前剧情走向，提出1-3个关键事件
        - 列出涉及的角色及其行动动机
        - 标注情感节奏（紧张/舒缓/高潮/过渡）
        - 简洁明确，控制在200字以内

        返回JSON，不要输出其他内容：
        {"plan":"写作计划描述","characters":[{"name":"角色名","action":"行动描述"}],"events":["关键事件1","关键事件2"],"emotion_arc":"情感节奏标签"}
        """;

    private static final String FALLBACK_EXECUTE = """
        你是一个专业的小说续写助手。严格按照批准的写作计划续写正文。
        核心要求：严格遵循计划中的剧情走向和角色行动、保持文风一致、多写动作和对话、在关键处截断不留收束句。
        只输出续写内容，不输出解释或评价。
        """;

    private static final String CONSISTENCY_PROMPT = """
        你是一个小说一致性检查器。分析生成的新内容与原有前文之间是否存在矛盾。

        请检查以下方面：
        1. 角色：姓名、身份、关系是否与前文一致
        2. 地点：场景位置是否与前文连贯
        3. 物品/设定：关键物品或设定的描述是否前后矛盾

        只报告确实存在的矛盾，不报告合理的剧情发展。
        如果无矛盾，返回 {"issues":[]}

        返回JSON格式：
        {"issues":[{"entity":"实体名","description":"矛盾描述","severity":"high|medium"}]}
        只返回JSON，不输出其他内容。
        """;

    public GenerationController(PromptAssemblyService promptAssemblyService,
                                PromptConfig promptConfig,
                                ChapterContentService chapterContentService,
                                ConsistencyCheckService consistencyCheckService,
                                CharacterService characterService,
                                CharacterDetailRepository characterDetailRepository,
                                GenerationLogService generationLogService,
                                @Qualifier("aiTaskExecutor") Executor executor) {
        this.promptAssemblyService = promptAssemblyService;
        this.promptConfig = promptConfig;
        this.chapterContentService = chapterContentService;
        this.consistencyCheckService = consistencyCheckService;
        this.characterService = characterService;
        this.characterDetailRepository = characterDetailRepository;
        this.generationLogService = generationLogService;
        this.executor = executor;
    }

    @GetMapping("/stream-buffer/{structureId}")
    public Map<String, String> getStreamBuffer(@PathVariable Long structureId) {
        String buffer = chapterContentService.getStreamBuffer(structureId);
        return Map.of("buffer", buffer != null ? buffer : "");
    }

    @PostMapping(value = "/continue", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter continueWriting(@RequestBody ContinueRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.execute(() -> {
            try {
                String fullContext = req.getContext();
                if (fullContext == null || fullContext.isBlank()) {
                    sendError(emitter, "前文内容不能为空");
                    return;
                }

                String systemPrompt;
                if (req.getBookId() != null) {
                    systemPrompt = promptAssemblyService.buildContinuePrompt(req.getBookId(),
                        promptConfig.get("assemble", FALLBACK_CONTINUE), fullContext);
                } else {
                    systemPrompt = promptConfig.get("continue", FALLBACK_CONTINUE);
                }

                String context = fullContext.length() > 2000
                    ? fullContext.substring(fullContext.length() - 2000)
                    : fullContext;

                String userMsg = "以下是一段小说正文，请直接从断点处接着往下写：\n\n"
                    + "---前文开始---\n" + context + "\n---前文结束---\n\n"
                    + req.getInstructions();

                // Stream buffer for recovery on disconnect
                StringBuilder streamBuffer = new StringBuilder();
                Long sid = req.getStructureId();
                if (sid != null) {
                    chapterContentService.clearStreamBuffer(sid);
                }

                String generated = streamFromDeepSeek(emitter, systemPrompt, userMsg,
                    req.getTemperature() != null ? req.getTemperature() : 1.2,
                    req.getTopP() != null ? req.getTopP() : 0.95,
                    req.getFrequencyPenalty() != null ? req.getFrequencyPenalty() : 0.3,
                    req.getPresencePenalty() != null ? req.getPresencePenalty() : 0.2,
                    req.getMaxTokens() != null ? req.getMaxTokens() : 800,
                    chunk -> {
                        streamBuffer.append(chunk);
                        if (sid != null && streamBuffer.length() % 200 == 0) {
                            safeUpdateBuffer(sid, streamBuffer.toString());
                        }
                    });

                // Save final buffer content and clear
                if (sid != null) {
                    safeUpdateBuffer(sid, streamBuffer.toString());
                    chapterContentService.clearStreamBuffer(sid);
                }

                // Consistency check after generation (5 types, parallel)
                if (!generated.isBlank() && generated.length() > 100) {
                    try {
                        var report = consistencyCheckService.checkAll(
                            truncate(fullContext, 3000), generated);
                        emitter.send(SseEmitter.event().name("consistency")
                            .data(objectMapper.writeValueAsString(report)));
                    } catch (Exception e) {
                        log.warn("Consistency check failed (non-blocking): {}", e.getMessage());
                    }
                }

                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                // On error, the buffer stays for recovery
                handleException(emitter, e);
            }
        });

        return emitter;
    }

    private void safeUpdateBuffer(Long structureId, String buffer) {
        try {
            chapterContentService.updateStreamBuffer(structureId, buffer);
        } catch (Exception e) {
            log.debug("Failed to update stream buffer: {}", e.getMessage());
        }
    }

    // ── Plan-then-Execute: Step 1 — generate writing plan (non-streaming) ──
    @PostMapping(value = "/continue-plan", produces = "application/json;charset=UTF-8")
    public ApiResponse<Map<String, Object>> continuePlan(@RequestBody ContinueRequest req) {
        long start = System.currentTimeMillis();
        String responseBody = null;
        boolean success = false;
        try {
            String context = req.getContext();
            if (context == null || context.isBlank()) {
                return ApiResponse.fail(400, "前文内容不能为空");
            }

            String systemPrompt = promptConfig.get("plan", PLAN_PROMPT);
            String truncated = context.length() > 2000
                ? context.substring(context.length() - 2000) : context;

            String userMsg = "前文：\n" + truncated + "\n\n请规划下一步写作方向。";

            Map<String, Object> body = Map.of(
                "model", "deepseek-chat",
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMsg)
                ),
                "temperature", 0.7,
                "max_tokens", 400,
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
            responseBody = resp.body();

            if (resp.statusCode() != 200) {
                log.error("Plan API error {}: {}", resp.statusCode(), responseBody);
                return ApiResponse.fail(500, "AI 服务返回错误");
            }

            var root = objectMapper.readTree(responseBody);
            var choices = root.get("choices");
            if (choices == null || choices.size() == 0) {
                return ApiResponse.fail(500, "AI 服务未返回结果");
            }

            String content = choices.get(0).get("message").get("content").asText();
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(content, Map.class);
            success = true;
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("Continue plan failed", e);
            return ApiResponse.fail(500, "生成计划失败: " + e.getMessage());
        } finally {
            generationLogService.logNonStreaming(responseBody, "deepseek-chat",
                System.currentTimeMillis() - start, req.getStructureId(),
                "continue-plan", success);
        }
    }

    // ── Plan-then-Execute: Step 2 — execute approved plan (SSE streaming) ──
    @PostMapping(value = "/continue-execute", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter continueExecute(@RequestBody ContinueRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.execute(() -> {
            try {
                String fullContext = req.getContext();
                if (fullContext == null || fullContext.isBlank()) {
                    sendError(emitter, "前文内容不能为空");
                    return;
                }

                String plan = req.getPlan();
                if (plan == null || plan.isBlank()) {
                    sendError(emitter, "写作计划不能为空");
                    return;
                }

                String systemPrompt;
                if (req.getBookId() != null) {
                    systemPrompt = promptAssemblyService.buildContinuePrompt(req.getBookId(),
                        promptConfig.get("assemble", FALLBACK_EXECUTE), fullContext);
                } else {
                    systemPrompt = promptConfig.get("continue-plan-execute", FALLBACK_EXECUTE);
                }

                // Inject the approved plan as writing guide
                systemPrompt += "\n\n【批准的写作计划——严格遵循】\n" + plan + "\n";

                String context = fullContext.length() > 2000
                    ? fullContext.substring(fullContext.length() - 2000)
                    : fullContext;

                String userMsg = "以下是一段小说正文，请按批准的写作计划接着往下写：\n\n"
                    + "---前文开始---\n" + context + "\n---前文结束---\n\n"
                    + req.getInstructions();

                // Stream buffer for recovery on disconnect
                StringBuilder streamBuffer = new StringBuilder();
                Long sid = req.getStructureId();
                if (sid != null) {
                    chapterContentService.clearStreamBuffer(sid);
                }

                String generated = streamFromDeepSeek(emitter, systemPrompt, userMsg,
                    req.getTemperature() != null ? req.getTemperature() : 1.2,
                    req.getTopP() != null ? req.getTopP() : 0.95,
                    req.getFrequencyPenalty() != null ? req.getFrequencyPenalty() : 0.3,
                    req.getPresencePenalty() != null ? req.getPresencePenalty() : 0.2,
                    req.getMaxTokens() != null ? req.getMaxTokens() : 800,
                    chunk -> {
                        streamBuffer.append(chunk);
                        if (sid != null && streamBuffer.length() % 200 == 0) {
                            safeUpdateBuffer(sid, streamBuffer.toString());
                        }
                    });

                if (sid != null) {
                    safeUpdateBuffer(sid, streamBuffer.toString());
                    chapterContentService.clearStreamBuffer(sid);
                }

                if (!generated.isBlank() && generated.length() > 100) {
                    try {
                        var report = consistencyCheckService.checkAll(
                            truncate(fullContext, 3000), generated);
                        emitter.send(SseEmitter.event().name("consistency")
                            .data(objectMapper.writeValueAsString(report)));
                    } catch (Exception e) {
                        log.warn("Consistency check failed (non-blocking): {}", e.getMessage());
                    }
                }

                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                handleException(emitter, e);
            }
        });

        return emitter;
    }

    @PostMapping(value = "/rewrite", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter rewriteWriting(@RequestBody RewriteRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.execute(() -> {
            try {
                String context = req.getContext();
                if (context == null || context.isBlank()) {
                    sendError(emitter, "原文不能为空");
                    return;
                }
                if (context.length() > 3000) {
                    context = context.substring(0, 3000);
                }

                String mode = req.getMode() != null ? req.getMode() : "polish";
                String rewritePrompt = getRewritePrompt(mode);
                if (rewritePrompt == null) {
                    sendError(emitter, "不支持的改写模式: " + mode);
                    return;
                }

                String userMsg = "请对以下段落进行" + getModeLabel(mode) + "：\n\n"
                    + "---原文开始---\n" + context + "\n---原文结束---";

                streamFromDeepSeek(emitter, rewritePrompt, userMsg,
                    req.getTemperature() != null ? req.getTemperature() : 0.8,
                    1.0, 0.0, 0.0,
                    req.getMaxTokens() != null ? req.getMaxTokens() : 1200,
                    null);

                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                handleException(emitter, e);
            }
        });

        return emitter;
    }

    // ── Suggest (author review) ──

    private static final String FALLBACK_SUGGEST = """
        你是一个资深小说编辑。分析以下段落，逐段给出改进建议。
        要求：
        - 每段最多一条建议，只对有改进空间的段落提建议
        - 类型：polish=润色文采, expand=丰富细节, consistency=纠正矛盾
        - reason 用中文简短说明改进原因

        返回JSON数组，不要输出其他内容：
        [{"paragraphIndex":0,"suggested":"改进后段落…","reason":"改进原因","type":"polish"}]
        """;

    @PostMapping(value = "/suggest", produces = "application/json;charset=UTF-8")
    public ApiResponse<SuggestResponse> suggest(@RequestBody SuggestRequest req) {
        long start = System.currentTimeMillis();
        String responseBody = null;
        boolean success = false;
        try {
            if (req.getContext() == null || req.getContext().isBlank()) {
                return ApiResponse.fail(400, "原文不能为空");
            }

            String systemPrompt = promptConfig.get("suggest", FALLBACK_SUGGEST);

            // Split text into paragraphs by double-newline, filtering empties
            String[] paragraphs = req.getContext().split("\\n\\s*\\n");
            java.util.Map<Integer, String> paragraphMap = new java.util.LinkedHashMap<>();
            StringBuilder numbered = new StringBuilder();
            for (int i = 0; i < paragraphs.length; i++) {
                String p = paragraphs[i].strip();
                if (!p.isEmpty()) {
                    paragraphMap.put(i, p);
                    numbered.append("[").append(i).append("] ").append(p).append("\n\n");
                }
            }

            String userMsg = "请分析以下段落：\n\n" + numbered;

            Map<String, Object> body = Map.of(
                "model", "deepseek-chat",
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMsg)
                ),
                "temperature", 0.4,
                "max_tokens", 2000,
                "stream", false
            );

            String json = objectMapper.writeValueAsString(body);
            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = httpClient.send(httpReq,
                HttpResponse.BodyHandlers.ofString());
            responseBody = resp.body();

            if (resp.statusCode() != 200) {
                log.error("Suggest API error {}: {}", resp.statusCode(), responseBody);
                return ApiResponse.fail(500, "AI 服务返回错误");
            }

            var root = objectMapper.readTree(responseBody);
            var choices = root.get("choices");
            if (choices == null || choices.size() == 0) {
                return ApiResponse.fail(500, "AI 服务未返回结果");
            }

            String content = choices.get(0).get("message").get("content").asText();
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            List<SuggestResponse.SuggestionItem> items = objectMapper.readValue(content,
                objectMapper.getTypeFactory().constructCollectionType(List.class, SuggestResponse.SuggestionItem.class));
            // Fill in original text from paragraph map (AI doesn't output it to avoid JSON escaping issues)
            for (SuggestResponse.SuggestionItem item : items) {
                if (item.getOriginal() == null || item.getOriginal().isBlank()) {
                    String orig = paragraphMap.get(item.getParagraphIndex());
                    if (orig != null) item.setOriginal(orig);
                }
            }
            SuggestResponse result = new SuggestResponse();
            result.setSuggestions(items);
            success = true;
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("Suggest failed", e);
            return ApiResponse.fail(500, "审改失败: " + e.getMessage());
        } finally {
            generationLogService.logNonStreaming(responseBody, "deepseek-chat",
                System.currentTimeMillis() - start, req.getStructureId(),
                "suggest: " + (req.getContext() != null ? req.getContext().substring(0, Math.min(req.getContext().length(), 500)) : ""), success);
        }
    }

    // ── Character chat ──

    private static final String CHAT_SYSTEM_PROMPT = """
        你正在扮演一个小说角色。你必须以角色的身份、语气和知识范围来回复。
        说话风格、用词偏好、情感表达方式必须与角色设定完全一致。
        不要说出角色不应该知道的信息。不要说教，不要跳出角色。
        保持对话自然流畅，像真的在和一个角色交谈。
        """;

    @PostMapping(value = "/chat-character/{characterId}", produces = "application/json;charset=UTF-8")
    public Map<String, Object> chatCharacter(@PathVariable Long characterId,
                                              @RequestBody Map<String, Object> req) {
        long start = System.currentTimeMillis();
        String responseBody = null;
        boolean success = false;
        try {
            var character = characterService.getById(characterId).orElse(null);
            if (character == null) {
                return Map.of("reply", "", "error", "角色不存在");
            }

            Long userId = SecurityUtil.getCurrentUserId();
            if (character.getBook() == null || character.getBook().getOwner() == null
                    || !character.getBook().getOwner().getId().equals(userId)) {
                return Map.of("reply", "", "error", "无权访问此角色");
            }

            String userMessage = req.get("message") != null ? req.get("message").toString() : "";
            if (userMessage.isBlank()) {
                return Map.of("reply", "", "error", "消息不能为空");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, String>> history = req.get("history") instanceof List
                ? (List<Map<String, String>>) req.get("history") : List.of();

            // Build character profile
            StringBuilder charProfile = new StringBuilder();
            charProfile.append("角色名：").append(character.getName()).append("\n");
            if (character.getGender() != null && !character.getGender().isBlank())
                charProfile.append("性别：").append(character.getGender()).append("\n");
            if (character.getIdentity() != null && !character.getIdentity().isBlank())
                charProfile.append("身份：").append(character.getIdentity()).append("\n");
            if (character.getAppearance() != null && !character.getAppearance().isBlank())
                charProfile.append("外貌：").append(character.getAppearance()).append("\n");

            // Add personality from CharacterDetail
            var detailOpt = characterDetailRepository.findByCharacterId(characterId);
            detailOpt.ifPresent(d -> {
                if (d.getTalkativeness() != null && !d.getTalkativeness().isBlank())
                    charProfile.append("说话量：").append(d.getTalkativeness()).append("\n");
                if (d.getSentenceStyle() != null && !d.getSentenceStyle().isBlank())
                    charProfile.append("句式风格：").append(d.getSentenceStyle()).append("\n");
                if (d.getWordPreference() != null && !d.getWordPreference().isBlank())
                    charProfile.append("用词偏好：").append(d.getWordPreference()).append("\n");
                if (d.getEmotionExpression() != null && !d.getEmotionExpression().isBlank())
                    charProfile.append("情感表达：").append(d.getEmotionExpression()).append("\n");
                if (d.getActionStyle() != null && !d.getActionStyle().isBlank())
                    charProfile.append("行动风格：").append(d.getActionStyle()).append("\n");
                if (d.getCoreDesire() != null && !d.getCoreDesire().isBlank())
                    charProfile.append("核心欲望：").append(d.getCoreDesire()).append("\n");
                if (d.getDeepFear() != null && !d.getDeepFear().isBlank())
                    charProfile.append("深层恐惧：").append(d.getDeepFear()).append("\n");
            });

            String systemPrompt = CHAT_SYSTEM_PROMPT + "\n\n角色设定：\n" + charProfile;

            // Build messages array: system + history (last 10) + current
            List<Map<String, String>> messages = new java.util.ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            int historyStart = Math.max(0, history.size() - 10);
            for (int i = historyStart; i < history.size(); i++) {
                messages.add(history.get(i));
            }
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> body = Map.of(
                "model", "deepseek-chat",
                "messages", messages,
                "temperature", 0.9,
                "max_tokens", 500,
                "stream", false
            );

            String json = objectMapper.writeValueAsString(body);
            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = httpClient.send(httpReq,
                HttpResponse.BodyHandlers.ofString());
            responseBody = resp.body();

            if (resp.statusCode() != 200) {
                log.error("Chat API error {}: {}", resp.statusCode(), responseBody);
                return Map.of("reply", "", "error", "AI 服务错误");
            }

            var root = objectMapper.readTree(responseBody);
            var choices = root.get("choices");
            String reply = choices != null && choices.size() > 0
                ? choices.get(0).get("message").get("content").asText()
                : "";
            success = true;
            return Map.of("reply", reply);
        } catch (Exception e) {
            log.error("Character chat failed", e);
            return Map.of("reply", "", "error", "对话失败: " + e.getMessage());
        } finally {
            generationLogService.logNonStreaming(responseBody, "deepseek-chat",
                System.currentTimeMillis() - start, null,
                "chat-character: " + characterId, success);
        }
    }

    // ── Pre-retrieval: AI optimize user instructions ──

    @PostMapping(value = "/optimize-instructions", produces = "application/json;charset=UTF-8")
    public ApiResponse<Map<String, String>> optimizeInstructions(@RequestBody Map<String, String> body) {
        String draft = body.get("draft");
        String context = body.get("context");
        Long bookId = body.containsKey("book_id") ? Long.valueOf(body.get("book_id")) : null;

        if (draft == null || draft.isBlank()) {
            return ApiResponse.ok(Map.of("optimized", draft != null ? draft : ""));
        }

        String ctxSnippet = context != null && context.length() > 500
            ? context.substring(context.length() - 500) : (context != null ? context : "");

        String systemPrompt = """
            你是一个写作助手的指令优化器。用户给了AI续写指令，你的任务是优化它：
            1. 补充不明确的细节（氛围、节奏、视角等）
            2. 保留用户原意，不要添加用户没提到的内容方向
            3. 用简洁的中文，不超过100字
            4. 直接输出优化后的指令，不要加"优化后："等前缀
            """;

        String userMsg = "当前前文片段：" + ctxSnippet + "\n\n用户指令：" + draft + "\n\n请输出优化后的指令：";

        try {
            Map<String, Object> reqBody = Map.of(
                "model", "deepseek-chat",
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMsg)
                ),
                "temperature", 0.5,
                "max_tokens", 150,
                "stream", false
            );

            String json = objectMapper.writeValueAsString(reqBody);
            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(15))
                .build();

            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                var root = objectMapper.readTree(resp.body());
                var choices = root.get("choices");
                if (choices != null && choices.size() > 0) {
                    String optimized = choices.get(0).get("message").get("content").asText().trim();
                    return ApiResponse.ok(Map.of("optimized", optimized));
                }
            }
            return ApiResponse.ok(Map.of("optimized", draft)); // fallback to original
        } catch (Exception e) {
            log.warn("Instruction optimization failed: {}", e.getMessage());
            return ApiResponse.ok(Map.of("optimized", draft)); // fallback
        }
    }

    // ── Consistency check ──

    private String checkConsistency(String beforeContext, String newContent) {
        String userMsg = "前文：\n" + beforeContext + "\n\n新生成内容：\n" + newContent;

        try {
            Map<String, Object> body = Map.of(
                "model", "deepseek-chat",
                "messages", List.of(
                    Map.of("role", "system", "content", CONSISTENCY_PROMPT),
                    Map.of("role", "user", "content", userMsg)
                ),
                "temperature", 0.1,
                "max_tokens", 400,
                "stream", false
            );

            String json = objectMapper.writeValueAsString(body);

            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = httpClient.send(httpReq,
                HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                var root = objectMapper.readTree(resp.body());
                var choices = root.get("choices");
                if (choices != null && choices.size() > 0) {
                    String content = choices.get(0).get("message").get("content").asText();
                    content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                    return content;
                }
            } else {
                log.warn("Consistency API error {}: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("Consistency check error: {}", e.getMessage());
        }
        return "{\"issues\":[]}";
    }

    // ── Streaming ──

    private String streamFromDeepSeek(SseEmitter emitter, String systemPrompt, String userMessage,
                                    double temperature, double topP, double freqPenalty,
                                    double presPenalty, int maxTokens,
                                    Consumer<String> onChunk) throws Exception {
        Map<String, Object> body = Map.of(
            "model", "deepseek-chat",
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ),
            "temperature", temperature,
            "top_p", topP,
            "frequency_penalty", freqPenalty,
            "presence_penalty", presPenalty,
            "max_tokens", maxTokens,
            "stream", true
        );

        String json = objectMapper.writeValueAsString(body);

        HttpRequest httpReq = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();

        HttpResponse<java.io.InputStream> resp = httpClient.send(httpReq,
            HttpResponse.BodyHandlers.ofInputStream());

        if (resp.statusCode() != 200) {
            String errBody = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
            log.error("DeepSeek API error {}: {}", resp.statusCode(), errBody);
            sendError(emitter, "AI 服务返回错误");
            return "";
        }

        StringBuilder fullText = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if ("[DONE]".equals(data)) break;
                    try {
                        var node = objectMapper.readTree(data);
                        var choices = node.get("choices");
                        if (choices != null && choices.size() > 0) {
                            var delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String chunk = delta.get("content").asText();
                                if (!chunk.isEmpty()) {
                                    fullText.append(chunk);
                                    emitter.send(SseEmitter.event().name("chunk").data(chunk));
                                    if (onChunk != null) onChunk.accept(chunk);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // skip malformed SSE chunks
                    }
                }
            }
        }
        return fullText.toString();
    }

    // ── Helpers ──

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) return text != null ? text : "";
        return text.substring(text.length() - maxChars);
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (Exception ignored) {}
        emitter.completeWithError(new RuntimeException(message));
    }

    private void handleException(SseEmitter emitter, Exception e) {
        log.error("Generation error", e);
        try {
            emitter.send(SseEmitter.event().name("error").data("生成失败: " + e.getMessage()));
        } catch (Exception ignored) {}
        emitter.completeWithError(e);
    }

    private String getRewritePrompt(String mode) {
        return switch (mode) {
            case "polish" -> promptConfig.get("polish", FALLBACK_POLISH);
            case "expand" -> promptConfig.get("expand", FALLBACK_EXPAND);
            case "summarize" -> promptConfig.get("summarize", FALLBACK_SUMMARIZE);
            case "fix" -> promptConfig.get("fix", FALLBACK_FIX);
            default -> null;
        };
    }

    private static String getModeLabel(String mode) {
        return switch (mode) {
            case "polish" -> "润色";
            case "expand" -> "扩写";
            case "summarize" -> "缩写";
            case "fix" -> "纠错";
            default -> "处理";
        };
    }
}
