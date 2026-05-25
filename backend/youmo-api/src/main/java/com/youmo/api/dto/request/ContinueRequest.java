package com.youmo.api.dto.request;

import lombok.Data;

@Data
public class ContinueRequest {
    private Long bookId;
    private String context;
    private String instructions = "续写下一段";
    private Double temperature = 1.2;
    private Double topP = 0.95;
    private Double frequencyPenalty = 0.3;
    private Double presencePenalty = 0.2;
    private Integer maxTokens = 800;
    private Long structureId;
}
