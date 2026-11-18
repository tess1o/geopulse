package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Data;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayload;

import java.time.Instant;

@Data
public class TimelineEditRequest {
    private TimelineEditKind kind;
    private Instant anchorStart;
    private Instant anchorEnd;
    private Instant affectedFrom;
    private Instant affectedTo;
    private CorrectionPayload payload;
    private Long favoriteId;
    private Long geocodingId;
    private Double latitude;
    private Double longitude;
    private String placeName;
    private String previewToken;
    /** When set, the group is explicitly replaced by the correction saved with this request. */
    private java.util.UUID replacesGroupId;
    /** Set by a batch save that already folded the whole batch, so each member is not previewed again. */
    private boolean pending;
}
