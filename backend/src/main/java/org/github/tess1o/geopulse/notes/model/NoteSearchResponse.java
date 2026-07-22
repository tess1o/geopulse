package org.github.tess1o.geopulse.notes.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NoteSearchResponse {
    private List<NoteDto> notes;
    private Integer totalCount;
    private Integer geopulseCount;
    private Integer memosCount;
}
