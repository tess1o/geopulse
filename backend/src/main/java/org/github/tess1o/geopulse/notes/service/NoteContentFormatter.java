package org.github.tess1o.geopulse.notes.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class NoteContentFormatter {

    private static final int TITLE_MAX_LENGTH = 255;
    private static final int SNIPPET_MAX_LENGTH = 500;

    String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String trimmed = title.trim();
        return trimmed.length() > TITLE_MAX_LENGTH ? trimmed.substring(0, TITLE_MAX_LENGTH) : trimmed;
    }

    String buildSnippet(String content) {
        return truncate(resolveSnippet(null, content), SNIPPET_MAX_LENGTH);
    }

    String resolveSnippet(String providedSnippet, String content) {
        if (providedSnippet != null && !providedSnippet.isBlank()) {
            return truncate(providedSnippet.trim(), SNIPPET_MAX_LENGTH);
        }
        String normalized = content == null ? "" : content
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("[#*_`>\\[\\]()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return truncate(normalized, SNIPPET_MAX_LENGTH);
    }

    boolean isTooLarge(String content, int maxBytes) {
        return content != null && content.getBytes(StandardCharsets.UTF_8).length > maxBytes;
    }

    String truncateByChars(String content, int maxBytes) {
        if (content == null) {
            return "";
        }
        int maxChars = Math.max(1000, Math.min(content.length(), maxBytes));
        return truncate(content, maxChars);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }
}
