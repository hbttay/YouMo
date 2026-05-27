package com.youmo.api.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class SuggestResponse {
    private List<SuggestionItem> suggestions;

    @Data
    public static class SuggestionItem {
        private int paragraphIndex;
        private String original;
        private String suggested;
        private String reason;
        private String type; // "polish" | "expand" | "consistency"
    }
}
