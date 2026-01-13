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
    private String openaiApiUrl; // Custom OpenAI API URL (defaults to https://api.openai.com/v1)
    private String openaiModel;
    private boolean openaiApiKeyConfigured; // Frontend-safe flag to indicate if key is set
    private boolean apiKeyRequired;
    private String customSystemMessage; // Optional custom system message (null = use default)

    public UserAISettings copy() {
        return UserAISettings.builder()
                .enabled(this.enabled)
                .openaiApiKey(this.openaiApiKey)
                .openaiApiUrl(this.openaiApiUrl)
                .openaiModel(this.openaiModel)
                .openaiApiKeyConfigured(this.openaiApiKeyConfigured)
                .apiKeyRequired(this.apiKeyRequired)
                .customSystemMessage(this.customSystemMessage)
                .build();
    }
}
