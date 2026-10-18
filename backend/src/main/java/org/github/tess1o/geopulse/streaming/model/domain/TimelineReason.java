package org.github.tess1o.geopulse.streaming.model.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One step of how the timeline engine decided on a stay, trip or data gap, such as "ended when a point 230 m away was
 * recorded". {@code code} names the decision and {@code params} hold the values it was based on, so the reason can be
 * shown in the user's language.
 */
public record TimelineReason(String code, Map<String, Object> params) {

    public TimelineReason {
        params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    public static TimelineReason of(String code) {
        return new TimelineReason(code, Map.of());
    }

    /** A reason with parameters given as alternating names and values. */
    public static TimelineReason of(String code, Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Parameters must be name/value pairs");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            params.put(String.valueOf(namesAndValues[i]), namesAndValues[i + 1]);
        }
        return new TimelineReason(code, params);
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        if (!params.isEmpty()) {
            map.put("params", new LinkedHashMap<>(params));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    static TimelineReason fromMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || !(map.get("code") instanceof String code)) {
            return null;
        }
        Object params = map.get("params");
        return new TimelineReason(code, params instanceof Map<?, ?> p ? (Map<String, Object>) p : Map.of());
    }
}
