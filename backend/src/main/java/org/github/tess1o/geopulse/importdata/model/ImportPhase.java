package org.github.tess1o.geopulse.importdata.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ImportPhase {
    VALIDATING,
    IMPORTING,
    CLEARING_EXISTING_DATA,
    TIMELINE_GENERATION,
    COVERAGE_RECALCULATION,
    COMPLETED,
    FAILED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
