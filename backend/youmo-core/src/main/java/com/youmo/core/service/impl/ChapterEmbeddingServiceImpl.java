package com.youmo.core.service.impl;

import com.youmo.common.entity.ChapterEmbedding;
import com.youmo.common.entity.ChapterSummary;
import com.youmo.core.repository.ChapterEmbeddingRepository;
import com.youmo.core.service.ChapterEmbeddingService;
import com.youmo.core.service.DeepSeekEmbeddingService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterEmbeddingServiceImpl implements ChapterEmbeddingService {

    private final ChapterEmbeddingRepository repository;
    private final DeepSeekEmbeddingService embeddingService;

    @Override
    public ChapterEmbedding embedAndSave(ChapterSummary summary) {
        if (repository.existsBySummaryId(summary.getId())) {
            log.debug("Embedding already exists for summary {}", summary.getId());
            return null;
        }

        String compressed = compressSummary(summary);
        List<Float> vector = embeddingService.embed(compressed);
        if (vector.isEmpty()) {
            log.warn("Failed to generate embedding for summary {}", summary.getId());
            return null;
        }

        // Dedup: skip if too similar to existing
        List<ChapterEmbedding> existing = repository.findByBookIdOrderByCreatedAtDesc(
            summary.getStructure().getBook().getId());
        for (ChapterEmbedding e : existing) {
            List<Float> ev = embeddingService.stringToVector(e.getEmbedding());
            if (!ev.isEmpty() && cosineSimilarity(vector, ev) > 0.95) {
                log.info("Skipping duplicate embedding for summary {} (similar to {})",
                    summary.getId(), e.getId());
                return null;
            }
        }

        ChapterEmbedding entity = new ChapterEmbedding();
        entity.setSummary(summary);
        entity.setBook(summary.getStructure().getBook());
        entity.setStructure(summary.getStructure());
        entity.setContentText(compressed);
        entity.setEmbedding(embeddingService.vectorToString(vector));
        entity.setCreatedAt(LocalDateTime.now());

        return repository.save(entity);
    }

    @Override
    public List<ChapterEmbedding> findSimilar(Long bookId, String queryText, int limit) {
        List<Float> queryVector = embeddingService.embed(queryText);
        if (queryVector.isEmpty()) return Collections.emptyList();
        return repository.findSimilarByBook(bookId,
            embeddingService.vectorToString(queryVector), limit);
    }

    @Override
    public void deleteBySummaryId(Long summaryId) {
        // find by summary_id via repository query
        repository.findAll().stream()
            .filter(e -> e.getSummary().getId().equals(summaryId))
            .findFirst()
            .ifPresent(repository::delete);
    }

    String compressSummary(ChapterSummary s) {
        StringBuilder sb = new StringBuilder();
        appendJsonField(sb, "核心事件", s.getCoreEvents());
        appendJsonField(sb, "出场角色", s.getAppearingCharacters());
        appendJsonField(sb, "角色状态变化", s.getCharacterStateChanges());
        appendJsonField(sb, "新伏笔", s.getNewForeshadowings());
        appendJsonField(sb, "关键场景", s.getKeyScenes());
        appendJsonField(sb, "世界观要素", s.getWorldElements());
        appendJsonField(sb, "情绪曲线", s.getEmotionCurvePoint());
        return sb.toString().trim();
    }

    private void appendJsonField(StringBuilder sb, String label, String json) {
        if (json == null || json.isBlank() || json.equals("[]") || json.equals("{}")) return;
        String plain = json
            .replace("\"", "")
            .replace("[", "")
            .replace("]", "")
            .replace("{", "")
            .replace("}", "")
            .replace("\\", "");
        if (plain.isBlank()) return;
        sb.append("[").append(label).append("] ").append(plain).append("\n");
    }

    private double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += (double) a.get(i) * b.get(i);
            normA += (double) a.get(i) * a.get(i);
            normB += (double) b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
