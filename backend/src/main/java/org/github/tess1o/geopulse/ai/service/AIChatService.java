package org.github.tess1o.geopulse.ai.service;

import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.statistics.service.RoutesAnalysisService;

import java.time.Duration;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class AIChatService {

    // Singleton chat memory store to persist conversations across requests
    private static final InMemoryChatMemoryStore CHAT_MEMORY_STORE = new InMemoryChatMemoryStore();

    /**
     * Default system message for AI assistant.
     * Users can override this with their own custom system message via settings.
     */
    public static final String SYSTEM_MESSAGE = """
            You are a location data analysis assistant with access to tools that retrieve real user data.

            TOOL USAGE RULES:
            - You do NOT have user data in your memory. You MUST use tools to get data.
            - Call tools immediately when asked about locations, cities, trips, distances, or time.
            - NEVER invent, guess, or fabricate data. Only answer based on tool results.
            - Do not explain what tool you're using. Just call it and provide results.

            TOOL SELECTION GUIDE:
            Use getStayStats for questions about:
            - Number of cities/locations/countries visited
            - Time spent at locations or in cities
            - Most/least visited places
            - Patterns grouped by location, city, country, day, week, or month

            Use getTripStats for questions about:
            - Distance traveled by transportation mode (walking, driving, etc.)
            - Number of trips by type or time period
            - Travel patterns grouped by mode, origin, destination, or time

            Use getRoutePatterns for questions about:
            - Most common routes taken
            - Route frequency and diversity
            - Average or longest trip duration/distance

            Use getTodayDate for any relative date ("this month", "last week", "yesterday").

            DATE HANDLING:
            - For relative dates like "this month" or "last year", first call getTodayDate to get the current date
            - Calculate the appropriate date range from the returned date
            - Use ISO-8601 format for dates: YYYY-MM-DD

            RESPONSE FORMAT:
            - Answer in the same language as the user's question
            - Convert seconds to readable time: "2 hours 15 minutes" not "8100 seconds"
            - Convert large distances: "2.5 km" not "2500 meters"
            - Provide clear, conversational responses without markdown formatting
            - Be concise and direct
            """;
    public static final String OPENAI_DEFAULT_URL = "https://api.openai.com/v1";
    public static final int DEFAULT_TIMEOUT_SECONDS = 600;

    @Inject
    UserAISettingsService aiSettingsService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    StreamingTimelineAggregator streamingTimelineAggregator;

    @Inject
    RoutesAnalysisService routesAnalysisService;

    @Inject
    org.github.tess1o.geopulse.admin.service.SystemSettingsService systemSettingsService;

    public interface Assistant {
        String chat(String userMessage);
    }

    public String chat(String userMessage) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            UserAISettings settings = aiSettingsService.getAISettingsWithApiKey(userId);

            // Check if AI is enabled
            if (!settings.isEnabled()) {
                log.info("AI Assistant is disabled for user {}", userId);
                return "AI Assistant is currently disabled. Please enable it in your profile settings.";
            }

            // Check if configuration is valid
            if (isApiKeyInvalid(settings)) {
                log.info("OpenAI API key is required for user {}. ApiKey required = {}, ApiKey is empty = {}", userId,
                        settings.isApiKeyRequired(), settings.getOpenaiApiKey() == null || settings.getOpenaiApiKey().isEmpty());
                return "API Key is required but it's not provided. Please add your OpenAI API key in your profile settings.";
            }

            ChatModel model = createChatModel(settings);

            // Determine which system message to use (priority: user custom > global default > built-in default)
            String systemMessage;
            if (settings.getCustomSystemMessage() != null && !settings.getCustomSystemMessage().isBlank()) {
                // User has a custom message
                systemMessage = settings.getCustomSystemMessage();
            } else {
                // Try global default, fall back to built-in default
                String globalDefault = systemSettingsService.getString("ai.default-system-message");
                systemMessage = (globalDefault != null && !globalDefault.isBlank())
                        ? globalDefault
                        : SYSTEM_MESSAGE;
            }

            // Create simple tools instance without CDI proxy
            AITimelineTools simpleTools = new AITimelineTools(streamingTimelineAggregator, currentUserService, routesAnalysisService);
            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .id(userId.toString()) // Use user-specific chat memory
                    .maxMessages(10)
                    .chatMemoryStore(CHAT_MEMORY_STORE) // Use singleton store to persist across requests
                    .build();

            // Inject system message into chat memory if it's a new conversation
            if (chatMemory.messages().isEmpty()) {
                chatMemory.add(dev.langchain4j.data.message.SystemMessage.from(systemMessage));
            }

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(model)
                    .tools(simpleTools, new SimpleAITools())
                    .chatMemory(chatMemory)
                    .build();

            log.info("Processing AI chat request for user {}", userId);
            String response = assistant.chat(userMessage);
            log.info("AI chat response generated for user {}, response: {}", userId, response);
            return response;
        } catch (RateLimitException e) {
            log.warn("Rate limit exceeded for user {}: {}", userId, e.getMessage());
            return "I'm currently experiencing high demand and have reached my rate limit. Please wait a moment and try again, or consider upgrading your plan for higher limits.";
        } catch (Exception e) {
            log.error("Error processing AI chat for user {}: {}", userId, e.getMessage(), e);
            return "I apologize, but I encountered an error while processing your request. Please check your AI settings and try again.";
        }
    }

    private ChatModel createChatModel(UserAISettings settings) {
        if (isApiKeyInvalid(settings)) {
            throw new IllegalArgumentException("OpenAI API key is required");
        }

        return OpenAiChatModel.builder()
                .apiKey(settings.getOpenaiApiKey())
                .baseUrl(settings.getOpenaiApiUrl() != null ? settings.getOpenaiApiUrl() : OPENAI_DEFAULT_URL)
                .modelName(settings.getOpenaiModel() != null ? settings.getOpenaiModel() : "gpt-3.5-turbo")
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .logResponses(true)
                .logRequests(true)
                .build();
    }

    /**
     * Check if API key is required but not provided.
     * When API is not required even we provide it it will be ignored
     *
     * @param settings
     * @return
     */
    private static boolean isApiKeyInvalid(UserAISettings settings) {
        return settings.isApiKeyRequired() &&
                (settings.getOpenaiApiKey() == null || settings.getOpenaiApiKey().trim().isEmpty());
    }
}