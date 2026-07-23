package org.github.tess1o.geopulse.notes.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateMemosConfigRequest {
    private String serverUrl;

    private String apiKey;

    @NotNull(message = "Enabled flag is required")
    private Boolean enabled;

    private NoteDestination defaultSaveDestination;
    private MemosVisibility defaultVisibility;
    private Integer maxNotesPerRequest;
    private Integer maxContentBytes;
    private Boolean searchCacheEnabled;
    private List<String> includeTags;
    private List<String> excludeTags;
}
