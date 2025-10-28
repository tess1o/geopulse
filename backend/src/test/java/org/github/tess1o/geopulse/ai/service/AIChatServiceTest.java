package org.github.tess1o.geopulse.ai.service;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.statistics.service.RoutesAnalysisService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test for AIChatService focusing on API key validation logic.
 * <p>
 * This test verifies the critical bug fix where Ollama (and other local LLM providers)
 * that don't require API keys were incorrectly rejected by the createChatModel() method.
 * <p>
 * The bug: createChatModel() always checked for API key existence, even when
 * settings.isApiKeyRequired() was false.
 */
@QuarkusTest
public class AIChatServiceTest {

    @Inject
    AIChatService aiChatService;

    private UserAISettingsService mockAISettingsService;
    private CurrentUserService mockCurrentUserService;
    private StreamingTimelineAggregator mockStreamingTimelineAggregator;
    private RoutesAnalysisService mockRoutesAnalysisService;

    private final UUID TEST_USER_ID = UUID.randomUUID();

    @BeforeEach
    public void setup() {
        // Create mocks
        mockAISettingsService = Mockito.mock(UserAISettingsService.class);
        mockCurrentUserService = Mockito.mock(CurrentUserService.class);
        mockStreamingTimelineAggregator = Mockito.mock(StreamingTimelineAggregator.class);
        mockRoutesAnalysisService = Mockito.mock(RoutesAnalysisService.class);

        // Install mocks
        QuarkusMock.installMockForType(mockAISettingsService, UserAISettingsService.class);
        QuarkusMock.installMockForType(mockCurrentUserService, CurrentUserService.class);
        QuarkusMock.installMockForType(mockStreamingTimelineAggregator, StreamingTimelineAggregator.class);
        QuarkusMock.installMockForType(mockRoutesAnalysisService, RoutesAnalysisService.class);

        // Setup default mock behavior
        when(mockCurrentUserService.getCurrentUserId()).thenReturn(TEST_USER_ID);
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
     * Expected: Should NOT throw exception in createChatModel() - this is the critical bug fix test
     *
     */
    @Test
    public void testOllamaWithoutApiKey_ShouldNotFailValidation() throws Exception {
        UserAISettings ollamaSettings = UserAISettings.builder()
                .enabled(true)
                .openaiApiKey(null) // No API key
                .openaiApiUrl("http://localhost:11434/v1")
                .openaiModel("llama2")
                .apiKeyRequired(false) // API key NOT required for Ollama
                .build();

        // Use reflection to test the private createChatModel() method directly
        Method createChatModelMethod = AIChatService.class.getDeclaredMethod("createChatModel", UserAISettings.class);
        createChatModelMethod.setAccessible(true);

        assertDoesNotThrow(() -> {
            try {
                createChatModelMethod.invoke(aiChatService, ollamaSettings);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }, "CRITICAL BUG: createChatModel() should not throw exception for Ollama (apiKeyRequired=false)!");
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
