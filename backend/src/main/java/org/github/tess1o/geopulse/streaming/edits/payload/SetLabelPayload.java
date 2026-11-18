package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

/** The visit label of an existing stay. */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SetLabelPayload(String visitName) implements CorrectionPayload {
}
