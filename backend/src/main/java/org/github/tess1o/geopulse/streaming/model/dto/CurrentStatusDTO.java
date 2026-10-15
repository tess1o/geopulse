package org.github.tess1o.geopulse.streaming.model.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The user's current activity, latest GPS point and today's totals. Only the object matching {@code state}
 * ({@code stay}, {@code trip} or {@code dataGap}) is set.
 */
public record CurrentStatusDTO(
        Instant generatedAt,
        @Schema(description = "The user's timezone; `today` uses it.") String timezone,
        CurrentActivityState state,
        StayStatus stay,
        TripStatus trip,
        DataGapStatus dataGap,
        LastPointStatus lastPoint,
        TodaySummary today
) {

    public record StayStatus(
            String placeName,
            @Schema(description = "Set when the place is one of the user's favorites.") Long favoriteId,
            Long geocodingId,
            @Schema(description = "`FAVORITE` or `GEOCODING`.") String locationSource,
            Instant since,
            @Schema(description = "Seconds since the stay started, counted to now.") long durationSeconds,
            double latitude,
            double longitude,
            String city,
            String country
    ) {
    }

    public record TripStatus(
            Instant since,
            @Schema(description = "Seconds since the trip started, counted to now.") long durationSeconds,
            @Schema(description = "Distance covered as of the last timeline update.") long distanceMeters,
            String movementType,
            double startLatitude,
            double startLongitude
    ) {
    }

    public record DataGapStatus(
            @Schema(description = "Time of the last GPS point before the gap.") Instant since,
            long durationSeconds
    ) {
    }

    public record LastPointStatus(
            Instant timestamp,
            @Schema(description = "When GeoPulse received the point.") Instant receivedAt,
            long ageSeconds,
            double latitude,
            double longitude,
            Double accuracy,
            Double battery,
            String sourceType
    ) {
    }

    public record TodaySummary(
            LocalDate date,
            @Schema(description = "Distance of trips that started today.") long distanceMeters,
            long tripCount,
            long stayCount,
            @Schema(description = "Distinct places of stays that started today.") long uniquePlaces,
            @Schema(description = "Duration of trips that started today.") long movingSeconds
    ) {
    }
}
