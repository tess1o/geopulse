package org.github.tess1o.geopulse.notes.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.github.tess1o.geopulse.notes.repository.TimelineNoteRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class TimelineNoteAnchorReattachmentService {

    @Inject
    TimelineNoteRepository noteRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    TimelineNoteLocationService locationService;

    @ConfigProperty(name = "geopulse.notes.anchor.matching.max_timestamp_delta_seconds", defaultValue = "21600")
    long maxTimestampDeltaSeconds;

    @ConfigProperty(name = "geopulse.notes.anchor.matching.max_point_distance_meters", defaultValue = "350.0")
    double maxPointDistanceMeters;

    int reattachAnchoredNotes(UUID userId) {
        int reattached = reattachStayNotes(userId) + reattachTripNotes(userId);
        if (reattached > 0) {
            log.info("Reattached {} GeoPulse notes after timeline regeneration for user {}", reattached, userId);
        }
        return reattached;
    }

    private int reattachStayNotes(UUID userId) {
        List<TimelineNoteEntity> notes = noteRepository.findUnmatchedAnchoredNotes(userId, NoteAnchorType.STAY);
        if (notes.isEmpty()) {
            return 0;
        }
        List<TimelineStayEntity> candidates = stayRepository.findByUserIdAndTimeRangeWithExpansion(
                userId,
                notes.stream()
                        .map(TimelineNoteEntity::getSourceItemStartTime)
                        .filter(Objects::nonNull)
                        .min(Instant::compareTo)
                        .orElse(Instant.EPOCH)
                        .minusSeconds(maxTimestampDeltaSeconds),
                Instant.now().plusSeconds(maxTimestampDeltaSeconds)
        );
        List<TimelineDataGapEntity> gaps = findDataGapsCoveringNotes(userId, notes);

        int count = 0;
        Set<Long> matchedStayIds = new HashSet<>();
        for (TimelineNoteEntity note : notes) {
            if (overlapsDataGap(note.getSourceItemStartTime(), gaps)) {
                // The note's original stay was reprocessed into a data gap, not a new stay -
                // leave it as a plain timestamp-anchored note rather than force-linking it to
                // an unrelated nearby stay.
                note.setAnchorType(NoteAnchorType.TIMESTAMP);
                continue;
            }
            TimelineStayEntity best = findBestStayMatch(note, candidates, matchedStayIds);
            if (best == null) {
                continue;
            }
            note.setStay(best);
            locationService.syncStayAnchor(note, best);
            matchedStayIds.add(best.getId());
            count++;
        }
        return count;
    }

    private int reattachTripNotes(UUID userId) {
        List<TimelineNoteEntity> notes = noteRepository.findUnmatchedAnchoredNotes(userId, NoteAnchorType.TRIP);
        if (notes.isEmpty()) {
            return 0;
        }
        List<TimelineTripEntity> candidates = tripRepository.findByUser(userId);
        List<TimelineDataGapEntity> gaps = findDataGapsCoveringNotes(userId, notes);

        int count = 0;
        Set<Long> matchedTripIds = new HashSet<>();
        for (TimelineNoteEntity note : notes) {
            if (overlapsDataGap(note.getSourceItemStartTime(), gaps)) {
                // The note's original trip was reprocessed into a data gap, not a new trip -
                // leave it as a plain timestamp-anchored note rather than force-linking it to
                // an unrelated nearby trip.
                note.setAnchorType(NoteAnchorType.TIMESTAMP);
                continue;
            }
            TimelineTripEntity best = findBestTripMatch(note, candidates, matchedTripIds);
            if (best == null) {
                continue;
            }
            note.setTrip(best);
            locationService.syncTripAnchor(note, best);
            matchedTripIds.add(best.getId());
            count++;
        }
        return count;
    }

    private TimelineStayEntity findBestStayMatch(TimelineNoteEntity note, List<TimelineStayEntity> candidates, Set<Long> matchedStayIds) {
        TimelineStayEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (TimelineStayEntity stay : candidates) {
            if (stay.getId() == null || matchedStayIds.contains(stay.getId()) || stay.getLocation() == null
                    || note.getSourceItemStartTime() == null
                    || note.getSourceStartLatitude() == null || note.getSourceStartLongitude() == null) {
                continue;
            }
            long timestampDeltaSeconds = Math.abs(Duration.between(note.getSourceItemStartTime(), stay.getTimestamp()).getSeconds());
            if (timestampDeltaSeconds > maxTimestampDeltaSeconds) {
                continue;
            }
            double distance = GeoUtils.haversine(
                    note.getSourceStartLatitude(),
                    note.getSourceStartLongitude(),
                    stay.getLocation().getY(),
                    stay.getLocation().getX()
            );
            if (distance > maxPointDistanceMeters) {
                continue;
            }
            double score = timestampDeltaSeconds + distance + Math.abs(nullToZero(note.getSourceItemDurationSeconds()) - stay.getStayDuration()) * 0.2;
            if (score < bestScore) {
                bestScore = score;
                best = stay;
            }
        }
        return best;
    }

    private TimelineTripEntity findBestTripMatch(TimelineNoteEntity note, List<TimelineTripEntity> candidates, Set<Long> matchedTripIds) {
        TimelineTripEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (TimelineTripEntity trip : candidates) {
            if (trip.getId() == null || matchedTripIds.contains(trip.getId()) || trip.getStartPoint() == null
                    || trip.getEndPoint() == null || note.getSourceItemStartTime() == null
                    || note.getSourceStartLatitude() == null || note.getSourceStartLongitude() == null
                    || note.getSourceEndLatitude() == null || note.getSourceEndLongitude() == null) {
                continue;
            }
            long timestampDeltaSeconds = Math.abs(Duration.between(note.getSourceItemStartTime(), trip.getTimestamp()).getSeconds());
            if (timestampDeltaSeconds > maxTimestampDeltaSeconds) {
                continue;
            }
            double startDistance = GeoUtils.haversine(
                    note.getSourceStartLatitude(),
                    note.getSourceStartLongitude(),
                    trip.getStartPoint().getY(),
                    trip.getStartPoint().getX()
            );
            double endDistance = GeoUtils.haversine(
                    note.getSourceEndLatitude(),
                    note.getSourceEndLongitude(),
                    trip.getEndPoint().getY(),
                    trip.getEndPoint().getX()
            );
            if (startDistance > maxPointDistanceMeters || endDistance > maxPointDistanceMeters) {
                continue;
            }
            double score = timestampDeltaSeconds
                    + startDistance
                    + endDistance
                    + Math.abs(nullToZero(note.getSourceItemDurationSeconds()) - trip.getTripDuration()) * 0.2
                    + Math.abs(nullToZero(note.getSourceDistanceMeters()) - trip.getDistanceMeters()) * 0.02;
            if (score < bestScore) {
                bestScore = score;
                best = trip;
            }
        }
        return best;
    }

    private long nullToZero(Long value) {
        return value != null ? value : 0L;
    }

    private List<TimelineDataGapEntity> findDataGapsCoveringNotes(UUID userId, List<TimelineNoteEntity> notes) {
        Instant minTime = notes.stream()
                .map(TimelineNoteEntity::getSourceItemStartTime)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
        Instant maxTime = notes.stream()
                .map(TimelineNoteEntity::getSourceItemStartTime)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        if (minTime == null || maxTime == null) {
            return List.of();
        }
        return dataGapRepository.findByUserIdAndTimeRangeWithExpansion(userId, minTime, maxTime);
    }

    private boolean overlapsDataGap(Instant sourceItemStartTime, List<TimelineDataGapEntity> gaps) {
        if (sourceItemStartTime == null) {
            return false;
        }
        for (TimelineDataGapEntity gap : gaps) {
            if (gap.getStartTime() != null && gap.getEndTime() != null
                    && !sourceItemStartTime.isBefore(gap.getStartTime())
                    && sourceItemStartTime.isBefore(gap.getEndTime())) {
                return true;
            }
        }
        return false;
    }
}
