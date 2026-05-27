package com.youmo.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.core.service.ConsistencyCheckService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConsistencyCheckServiceImpl implements ConsistencyCheckService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Executor executor;

    public ConsistencyCheckServiceImpl(@Qualifier("aiTaskExecutor") Executor executor) {
        this.executor = executor;
    }

    private static final String CHAR_PROMPT = """
        你是一个角色一致性检查器。检查新生成内容中的角色是否与前文设定一致。
        关注：角色名称、性别、身份、性格特征、说话风格是否有矛盾。
        只报告确实存在的矛盾。如果无矛盾，返回 {"issues":[]}
        返回JSON：{"issues":[{"entity":"角色名","description":"矛盾描述","severity":"high|medium"}]}
        只返回JSON，不输出其他内容。
        """;

    private static final String TIMELINE_PROMPT = """
        你是一个时间线检查器。检查新生成内容中的时间线是否与前文连续、合理。
        关注：事件顺序是否颠倒、时间跨度是否矛盾、季节/昼夜是否一致。
        只报告确实存在的矛盾。如果无矛盾，返回 {"issues":[]}
        返回JSON：{"issues":[{"entity":"时间点/事件","description":"矛盾描述","severity":"high|medium"}]}
        只返回JSON，不输出其他内容。
        """;

    private static final String WORLD_PROMPT = """
        你是一个世界观检查器。检查新生成内容是否违反已建立的世界观设定。
        关注：修炼体系规则、地理/势力设定、物品/能力设定是否矛盾。
        只报告确实存在的矛盾。如果无矛盾，返回 {"issues":[]}
        返回JSON：{"issues":[{"entity":"设定名","description":"矛盾描述","severity":"high|medium"}]}
        只返回JSON，不输出其他内容。
        """;

    private static final String FORESHADOW_PROMPT = """
        你是一个伏笔检查器。检查新内容中是否出现了与前文伏笔冲突的情节。
        关注：前文埋下的线索是否被忽略、已揭示的信息是否被重新当作未知。
        只报告确实存在的矛盾。如果无矛盾，返回 {"issues":[]}
        返回JSON：{"issues":[{"entity":"伏笔/线索","description":"矛盾描述","severity":"high|medium"}]}
        只返回JSON，不输出其他内容。
        """;

    private static final String TONE_PROMPT = """
        你是一个文风检查器。检查新内容的文风基调是否与前文一致。
        关注：叙事语气突变、视角切换不一致、语言风格落差过大。
        只报告确实存在的矛盾。如果无矛盾，返回 {"issues":[]}
        返回JSON：{"issues":[{"entity":"文风要素","description":"矛盾描述","severity":"medium"}]}
        只返回JSON，不输出其他内容。
        """;

    @Override
    public ConsistencyReport checkAll(String beforeContext, String newContent) {
        String truncatedBefore = beforeContext.length() > 2000
            ? beforeContext.substring(beforeContext.length() - 2000) : beforeContext;
        String userMsg = "前文：\n" + truncatedBefore + "\n\n新生成内容：\n" + newContent;

        Map<String, String> prompts = Map.of(
            "character", CHAR_PROMPT,
            "timeline", TIMELINE_PROMPT,
            "world", WORLD_PROMPT,
            "foreshadowing", FORESHADOW_PROMPT,
            "tone", TONE_PROMPT
        );

        var futures = prompts.entrySet().stream()
            .map(e -> CompletableFuture.supplyAsync(() -> runCheck(e.getKey(), e.getValue(), userMsg), executor))
            .toList();

        List<ConsistencyIssue> charIssues = new ArrayList<>();
        List<ConsistencyIssue> timelineIssues = new ArrayList<>();
        List<ConsistencyIssue> worldIssues = new ArrayList<>();
        List<ConsistencyIssue> foreshadowIssues = new ArrayList<>();
        List<ConsistencyIssue> toneIssues = new ArrayList<>();

        String[] types = {"character", "timeline", "world", "foreshadowing", "tone"};
        for (int i = 0; i < futures.size(); i++) {
            try {
                Map.Entry<String, List<ConsistencyIssue>> entry = futures.get(i).get(15, TimeUnit.SECONDS);
                switch (entry.getKey()) {
                    case "character" -> charIssues = entry.getValue();
                    case "timeline" -> timelineIssues = entry.getValue();
                    case "world" -> worldIssues = entry.getValue();
                    case "foreshadowing" -> foreshadowIssues = entry.getValue();
                    case "tone" -> toneIssues = entry.getValue();
                }
            } catch (TimeoutException e) {
                futures.get(i).cancel(true);
                log.warn("Consistency check {} timed out", types[i]);
            } catch (Exception e) {
                log.warn("Consistency check {} failed: {}", types[i], e.getMessage());
            }
        }

        return new ConsistencyReport(charIssues, timelineIssues, worldIssues, foreshadowIssues, toneIssues);
    }

    private Map.Entry<String, List<ConsistencyIssue>> runCheck(String type, String systemPrompt, String userMsg) {
        try {
            Map<String, Object> body = Map.of(
                "model", "deepseek-chat",
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMsg)
                ),
                "temperature", 0.1,
                "max_tokens", 300,
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
                    var issuesNode = objectMapper.readTree(content).get("issues");
                    List<ConsistencyIssue> issues = new ArrayList<>();
                    if (issuesNode != null) {
                        for (var node : issuesNode) {
                            issues.add(new ConsistencyIssue(
                                node.has("entity") ? node.get("entity").asText() : "",
                                node.has("description") ? node.get("description").asText() : "",
                                node.has("severity") ? node.get("severity").asText() : "medium",
                                type
                            ));
                        }
                    }
                    return Map.entry(type, issues);
                }
            }
        } catch (Exception e) {
            log.warn("Consistency check {} failed: {}", type, e.getMessage());
        }
        return Map.entry(type, List.of());
    }
}
