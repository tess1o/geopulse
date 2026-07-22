package org.github.tess1o.geopulse.notes.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NoteMapMarkersResponse {
    private List<NoteMapMarkerDto> markers;
    private Integer totalNotes;
    private Integer locatedNotes;
}
