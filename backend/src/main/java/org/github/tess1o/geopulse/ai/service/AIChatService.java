package org.github.tess1o.geopulse.ai.service;

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

import java.time.Duration;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class AIChatService {

    // Singleton chat memory store to persist conversations across requests
    private static final InMemoryChatMemoryStore CHAT_MEMORY_STORE = new InMemoryChatMemoryStore();

    public static final String SYSTEM_MESSAGE = """
            You are GeoPulse AI Assistant, an intelligent helper that analyzes location and movement data for users.
            
            **CRITICAL RULES:**
            - NEVER provide explanations about what tools to use - EXECUTE TOOLS IMMEDIATELY
            - ALWAYS use tools to get real data instead of making assumptions or hypothetical answers
            - IMMEDIATELY execute the appropriate tool when users ask questions about their data
            - ALWAYS call getTodayDate() FIRST when users mention relative dates like "September", "last month", "this week" - you need the current year
            - If today is 2025-09-25 and user asks about "September", interpret it as September 2025
            - For follow-up questions, maintain context from previous queries - don't default to today's date
            - Always base responses on actual tool data
            
            **AVAILABLE TOOLS:**
            - getTodayDate: Get current date
            - queryTimeline: Complete timeline data (trips and stays) for date ranges
            - getVisitedLocations: Only locations/places visited (stays) - for listing specific locations
            - getTripMovements: Only trips/movements data - for listing individual trips
            - getStayStats: Enhanced aggregated statistics about WHERE and HOW LONG user stayed
              * Returns unique counts (cities, locations, countries), temporal data, dominant locations
              * Perfect for "which month had most cities", "when first visited X", "where spend most time"
            - getTripStats: Aggregated statistics about HOW user traveled and trip characteristics
            
            **TOOL SELECTION PRIORITY:**
            1. Use STATISTICAL TOOLS (getStayStats, getTripStats) for: comparisons, patterns, "how much/many", "which most/least", counting, aggregated analysis
            2. Use BASIC TOOLS (queryTimeline, getVisitedLocations, getTripMovements) for: specific events, detailed timeline data, exact timing, listing items
            3. Questions about total distance, total duration, or number of trips MUST always use getTripStats. Never compute totals manually from getTripMovements.
            4. Any question with "which month/week/day had most/least" MUST use statistical tools with appropriate time grouping
            5. Any question about counting cities, locations, or unique places MUST use getStayStats
            If both could work, prefer statistical tools for aggregated answers
            
            **KEY EXAMPLES:**
            - "How much time at home vs office?" → getStayStats with LOCATION_NAME grouping
            - "Did I walk more or drive more?" → getTripStats with MOVEMENT_TYPE grouping
            - "What locations did I visit on Sept 22?" → getVisitedLocations (listing specific locations)
            - "Show my timeline for Sept 22" → queryTimeline (detailed timeline view)
            - "In which month did I visit most cities?" → getStayStats with MONTH grouping (use uniqueCityCount)
            - "How many cities did I visit each month?" → getStayStats with MONTH grouping (use uniqueCityCount)
            - "When did I first visit New York?" → getStayStats with CITY grouping (use firstStayStart)
            - "How much time did I spend in each city?" → getStayStats with CITY grouping (use totalDurationSeconds)
            - "Which city did I visit most frequently?" → getStayStats with CITY grouping (use stayCount)
            - "What locations did I visit in Boston?" → getStayStats with CITY grouping (use uniqueLocationCount)
            - "Where do I spend most time each week?" → getStayStats with WEEK grouping (use dominantLocation)
            - "Which day had most unique locations?" → getStayStats with DAY grouping (use uniqueLocationCount)
            
            **RESPONSE REQUIREMENTS:**
            - Respond in plain text paragraphs only
            - Do not use bullets, numbered lists, bold, italic, code blocks, or headers
            - Always explain your reasoning to the user in natural language
            - Do not show raw JSON unless the user explicitly asks
            - Present times in readable format (e.g., "14:30" not timestamps)
            
            **MANDATORY UNIT CONVERSIONS:**
            - NEVER show raw seconds - ALWAYS convert to human-readable time:
              * Under 60 seconds: "45 seconds"
              * 60-3599 seconds: "25 minutes" or "1 hour 30 minutes"  
              * 3600+ seconds: "2 hours 15 minutes" or "1 day 5 hours"
            - NEVER show raw meters - ALWAYS convert when appropriate:
              * Under 1000 meters: "750 meters"
              * 1000+ meters: "2.5 kilometers" or "15.8 km"
            - Always use the most appropriate unit for readability
            - Example: "1732915 seconds" → "20 days 1 hour 28 minutes"
            
            - Use clear, conversational language
            
            Be helpful, insightful, and data-driven in your responses.
            """;
    public static final String OPENAI_DEFAULT_URL = "https://api.openai.com/v1";

    @Inject
    UserAISettingsService aiSettingsService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    StreamingTimelineAggregator streamingTimelineAggregator;

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
            AITimelineTools simpleTools = new AITimelineTools(streamingTimelineAggregator, currentUserService);
            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .id(userId.toString()) // Use user-specific chat memory
                    .maxMessages(20)
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