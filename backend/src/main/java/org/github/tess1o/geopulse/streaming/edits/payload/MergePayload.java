package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

/** Which neighbour an event is merged with. */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record MergePayload(MergeDirection direction) implements CorrectionPayload {

    public enum MergeDirection {
        PREVIOUS,
        NEXT
    }
}
