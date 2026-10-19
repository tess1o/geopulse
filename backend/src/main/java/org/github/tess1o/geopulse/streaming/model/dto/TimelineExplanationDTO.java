package org.github.tess1o.geopulse.streaming.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;

import java.util.List;
import java.util.Map;

/**
 * Why the timeline engine created a stay, trip or data gap as it is.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Why the timeline engine created this event as it is: how it starts and ends, and what was "
        + "decided on the way. Each reason has a code and the values it was based on.")
public record TimelineExplanationDTO(
        @Schema(description = "How the event starts, e.g. FIRST_POINT, AFTER_DATA_GAP, ARRIVAL, LEFT_STAY, SHORT_STOP.")
        Reason start,
        @Schema(description = "Decisions made while the event was open, e.g. CONFIRMED, MERGED.")
        List<Reason> notes,
        @Schema(description = "How the event ends, e.g. MOVED_AWAY, ARRIVAL, DATA_GAP, LAST_POINT.")
        Reason end) {

    public record Reason(String code, Map<String, Object> params) {
    }

    /** The DTO of a stored explanation, or null when there is none. */
    public static TimelineExplanationDTO from(Map<String, Object> stored) {
        TimelineExplanation explanation = TimelineExplanation.fromMap(stored);
        if (explanation == null || explanation.isEmpty()) {
            return null;
        }
        return new TimelineExplanationDTO(reason(explanation.getStart()),
                explanation.getNotes().stream().map(TimelineExplanationDTO::reason).toList(),
                reason(explanation.getEnd()));
    }

    private static Reason reason(TimelineReason reason) {
        return reason == null ? null : new Reason(reason.code(), reason.params());
    }
}
