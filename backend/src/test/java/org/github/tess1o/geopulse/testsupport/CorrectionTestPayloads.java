package org.github.tess1o.geopulse.testsupport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.github.tess1o.geopulse.streaming.edits.*;
import org.github.tess1o.geopulse.streaming.edits.payload.*;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEventType;
import java.time.Instant;
import java.util.*;

/** Builds typed fixtures from the same small JSON examples used by API and old-export tests. */
public final class CorrectionTestPayloads {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private CorrectionTestPayloads() {}
    public static CorrectionPayload payload(TimelineEditKind kind, Object value) {
        return value instanceof CorrectionPayload p ? p : CorrectionPayloads.fromLegacyJson(kind, JSON.valueToTree(value), JSON);
    }
    public static void set(TimelineEditRequest request, Object value) {
        request.setPayload(payload(request.getKind(), value));
    }
    public static void put(TimelineEditRequest request, String key, Object value) {
        var map = map(request.getPayload());
        map.put(key, value);
        set(request, map);
    }
    public static Map<String, Object> map(Object value) {
        Map<String, Object> map = value == null ? new LinkedHashMap<>() : JSON.convertValue(value, new TypeReference<>() {});
        map.values().removeIf(Objects::isNull);
        return map;
    }
    public static TimelineEditCommand command(Long id, long seq, TimelineEditKind kind, Instant from, Instant to, Object payload) {
        return new TimelineEditCommand(id, seq, kind, from, to, payload(kind, payload), payload instanceof Map<?, ?> m && m.get("sourceType") != null ? TimelineEventType.valueOf(m.get("sourceType").toString()) : null, List.of());
    }
    public static TimelineEditCommand command(Long id, long seq, TimelineEditKind kind, Instant from, Instant to, Object payload, TimelineEventType type, List<Long> prerequisites) {
        return new TimelineEditCommand(id, seq, kind, from, to, payload(kind, payload), type, prerequisites);
    }
}
