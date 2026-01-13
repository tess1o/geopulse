package org.github.tess1o.geopulse.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class UserAISettingsService {

    public static final String OPENAI_DEFAULT_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-3.5-turbo";

    @Inject
    AIEncryptionService encryptionService;

    @Inject
    EntityManager em;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void saveAISettings(UUID userId, UserAISettings settings) {
        try {
            UserEntity user = em.find(UserEntity.class, userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found: " + userId);
            }

            // Manually retrieve current encrypted API key to avoid nested transaction issues
            String currentEncryptedApiKey = null;
            if (user.getAiSettingsEncrypted() != null && !user.getAiSettingsEncrypted().isBlank()) {
                try {
                    String decryptedJson = encryptionService.decrypt(user.getAiSettingsEncrypted(), user.getAiSettingsKeyId());
                    UserAISettings currentSettings = objectMapper.readValue(decryptedJson, UserAISettings.class);
                    if (currentSettings.getOpenaiApiKey() != null && !currentSettings.getOpenaiApiKey().isEmpty()) {
                        currentEncryptedApiKey = currentSettings.getOpenaiApiKey();
                    }
                } catch (Exception e) {
                    log.warn("Failed to retrieve current API key for user {}: {}", userId, e.getMessage());
                    // If decryption fails (e.g., encryption key changed), allow user to set new settings
                    // currentEncryptedApiKey remains null, so the user must provide a new API key
                }
            }

            UserAISettings settingsToSave = settings.copy();

            // Handle API key
            if (settings.getOpenaiApiKey() != null && !settings.getOpenaiApiKey().isBlank()) {
                // User provided a new API key - encrypt it
                settingsToSave.setOpenaiApiKey(encryptionService.encrypt(settings.getOpenaiApiKey()));
            } else if (currentEncryptedApiKey != null) {
                // No new API key provided - keep the existing encrypted key
                settingsToSave.setOpenaiApiKey(currentEncryptedApiKey);
            } else {
                // No current API key and none provided - set to empty string
                settingsToSave.setOpenaiApiKey("");
            }

            settingsToSave.setApiKeyRequired(settings.isApiKeyRequired());

            // Handle custom system message - always use what's provided (null or value)
            // Frontend sends null to use default, or a string to override
            settingsToSave.setCustomSystemMessage(settings.getCustomSystemMessage());

            String json = objectMapper.writeValueAsString(settingsToSave);
            String encryptedJson = encryptionService.encrypt(json);

            user.setAiSettingsEncrypted(encryptedJson);
            user.setAiSettingsKeyId(encryptionService.getCurrentKeyId());

            em.merge(user);
            em.flush(); // Force immediate persistence to detect any issues

            log.info("Successfully saved AI settings for user {}", userId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save AI settings", e);
        }
    }

    @SneakyThrows
    public List<String> testConnectionAndFetchModels(UUID userId, UserAISettings settings) {
        UserAISettings dbSettings;
        try {
            dbSettings = getAISettingsWithApiKey(userId);
        } catch (RuntimeException e) {
            // If decryption fails (e.g., encryption key changed), use empty settings
            dbSettings = UserAISettings.builder()
                    .openaiApiKey("")
                    .customSystemMessage(null)
                    .build();
        }

        String apiKey = Optional.ofNullable(settings.getOpenaiApiKey())
                .filter(s -> !s.isBlank())
                .orElse(dbSettings.getOpenaiApiKey());

        if (settings.isApiKeyRequired() && (apiKey == null || apiKey.isBlank())) {
            throw new WebApplicationException("API key is required but not provided.", jakarta.ws.rs.core.Response.Status.BAD_REQUEST);
        }

        RestClientBuilder clientBuilder = RestClientBuilder.newBuilder()
                .baseUri(new URI(settings.getOpenaiApiUrl()));


        if (settings.isApiKeyRequired()) {
            clientBuilder.header("Authorization", "Bearer " + apiKey);
        }

        GeoPulseOpenAIClient client = clientBuilder.build(GeoPulseOpenAIClient.class);

        try {
            GeoPulseOpenAIClient.ModelsResponse models = client.getModels();
            if (models == null || models.data() == null) {
                throw new WebApplicationException("Failed to fetch models: empty response from provider.", jakarta.ws.rs.core.Response.Status.BAD_GATEWAY);
            }
            return models.data()
                    .stream().map(GeoPulseOpenAIClient.Model::id)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Transactional
    public UserAISettings getAISettings(UUID userId) {
        try {
            UserEntity user = em.find(UserEntity.class, userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found: " + userId);
            }

            if (user.getAiSettingsEncrypted() == null || user.getAiSettingsEncrypted().isBlank()) {
                return UserAISettings.builder()
                        .enabled(false)
                        .openaiApiKey(null)
                        .openaiApiUrl(OPENAI_DEFAULT_URL)
                        .openaiModel(DEFAULT_OPENAI_MODEL)
                        .openaiApiKeyConfigured(false)
                        .apiKeyRequired(true)
                        .customSystemMessage(null) // Use default
                        .build();
            }

            String decryptedJson = encryptionService.decrypt(user.getAiSettingsEncrypted(), user.getAiSettingsKeyId());
            UserAISettings settings = objectMapper.readValue(decryptedJson, UserAISettings.class);

            boolean hasApiKey = settings.getOpenaiApiKey() != null && !settings.getOpenaiApiKey().isBlank();

            return UserAISettings.builder()
                    .enabled(settings.isEnabled())
                    .openaiApiKey(null)
                    .openaiApiUrl(settings.getOpenaiApiUrl() != null ? settings.getOpenaiApiUrl() : OPENAI_DEFAULT_URL)
                    .openaiModel(settings.getOpenaiModel() != null ? settings.getOpenaiModel() : DEFAULT_OPENAI_MODEL)
                    .openaiApiKeyConfigured(hasApiKey)
                    .apiKeyRequired(settings.isApiKeyRequired())
                    .customSystemMessage(settings.getCustomSystemMessage())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve AI settings", e);
        }
    }

    @Transactional
    public UserAISettings getAISettingsWithApiKey(UUID userId) {
        try {
            UserEntity user = em.find(UserEntity.class, userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found: " + userId);
            }

            if (user.getAiSettingsEncrypted() == null || user.getAiSettingsEncrypted().isBlank()) {
                return UserAISettings.builder()
                        .enabled(false)
                        .openaiApiKey("")
                        .openaiApiUrl(OPENAI_DEFAULT_URL)
                        .openaiModel(DEFAULT_OPENAI_MODEL)
                        .openaiApiKeyConfigured(false)
                        .apiKeyRequired(true)
                        .customSystemMessage(null) // Use default
                        .build();
            }

            String decryptedJson = encryptionService.decrypt(user.getAiSettingsEncrypted(), user.getAiSettingsKeyId());
            UserAISettings settings = objectMapper.readValue(decryptedJson, UserAISettings.class);

            // Only attempt to decrypt API key if it exists and is not empty
            if (settings.getOpenaiApiKey() != null && !settings.getOpenaiApiKey().isEmpty()) {
                try {
                    settings.setOpenaiApiKey(encryptionService.decrypt(settings.getOpenaiApiKey(), user.getAiSettingsKeyId()));
                    settings.setOpenaiApiKeyConfigured(true);
                } catch (RuntimeException e) {
                    log.warn("Failed to decrypt API key for user {}: {}", userId, e.getMessage());
                    settings.setOpenaiApiKey("");
                    settings.setOpenaiApiKeyConfigured(false);
                }
            } else {
                settings.setOpenaiApiKey("");
                settings.setOpenaiApiKeyConfigured(false);
            }

            if (settings.getOpenaiApiUrl() == null || settings.getOpenaiApiUrl().isBlank()) {
                settings.setOpenaiApiUrl(OPENAI_DEFAULT_URL);
            }

            return settings;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve AI settings with API key", e);
        }
    }
}
