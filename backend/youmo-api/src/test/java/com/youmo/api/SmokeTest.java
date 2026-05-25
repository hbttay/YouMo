package com.youmo.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.youmo.core.service.ContextKeywordExtractor;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * E2E smoke tests — keep under 200 lines.
 * Run with: mvn test -pl backend/youmo-api -Dspring.profiles.active=local
 */
class SmokeTest {

    // ── Test 1: ContextKeywordExtractor extracts Chinese keywords ──

    @Test
    void keywordExtractor_chineseText_returnsKeywords() {
        ContextKeywordExtractor extractor = new ContextKeywordExtractor();

        Set<String> keywords = extractor.extractKeywords(
            "少年叶凡持剑入修仙世界。他在青云山脉中修炼，"
                + "三年结丹步入金丹期，一剑破万法。");

        assertThat(keywords).isNotEmpty();
        // Greedy {2,4} regex extracts multi-char chunks: 叶凡 is inside "少年叶凡"
        assertThat(keywords.stream().anyMatch(k ->
            k.contains("叶凡") || k.contains("修仙") || k.contains("金丹"))).isTrue();
    }

    // ── Test 2: keyword extraction with empty text ──

    @Test
    void keywordExtractor_emptyText_returnsEmptySet() {
        ContextKeywordExtractor extractor = new ContextKeywordExtractor();
        assertThat(extractor.extractKeywords("")).isEmpty();
        assertThat(extractor.extractKeywords(null)).isEmpty();
    }

    // ── Test 3: keyword extraction deduplicates ──

    @Test
    void keywordExtractor_deduplicatesByFrequency() {
        ContextKeywordExtractor extractor = new ContextKeywordExtractor();

        Set<String> keywords = extractor.extractKeywords(
            "修炼修炼修炼，金丹金丹，剑法剑法，修炼金丹");

        assertThat(keywords).isNotEmpty();
        // Greedy {2,4} regex: "修炼修炼修炼" → ["修炼修炼", "修炼"]
        assertThat(keywords).contains("修炼");
        // "金丹" appears inside "金丹金丹" and "修炼金丹"
        assertThat(keywords.stream().anyMatch(k -> k.contains("金丹"))).isTrue();
    }
}
