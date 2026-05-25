package com.youmo.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CreationMode {
    LINEAR("线性叙事"),
    DIVERGENT("分支叙事");

    private final String label;

    CreationMode(String label) { this.label = label; }

    @JsonValue
    public String getValue() { return name(); }

    public String getLabel() { return label; }

    @JsonCreator
    public static CreationMode from(String value) {
        if (value == null) return null;
        for (CreationMode m : values()) {
            if (m.name().equalsIgnoreCase(value) || m.label.equals(value)) return m;
        }
        throw new IllegalArgumentException("Unknown creation mode: " + value);
    }
}
