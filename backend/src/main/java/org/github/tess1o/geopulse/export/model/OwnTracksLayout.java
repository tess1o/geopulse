package org.github.tess1o.geopulse.export.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * JSON shape of an OwnTracks export.
 */
public enum OwnTracksLayout {
    /** {@code {"locations": [...]}}, as produced by OwnTracks Recorder's {@code ocat}. */
    OCAT("ocat"),
    /** A plain JSON array of location messages. */
    ARRAY("array");

    private final String value;

    OwnTracksLayout(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
