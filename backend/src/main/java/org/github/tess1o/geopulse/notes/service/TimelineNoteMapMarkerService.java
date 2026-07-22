package org.github.tess1o.geopulse.notes.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.notes.model.NoteDto;
import org.github.tess1o.geopulse.notes.model.NoteLocationSource;
import org.github.tess1o.geopulse.notes.model.NoteMapMarkerDto;
import org.github.tess1o.geopulse.notes.model.NoteMapMarkersResponse;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TimelineNoteMapMarkerService {

    NoteMapMarkersResponse buildMarkers(List<NoteDto> notes, Integer coordinatePrecision) {
        int safePrecision = sanitizeCoordinatePrecision(coordinatePrecision);
        Map<String, MapMarkerAccumulator> groups = new LinkedHashMap<>();
        int locatedCount = 0;

        for (NoteDto note : notes) {
            if (note.getLatitude() == null || note.getLongitude() == null) {
                continue;
            }

            locatedCount++;
            double roundedLat = roundCoordinate(note.getLatitude(), safePrecision);
            double roundedLon = roundCoordinate(note.getLongitude(), safePrecision);
            String key = roundedLat + "," + roundedLon;
            groups.compute(key, (ignored, existing) -> {
                if (existing == null) {
                    return new MapMarkerAccumulator(roundedLat, roundedLon, note.getEventTime(), note.getLocationSource(), 1, note);
                }
                return existing.add(note);
            });
        }

        List<NoteMapMarkerDto> markers = groups.values().stream()
                .map(group -> NoteMapMarkerDto.builder()
                        .latitude(group.latitude())
                        .longitude(group.longitude())
                        .count(group.count())
                        .latestEventTime(group.latestEventTime())
                        .locationSource(group.locationSource())
                        .singleNote(group.count() == 1 ? group.singleNote() : null)
                        .build())
                .sorted(Comparator.comparing(
                        NoteMapMarkerDto::getLatestEventTime,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        return NoteMapMarkersResponse.builder()
                .markers(markers)
                .totalNotes(notes.size())
                .locatedNotes(locatedCount)
                .build();
    }

    private int sanitizeCoordinatePrecision(Integer precision) {
        if (precision == null) {
            return TimelineNoteConstants.DEFAULT_MAP_COORDINATE_PRECISION;
        }
        return Math.max(3, Math.min(6, precision));
    }

    private double roundCoordinate(double value, int precision) {
        double factor = Math.pow(10, precision);
        return Math.round(value * factor) / factor;
    }

    private record MapMarkerAccumulator(
            double latitude,
            double longitude,
            Instant latestEventTime,
            NoteLocationSource locationSource,
            int count,
            NoteDto singleNote
    ) {
        private MapMarkerAccumulator add(NoteDto note) {
            Instant latest = latestEventTime;
            if (latest == null || (note.getEventTime() != null && note.getEventTime().isAfter(latest))) {
                latest = note.getEventTime();
            }
            NoteLocationSource source = locationSource == note.getLocationSource()
                    ? locationSource
                    : NoteLocationSource.NONE;
            return new MapMarkerAccumulator(latitude, longitude, latest, source, count + 1, null);
        }
    }
}
