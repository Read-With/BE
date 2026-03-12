package com.kw.readwith.config;

import java.util.Locale;

public enum LocatorMode {
    OFF("off"),
    ON("on"),
    STRICT("strict");

    private final String propertyValue;

    LocatorMode(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String getPropertyValue() {
        return propertyValue;
    }

    public boolean isOff() {
        return this == OFF;
    }

    public boolean isStrict() {
        return this == STRICT;
    }

    public static LocatorMode from(String value) {
        if (value == null || value.isBlank()) {
            return ON;
        }

        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');

        return valueOf(normalized);
    }
}
