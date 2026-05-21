package com.youmo.api.dto.request;

import lombok.Data;

@Data
public class MoveNodeRequest {

    private Long newParentId;
    private Integer newSequence;
}
