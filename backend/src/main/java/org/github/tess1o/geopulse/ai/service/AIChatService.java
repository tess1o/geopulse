package org.github.tess1o.geopulse.ai.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.client.exception.ContextLengthExceededException;
import org.github.tess1o.geopulse.ai.client.exception.RateLimitException;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.ai.orchestration.AIChatOrchestrator;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;

import java.util.UUID;

@ApplicationScoped
@Slf4j
public class AIChatService {

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

            CONVERSATION CONTEXT:
            - Before calling a tool, check if the answer is already in the conversation history.
            - For follow-up questions (like "on what date?", "how long?"), look at your previous responses first.
            - Only call tools if you need NEW data that isn't in the conversation history.
            - Avoid calling tools with broad queries that return too much data.

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

    @Inject
    UserAISettingsService aiSettingsService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    AIChatOrchestrator orchestrator;

    @Inject
    SystemSettingsService systemSettingsService;

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

            log.info("Processing AI chat request for user {}", userId);
            String response = orchestrator.chat(userId, userMessage, settings, systemMessage);
            log.info("AI chat response generated for user {}", userId);
            return response;
        } catch (ContextLengthExceededException e) {
            log.warn("Context length exceeded for user {}: {}", userId, e.getMessage());
            return "The conversation or data results are too large. Please try:\n" +
                   "1. Ask a more specific question with a narrower date range\n" +
                   "2. Clear your chat history and start a new conversation\n" +
                   "3. Break your question into smaller parts";
        } catch (RateLimitException e) {
            log.warn("Rate limit exceeded for user {}: {}", userId, e.getMessage());
            return "I'm currently experiencing high demand and have reached my rate limit. Please wait a moment and try again, or consider upgrading your plan for higher limits.";
        } catch (Exception e) {
            log.error("Error processing AI chat for user {}: {}", userId, e.getMessage(), e);
            return "I apologize, but I encountered an error while processing your request. Please check your AI settings and try again.";
        }
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