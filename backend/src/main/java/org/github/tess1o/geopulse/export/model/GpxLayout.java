package org.github.tess1o.geopulse.export.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a GPX export is packaged.
 */
public enum GpxLayout {
    /** One GPX file with all trips and stays. */
    SINGLE("single"),
    /** A ZIP archive with one GPX file per trip or stay. */
    ZIP_PER_TRIP("zip-per-trip"),
    /** A ZIP archive with one GPX file per day. */
    ZIP_PER_DAY("zip-per-day");

    private final String value;

    GpxLayout(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
