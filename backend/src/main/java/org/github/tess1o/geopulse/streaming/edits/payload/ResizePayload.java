package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;

/** New start and end of an existing event. */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResizePayload(Instant start, Instant end) implements CorrectionPayload {
}
