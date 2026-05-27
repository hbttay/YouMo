package com.youmo.core.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.Feedback;
import com.youmo.common.enums.FeedbackCategory;
import com.youmo.common.enums.FeedbackSeverity;
import com.youmo.common.enums.FeedbackStatus;
import com.youmo.core.repository.FeedbackRepository;
import com.youmo.core.service.FeedbackService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class FeedbackServiceImpl implements FeedbackService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    private final FeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static final String ANALYZE_PROMPT = """
        你是一个用户反馈分析专家。请分析以下用户反馈，按JSON格式输出分析结果：
        {
          "category": "BUG|FEATURE_REQUEST|UX|PERFORMANCE|CONTENT_QUALITY|OTHER",
          "severity": "LOW|MEDIUM|HIGH|CRITICAL",
          "escalate_to_tech": true/false,
          "summary": "一句话总结（中文）"
        }

        分类标准：
        - BUG：功能异常、报错、数据丢失
        - FEATURE_REQUEST：希望增加新功能或改进
        - UX：界面交互体验问题
        - PERFORMANCE：响应慢、卡顿
        - CONTENT_QUALITY：AI生成内容质量问题
        - OTHER：其他

        严重等级：
        - LOW：小建议，不影响使用
        - MEDIUM：影响体验但不阻塞
        - HIGH：功能受阻或数据异常
        - CRITICAL：系统崩溃、安全漏洞

        技术团队升级（escalate_to_tech）规则：
        - true：BUG、PERFORMANCE类型，或严重等级为HIGH/CRITICAL
        - false：FEATURE_REQUEST、UX、CONTENT_QUALITY且等级为LOW/MEDIUM

        只输出JSON，不要其他内容。
        """;

    public FeedbackServiceImpl(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @Override
    @Transactional
    public Feedback create(Feedback feedback) {
        feedback.setStatus(FeedbackStatus.PENDING);
        return feedbackRepository.save(feedback);
    }

    @Override
    public Feedback getById(Long id) {
        return feedbackRepository.findById(id)
            .orElseThrow(() -> new BusinessException(404, "反馈不存在"));
    }

    @Override
    public List<Feedback> listAll(String status, String category, String severity, Boolean escalateToTech) {
        if (status != null && !status.isBlank()) {
            return feedbackRepository.findByStatusOrderByCreatedAtDesc(
                FeedbackStatus.valueOf(status.toUpperCase()));
        }
        if (category != null && !category.isBlank()) {
            return feedbackRepository.findByCategoryOrderByCreatedAtDesc(
                FeedbackCategory.valueOf(category.toUpperCase()));
        }
        if (severity != null && !severity.isBlank()) {
            return feedbackRepository.findBySeverityOrderByCreatedAtDesc(
                FeedbackSeverity.valueOf(severity.toUpperCase()));
        }
        if (Boolean.TRUE.equals(escalateToTech)) {
            return feedbackRepository.findByEscalateToTechTrueOrderByCreatedAtDesc();
        }
        return feedbackRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public Feedback updateStatus(Long id, String status) {
        Feedback feedback = getById(id);
        feedback.setStatus(FeedbackStatus.valueOf(status.toUpperCase()));
        return feedbackRepository.save(feedback);
    }

    @Override
    @Transactional
    public Feedback analyze(Long id) {
        Feedback feedback = getById(id);
        if (feedback.getContent() == null || feedback.getContent().isBlank()) {
            throw new BusinessException(400, "反馈内容为空，无法分析");
        }

        try {
            String result = callDeepSeek(ANALYZE_PROMPT, "请分析以下用户反馈：\n" + feedback.getContent());
            String json = extractJson(result);
            JsonNode node = objectMapper.readTree(json);

            String category = node.has("category") ? node.get("category").asText().toUpperCase() : "OTHER";
            String severity = node.has("severity") ? node.get("severity").asText().toUpperCase() : "MEDIUM";
            boolean escalate = node.has("escalate_to_tech") && node.get("escalate_to_tech").asBoolean();

            feedback.setCategory(FeedbackCategory.valueOf(category));
            feedback.setSeverity(FeedbackSeverity.valueOf(severity));
            feedback.setEscalateToTech(escalate);
            feedback.setAiAnalysis(objectMapper.writeValueAsString(node));
            feedback.setStatus(FeedbackStatus.REVIEWED);

            return feedbackRepository.save(feedback);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 分析反馈失败 id={}", id, e);
            throw new BusinessException(500, "AI 分析失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Long> getStats() {
        List<Feedback> all = feedbackRepository.findAll();
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", (long) all.size());
        stats.put("pending", all.stream().filter(f -> f.getStatus() == FeedbackStatus.PENDING).count());
        stats.put("reviewed", all.stream().filter(f -> f.getStatus() == FeedbackStatus.REVIEWED).count());
        stats.put("escalated", all.stream().filter(f -> f.getStatus() == FeedbackStatus.ESCALATED).count());
        stats.put("resolved", all.stream().filter(f -> f.getStatus() == FeedbackStatus.RESOLVED).count());
        stats.put("needTech", all.stream().filter(f -> Boolean.TRUE.equals(f.getEscalateToTech())).count());
        return stats;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!feedbackRepository.existsById(id)) {
            throw new BusinessException(404, "反馈不存在");
        }
        feedbackRepository.deleteById(id);
    }

    // ── DeepSeek API call ──
    private String callDeepSeek(String systemPrompt, String userMessage) throws Exception {
        Map<String, Object> body = Map.of(
            "model", "deepseek-chat",
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ),
            "temperature", 0.3,
            "max_tokens", 300,
            "stream", false
        );

        String json = objectMapper.writeValueAsString(body);
        HttpRequest httpReq = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            log.error("DeepSeek API error {}: {}", resp.statusCode(), resp.body());
            throw new RuntimeException("AI 服务返回错误");
        }
        JsonNode node = objectMapper.readTree(resp.body());
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
}
