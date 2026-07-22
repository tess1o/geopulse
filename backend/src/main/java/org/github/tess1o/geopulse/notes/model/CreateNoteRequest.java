package org.github.tess1o.geopulse.notes.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateNoteRequest {
    private NoteDestination destination;

    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @NotBlank(message = "Note content is required")
    private String contentMarkdown;

    private Instant eventTime;
    private NoteAnchorType anchorType;
    private Long anchorId;
    private Double latitude;
    private Double longitude;
    private MemosVisibility visibility;
}
