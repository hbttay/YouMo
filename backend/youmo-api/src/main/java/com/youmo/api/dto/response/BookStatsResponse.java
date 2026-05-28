package com.youmo.api.dto.response;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookStatsResponse {
    private int totalWords;
    private int volumeCount;
    private int chapterCount;
    private int completedChapters;
    private int characterCount;
    private Map<String, Integer> charactersByDepth;
    private Map<String, Integer> nodeStatusBreakdown;
    private Map<String, Integer> sourceBreakdown;
    private List<ChapterWordStat> chapterWordCounts;
    private List<VolumeWordStat> volumeWordCounts;
    private List<CharacterAppearanceStat> characterAppearances;
    private List<DailyWordStat> dailyActivity;

    @Data
    @AllArgsConstructor
    public static class ChapterWordStat {
        private String title;
        private int wordCount;
    }

    @Data
    @AllArgsConstructor
    public static class VolumeWordStat {
        private String title;
        private int wordCount;
        private int chapterCount;
        private int completedChapters;
    }

    @Data
    @AllArgsConstructor
    public static class CharacterAppearanceStat {
        private String name;
        private int chapterCount;
    }

    @Data
    @AllArgsConstructor
    public static class DailyWordStat {
        private String date;
        private int wordCount;
    }
}
