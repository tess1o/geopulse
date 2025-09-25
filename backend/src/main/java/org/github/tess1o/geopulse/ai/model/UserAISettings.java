package org.github.tess1o.geopulse.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserAISettings {

    private boolean enabled;
    private String openaiApiKey; // Used for saving only, not returned in GET requests
    private String openaiModel;
    private boolean openaiApiKeyConfigured; // Frontend-safe flag to indicate if key is set

    public UserAISettings copy() {
        return UserAISettings.builder()
                .enabled(this.enabled)
                .openaiApiKey(this.openaiApiKey)
                .openaiModel(this.openaiModel)
                .openaiApiKeyConfigured(this.openaiApiKeyConfigured)
                .build();
    }
}
