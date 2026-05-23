package com.youmo.api.dto.request;

import lombok.Data;

@Data
public class CreateOutlineNodeRequest {
    private String title;
    private String nodeType;
    private Integer sequence;
    private Long parentId;
    private String writingGoal;
}
