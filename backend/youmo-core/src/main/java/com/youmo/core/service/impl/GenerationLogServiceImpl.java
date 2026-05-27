package com.youmo.core.service.impl;

import com.youmo.common.entity.GenerationLog;
import com.youmo.core.repository.ChapterStructureRepository;
import com.youmo.core.repository.GenerationLogRepository;
import com.youmo.core.service.GenerationLogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class GenerationLogServiceImpl implements GenerationLogService {

    private final GenerationLogRepository generationLogRepository;
    private final ChapterStructureRepository structureRepository;
    private final Executor executor;

    public GenerationLogServiceImpl(GenerationLogRepository generationLogRepository,
                                     ChapterStructureRepository structureRepository,
                                     @Qualifier("aiTaskExecutor") Executor executor) {
        this.generationLogRepository = generationLogRepository;
        this.structureRepository = structureRepository;
        this.executor = executor;
    }

    private static final BigDecimal INPUT_PRICE = new BigDecimal("0.000001");
    private static final BigDecimal OUTPUT_PRICE = new BigDecimal("0.000002");

    @Override
    @Transactional
    public GenerationLog log(GenerationLog log) {
        return generationLogRepository.save(log);
    }

    @Override
    public List<GenerationLog> listByStructureId(Long structureId) {
        return generationLogRepository.findByStructureId(structureId);
    }

    @Override
    public void logNonStreaming(String responseBody, String model, long durationMs,
                                 Long structureId, String promptSnapshot, boolean success) {
        CompletableFuture.runAsync(() -> {
            try {
                GenerationLog entry = new GenerationLog();
                entry.setModel(model != null ? model : "deepseek-chat");
                entry.setDurationMs((int) durationMs);
                entry.setSuccess(success);
                if (promptSnapshot != null) {
                    entry.setPromptSnapshot(promptSnapshot.length() > 2000
                        ? promptSnapshot.substring(0, 2000) : promptSnapshot);
                }
                if (structureId != null) {
                    structureRepository.findById(structureId).ifPresent(entry::setStructure);
                }
                if (success && responseBody != null) {
                    parseUsage(responseBody, entry);
                }
                generationLogRepository.save(entry);
            } catch (Exception e) {
                log.warn("Failed to write generation log: {}", e.getMessage());
            }
        }, executor);
    }

    private void parseUsage(String responseBody, GenerationLog entry) {
        try {
            int usageIdx = responseBody.indexOf("\"usage\"");
            if (usageIdx < 0) return;
            String section = responseBody.substring(usageIdx,
                Math.min(responseBody.length(), usageIdx + 200));
            entry.setInputTokens(extractInt(section, "prompt_tokens"));
            entry.setOutputTokens(extractInt(section, "completion_tokens"));
            if (entry.getInputTokens() != null && entry.getOutputTokens() != null) {
                entry.setCost(BigDecimal.ZERO
                    .add(INPUT_PRICE.multiply(BigDecimal.valueOf(entry.getInputTokens())))
                    .add(OUTPUT_PRICE.multiply(BigDecimal.valueOf(entry.getOutputTokens())))
                    .setScale(4, RoundingMode.HALF_UP));
            }
        } catch (Exception e) {
            log.warn("Failed to parse usage: {}", e.getMessage());
        }
    }

    private Integer extractInt(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(":", keyIdx);
        if (colonIdx < 0) return null;
        String substr = json.substring(colonIdx + 1).trim();
        StringBuilder num = new StringBuilder();
        for (char c : substr.toCharArray()) {
            if (Character.isDigit(c) || (num.length() == 0 && c == '-')) {
                num.append(c);
            } else if (num.length() > 0) {
                break;
            }
        }
        return num.length() > 0 ? Integer.parseInt(num.toString()) : null;
    }
}
