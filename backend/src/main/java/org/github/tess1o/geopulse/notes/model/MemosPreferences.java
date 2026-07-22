package org.github.tess1o.geopulse.notes.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemosPreferences implements Serializable {
    private String serverUrl;
    private String apiKey;
    private Boolean enabled;

    @Builder.Default
    private NoteDestination defaultSaveDestination = NoteDestination.GEOPULSE;

    @Builder.Default
    private MemosVisibility defaultVisibility = MemosVisibility.PRIVATE;

    @Builder.Default
    private Integer maxNotesPerRequest = 1000;

    @Builder.Default
    private Integer maxContentBytes = 64_000;

    @Builder.Default
    private Boolean searchCacheEnabled = true;
}
