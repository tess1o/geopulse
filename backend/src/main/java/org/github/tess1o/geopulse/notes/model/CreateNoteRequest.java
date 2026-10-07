package org.github.tess1o.geopulse.notes.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class CreateNoteRequest {
    @Schema(examples = "GEOPULSE")
    private NoteDestination destination;

    @Size(max = 255, message = "Title cannot exceed 255 characters")
    @Schema(examples = "Lunch at the market")
    private String title;

    @NotBlank(message = "Note content is required")
    @Schema(examples = "Great **borscht** here.")
    private String contentMarkdown;

    @Schema(examples = "2025-06-07T12:30:00Z")
    private Instant eventTime;
    @Schema(examples = "STAY")
    private NoteAnchorType anchorType;
    @Schema(examples = "512")
    private Long anchorId;
    @Schema(examples = "49.8397")
    private Double latitude;
    @Schema(examples = "24.0297")
    private Double longitude;
    @Schema(examples = "PRIVATE")
    private MemosVisibility visibility;
}
