package org.github.tess1o.geopulse.notes.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.notes.model.MemosConfigResponse;
import org.github.tess1o.geopulse.notes.model.MemosPreferences;
import org.github.tess1o.geopulse.notes.model.MemosVisibility;
import org.github.tess1o.geopulse.notes.model.NoteDestination;

import java.util.Locale;

@ApplicationScoped
public class MemosPreferencesService {

    MemosConfigResponse toConfigResponse(MemosPreferences preferences) {
        return MemosConfigResponse.builder()
                .serverUrl(preferences.getServerUrl())
                .apiKey(preferences.getApiKey())
                .enabled(Boolean.TRUE.equals(preferences.getEnabled()))
                .defaultSaveDestination(preferences.getDefaultSaveDestination())
                .defaultVisibility(preferences.getDefaultVisibility())
                .maxNotesPerRequest(preferences.getMaxNotesPerRequest())
                .maxContentBytes(preferences.getMaxContentBytes())
                .build();
    }

    MemosPreferences applyDefaults(MemosPreferences preferences) {
        MemosPreferences safe = preferences != null ? preferences : new MemosPreferences();
        if (safe.getDefaultSaveDestination() == null) {
            safe.setDefaultSaveDestination(NoteDestination.GEOPULSE);
        }
        if (safe.getDefaultVisibility() == null) {
            safe.setDefaultVisibility(MemosVisibility.PRIVATE);
        }
        if (safe.getMaxNotesPerRequest() == null || safe.getMaxNotesPerRequest() <= 0) {
            safe.setMaxNotesPerRequest(TimelineNoteConstants.DEFAULT_SEARCH_LIMIT);
        }
        if (safe.getMaxContentBytes() == null || safe.getMaxContentBytes() <= 0) {
            safe.setMaxContentBytes(TimelineNoteConstants.DEFAULT_MEMOS_CONTENT_BYTES);
        }
        return safe;
    }

    String normalizeServerUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return serverUrl;
        }
        String normalized = serverUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    int resolvePositiveOrDefault(Integer requested, Integer existing, int fallback) {
        if (requested != null && requested > 0) {
            return requested;
        }
        if (existing != null && existing > 0) {
            return existing;
        }
        return fallback;
    }

    String resolveErrorMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null) {
            if (message.contains("401") || message.contains("Unauthorized")) {
                return "Authentication failed";
            }
            if (message.contains("404") || message.contains("Not Found")) {
                return "Server not found";
            }
            if (message.toLowerCase(Locale.ROOT).contains("timeout") || message.contains("Connection refused")) {
                return "Connection timeout";
            }
        }
        return "Failed to connect to Memos server";
    }
}
