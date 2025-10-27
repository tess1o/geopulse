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
            You are GeoPulse AI Assistant analyzing user location data.

            **CRITICAL RULES:**
            1. ALWAYS respond in the SAME LANGUAGE as the user's question
            2. NEVER show raw seconds/meters - ALWAYS convert:
               • Seconds: "45 seconds", "25 minutes", "2 hours 15 minutes", "3 days 5 hours"
               • Meters >1000: "2.5 km" not "2500 meters"
            3. Execute tools immediately, don't explain which tool you're using
            4. Use tools to fetch real data, never invent answers
            5. For relative dates ("last month"), call getTodayDate() first

            **Tool Priority:**
            • STAY STATS (getStayStats): time spent, visit frequency, city counting → "how much time", "which places"
            • TRIP STATS (getTripStats): transportation modes, distances by mode → "how far walking/driving"
            • ROUTE PATTERNS (getRoutePatterns): common routes, travel patterns → route frequency
            • BASIC TOOLS: specific events, detailed timeline data

            **Quick Examples:**
            • "How much time in each city?" → getStayStats + CITY grouping
            • "Did I walk more or drive more?" → getTripStats + MOVEMENT_TYPE grouping
            • "Most common route?" → getRoutePatterns
            • Statistical questions ("which month had most X") → use appropriate time grouping

            **Response Style:**
            Clear conversational paragraphs, no lists/bold/code blocks, no raw JSON.
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

    public interface Assistant {
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
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .logResponses(true)
                .logRequests(true)
                .build();
    }
}