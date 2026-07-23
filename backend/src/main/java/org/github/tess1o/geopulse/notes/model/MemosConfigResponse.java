package org.github.tess1o.geopulse.notes.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MemosConfigResponse {
    private String serverUrl;
    private String apiKey;
    private Boolean enabled;
    private NoteDestination defaultSaveDestination;
    private MemosVisibility defaultVisibility;
    private Integer maxNotesPerRequest;
    private Integer maxContentBytes;
    private Boolean searchCacheEnabled;
    private List<String> includeTags;
    private List<String> excludeTags;
}
