package org.github.tess1o.geopulse.ai.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for UserAISettingsService.
 * Tests the full lifecycle of AI settings: save, retrieve, update, and edge cases.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
public class UserAISettingsServiceTest {

    @Inject
    UserAISettingsService aiSettingsService;

    @Inject
    UserRepository userRepository;

    private UUID testUserId;

    @BeforeEach
    @Transactional
    public void setup() {
        // Clean up existing users
        //userRepository.findAll().stream().forEach(user -> userRepository.delete(user));

        // Create a test user
        UserEntity testUser = new UserEntity();
        testUser.setEmail("ai-test@example.com");
        testUser.setFullName("AI Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setTimezone("UTC");
        userRepository.persist(testUser);

        testUserId = testUser.getId();
    }

    @AfterEach
    @Transactional
    public void cleanup() {
        userRepository.deleteById(testUserId);
    }

    @Test
    @Transactional
    public void testGetDefaultAISettings() {
        // When no settings are configured, should return defaults
        UserAISettings settings = aiSettingsService.getAISettings(testUserId);

        assertNotNull(settings);
        assertFalse(settings.isEnabled());
        assertNull(settings.getOpenaiApiKey()); // API key should never be exposed in GET
        assertEquals(UserAISettingsService.OPENAI_DEFAULT_URL, settings.getOpenaiApiUrl());
        assertEquals(UserAISettingsService.DEFAULT_OPENAI_MODEL, settings.getOpenaiModel());
        assertFalse(settings.isOpenaiApiKeyConfigured());
        assertTrue(settings.isApiKeyRequired()); // Default is API key required
    }

    @Test
    @Transactional
    public void testSaveAndRetrieveAISettingsWithApiKey() {
        // Save settings with API key (typical OpenAI scenario)
        UserAISettings settingsToSave = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("sk-test-api-key-12345")
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-4")
                .apiKeyRequired(true)
                .build();

        aiSettingsService.saveAISettings(testUserId, settingsToSave);

        // Retrieve settings (without API key)
        UserAISettings retrievedSettings = aiSettingsService.getAISettings(testUserId);

        assertTrue(retrievedSettings.isEnabled());
        assertNull(retrievedSettings.getOpenaiApiKey()); // API key should never be exposed
        assertTrue(retrievedSettings.isOpenaiApiKeyConfigured()); // But flag should indicate it exists
        assertEquals("https://api.openai.com/v1", retrievedSettings.getOpenaiApiUrl());
        assertEquals("gpt-4", retrievedSettings.getOpenaiModel());
        assertTrue(retrievedSettings.isApiKeyRequired());

        // Retrieve settings with API key (for internal use)
        UserAISettings settingsWithKey = aiSettingsService.getAISettingsWithApiKey(testUserId);
        assertEquals("sk-test-api-key-12345", settingsWithKey.getOpenaiApiKey());
    }

    @Test
    @Transactional
    public void testSaveAISettingsWithoutApiKey_Ollama() {
        // Save settings WITHOUT API key (Ollama scenario)
        UserAISettings settingsToSave = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey(null) // No API key provided
                .openaiApiUrl("http://localhost:11434/v1")
                .openaiModel("llama2")
                .apiKeyRequired(false) // API key not required for Ollama
                .build();

        aiSettingsService.saveAISettings(testUserId, settingsToSave);

        // Retrieve settings
        UserAISettings retrievedSettings = aiSettingsService.getAISettings(testUserId);

        assertTrue(retrievedSettings.isEnabled());
        assertNull(retrievedSettings.getOpenaiApiKey());
        assertFalse(retrievedSettings.isOpenaiApiKeyConfigured()); // No API key configured
        assertEquals("http://localhost:11434/v1", retrievedSettings.getOpenaiApiUrl());
        assertEquals("llama2", retrievedSettings.getOpenaiModel());
        assertFalse(retrievedSettings.isApiKeyRequired());

        // Retrieve settings with API key - should return empty string
        UserAISettings settingsWithKey = aiSettingsService.getAISettingsWithApiKey(testUserId);
        assertNotNull(settingsWithKey.getOpenaiApiKey());
        assertTrue(settingsWithKey.getOpenaiApiKey().isEmpty());
        assertFalse(settingsWithKey.isOpenaiApiKeyConfigured());
    }

    @Test
    @Transactional
    public void testUpdateSettingsWithoutProvidingNewApiKey() {
        // First, save settings with API key
        UserAISettings initialSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("sk-original-key")
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-3.5-turbo")
                .apiKeyRequired(true)
                .build();

        aiSettingsService.saveAISettings(testUserId, initialSettings);

        // Now update settings WITHOUT providing API key (should preserve existing key)
        UserAISettings updatedSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey(null) // Not providing a new key
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-4") // Changed model
                .apiKeyRequired(true)
                .build();

        aiSettingsService.saveAISettings(testUserId, updatedSettings);

        // Verify the API key was preserved
        UserAISettings retrievedSettings = aiSettingsService.getAISettingsWithApiKey(testUserId);
        assertEquals("sk-original-key", retrievedSettings.getOpenaiApiKey());
        assertEquals("gpt-4", retrievedSettings.getOpenaiModel());
    }

    @Test
    @Transactional
    public void testUpdateApiKey() {
        // First, save settings with API key
        UserAISettings initialSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("sk-old-key")
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-3.5-turbo")
                .apiKeyRequired(true)
                .build();

        aiSettingsService.saveAISettings(testUserId, initialSettings);

        // Update with new API key
        UserAISettings updatedSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("sk-new-key") // Providing new key
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-3.5-turbo")
                .apiKeyRequired(true)
                .build();

        aiSettingsService.saveAISettings(testUserId, updatedSettings);

        // Verify the API key was updated
        UserAISettings retrievedSettings = aiSettingsService.getAISettingsWithApiKey(testUserId);
        assertEquals("sk-new-key", retrievedSettings.getOpenaiApiKey());
    }

    @Test
    @Transactional
    public void testSwitchFromOpenAIToOllama() {
        // Start with OpenAI configuration (with API key)
        UserAISettings openAISettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("sk-openai-key")
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-4")
                .apiKeyRequired(true)
                .build();

        aiSettingsService.saveAISettings(testUserId, openAISettings);

        // Switch to Ollama (no API key required)
        UserAISettings ollamaSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey(null) // No API key for Ollama
                .openaiApiUrl("http://localhost:11434/v1")
                .openaiModel("llama2")
                .apiKeyRequired(false) // API key not required
                .build();

        aiSettingsService.saveAISettings(testUserId, ollamaSettings);

        // Verify settings
        UserAISettings retrievedSettings = aiSettingsService.getAISettings(testUserId);
        assertFalse(retrievedSettings.isApiKeyRequired());
        assertEquals("http://localhost:11434/v1", retrievedSettings.getOpenaiApiUrl());
        assertEquals("llama2", retrievedSettings.getOpenaiModel());
    }

    @Test
    @Transactional
    public void testHandleCorruptedSettings() {
        // First, save valid settings
        UserAISettings validSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("sk-valid-key")
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-3.5-turbo")
                .apiKeyRequired(true)
                .build();

        aiSettingsService.saveAISettings(testUserId, validSettings);

        // Manually corrupt the encrypted settings in the database
        UserEntity user = userRepository.findById(testUserId);
        user.setAiSettingsEncrypted("corrupted-data-not-valid-base64!");
        userRepository.persist(user);

        // Now try to save new settings - should not fail due to corruption
        UserAISettings newSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("sk-new-key-after-corruption")
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-4")
                .apiKeyRequired(true)
                .build();

        // Should not throw exception even though old settings are corrupted
        assertDoesNotThrow(() -> aiSettingsService.saveAISettings(testUserId, newSettings));

        // Verify new settings were saved successfully
        UserAISettings retrievedSettings = aiSettingsService.getAISettingsWithApiKey(testUserId);
        assertEquals("sk-new-key-after-corruption", retrievedSettings.getOpenaiApiKey());
    }

    @Test
    @Transactional
    public void testEmptyApiKeyDoesNotCauseDecryptionError() {
        // Save settings with empty API key (edge case)
        UserAISettings settingsWithEmptyKey = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("") // Explicitly empty
                .openaiApiUrl("http://localhost:11434/v1")
                .openaiModel("llama2")
                .apiKeyRequired(false)
                .build();

        aiSettingsService.saveAISettings(testUserId, settingsWithEmptyKey);

        // Retrieve settings - should not fail with decryption error
        UserAISettings retrievedSettings = assertDoesNotThrow(
                () -> aiSettingsService.getAISettingsWithApiKey(testUserId)
        );

        assertNotNull(retrievedSettings);
        assertTrue(retrievedSettings.getOpenaiApiKey().isEmpty());
        assertFalse(retrievedSettings.isOpenaiApiKeyConfigured());
    }

    @Test
    @Transactional
    public void testDisableAI() {
        // Enable AI first
        UserAISettings enabledSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("sk-key")
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-3.5-turbo")
                .apiKeyRequired(true)
                .build();

        aiSettingsService.saveAISettings(testUserId, enabledSettings);

        // Disable AI
        UserAISettings disabledSettings = UserAISettings.builder()
                .enabled(false) // Disable
                .openaiApiKey(null)
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-3.5-turbo")
                .apiKeyRequired(true)
                .build();

        aiSettingsService.saveAISettings(testUserId, disabledSettings);

        // Verify AI is disabled
        UserAISettings retrievedSettings = aiSettingsService.getAISettings(testUserId);
        assertFalse(retrievedSettings.isEnabled());
    }

    @Test
    public void testGetSettingsForNonExistentUser() {
        UUID nonExistentUserId = UUID.randomUUID();

        // Should throw exception for non-existent user
        assertThrows(Exception.class,
                () -> aiSettingsService.getAISettings(nonExistentUserId));
    }
}
