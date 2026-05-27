package com.youmo.api.dto.request;

import com.youmo.common.enums.DepthLevel;
import lombok.Data;

@Data
public class CreateCharacterRequest {
    private Long bookId;
    private String name;
    private String gender;
    private String ageDescription;
    private String appearance;
    private String origin;
    private String identity;
    private String race;
    private DepthLevel depthLevel = DepthLevel.L1;
    private String extraAttributes;
}
