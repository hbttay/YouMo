package com.youmo.core.service;

import com.youmo.common.entity.BookStyleProfile;
import java.util.Map;

public interface StyleProfileService {

    BookStyleProfile getOrCreate(Long bookId);

    /** Run full analysis: Java metrics + AI style analysis using sample chapters */
    BookStyleProfile analyze(Long bookId, java.util.List<String> sampleTexts);
}
