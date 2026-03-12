package com.kw.readwith.config;

import java.util.Locale;

public enum ApiContractMode {
    PREPARE("prepare"),
    V2_ONLY("v2_only"),
    V2_ONLY_STRICT("v2_only_strict");

    private final String propertyValue;

    ApiContractMode(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String getPropertyValue() {
        return propertyValue;
    }

    public boolean blocksLegacyRoutes() {
        return this != PREPARE;
    }

    public boolean isStrict() {
        return this == V2_ONLY_STRICT;
    }

    public static ApiContractMode from(String value) {
        if (value == null || value.isBlank()) {
            return PREPARE;
        }

        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');

        return valueOf(normalized);
    }
}
