package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.model.dto.CurrentActivityState;
import org.github.tess1o.geopulse.streaming.model.dto.CurrentStatusDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Works out what a user is doing right now. The timeline stores no "in progress" marker and its tail is rebuilt
 * every few minutes, so the current item is derived on read: an ongoing data gap, otherwise whichever of the
 * latest stay and latest trip started last.
 */
@ApplicationScoped
public class CurrentActivityService {

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    StreamingTimelineConverter converter;

    /**
     * The current item. {@code stay}, {@code trip} or {@code gapStart} is set to match {@code state};
     * {@code stayLocationSource} is the stay's {@code FAVORITE}/{@code GEOCODING} source.
     */
    public record Snapshot(CurrentActivityState state, Instant since, TimelineStayLocationDTO stay,
                           String stayLocationSource, TimelineTripDTO trip, GpsPointEntity lastPoint) {
    }

    @Transactional
    public Snapshot resolve(UUID userId, Instant now) {
        Optional<GpsPointEntity> lastPoint = gpsPointRepository.findLatest(userId);
        if (lastPoint.isEmpty()) {
            return new Snapshot(CurrentActivityState.NO_DATA, null, null, null, null, null);
        }
        GpsPointEntity point = lastPoint.get();

        // Same rule as the timeline view: a gap is ongoing when it starts at the latest GPS point
        Optional<TimelineDataGapEntity> ongoingGap = dataGapRepository.findLatestByUserId(userId)
                .filter(gap -> gap.getStartTime() != null && gap.getStartTime().equals(point.getTimestamp()));
        if (ongoingGap.isPresent()) {
            return new Snapshot(CurrentActivityState.DATA_GAP, ongoingGap.get().getStartTime(), null, null, null, point);
        }

        Optional<TimelineStayEntity> stay = stayRepository.findLatestWithPlaceAtOrBefore(userId, now);
        Optional<TimelineTripEntity> trip = tripRepository.findLatestByUserIdAtOrBefore(userId, now);
        boolean tripIsLatest = trip.isPresent()
                && (stay.isEmpty() || trip.get().getTimestamp().isAfter(stay.get().getTimestamp()));
        if (tripIsLatest) {
            return new Snapshot(CurrentActivityState.TRIP, trip.get().getTimestamp(), null, null,
                    converter.convertTripEntityToDto(trip.get()), point);
        }
        if (stay.isPresent()) {
            TimelineStayEntity entity = stay.get();
            String source = entity.getLocationSource() != null ? entity.getLocationSource().name() : null;
            return new Snapshot(CurrentActivityState.STAY, entity.getTimestamp(),
                    converter.convertStayEntityToDto(entity), source, null, point);
        }
        return new Snapshot(CurrentActivityState.UNKNOWN, null, null, null, null, point);
    }

    @Transactional
    public CurrentStatusDTO getStatus(UUID userId, String timezone, Instant now) {
        ZoneId zone = resolveZone(timezone);
        Snapshot snapshot = resolve(userId, now);
        GpsPointEntity point = snapshot.lastPoint();

        CurrentStatusDTO.StayStatus stay = snapshot.stay() == null ? null : new CurrentStatusDTO.StayStatus(
                snapshot.stay().getLocationName(),
                snapshot.stay().getFavoriteId(),
                snapshot.stay().getGeocodingId(),
                snapshot.stayLocationSource(),
                snapshot.since(),
                secondsBetween(snapshot.since(), now),
                snapshot.stay().getLatitude(),
                snapshot.stay().getLongitude(),
                snapshot.stay().getCity(),
                snapshot.stay().getCountry());
        CurrentStatusDTO.TripStatus trip = snapshot.trip() == null ? null : new CurrentStatusDTO.TripStatus(
                snapshot.since(),
                secondsBetween(snapshot.since(), now),
                snapshot.trip().getDistanceMeters(),
                snapshot.trip().getMovementType(),
                snapshot.trip().getLatitude(),
                snapshot.trip().getLongitude());
        CurrentStatusDTO.DataGapStatus gap = snapshot.state() != CurrentActivityState.DATA_GAP ? null
                : new CurrentStatusDTO.DataGapStatus(snapshot.since(), secondsBetween(snapshot.since(), now));
        CurrentStatusDTO.LastPointStatus lastPoint = point == null ? null : new CurrentStatusDTO.LastPointStatus(
                point.getTimestamp(),
                point.getCreatedAt(),
                secondsBetween(point.getTimestamp(), now),
                point.getCoordinates().getY(),
                point.getCoordinates().getX(),
                point.getAccuracy(),
                point.getBattery(),
                point.getSourceType() != null ? point.getSourceType().name() : null);

        return new CurrentStatusDTO(now, zone.getId(), snapshot.state(), stay, trip, gap, lastPoint,
                todaySummary(userId, zone, now));
    }

    private CurrentStatusDTO.TodaySummary todaySummary(UUID userId, ZoneId zone, Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant from = today.atStartOfDay(zone).toInstant();
        long[] trips = tripRepository.aggregateTripsStartedBetween(userId, from, now);
        long[] stays = stayRepository.countStaysAndPlacesStartedBetween(userId, from, now);
        return new CurrentStatusDTO.TodaySummary(today, trips[1], trips[0], stays[0], stays[1], trips[2]);
    }

    private static long secondsBetween(Instant from, Instant to) {
        return from == null ? 0 : Math.max(0, Duration.between(from, to).getSeconds());
    }

    private static ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
