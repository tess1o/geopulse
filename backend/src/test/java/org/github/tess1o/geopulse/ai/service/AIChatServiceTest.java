package org.github.tess1o.geopulse.ai.service;

import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.ai.orchestration.AIChatOrchestrator;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
/**
 * Test for AIChatService focusing on API key validation logic.
 * <p>
 * This test verifies that Ollama (and other local LLM providers)
 * that don't require API keys are correctly handled by the validation logic.
 * <p>
 * The service should only enforce API key presence when settings.isApiKeyRequired() is true.
 */
@Tag("unit")
public class AIChatServiceTest {
    AIChatService aiChatService;
    UserAISettingsService mockAISettingsService;
    CurrentUserService mockCurrentUserService;
    AIChatOrchestrator mockOrchestrator;
    SystemSettingsService mockSystemSettingsService;
    private final UUID TEST_USER_ID = UUID.randomUUID();
    @BeforeEach
    public void setup() {
        this.aiChatService = new AIChatService();
        mockAISettingsService = Mockito.mock(UserAISettingsService.class);
        mockCurrentUserService = Mockito.mock(CurrentUserService.class);
        mockOrchestrator = Mockito.mock(AIChatOrchestrator.class);
        mockSystemSettingsService = Mockito.mock(SystemSettingsService.class);
        injectField(aiChatService, "aiSettingsService", mockAISettingsService);
        injectField(aiChatService, "currentUserService", mockCurrentUserService);
        injectField(aiChatService, "orchestrator", mockOrchestrator);
        injectField(aiChatService, "systemSettingsService", mockSystemSettingsService);
        // Setup default mock behavior
        when(mockCurrentUserService.getCurrentUserId()).thenReturn(TEST_USER_ID);
        when(mockSystemSettingsService.getString("ai.default-system-message")).thenReturn(null);
        when(mockOrchestrator.chat(any(), any(), any(), any())).thenReturn("mocked-orchestrator-response");
    }

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inject field '" + fieldName + "'", e);
        }
    }
    /**
     * Test Case 1: OpenAI with API key (apiKeyRequired = true, key provided)
     * Expected: Should validate and require API key
     */
    @Test
    public void testOpenAIWithApiKey_Success() {
        UserAISettings openAISettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("sk-test-key-12345")
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-3.5-turbo")
                .apiKeyRequired(true)
                .build();
        when(mockAISettingsService.getAISettingsWithApiKey(TEST_USER_ID))
                .thenReturn(openAISettings);
        // Note: This will fail with actual API call, but we're testing validation logic
        // The validation should pass (not return error message about missing API key)
        String result = aiChatService.chat("Hello");
        // Should not return the "API key required" error message
        assertNotEquals("API Key is required but it's not provided. Please add your OpenAI API key in your profile settings.",
                result);
    }
    /**
     * Test Case 2: OpenAI WITHOUT API key (apiKeyRequired = true, key missing)
     * Expected: Should return error message about missing API key
     */
    @Test
    public void testOpenAIWithoutApiKey_ShouldFail() {
        UserAISettings openAISettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey(null) // Missing API key
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-3.5-turbo")
                .apiKeyRequired(true) // But API key is required
                .build();
        when(mockAISettingsService.getAISettingsWithApiKey(TEST_USER_ID))
                .thenReturn(openAISettings);
        String result = aiChatService.chat("Hello");
        // Should return error message about missing API key
        assertEquals("API Key is required but it's not provided. Please add your OpenAI API key in your profile settings.",
                result);
    }
    /**
     * Test Case 3: Ollama WITHOUT API key (apiKeyRequired = false, key missing)
     * Expected: Should NOT fail validation (should pass API key validation check)
     *
     */
    @Test
    public void testOllamaWithoutApiKey_ShouldNotFailValidation() {
        UserAISettings ollamaSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey(null) // No API key
                .openaiApiUrl("http://localhost:11434/v1")
                .openaiModel("llama2")
                .apiKeyRequired(false) // API key NOT required for Ollama
                .build();
        when(mockAISettingsService.getAISettingsWithApiKey(TEST_USER_ID))
                .thenReturn(ollamaSettings);
        String result = aiChatService.chat("Hello");
        // Should NOT return the "API key required" error message
        assertNotEquals("API Key is required but it's not provided. Please add your OpenAI API key in your profile settings.",
                result,
                "CRITICAL BUG: Ollama (apiKeyRequired=false) should not fail validation!");
    }
    /**
     * Test Case 4: Ollama with empty string API key (apiKeyRequired = false)
     * Expected: Should NOT fail validation
     */
    @Test
    public void testOllamaWithEmptyApiKey_ShouldNotFailValidation() {
        UserAISettings ollamaSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("") // Empty string instead of null
                .openaiApiUrl("http://localhost:11434/v1")
                .openaiModel("llama2")
                .apiKeyRequired(false)
                .build();
        when(mockAISettingsService.getAISettingsWithApiKey(TEST_USER_ID))
                .thenReturn(ollamaSettings);
        String result = aiChatService.chat("Hello");
        // Should NOT return the "API key required" error message
        assertNotEquals("API Key is required but it's not provided. Please add your OpenAI API key in your profile settings.",
                result,
                "CRITICAL BUG: Ollama with empty API key (apiKeyRequired=false) should not fail validation!");
    }
    /**
     * Test Case 5: Custom LLM provider without API key (apiKeyRequired = false)
     * Expected: Should NOT fail validation
     */
    @Test
    public void testCustomProviderWithoutApiKey_ShouldNotFailValidation() {
        UserAISettings customSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey(null)
                .openaiApiUrl("http://custom-llm.local:8080/v1")
                .openaiModel("custom-model")
                .apiKeyRequired(false) // Custom provider doesn't need API key
                .build();
        when(mockAISettingsService.getAISettingsWithApiKey(TEST_USER_ID))
                .thenReturn(customSettings);
        String result = aiChatService.chat("Hello");
        // Should NOT return the "API key required" error message
        assertNotEquals("API Key is required but it's not provided. Please add your OpenAI API key in your profile settings.",
                result,
                "Custom LLM provider (apiKeyRequired=false) should not fail API key validation!");
    }
    /**
     * Test Case 6: AI disabled
     * Expected: Should return disabled message
     */
    @Test
    public void testAIDisabled() {
        UserAISettings disabledSettings = UserAISettings.builder()
                .enabled(false) // AI is disabled
                .openaiApiKey("sk-key")
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-3.5-turbo")
                .apiKeyRequired(true)
                .build();
        when(mockAISettingsService.getAISettingsWithApiKey(TEST_USER_ID))
                .thenReturn(disabledSettings);
        String result = aiChatService.chat("Hello");
        assertEquals("AI Assistant is currently disabled. Please enable it in your profile settings.",
                result);
    }
    /**
     * Test Case 7: OpenAI with whitespace-only API key
     * Expected: Should fail validation
     */
    @Test
    public void testOpenAIWithWhitespaceApiKey_ShouldFail() {
        UserAISettings openAISettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey("   ") // Whitespace only
                .openaiApiUrl("https://api.openai.com/v1")
                .openaiModel("gpt-3.5-turbo")
                .apiKeyRequired(true)
                .build();
        when(mockAISettingsService.getAISettingsWithApiKey(TEST_USER_ID))
                .thenReturn(openAISettings);
        String result = aiChatService.chat("Hello");
        // Should return error message (whitespace should be treated as blank)
        assertEquals("API Key is required but it's not provided. Please add your OpenAI API key in your profile settings.",
                result);
    }
}
