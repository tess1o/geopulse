package org.github.tess1o.geopulse.importdata.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ImportStatus {
    VALIDATING,
    PROCESSING,
    COMPLETED,
    FAILED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
