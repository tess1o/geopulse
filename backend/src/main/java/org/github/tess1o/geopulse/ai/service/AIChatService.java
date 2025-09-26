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

    public static final String SYSTEM_MESSAGE = """
            You are GeoPulse AI Assistant, a smart helper for analyzing user location data.

            **Core Directives:**
            - Execute tools immediately to answer questions; do not explain which tool you are using.
            - Always use tools to fetch real data. Do not invent answers.
            - For questions involving dates like "last month" or "this week," call `getTodayDate()` first to establish the current date.
            - Base all responses on the data returned by the tools.

            **Tool Selection Priority:**
            1. Use STAY STATS (`getStayStats`) for: time spent at locations, visit frequency, city/location counting, "how much time", "which places"
            2. Use TRIP STATS (`getTripStats`) for: transportation modes, travel distances by mode, speed analysis, "how far by walking/driving"  
            3. Use ROUTE PATTERNS (`getRoutePatterns`) for: most common routes, route frequency, "Home→Office", travel pattern diversity
            4. Use BASIC TOOLS (`queryTimeline`, `getVisitedLocations`, `getTripMovements`) for: specific events, detailed timeline data, listing items
            5. Any question with "which month/week/day had most/least" MUST use statistical tools with appropriate time grouping

            **Key Examples:**
            - "How much time at each location?" → getStayStats with LOCATION_NAME grouping
            - "How much time in each city?" → getStayStats with CITY grouping  
            - "Which city did I visit most frequently?" → getStayStats with CITY grouping
            - "What locations did I visit in Boston?" → getStayStats with CITY grouping
            - "In which month did I visit most cities?" → getStayStats with MONTH grouping (use uniqueCityCount)
            - "Which week had most travel diversity?" → getStayStats with WEEK grouping (use uniqueLocationCount)
            - "When did I first visit New York?" → getStayStats with CITY grouping (use firstStayStart)
            - "Where do I spend most time each week?" → getStayStats with WEEK grouping (use dominantLocation)
            - "Did I walk more or drive more?" → getTripStats with MOVEMENT_TYPE grouping
            - "How far did I travel by car vs walking?" → getTripStats with MOVEMENT_TYPE grouping
            - "What's my average travel speed?" → getTripStats (use avgSpeedKmh field)
            - "What's my most common route?" → getRoutePatterns (use mostCommonRoute field)
            - "How many unique routes do I take?" → getRoutePatterns (use uniqueRoutesCount field)
            - "What's my longest trip?" → getRoutePatterns (use longestTripDistance/Duration fields)

            **Enhanced Fields Usage:**
            - Use uniqueCityCount for counting distinct cities in each group
            - Use uniqueLocationCount for counting distinct locations in each group
            - Use firstStayStart for "when did I first visit X" questions
            - Use dominantLocation for "where do I spend most time" questions
            - Use avgSpeedKmh for speed analysis and efficiency comparisons
            - Use minDurationSeconds/maxDurationSeconds for trip duration ranges
            - Use mostCommonRoute for identifying primary travel patterns

            **MANDATORY Unit Conversions:**
            - NEVER show raw seconds - ALWAYS convert to human-readable time:
              * Under 60 seconds: "45 seconds"
              * 60-3599 seconds: "25 minutes" or "1 hour 30 minutes"  
              * 3600+ seconds: "2 hours 15 minutes" or "1 day 5 hours"
            - NEVER show raw meters over 1000 - convert to kilometers: "2.5 km"
            - Example: "1732915 seconds" → "20 days 1 hour 28 minutes"

            **Response Requirements:**
            - Respond in clear, conversational paragraphs
            - Do not use lists, bold/italic text, or code blocks
            - Do not show raw JSON data to the user
            """;
    public static final String OPENAI_DEFAULT_URL = "https://api.openai.com/v1";

    @Inject
    UserAISettingsService aiSettingsService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    StreamingTimelineAggregator streamingTimelineAggregator;
    
    @Inject
    RoutesAnalysisService routesAnalysisService;

    interface Assistant {
        @SystemMessage(SYSTEM_MESSAGE)
        String chat(String userMessage);
    }

    public String chat(String userMessage) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            UserAISettings settings = aiSettingsService.getAISettingsWithApiKey(userId);

            // Check if AI is enabled
            if (!settings.isEnabled()) {
                return "AI Assistant is currently disabled. Please enable it in your profile settings.";
            }

            // Check if configuration is valid
            if (settings.getOpenaiApiKey() == null || settings.getOpenaiApiKey().isBlank()) {
                return "AI Assistant is not configured. Please add your OpenAI API key in your profile settings.";
            }

            log.info("AI enabled for user {}", userId);
            ChatModel model = createChatModel(settings);

            // Create simple tools instance without CDI proxy
            AITimelineTools simpleTools = new AITimelineTools(streamingTimelineAggregator, currentUserService, routesAnalysisService);
            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .id(userId.toString()) // Use user-specific chat memory
                    .maxMessages(10)
                    .chatMemoryStore(CHAT_MEMORY_STORE) // Use singleton store to persist across requests
                    .build();

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(model)
                    .tools(simpleTools, new SimpleAITools())
                    .chatMemory(chatMemory)
                    .build();

            log.info("Created AI assistant with tools for user {}", userId);


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
        if (settings.getOpenaiApiKey() == null || settings.getOpenaiApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("OpenAI API key is required");
        }

        return OpenAiChatModel.builder()
                .apiKey(settings.getOpenaiApiKey())
                .baseUrl(settings.getOpenaiApiUrl() != null ? settings.getOpenaiApiUrl() : OPENAI_DEFAULT_URL)
                .modelName(settings.getOpenaiModel() != null ? settings.getOpenaiModel() : "gpt-3.5-turbo")
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}