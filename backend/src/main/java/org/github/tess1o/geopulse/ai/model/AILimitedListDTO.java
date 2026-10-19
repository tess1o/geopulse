package org.github.tess1o.geopulse.ai.model;

import java.util.List;

/**
 * A list capped for AI context. {@code totalCount} is the size before capping, so the model can tell when
 * {@code items} is not everything.
 */
public record AILimitedListDTO<T>(List<T> items, int totalCount, boolean truncated) {

    public static <T> AILimitedListDTO<T> of(List<T> all, int limit) {
        List<T> items = all.size() > limit ? all.subList(0, limit) : all;
        return new AILimitedListDTO<>(List.copyOf(items), all.size(), all.size() > limit);
    }
}
