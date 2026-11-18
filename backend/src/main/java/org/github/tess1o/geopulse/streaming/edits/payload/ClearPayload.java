package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

/** How the time of a removed event is filled. */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClearPayload(RemovalResolution resolution) implements CorrectionPayload {

    public enum RemovalResolution {
        GAP,
        EXTEND_PREVIOUS,
        EXTEND_NEXT,
        MERGE,
        DISMISS
    }
}
