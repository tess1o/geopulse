package org.github.tess1o.geopulse.notes.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.notes.model.NoteDto;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class TimelineNoteSearchSupport {

    int resolveLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return TimelineNoteConstants.DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(requestedLimit, TimelineNoteConstants.MAX_SEARCH_LIMIT);
    }

    Comparator<NoteDto> noteComparator() {
        return Comparator.comparing(NoteDto::getEventTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(note -> note.getSource() != null ? note.getSource().name() : "")
                .thenComparing(note -> note.getId() != null ? String.valueOf(note.getId()) : String.valueOf(note.getExternalId()));
    }

    List<NoteDto> filterByRadius(List<NoteDto> notes, Double latitude, Double longitude, Double radiusMeters) {
        if (latitude == null || longitude == null || radiusMeters == null || radiusMeters <= 0) {
            return notes;
        }
        return notes.stream()
                .filter(note -> note.getLatitude() != null && note.getLongitude() != null)
                .filter(note -> GeoUtils.haversine(latitude, longitude, note.getLatitude(), note.getLongitude()) <= radiusMeters)
                .toList();
    }
}
