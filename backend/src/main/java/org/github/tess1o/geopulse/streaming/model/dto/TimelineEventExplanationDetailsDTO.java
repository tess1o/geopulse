package org.github.tess1o.geopulse.streaming.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * A stay, trip or data gap with the GPS points the timeline engine decided it from, for showing on a map why it is as
 * it is.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Why the timeline engine created a stay, trip or data gap as it is, with the GPS points of the "
        + "event and a few points before and after it. Each point says whether the engine used it and which decision "
        + "it was part of.")
public record TimelineEventExplanationDetailsDTO(
        @Schema(description = "STAY, TRIP or DATA_GAP.")
        String kind,
        Long id,
        Instant startTime,
        Instant endTime,
        @Schema(description = "The reasons the engine recorded when it created the event.")
        TimelineExplanationDTO explanation,
        @Schema(description = "Stay: the stored location. It is the location of a favorite when the stay matched one.")
        Coordinates location,
        @Schema(description = "Stay: the average of the points the engine used, which is the centre it measured "
                + "the stay radius from.")
        Coordinates centre,
        @Schema(description = "Stay: the stay radius in meters, from the current timeline settings.")
        Integer radiusMeters,
        @Schema(description = "Trip: where it starts.")
        Coordinates startPoint,
        @Schema(description = "Trip: where it ends.")
        Coordinates endPoint,
        @Schema(description = "The current values of the timeline settings that decide stays, trips and data gaps.")
        Settings settings,
        @Schema(description = "Settings whose value when the timeline was built differs from the current value. The "
                + "timeline follows the new value only after it is regenerated.")
        List<SettingChange> settingsChanged,
        @Schema(description = "The GPS points of the event and up to 10 points before and after it, oldest first.")
        List<Point> points,
        @Schema(description = "How many points were left out because they were less accurate than the GPS accuracy "
                + "threshold.")
        Filtered filtered,
        @Schema(description = "Filters of the user's active GPS sources that drop points before they are stored. "
                + "Points dropped this way are not in `points`.")
        List<IngestionFilter> ingestionFilters,
        @Schema(description = "True when the event has more points than are returned; ordinary used points were "
                + "thinned out, every left-out or deciding point is kept.")
        boolean truncated,
        @Schema(description = "How many stored points the event has.")
        int totalEventPoints) {

    @Schema(name = "TimelineExplanationCoordinates")
    public record Coordinates(double latitude, double longitude) {
    }

    @Schema(name = "TimelineExplanationSettings")
    public record Settings(
            Integer staypointRadiusMeters,
            Integer staypointMinDurationMinutes,
            @Schema(description = "The GPS accuracy threshold in meters, or null when points are not filtered by "
                    + "accuracy.")
            Double staypointMaxAccuracyThreshold,
            Double staypointVelocityThreshold,
            Integer tripArrivalDetectionMinDurationSeconds,
            Integer tripArrivalMinPoints,
            Integer dataGapThresholdSeconds,
            Integer dataGapMinDurationSeconds,
            Boolean isMergeEnabled,
            Integer mergeMaxDistanceMeters,
            Integer mergeMaxTimeGapMinutes) {
    }

    @Schema(name = "TimelineExplanationSettingChange")
    public record SettingChange(
            @Schema(description = "Setting name, e.g. staypointRadiusMeters.")
            String setting,
            Number builtWith,
            Number current) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "TimelineExplanationPoint")
    public record Point(
            Instant timestamp,
            double latitude,
            double longitude,
            @Schema(description = "Accuracy in meters, or null when the source did not send one.")
            Double accuracyMeters,
            Double speedKmh,
            @Schema(description = "BEFORE, EVENT or AFTER: whether the point is before, in or after the event.")
            String part,
            @Schema(description = "False when the point was left out for its accuracy.")
            boolean used,
            @Schema(description = "Stay: meters from the centre.")
            Double distanceFromCentreMeters,
            @Schema(description = "Stay: true when the point is farther from the centre than the stay radius.")
            Boolean outsideRadius,
            @Schema(description = "Codes of the reasons this point was part of, e.g. ARRIVAL for the points that "
                    + "made up the stop, MOVED_AWAY for the point that ended a stay.")
            List<String> reasons) {
    }

    @Schema(name = "TimelineExplanationFiltered")
    public record Filtered(
            @Schema(description = "Points in the event that were left out.")
            int inEvent,
            @Schema(description = "Points before and after the event that were left out.")
            int inContext,
            @Schema(description = "The GPS accuracy threshold in meters, or null when points are not filtered.")
            Double thresholdMeters) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "TimelineExplanationIngestionFilter")
    public record IngestionFilter(
            @Schema(description = "Source type, e.g. OWNTRACKS.")
            String sourceType,
            @Schema(description = "Username or device of the source, when it has one.")
            String name,
            @Schema(description = "Points less accurate than this, in meters, are not stored.")
            Integer maxAccuracyMeters,
            @Schema(description = "Points faster than this, in km/h, are not stored.")
            Integer maxSpeedKmh,
            @Schema(description = "Points that repeat the previous location within this many minutes are not stored.")
            Integer duplicateThresholdMinutes) {
    }
}
