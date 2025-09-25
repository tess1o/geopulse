package org.github.tess1o.geopulse.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.UUID;

@ApplicationScoped
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

            UserAISettings settingsToSave;
            
            // If API key is not provided in the request, preserve the existing one
            if (settings.getOpenaiApiKey() == null || settings.getOpenaiApiKey().isBlank()) {
                // Get existing settings to preserve the API key
                UserAISettings existingSettings = null;
                if (user.getAiSettingsEncrypted() != null && !user.getAiSettingsEncrypted().isBlank()) {
                    try {
                        String decryptedJson = encryptionService.decrypt(
                                user.getAiSettingsEncrypted(),
                                user.getAiSettingsKeyId()
                        );
                        existingSettings = objectMapper.readValue(decryptedJson, UserAISettings.class);
                    } catch (Exception e) {
                        // If we can't decrypt existing settings, continue without preserving API key
                        existingSettings = null;
                    }
                }
                
                // Create settings with preserved API key
                settingsToSave = UserAISettings.builder()
                        .enabled(settings.isEnabled())
                        .openaiApiUrl(settings.getOpenaiApiUrl() != null ? settings.getOpenaiApiUrl() : OPENAI_DEFAULT_URL)
                        .openaiModel(settings.getOpenaiModel())
                        .openaiApiKey(existingSettings != null ? existingSettings.getOpenaiApiKey() : "")
                        .build();
            } else {
                // New API key provided - encrypt it
                settingsToSave = settings.copy();
                settingsToSave.setOpenaiApiKey(
                        encryptionService.encrypt(settingsToSave.getOpenaiApiKey())
                );
            }

            // Convert to JSON and encrypt entire blob
            String json = objectMapper.writeValueAsString(settingsToSave);
            String encryptedJson = encryptionService.encrypt(json);

            // Store in database
            user.setAiSettingsEncrypted(encryptedJson);
            user.setAiSettingsKeyId(encryptionService.getCurrentKeyId());
            em.merge(user);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save AI settings", e);
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
                // Return default OpenAI settings (disabled by default)
                return UserAISettings.builder()
                        .enabled(false)
                        .openaiApiKey(null) // Never send actual key to frontend
                        .openaiApiUrl(OPENAI_DEFAULT_URL) // Default OpenAI API URL
                        .openaiModel(DEFAULT_OPENAI_MODEL)
                        .openaiApiKeyConfigured(false)
                        .build();
            }

            // Decrypt JSON blob
            String decryptedJson = encryptionService.decrypt(
                    user.getAiSettingsEncrypted(),
                    user.getAiSettingsKeyId()
            );

            UserAISettings settings = objectMapper.readValue(decryptedJson, UserAISettings.class);

            // Check if API key is configured but don't send actual key to frontend
            boolean hasApiKey = settings.getOpenaiApiKey() != null && !settings.getOpenaiApiKey().isBlank();
            
            // Return settings without the actual API key for security
            return UserAISettings.builder()
                    .enabled(settings.isEnabled())
                    .openaiApiKey(null) // Never send actual key to frontend
                    .openaiApiUrl(settings.getOpenaiApiUrl() != null ? settings.getOpenaiApiUrl() : OPENAI_DEFAULT_URL)
                    .openaiModel(settings.getOpenaiModel() != null ? settings.getOpenaiModel() : DEFAULT_OPENAI_MODEL)
                    .openaiApiKeyConfigured(hasApiKey)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve AI settings", e);
        }
    }

    /**
     * Get AI settings with actual decrypted API key for internal service use only.
     * This method should never be called from REST endpoints.
     */
    @Transactional
    public UserAISettings getAISettingsWithApiKey(UUID userId) {
        try {
            UserEntity user = em.find(UserEntity.class, userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found: " + userId);
            }

            if (user.getAiSettingsEncrypted() == null || user.getAiSettingsEncrypted().isBlank()) {
                // Return default settings
                return UserAISettings.builder()
                        .enabled(false)
                        .openaiApiKey("")
                        .openaiApiUrl(OPENAI_DEFAULT_URL)
                        .openaiModel(DEFAULT_OPENAI_MODEL)
                        .openaiApiKeyConfigured(false)
                        .build();
            }

            // Decrypt JSON blob
            String decryptedJson = encryptionService.decrypt(
                    user.getAiSettingsEncrypted(),
                    user.getAiSettingsKeyId()
            );

            UserAISettings settings = objectMapper.readValue(decryptedJson, UserAISettings.class);

            // Decrypt API key for internal use
            if (settings.getOpenaiApiKey() != null && !settings.getOpenaiApiKey().isBlank()) {
                settings.setOpenaiApiKey(
                        encryptionService.decrypt(settings.getOpenaiApiKey(), user.getAiSettingsKeyId())
                );
                settings.setOpenaiApiKeyConfigured(true);
            } else {
                settings.setOpenaiApiKeyConfigured(false);
            }

            // Ensure we have a default URL if none is set
            if (settings.getOpenaiApiUrl() == null || settings.getOpenaiApiUrl().isBlank()) {
                settings.setOpenaiApiUrl(OPENAI_DEFAULT_URL);
            }

            return settings;
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve AI settings with API key", e);
        }
    }

}
