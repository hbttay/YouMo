package com.youmo.core.service;

import com.youmo.common.entity.ChapterSummary;

public interface ChapterAnalysisService {

    /** Analyze chapter content via AI and return a populated ChapterSummary (not yet saved) */
    ChapterSummary analyze(Long bookId, Long structureId, String chapterContent);
}
