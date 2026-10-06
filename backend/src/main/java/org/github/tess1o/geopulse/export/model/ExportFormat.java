package org.github.tess1o.geopulse.export.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ExportFormat {
    GEOPULSE,
    GPX,
    OWNTRACKS,
    GEOJSON,
    CSV;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
