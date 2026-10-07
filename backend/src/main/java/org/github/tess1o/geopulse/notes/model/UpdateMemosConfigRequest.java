package org.github.tess1o.geopulse.notes.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class UpdateMemosConfigRequest {
    @Schema(examples = "https://memos.example.com")
    private String serverUrl;

    @Schema(examples = "memos-access-token")
    private String apiKey;

    @NotNull(message = "Enabled flag is required")
    @Schema(examples = "true")
    private Boolean enabled;

    @Schema(examples = "MEMOS")
    private NoteDestination defaultSaveDestination;
    @Schema(examples = "PRIVATE")
    private MemosVisibility defaultVisibility;
    @Schema(examples = "500")
    private Integer maxNotesPerRequest;
    @Schema(examples = "64000")
    private Integer maxContentBytes;
    @Schema(examples = "true")
    private Boolean searchCacheEnabled;
    @Schema(examples = "[\"travel\"]")
    private List<String> includeTags;
    @Schema(examples = "[\"private\"]")
    private List<String> excludeTags;
}
