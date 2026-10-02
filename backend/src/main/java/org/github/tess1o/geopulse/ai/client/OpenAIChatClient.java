package org.github.tess1o.geopulse.ai.client;

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
import org.github.tess1o.geopulse.ai.client.exception.OpenAiApiException;
import org.github.tess1o.geopulse.ai.model.UserAISettings;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Slf4j
public class OpenAIChatClient {

    @Inject
    SystemSettingsService systemSettingsService;

    public ChatResponse chat(ChatRequest request, UserAISettings settings) {
        long startedAt = System.nanoTime();
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
                logResponse(response, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            }

            return response;

        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();

            // Read only to classify context-limit failures; provider bodies are never logged.
            String errorBody = null;
            try {
                if (e.getResponse().hasEntity()) {
                    errorBody = e.getResponse().readEntity(String.class);
                }
            } catch (Exception ex) {
                log.warn("AI provider error body could not be read: provider=OPENAI, errorClass={}", ex.getClass().getSimpleName());
            }

            // Check for context_length_exceeded error
            if (errorBody != null && errorBody.contains("context_length_exceeded")) {
                log.warn("AI request failed: provider=OPENAI, status={}, errorClass=ContextLengthExceededException", status);
                throw new ContextLengthExceededException("Conversation or tool results exceeded token limit. Try asking a more specific question or clearing chat history.", e);
            }

            if (status == 429) {
                log.warn("AI request failed: provider=OPENAI, status={}, errorClass=RateLimitException", status);
                throw new RateLimitException("OpenAI API rate limit exceeded", e);
            } else if (status == 401 || status == 403) {
                log.warn("AI request failed: provider=OPENAI, status={}, errorClass=AuthenticationException", status);
                throw new AuthenticationException("Invalid API key or unauthorized access", e);
            } else if (status >= 400 && status < 500) {
                log.error("AI request failed: provider=OPENAI, status={}, errorClass={}", status, e.getClass().getSimpleName(), e);
                throw new IllegalArgumentException("Invalid request to OpenAI API", e);
            } else if (status >= 500) {
                log.error("AI request failed: provider=OPENAI, status={}, errorClass={}", status, e.getClass().getSimpleName(), e);
                throw new OpenAiApiException("OpenAI API server error", e);
            }

            throw new OpenAiApiException("OpenAI API request failed", e);
        } catch (Exception e) {
            log.error("AI request failed: provider=OPENAI, status=unknown, errorClass={}", e.getClass().getSimpleName(), e);
            throw new OpenAiApiException("Failed to call OpenAI API", e);
        }
    }

    private static void logResponse(ChatResponse response, long latencyMs) {
        int choices = response.choices() == null ? 0 : response.choices().size();
        if (response.usage() != null) {
            log.info("AI response metadata: provider=OPENAI, model={}, choices={}, latencyMs={}, promptTokens={}, completionTokens={}, totalTokens={}",
                    response.model(), choices, latencyMs, response.usage().promptTokens(),
                    response.usage().completionTokens(), response.usage().totalTokens());
        } else {
            log.info("AI response metadata: provider=OPENAI, model={}, choices={}, latencyMs={}", response.model(), choices, latencyMs);
        }
    }

    private void logRequest(ChatRequest request, UserAISettings settings) {
        log.info("AI request metadata: provider=OPENAI, model={}, messages={}, tools={}, apiKeyRequired={}",
                settings.getOpenaiModel(), request.messages() == null ? 0 : request.messages().size(),
                request.tools() == null ? 0 : request.tools().size(), settings.isApiKeyRequired());
    }
}
