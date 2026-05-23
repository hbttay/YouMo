package com.youmo.api.dto.request;

import com.youmo.common.enums.CharacterMode;
import com.youmo.common.enums.CreationMode;
import com.youmo.common.enums.LengthType;
import lombok.Data;

@Data
public class CreateBookRequest {
    private String title;
    private String coreIdea;
    private String theme;
    private String toneLabels;
    private String oneSentence;
    private String targetReaderProfile;
    private Short violenceLevel = 3;
    private Short romanceLevel = 1;
    private Short politicsLevel = 1;
    private Short civilityLevel = 5;
    private String targetLength;
    private CreationMode creationMode;
    private CharacterMode characterMode;
    private LengthType lengthType;
    private Integer estimatedWords;
    private String extraAttributes;
}
