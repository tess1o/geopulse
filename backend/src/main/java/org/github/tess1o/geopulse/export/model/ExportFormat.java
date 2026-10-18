package org.github.tess1o.geopulse.export.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ExportFormat {
    GEOPULSE,
    GPX,
    OWNTRACKS,
    GEOJSON,
    CSV,
    /** Anonymized archive for reproducing a timeline issue; created with {@code POST /exports/debug} only. */
    DEBUG;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
