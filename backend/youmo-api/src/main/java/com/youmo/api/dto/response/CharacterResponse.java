package com.youmo.api.dto.response;

import com.youmo.common.entity.Character;
import lombok.Data;

@Data
public class CharacterResponse {
    private Long id;
    private String name;
    private String gender;
    private String ageDescription;
    private String appearance;
    private String origin;
    private String identity;
    private String depthLevel;

    public static CharacterResponse from(Character c) {
        CharacterResponse r = new CharacterResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setGender(c.getGender());
        r.setAgeDescription(c.getAgeDescription());
        r.setAppearance(c.getAppearance());
        r.setOrigin(c.getOrigin());
        r.setIdentity(c.getIdentity());
        r.setDepthLevel(c.getDepthLevel().name());
        return r;
    }
}
