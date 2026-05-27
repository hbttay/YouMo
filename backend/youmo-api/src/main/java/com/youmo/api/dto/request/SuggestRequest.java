package com.youmo.api.dto.request;

import lombok.Data;

@Data
public class SuggestRequest {
    private Long bookId;
    private String context;
    private Long structureId;
}
