package org.github.tess1o.geopulse.notes.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.notes.model.CreateNoteRequest;
import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.NoteDto;
import org.github.tess1o.geopulse.notes.model.NoteLocationSource;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TimelineNoteLocationService {

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @ConfigProperty(name = "geopulse.notes.trip-marker.max-gps-gap-seconds", defaultValue = "900")
    long maxTripMarkerGpsGapSeconds;

    void attachAnchor(UUID userId, TimelineNoteEntity note, Long anchorId) {
        if (note.getAnchorType() == NoteAnchorType.STAY && anchorId != null) {
            TimelineStayEntity stay = stayRepository.findByIdOptional(anchorId)
                    .filter(entity -> entity.getUser() != null && userId.equals(entity.getUser().getId()))
                    .orElseThrow(() -> new NoSuchElementException("Stay not found"));
            note.setStay(stay);
            syncStayAnchor(note, stay);
            if (note.getLocation() == null && stay.getLocation() != null) {
                note.setLocation(stay.getLocation());
                note.setLocationSource(NoteLocationSource.DERIVED_STAY);
            }
        } else if (note.getAnchorType() == NoteAnchorType.TRIP && anchorId != null) {
            TimelineTripEntity trip = tripRepository.findByIdOptional(anchorId)
                    .filter(entity -> entity.getUser() != null && userId.equals(entity.getUser().getId()))
                    .orElseThrow(() -> new NoSuchElementException("Trip not found"));
            note.setTrip(trip);
            syncTripAnchor(note, trip);
            if (note.getLocation() == null) {
                TimelineNoteResolvedLocation location = resolveTripNoteLocation(userId, trip, note.getEventTime());
                if (location.latitude() != null && location.longitude() != null) {
                    note.setLocation(GeoUtils.createPoint(location.longitude(), location.latitude()));
                    note.setLocationSource(location.source());
                }
            }
        }
    }

    void resolveTimelineLocations(UUID userId, Instant startTime, Instant endTime, List<NoteDto> notes) {
        if (notes.isEmpty()) {
            return;
        }

        List<TimelineStayEntity> stays = stayRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        List<TimelineTripEntity> trips = tripRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);

        for (NoteDto note : notes) {
            if (note.getLatitude() != null && note.getLongitude() != null) {
                continue;
            }
            if (note.getEventTime() == null) {
                continue;
            }

            TimelineStayEntity stay = findContainingStay(stays, note.getEventTime());
            if (stay != null && stay.getLocation() != null) {
                note.setLatitude(stay.getLocation().getY());
                note.setLongitude(stay.getLocation().getX());
                note.setLocationSource(NoteLocationSource.DERIVED_STAY);
                note.setAnchorType(NoteAnchorType.STAY);
                note.setAnchorId(stay.getId());
                continue;
            }

            TimelineTripEntity trip = findContainingTrip(trips, note.getEventTime());
            if (trip != null) {
                TimelineNoteResolvedLocation location = resolveTripNoteLocation(userId, trip, note.getEventTime());
                if (location.latitude() != null && location.longitude() != null) {
                    note.setLatitude(location.latitude());
                    note.setLongitude(location.longitude());
                    note.setLocationSource(location.source());
                    note.setAnchorType(NoteAnchorType.TRIP);
                    note.setAnchorId(trip.getId());
                }
            }
        }
    }

    TimelineNoteResolvedLocation resolveCreateRequestLocation(UUID userId, CreateNoteRequest request, Instant eventTime) {
        if (request.getLatitude() != null && request.getLongitude() != null) {
            return new TimelineNoteResolvedLocation(request.getLatitude(), request.getLongitude(), null, NoteLocationSource.EXPLICIT);
        }
        if (request.getAnchorType() == NoteAnchorType.STAY && request.getAnchorId() != null) {
            return stayRepository.findByIdOptional(request.getAnchorId())
                    .filter(stay -> stay.getUser() != null && userId.equals(stay.getUser().getId()))
                    .filter(stay -> stay.getLocation() != null)
                    .map(stay -> new TimelineNoteResolvedLocation(
                            stay.getLocation().getY(),
                            stay.getLocation().getX(),
                            stay.getLocationName(),
                            NoteLocationSource.DERIVED_STAY
                    ))
                    .orElse(new TimelineNoteResolvedLocation(null, null, null, NoteLocationSource.NONE));
        }
        if (request.getAnchorType() == NoteAnchorType.TRIP && request.getAnchorId() != null) {
            return tripRepository.findByIdOptional(request.getAnchorId())
                    .filter(trip -> trip.getUser() != null && userId.equals(trip.getUser().getId()))
                    .map(trip -> resolveTripNoteLocation(userId, trip, eventTime))
                    .orElse(new TimelineNoteResolvedLocation(null, null, null, NoteLocationSource.NONE));
        }
        return new TimelineNoteResolvedLocation(null, null, null, NoteLocationSource.NONE);
    }

    Instant resolveEventTime(UUID userId, CreateNoteRequest request) {
        if (request.getEventTime() != null) {
            return request.getEventTime();
        }
        if (request.getAnchorType() == NoteAnchorType.STAY && request.getAnchorId() != null) {
            return stayRepository.findByIdOptional(request.getAnchorId())
                    .filter(stay -> stay.getUser() != null && userId.equals(stay.getUser().getId()))
                    .map(TimelineStayEntity::getTimestamp)
                    .orElse(Instant.now());
        }
        if (request.getAnchorType() == NoteAnchorType.TRIP && request.getAnchorId() != null) {
            return tripRepository.findByIdOptional(request.getAnchorId())
                    .filter(trip -> trip.getUser() != null && userId.equals(trip.getUser().getId()))
                    .map(TimelineTripEntity::getTimestamp)
                    .orElse(Instant.now());
        }
        return Instant.now();
    }

    void syncStayAnchor(TimelineNoteEntity note, TimelineStayEntity stay) {
        note.setSourceItemStartTime(stay.getTimestamp());
        note.setSourceItemDurationSeconds(stay.getStayDuration());
        if (stay.getLocation() != null) {
            note.setSourceStartLatitude(stay.getLocation().getY());
            note.setSourceStartLongitude(stay.getLocation().getX());
        }
    }

    void syncTripAnchor(TimelineNoteEntity note, TimelineTripEntity trip) {
        note.setSourceItemStartTime(trip.getTimestamp());
        note.setSourceItemDurationSeconds(trip.getTripDuration());
        note.setSourceDistanceMeters(trip.getDistanceMeters());
        if (trip.getStartPoint() != null) {
            note.setSourceStartLatitude(trip.getStartPoint().getY());
            note.setSourceStartLongitude(trip.getStartPoint().getX());
        }
        if (trip.getEndPoint() != null) {
            note.setSourceEndLatitude(trip.getEndPoint().getY());
            note.setSourceEndLongitude(trip.getEndPoint().getX());
        }
    }

    private TimelineNoteResolvedLocation resolveTripNoteLocation(UUID userId, TimelineTripEntity trip, Instant eventTime) {
        if (trip.getStartPoint() == null || trip.getEndPoint() == null || trip.getTimestamp() == null || eventTime == null) {
            return new TimelineNoteResolvedLocation(null, null, null, NoteLocationSource.NONE);
        }

        Instant tripStart = trip.getTimestamp();
        Instant tripEnd = tripStart.plusSeconds(Math.max(0L, trip.getTripDuration()));
        if (eventTime.isBefore(tripStart) || eventTime.isAfter(tripEnd)) {
            eventTime = tripStart;
        }

        Optional<GpsPointEntity> before = gpsPointRepository.findLatestByUserIdAtOrBeforeTimestamp(userId, eventTime)
                .filter(point -> !point.getTimestamp().isBefore(tripStart) && !point.getTimestamp().isAfter(tripEnd));
        Optional<GpsPointEntity> after = gpsPointRepository.findEarliestByUserIdAtOrAfterTimestamp(userId, eventTime)
                .filter(point -> !point.getTimestamp().isBefore(tripStart) && !point.getTimestamp().isAfter(tripEnd));
        Optional<GpsPointEntity> nearest = chooseNearestPoint(before, after, eventTime);
        if (nearest.isPresent() && nearest.get().getCoordinates() != null) {
            long deltaSeconds = Math.abs(Duration.between(nearest.get().getTimestamp(), eventTime).getSeconds());
            if (deltaSeconds <= maxTripMarkerGpsGapSeconds) {
                return new TimelineNoteResolvedLocation(
                        nearest.get().getCoordinates().getY(),
                        nearest.get().getCoordinates().getX(),
                        null,
                        NoteLocationSource.DERIVED_TRIP_GPS
                );
            }
        }

        double fraction = calculateTripFraction(tripStart, tripEnd, eventTime);
        double lat = trip.getStartPoint().getY() + (trip.getEndPoint().getY() - trip.getStartPoint().getY()) * fraction;
        double lon = trip.getStartPoint().getX() + (trip.getEndPoint().getX() - trip.getStartPoint().getX()) * fraction;
        return new TimelineNoteResolvedLocation(lat, lon, null, NoteLocationSource.DERIVED_TRIP_INTERPOLATED);
    }

    private Optional<GpsPointEntity> chooseNearestPoint(Optional<GpsPointEntity> before, Optional<GpsPointEntity> after, Instant eventTime) {
        if (before.isEmpty()) {
            return after;
        }
        if (after.isEmpty()) {
            return before;
        }
        long beforeDelta = Math.abs(Duration.between(before.get().getTimestamp(), eventTime).getSeconds());
        long afterDelta = Math.abs(Duration.between(after.get().getTimestamp(), eventTime).getSeconds());
        return beforeDelta <= afterDelta ? before : after;
    }

    private double calculateTripFraction(Instant tripStart, Instant tripEnd, Instant eventTime) {
        long duration = Math.max(1L, Duration.between(tripStart, tripEnd).toMillis());
        long elapsed = Math.max(0L, Math.min(duration, Duration.between(tripStart, eventTime).toMillis()));
        return (double) elapsed / (double) duration;
    }

    private TimelineStayEntity findContainingStay(List<TimelineStayEntity> stays, Instant eventTime) {
        for (TimelineStayEntity stay : stays) {
            Instant start = stay.getTimestamp();
            if (containsEventTime(start, stay.getStayDuration(), eventTime)) {
                return stay;
            }
        }
        return null;
    }

    private TimelineTripEntity findContainingTrip(List<TimelineTripEntity> trips, Instant eventTime) {
        for (TimelineTripEntity trip : trips) {
            Instant start = trip.getTimestamp();
            if (containsEventTime(start, trip.getTripDuration(), eventTime)) {
                return trip;
            }
        }
        return null;
    }

    private boolean containsEventTime(Instant start, long durationSeconds, Instant eventTime) {
        if (start == null || eventTime == null) {
            return false;
        }
        if (durationSeconds <= 0) {
            return eventTime.equals(start);
        }

        Instant end = start.plusSeconds(durationSeconds);
        return !eventTime.isBefore(start) && eventTime.isBefore(end);
    }
}
