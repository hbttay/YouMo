package com.youmo.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LengthType {
    SHORT("短篇"),
    MEDIUM("中篇"),
    LONG("长篇");

    private final String label;

    LengthType(String label) { this.label = label; }

    @JsonValue
    public String getValue() { return name(); }

    public String getLabel() { return label; }

    @JsonCreator
    public static LengthType from(String value) {
        if (value == null) return null;
        for (LengthType m : values()) {
            if (m.name().equalsIgnoreCase(value) || m.label.equals(value)) return m;
        }
        throw new IllegalArgumentException("Unknown length type: " + value);
    }
}
