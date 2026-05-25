package com.youmo.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CharacterMode {
    FIXED("固定角色"),
    INSPIRED("灵感自由");

    private final String label;

    CharacterMode(String label) { this.label = label; }

    @JsonValue
    public String getValue() { return name(); }

    public String getLabel() { return label; }

    @JsonCreator
    public static CharacterMode from(String value) {
        if (value == null) return null;
        for (CharacterMode m : values()) {
            if (m.name().equalsIgnoreCase(value) || m.label.equals(value)) return m;
        }
        throw new IllegalArgumentException("Unknown character mode: " + value);
    }
}
