package com.youmo.api.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookReportResponse {
    private String overallAssessment;
    private List<VolumeAnalysis> volumeAnalyses;
    private CharacterArcAnalysis characterArcs;
    private ForeshadowingAnalysis foreshadowing;
    private List<WritingAdvice> topAdvice;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeAnalysis {
        private String title;
        private Integer wordCount;
        private String quality;
        private String pacing;
        private String highlight;
        private String issue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterArcAnalysis {
        private Integer totalCharacters;
        private List<CharacterArcItem> arcs;
        private String overallAssessment;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterArcItem {
        private String name;
        private String depthLevel;
        private Integer appearanceChapters;
        private String arcCompleteness;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForeshadowingAnalysis {
        private Integer totalPlanted;
        private Integer recycled;
        private String recoveryRate;
        private String assessment;
        private List<String> unrecycledItems;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WritingAdvice {
        private String category;
        private String suggestion;
        private String priority;
    }
}
