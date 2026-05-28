package com.youmo.api.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private List<SearchMatch> matches;
    private int totalMatches;
    private String query;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchMatch {
        private String type;       // chapter | character | world | outline
        private String title;      // chapter title / character name / setting label
        private String snippet;    // text around the match (max 120 chars)
        private Long structureId;  // for navigation (chapters)
        private String nodeType;   // CHAPTER | SCENE (for navigation)
        private Long characterId;  // for character matches
    }
}
