package org.github.tess1o.geopulse.notes.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdateNoteRequest {
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @NotBlank(message = "Note content is required")
    private String contentMarkdown;

    private Instant eventTime;
    private Double latitude;
    private Double longitude;
}
