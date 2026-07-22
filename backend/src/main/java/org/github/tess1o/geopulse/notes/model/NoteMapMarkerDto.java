package org.github.tess1o.geopulse.notes.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class NoteMapMarkerDto {
    private Double latitude;
    private Double longitude;
    private Integer count;
    private Instant latestEventTime;
    private NoteLocationSource locationSource;
    private NoteDto singleNote;
}
