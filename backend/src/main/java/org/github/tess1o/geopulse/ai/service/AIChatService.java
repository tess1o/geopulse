package org.github.tess1o.geopulse.ai.service;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.client.exception.AuthenticationException;
import org.github.tess1o.geopulse.ai.client.exception.ContextLengthExceededException;
import org.github.tess1o.geopulse.ai.client.exception.RateLimitException;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.ai.orchestration.AIChatOrchestrator;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;

import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AI_API_KEY_REQUIRED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AI_CONTEXT_TOO_LARGE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AI_DISABLED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AI_PROVIDER_AUTHENTICATION_FAILED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AI_RATE_LIMITED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_AI_REQUEST;

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
            - Default target scope is SELF. Use FRIEND scope only when user asks about a friend.
            - For friend queries, call listAccessibleTimelineFriends first to find valid friend identifiers.
            - If tool result returns an ambiguous friend error with candidates, ask user to choose one.
            - Do NOT silently fallback to SELF when user asked about a friend.
            - v1 supports one friend at a time only (no multi-friend comparison).

            CONVERSATION CONTEXT:
            - Before calling a tool, check if the answer is already in the conversation history.
            - For follow-up questions (like "on what date?", "how long?"), look at your previous responses first.
            - Only call tools if you need NEW data that isn't in the conversation history.
            - Avoid calling tools with broad queries that return too much data.

            TOOL SELECTION GUIDE:
            Use listAccessibleTimelineFriends for questions about:
            - Which friends shared timeline access
            - Finding valid friend identifiers before FRIEND-scoped tool calls

            Use listAccessibleLiveFriends and getFriendLiveLocation for questions about:
            - \"Where is my friend right now?\"
            - Current/live friend location
            - Which friends share live location access
            - If live location is stale, clearly say it is LAST KNOWN location and include timestamp
            - Always include when the friend was last seen for live location answers

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
                throw new GeoPulseException(AI_DISABLED, "AI Assistant is disabled");
            }

            // Check if configuration is valid
            if (isApiKeyInvalid(settings)) {
                log.info("OpenAI API key is required for user {}. ApiKey required = {}, ApiKey is empty = {}", userId,
                        settings.isApiKeyRequired(), settings.getOpenaiApiKey() == null || settings.getOpenaiApiKey().isEmpty());
                throw new GeoPulseException(AI_API_KEY_REQUIRED, "An AI provider API key is required");
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
            throw new GeoPulseException(AI_CONTEXT_TOO_LARGE,
                    "The conversation or data results are too large", e);
        } catch (RateLimitException e) {
            throw new GeoPulseException(AI_RATE_LIMITED, "The AI provider rate limit was exceeded", e);
        } catch (AuthenticationException e) {
            throw new GeoPulseException(AI_PROVIDER_AUTHENTICATION_FAILED,
                    "The AI provider rejected the configured credentials", e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_AI_REQUEST, "The AI provider rejected the request", e);
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
