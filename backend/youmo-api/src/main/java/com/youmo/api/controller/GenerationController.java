package com.youmo.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.api.config.PromptConfig;
import com.youmo.api.dto.request.ContinueRequest;
import com.youmo.api.dto.request.RewriteRequest;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    // ── Fallback prompts (used when youmo-prompts/ directory is absent) ──
    // The tuned versions live in youmo-prompts/*.txt (not in public repo).

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

    public GenerationController(PromptAssemblyService promptAssemblyService,
                                PromptConfig promptConfig) {
        this.promptAssemblyService = promptAssemblyService;
        this.promptConfig = promptConfig;
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

                // Use dynamic prompt assembly when bookId is provided
                String systemPrompt;
                if (req.getBookId() != null) {
                    systemPrompt = promptAssemblyService.buildContinuePrompt(req.getBookId(),
                        promptConfig.get("assemble", FALLBACK_CONTINUE), fullContext);
                } else {
                    systemPrompt = promptConfig.get("continue", FALLBACK_CONTINUE);
                }

                // Truncate for DeepSeek API payload
                String context = fullContext.length() > 2000
                    ? fullContext.substring(fullContext.length() - 2000)
                    : fullContext;

                String userMsg = "以下是一段小说正文，请直接从断点处接着往下写：\n\n"
                    + "---前文开始---\n" + context + "\n---前文结束---\n\n"
                    + req.getInstructions();

                streamFromDeepSeek(emitter, systemPrompt, userMsg,
                    req.getTemperature() != null ? req.getTemperature() : 1.2,
                    req.getTopP() != null ? req.getTopP() : 0.95,
                    req.getFrequencyPenalty() != null ? req.getFrequencyPenalty() : 0.3,
                    req.getPresencePenalty() != null ? req.getPresencePenalty() : 0.2,
                    req.getMaxTokens() != null ? req.getMaxTokens() : 800);
            } catch (Exception e) {
                handleException(emitter, e);
            }
        }).start();

        return emitter;
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

                // rewrite uses lower temperature for fidelity
                streamFromDeepSeek(emitter, rewritePrompt, userMsg,
                    req.getTemperature() != null ? req.getTemperature() : 0.8,
                    1.0, 0.0, 0.0,
                    req.getMaxTokens() != null ? req.getMaxTokens() : 1200);
            } catch (Exception e) {
                handleException(emitter, e);
            }
        }).start();

        return emitter;
    }

    private void streamFromDeepSeek(SseEmitter emitter, String systemPrompt, String userMessage,
                                    double temperature, double topP, double freqPenalty,
                                    double presPenalty, int maxTokens) throws Exception {
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
            return;
        }

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
                                    emitter.send(SseEmitter.event().name("chunk").data(chunk));
                                }
                            }
                        }
                    } catch (Exception e) {
                        // skip malformed SSE chunks
                    }
                }
            }
        }
        emitter.send(SseEmitter.event().name("done").data(""));
        emitter.complete();
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
