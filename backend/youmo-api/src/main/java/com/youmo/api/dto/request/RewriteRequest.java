package com.youmo.api.dto.request;

import lombok.Data;

@Data
public class RewriteRequest {
    private String context;
    private String mode = "polish"; // polish | expand | summarize | fix-consistency
    private String instructions;
    private Double temperature = 1.0;
    private Integer maxTokens = 800;
}
