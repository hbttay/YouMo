package com.youmo.core.service;

import com.youmo.common.entity.ChapterEmbedding;
import com.youmo.common.entity.ChapterSummary;
import java.util.List;

public interface ChapterEmbeddingService {

    ChapterEmbedding embedAndSave(ChapterSummary summary);

    List<ChapterEmbedding> findSimilar(Long bookId, String queryText, int limit);

    void deleteBySummaryId(Long summaryId);
}
