package com.youmo.api.dto.response;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BookStatsResponse {
    private int totalWords;
    private int volumeCount;
    private int chapterCount;
    private int completedChapters;
    private int characterCount;
    private Map<String, Integer> charactersByDepth;
    private List<ChapterWordStat> chapterWordCounts;

    @Data
    @AllArgsConstructor
    public static class ChapterWordStat {
        private String title;
        private int wordCount;
    }
}
