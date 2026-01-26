package org.github.tess1o.geopulse.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.client.dto.ChatRequest;
import org.github.tess1o.geopulse.ai.client.dto.ChatResponse;
import org.github.tess1o.geopulse.ai.client.exception.AuthenticationException;
import org.github.tess1o.geopulse.ai.client.exception.ContextLengthExceededException;
import org.github.tess1o.geopulse.ai.client.exception.RateLimitException;
import org.github.tess1o.geopulse.ai.model.UserAISettings;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Slf4j
public class OpenAIChatClient {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SystemSettingsService systemSettingsService;

    public ChatResponse chat(ChatRequest request, UserAISettings settings) {
        try {
            // Log the request details if logging is enabled
            boolean loggingEnabled = systemSettingsService.getBoolean("ai.logging.enabled");
            if (loggingEnabled) {
                logRequest(request, settings);
            }

            RestClientBuilder builder = RestClientBuilder.newBuilder()
                    .baseUri(new URI(settings.getOpenaiApiUrl()))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(600, TimeUnit.SECONDS);

            if (settings.isApiKeyRequired()) {
                builder.header("Authorization", "Bearer " + settings.getOpenaiApiKey());
            }

            OpenAIChatRestClient client = builder.build(OpenAIChatRestClient.class);
            ChatResponse response = client.chatCompletion(request);

            // Log the response details if logging is enabled
            if (loggingEnabled) {
                logResponse(response);
            }

            return response;

        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();

            // Try to extract and log the error response body
            String errorBody = null;
            try {
                if (e.getResponse().hasEntity()) {
                    errorBody = e.getResponse().readEntity(String.class);
                    log.error("OpenAI API Error Response (" + status + "): " + errorBody);
                }
            } catch (Exception ex) {
                log.warn("Could not read error response body", ex);
            }

            // Check for context_length_exceeded error
            if (errorBody != null && errorBody.contains("context_length_exceeded")) {
                log.warn("Context length exceeded - conversation or tool results too large");
                throw new ContextLengthExceededException("Conversation or tool results exceeded token limit. Try asking a more specific question or clearing chat history.", e);
            }

            if (status == 429) {
                log.warn("Rate limit exceeded for user AI settings");
                throw new RateLimitException("OpenAI API rate limit exceeded", e);
            } else if (status == 401 || status == 403) {
                log.warn("Authentication failed for OpenAI API");
                throw new AuthenticationException("Invalid API key or unauthorized access", e);
            } else if (status >= 400 && status < 500) {
                log.error("Client error calling OpenAI API: {}", status, e);
                String errorMessage = "Invalid request to OpenAI API: " + e.getMessage();
                if (errorBody != null) {
                    errorMessage += " - Response: " + errorBody;
                }
                throw new IllegalArgumentException(errorMessage, e);
            } else if (status >= 500) {
                log.error("Server error from OpenAI API: {}", status, e);
                throw new RuntimeException("OpenAI API server error", e);
            }

            throw e;
        } catch (Exception e) {
            log.error("Unexpected error calling OpenAI API", e);
            throw new RuntimeException("Failed to call OpenAI API", e);
        }
    }

    private static void logResponse(ChatResponse response) {
        log.info("=== OpenAI Response ===");
        log.info("Response ID: " + response.id());
        log.info("Model: " + response.model());
        log.info("Choices: " + (response.choices() != null ? response.choices().size() : 0));
        if (response.usage() != null) {
            log.info("Tokens - Prompt: " + response.usage().promptTokens() +
                    ", Completion: " + response.usage().completionTokens() +
                    ", Total: " + response.usage().totalTokens());
        }
    }

    private void logRequest(ChatRequest request, UserAISettings settings) {
        log.info("=== OpenAI Request ===");
        log.info("URL: " + settings.getOpenaiApiUrl());
        log.info("Model: " + settings.getOpenaiModel());
        log.info("API Key Required: " + settings.isApiKeyRequired());
        log.info("API Key Present: " + (settings.getOpenaiApiKey() != null && !settings.getOpenaiApiKey().isEmpty()));
        log.info("Messages count: " + (request.messages() != null ? request.messages().size() : 0));
        log.info("Tools count: " + (request.tools() != null ? request.tools().size() : 0));

        try {
            String requestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
            log.info("Request Body:\n" + requestJson);
        } catch (Exception e) {
            log.warn("Failed to serialize request for logging", e);
        }
    }
}
