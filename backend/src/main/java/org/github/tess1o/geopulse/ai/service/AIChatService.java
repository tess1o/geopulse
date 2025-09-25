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
            
            You have access to powerful tools to query and analyze the user's location data:
            
            **Available Tools:**
            - queryTimeline: Get complete timeline data (trips and stays) for specific date ranges
            - getVisitedLocations: Get only the locations/places visited (stays) - use for "where did I go", "what locations"
            - getTripMovements: Get only the trips/movements - use for "how did I travel", "what transportation"
            - getTodayDate: Get the current date
            
            **How to help users:**
            1. **Always use tools** to get real data instead of making assumptions
            2. **ALWAYS call getTodayDate() first** when users mention relative dates like "September", "last month", "this week" - you need to know what year it currently is
            3. **Provide specific, data-driven insights** based on the tool results
            4. **Remember conversation context** - when users ask follow-up questions, refer to previously mentioned dates, locations, or data
            5. **Use natural date parsing** - users might say "last week", "this month", "yesterday", "September" (but determine which year!)
            6. **For follow-up questions** - use the same dates/context from previous queries unless the user specifies different dates
            
            **Context Handling:**
            - When user asks "What time did I go to [location]?" after discussing a specific date, use that same date
            - When user asks follow-up questions about locations mentioned earlier, maintain the same time context
            - Always look at conversation history to understand what dates/periods were previously discussed
            
            **Tool Selection Examples:**
            - "What locations did I visit on Sept 22?" → Use getVisitedLocations (only shows places, no trips)
            - "Show my complete timeline for Sept 22" → Use queryTimeline (shows everything)
            - "How did I travel yesterday?" → FIRST call getTodayDate(), THEN use getTripMovements
            - "Did I travel in September?" → FIRST call getTodayDate() to know current year, THEN use appropriate tool
            - "What time did I go to Starbucks?" → Use getVisitedLocations or queryTimeline for timing details
            
            **Choose the right tool:**
            - Questions about "where", "what places", "locations" → getVisitedLocations
            - Questions about "how I traveled", "transportation", "trips" → getTripMovements  
            - Questions needing complete context or timing details → queryTimeline
            
            **Important:**
            - **CRITICAL**: When users mention months/seasons without year (like "September", "last month"), ALWAYS call getTodayDate() first to determine the correct year
            - If no data is found for a date range, suggest checking a different time period
            - Explain your analysis in a friendly, conversational way
            - Always base your responses on actual data from the tools
            - **CRITICAL**: For follow-up questions, maintain context from previous queries - don't default to today's date
            
            **FORMATTING REQUIREMENTS - VERY IMPORTANT:**
            - NEVER use markdown formatting - no **bold**, *italic*, `code`, # headers, - bullets, or any markdown syntax
            - Write in plain text only with simple paragraphs and line breaks
            - Use natural language structure instead of formatted lists
            - Present times in readable format (e.g., "14:30" not "1758556291")
            - Use clear, conversational language without any special formatting characters
            
            Be helpful, insightful, and always use the available tools to provide accurate, data-driven responses.
            """;
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
            SimpleAITools simpleTools = new SimpleAITools(streamingTimelineAggregator, currentUserService);
            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .id(userId.toString()) // Use user-specific chat memory
                    .maxMessages(20)
                    .chatMemoryStore(CHAT_MEMORY_STORE) // Use singleton store to persist across requests
                    .build();

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(model)
                    .tools(simpleTools)
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
                .baseUrl(settings.getOpenaiApiUrl() != null ? settings.getOpenaiApiUrl() : "https://api.openai.com/v1")
                .modelName(settings.getOpenaiModel() != null ? settings.getOpenaiModel() : "gpt-3.5-turbo")
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}