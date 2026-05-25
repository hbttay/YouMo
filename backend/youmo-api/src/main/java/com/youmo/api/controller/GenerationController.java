package com.youmo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.api.config.PromptConfig;
import com.youmo.api.dto.request.ContinueRequest;
import com.youmo.api.dto.request.RewriteRequest;
import com.youmo.core.service.ChapterContentService;
import com.youmo.core.service.PromptAssemblyService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
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
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final PromptAssemblyService promptAssemblyService;
    private final PromptConfig promptConfig;
    private final ChapterContentService chapterContentService;

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
                                ChapterContentService chapterContentService) {
        this.promptAssemblyService = promptAssemblyService;
        this.promptConfig = promptConfig;
        this.chapterContentService = chapterContentService;
    }

    @GetMapping("/stream-buffer/{structureId}")
    public Map<String, String> getStreamBuffer(@PathVariable Long structureId) {
        String buffer = chapterContentService.getStreamBuffer(structureId);
        return Map.of("buffer", buffer != null ? buffer : "");
    }

    @PostMapping(value = "/continue", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter continueWriting(@RequestBody ContinueRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);

        new Thread(() -> {
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

                // Lightweight consistency check after generation
                if (!generated.isBlank() && generated.length() > 100) {
                    try {
                        String issues = checkConsistency(
                            truncate(fullContext, 3000), generated);
                        emitter.send(SseEmitter.event().name("consistency").data(issues));
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
        }).start();

        return emitter;
    }

    private void safeUpdateBuffer(Long structureId, String buffer) {
        try {
            chapterContentService.updateStreamBuffer(structureId, buffer);
        } catch (Exception e) {
            log.debug("Failed to update stream buffer: {}", e.getMessage());
        }
    }

    @PostMapping(value = "/rewrite", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter rewriteWriting(@RequestBody RewriteRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);

        new Thread(() -> {
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
        }).start();

        return emitter;
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
            default -> null;
        };
    }

    private static String getModeLabel(String mode) {
        return switch (mode) {
            case "polish" -> "润色";
            case "expand" -> "扩写";
            case "summarize" -> "缩写";
            default -> "处理";
        };
    }
}
