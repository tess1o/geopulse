package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Data;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;

import java.time.Instant;
import java.util.LinkedHashMap;

@Data
public class TimelineEditRequest {
    private TimelineEditKind kind;
    private Instant anchorStart;
    private Instant anchorEnd;
    private Instant affectedFrom;
    private Instant affectedTo;
    private LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
    private Long favoriteId;
    private Long geocodingId;
    private Double latitude;
    private Double longitude;
    private String placeName;
    private boolean supersede;
}
