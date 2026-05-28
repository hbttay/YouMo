package com.youmo.api.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WritingGuideResponse {
    private List<GuideItem> guides;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuideItem {
        private String dimension;
        private String issue;
        private String suggestion;
        private String severity;
    }
}
