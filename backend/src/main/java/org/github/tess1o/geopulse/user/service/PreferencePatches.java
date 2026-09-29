package org.github.tess1o.geopulse.user.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Applies a partial update to a JSONB preference document without per-field code.
 *
 * <p>Patch semantics, shared by every preference document:
 * <ul>
 *     <li>{@code null} (or absent) leaves the stored value unchanged;</li>
 *     <li>a blank string removes the stored value, so it resolves to its default again;</li>
 *     <li>any other value replaces the stored one. Strings are trimmed and lists are replaced, not merged.</li>
 * </ul>
 */
final class PreferencePatches {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private PreferencePatches() {
    }

    @SuppressWarnings("unchecked")
    static <T> T apply(T current, T patch) {
        ObjectNode merged = MAPPER.valueToTree(current);
        if (patch != null) {
            ObjectNode changes = MAPPER.valueToTree(patch);
            changes.fields().forEachRemaining(field -> {
                JsonNode value = field.getValue();
                if (value.isTextual()) {
                    String trimmed = value.asText().trim();
                    if (trimmed.isEmpty()) {
                        merged.remove(field.getKey());
                        return;
                    }
                    value = TextNode.valueOf(trimmed);
                }
                merged.set(field.getKey(), value);
            });
        }
        try {
            return (T) MAPPER.treeToValue(merged, current.getClass());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not apply preference update to " + current.getClass().getSimpleName(), e);
        }
    }
}
