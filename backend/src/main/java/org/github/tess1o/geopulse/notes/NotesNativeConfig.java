package org.github.tess1o.geopulse.notes;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.notes.model.*;

@RegisterForReflection(targets = {
        TimelineNoteEntity.class,
        NoteDto.class,
        NoteSearchResponse.class,
        NoteMapMarkerDto.class,
        NoteMapMarkersResponse.class,
        CreateNoteRequest.class,
        UpdateNoteRequest.class,
        MemosPreferences.class,
        MemosConfigResponse.class,
        UpdateMemosConfigRequest.class,
        TestMemosConnectionRequest.class,
        TestMemosConnectionResponse.class,
        MemosLocation.class,
        MemosMemo.class,
        MemosListResponse.class,
        MemosCreateMemoRequest.class,
        NoteAnchorType.class,
        NoteDestination.class,
        NoteLocationSource.class,
        NoteSource.class,
        MemosVisibility.class
})
public class NotesNativeConfig {
}
