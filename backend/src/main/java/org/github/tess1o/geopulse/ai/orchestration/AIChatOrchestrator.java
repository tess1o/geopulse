package org.github.tess1o.geopulse.ai.orchestration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.client.OpenAIChatClient;
import org.github.tess1o.geopulse.ai.client.dto.*;
import org.github.tess1o.geopulse.ai.memory.ChatMemoryStore;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.ai.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class AIChatOrchestrator {

    private static final int MAX_FUNCTION_CALLS = 10;

    @Inject
    OpenAIChatClient openAIClient;

    @Inject
    ToolRegistry toolRegistry;

    @Inject
    ChatMemoryStore chatMemory;

    @Inject
    SystemSettingsService systemSettingsService;

    public String chat(UUID userId, String userMessage, UserAISettings settings, String systemMessage) {
        // Chat Memory Strategy:
        // - Store in persistent memory: user messages + final assistant responses (finish_reason="stop")
        // - Do NOT store: system messages, tool_calls messages, tool response messages
        // - Tool calls/responses are only needed within the current conversation iteration
        // This ensures the conversation history remains clean and valid for subsequent queries

        // 1. Build message list (system + history + user)
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemMessage, null, null)); // Temporary, not stored
        messages.addAll(chatMemory.getMessages(userId)); // Load persistent history

        ChatMessage userMsg = new ChatMessage("user", userMessage, null, null);
        messages.add(userMsg);
        chatMemory.addMessage(userId, userMsg); // Store user message

        boolean loggingEnabled = systemSettingsService.getBoolean("ai.logging.enabled");
        if (loggingEnabled) {
            log.info("Starting AI chat for user " + userId + " with message: " + userMessage);
        }

        // 2. Function calling loop
        int functionCallCount = 0;
        while (functionCallCount < MAX_FUNCTION_CALLS) {
            ChatRequest request = new ChatRequest(
                    settings.getOpenaiModel(),
                    messages,
                    toolRegistry.getAllToolDefinitions(),
                    null,
                    null
            );

            if (loggingEnabled) {
                log.info("Sending request to OpenAI (iteration " + (functionCallCount + 1) + ")");
                log.info("Current message count: " + messages.size());
                log.info("Available tools: " + toolRegistry.getAllToolDefinitions().size());
            }
            ChatResponse response = openAIClient.chat(request, settings);

            if (response.choices() == null || response.choices().isEmpty()) {
                log.error("No choices returned from OpenAI API");
                return "I apologize, but I received an invalid response. Please try again.";
            }

            Choice choice = response.choices().get(0);
            ChatMessage assistantMessage = choice.message();
            String finishReason = choice.finishReason();

            if (loggingEnabled) {
                log.info("Received response with finish_reason: " + finishReason);
            }

            // 3. Handle finish reason
            if ("stop".equals(finishReason)) {
                // Normal completion - store final assistant response in persistent memory
                chatMemory.addMessage(userId, assistantMessage); // This is the final answer after tool calls
                if (loggingEnabled) {
                    log.info("Chat completed normally for user " + userId);
                }
                return assistantMessage.getContent();
            }

            if ("tool_calls".equals(finishReason)) {
                // Assistant wants to call tools
                // NOTE: We add this to the current conversation messages, but NOT to persistent chat memory
                // Tool calls and tool responses are only needed for this conversation iteration
                messages.add(assistantMessage);

                if (assistantMessage.getToolCalls() == null || assistantMessage.getToolCalls().isEmpty()) {
                    log.error("Finish reason is tool_calls but no tool calls present");
                    return "I encountered an error processing your request. Please try again.";
                }

                // Execute each tool call
                for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                    String toolName = toolCall.function().name();
                    String arguments = toolCall.function().arguments();

                    if (loggingEnabled) {
                        log.info("Executing tool: " + toolName + " with arguments: " + arguments);
                    }

                    try {
                        String result = toolRegistry.invokeTool(toolName, arguments);

                        // Truncate result if it's too large to avoid token limit issues
                        int maxToolResultLength = systemSettingsService.getInteger("ai.tool-result.max-length");
                        String truncatedResult = result;
                        if (result.length() > maxToolResultLength) {
                            truncatedResult = result.substring(0, maxToolResultLength)
                                + "\n\n[TRUNCATED - Result too large. "
                                + (result.length() - maxToolResultLength)
                                + " characters omitted. Consider using more specific date ranges or filters.]";
                            log.warn("Tool result truncated from {} to {} characters for tool: {}",
                                result.length(), maxToolResultLength, toolName);
                        }

                        if (loggingEnabled) {
                            log.info("Tool " + toolName + " returned: " + truncatedResult);
                        }

                        ChatMessage toolResponse = new ChatMessage("tool", truncatedResult, null, null);
                        toolResponse.setToolCallId(toolCall.id());

                        // Add to current conversation only, NOT to persistent chat memory
                        messages.add(toolResponse);
                    } catch (Exception e) {
                        log.error("Error executing tool " + toolName, e);
                        String errorMessage = "{\"error\": \"Failed to execute tool: " + e.getMessage() + "\"}";
                        ChatMessage toolResponse = new ChatMessage("tool", errorMessage, null, null);
                        toolResponse.setToolCallId(toolCall.id());

                        // Add to current conversation only, NOT to persistent chat memory
                        messages.add(toolResponse);
                    }
                }

                functionCallCount++;
                if (loggingEnabled) {
                    log.info("Function call count: " + functionCallCount);
                }
                continue; // Loop back to LLM with tool results
            }

            if ("length".equals(finishReason)) {
                if (loggingEnabled) {
                    log.info("Response too long for user " + userId);
                }
                return "Response too long. Please ask a more specific question.";
            }

            // Unknown finish reason
            log.error("Unknown finish reason: " + finishReason);
            throw new IllegalStateException("Unknown finish reason: " + finishReason);
        }

        if (loggingEnabled) {
            log.info("Max function calls reached for user " + userId);
        }
        return "Query too complex. Please break it into smaller questions.";
    }
}
