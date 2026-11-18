package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;

/** The movement type of an existing trip. */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SetTripTypePayload(TripType movementType) implements CorrectionPayload {
}
