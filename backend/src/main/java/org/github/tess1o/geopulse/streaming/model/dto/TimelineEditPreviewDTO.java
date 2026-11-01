package org.github.tess1o.geopulse.streaming.model.dto;

import java.time.Instant;
import java.util.List;

public record TimelineEditPreviewDTO(List<Event> events) {
    public record Event(String type, Instant start, Instant end, String label, String locationName) {
    }
}
