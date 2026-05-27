package com.youmo.core.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youmo.core.service.DeepSeekEmbeddingService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeepSeekEmbeddingServiceImpl implements DeepSeekEmbeddingService {

    @Value("${embedding.api-key:}")
    private String apiKey;

    @Value("${embedding.base-url:http://127.0.0.1:5010}")
    private String baseUrl;

    @Value("${embedding.model:BAAI/bge-small-zh-v1.5}")
    private String embeddingModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    @Override
    public List<Float> embed(String text) {
        List<List<Float>> batch = embedBatch(Collections.singletonList(text));
        return batch.isEmpty() ? Collections.emptyList() : batch.get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        try {
            Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", texts
            );
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/embeddings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Embedding API returned {}: {}", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            Map<String, Object> result = objectMapper.readValue(response.body(),
                new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
            if (data == null) return Collections.emptyList();

            return data.stream()
                .sorted((a, b) -> {
                    Object ia = a.get("index");
                    Object ib = b.get("index");
                    if (ia instanceof Integer && ib instanceof Integer) {
                        return ((Integer) ia).compareTo((Integer) ib);
                    }
                    return 0;
                })
                .map(item -> {
                    @SuppressWarnings("unchecked")
                    List<Double> embedding = (List<Double>) item.get("embedding");
                    if (embedding == null) return Collections.<Float>emptyList();
                    return embedding.stream().map(Double::floatValue).toList();
                })
                .toList();

        } catch (Exception e) {
            log.error("Embedding API call failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public String vectorToString(List<Float> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(vector.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    public List<Float> stringToVector(String str) {
        if (str == null || str.isBlank()) return Collections.emptyList();
        String trimmed = str.replace("[", "").replace("]", "").trim();
        if (trimmed.isEmpty()) return Collections.emptyList();
        String[] parts = trimmed.split(",");
        return java.util.Arrays.stream(parts)
            .map(String::trim)
            .map(Float::parseFloat)
            .toList();
    }
}
