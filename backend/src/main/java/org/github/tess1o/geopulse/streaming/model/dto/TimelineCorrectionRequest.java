package org.github.tess1o.geopulse.streaming.model.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import lombok.Data;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionOp;
import org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayload;
import org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayloads;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_REQUEST;

/**
 * One request shape for both ways of saving corrections: an activity batch ({@code activities}) and a single event
 * correction ({@code op} with an anchor). Both are saved as one correction group. The payload is bound to the typed
 * payload record selected by {@code op}; clients do not send the internal {@code payloadType} discriminator.
 */
@Data
@JsonDeserialize(using = TimelineCorrectionRequest.Deserializer.class)
public class TimelineCorrectionRequest {
    /** Single-correction operations. */
    private TimelineCorrectionOp op;
    private Instant anchorStart;
    private Instant anchorEnd;
    private Instant affectedFrom;
    private Instant affectedTo;
    private CorrectionPayload payload;
    private Long favoriteId;
    private Long geocodingId;
    private Double latitude;
    private Double longitude;
    private String placeName;

    /** Activity batch fields. */
    private Long tripId;
    @Valid
    private List<ActivityCorrectionService.Activity> activities;
    private Instant contextStart;
    private Instant contextEnd;

    private String previewToken;
    private UUID replacesGroupId;

    public boolean activityBatch() {
        return activities != null;
    }

    public ActivityCorrectionService.Request toActivityRequest() {
        return new ActivityCorrectionService.Request(tripId, activities, contextStart, contextEnd,
                previewToken, replacesGroupId);
    }

    public TimelineEditRequest toEditRequest() {
        TimelineEditRequest request = new TimelineEditRequest();
        request.setKind(op == null ? null : op.toKind());
        request.setAnchorStart(anchorStart);
        request.setAnchorEnd(anchorEnd);
        request.setAffectedFrom(affectedFrom);
        request.setAffectedTo(affectedTo);
        request.setPayload(payload);
        request.setFavoriteId(favoriteId);
        request.setGeocodingId(geocodingId);
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setPlaceName(placeName);
        request.setPreviewToken(previewToken);
        request.setReplacesGroupId(replacesGroupId);
        return request;
    }

    /** Binds the typed payload from the operation; the wire payload itself stays free of the discriminator. */
    public static final class Deserializer extends JsonDeserializer<TimelineCorrectionRequest> {
        @Override
        public TimelineCorrectionRequest deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            ObjectMapper mapper = (ObjectMapper) parser.getCodec();
            JsonNode node = mapper.readTree(parser);
            TimelineCorrectionRequest request = new TimelineCorrectionRequest();
            JsonNode opNode = node.get("op");
            if (opNode != null && !opNode.isNull()) {
                request.op = mapper.treeToValue(opNode, TimelineCorrectionOp.class);
            }
            request.anchorStart = instant(node.get("anchorStart"), mapper);
            request.anchorEnd = instant(node.get("anchorEnd"), mapper);
            request.affectedFrom = instant(node.get("affectedFrom"), mapper);
            request.affectedTo = instant(node.get("affectedTo"), mapper);
            request.favoriteId = longValue(node.get("favoriteId"));
            request.geocodingId = longValue(node.get("geocodingId"));
            request.latitude = doubleValue(node.get("latitude"));
            request.longitude = doubleValue(node.get("longitude"));
            request.placeName = text(node.get("placeName"));
            request.tripId = longValue(node.get("tripId"));
            request.contextStart = instant(node.get("contextStart"), mapper);
            request.contextEnd = instant(node.get("contextEnd"), mapper);
            request.previewToken = text(node.get("previewToken"));
            request.replacesGroupId = uuid(node.get("replacesGroupId"));
            JsonNode activities = node.get("activities");
            if (activities != null && !activities.isNull()) {
                request.activities = mapper.convertValue(activities,
                        new TypeReference<List<ActivityCorrectionService.Activity>>() {
                        });
            }
            JsonNode payload = node.get("payload");
            if (payload != null && !payload.isNull() && request.op != null) {
                try {
                    request.payload = CorrectionPayloads.fromLegacyJson(request.op.toKind(), payload, mapper);
                } catch (IllegalArgumentException invalid) {
                    throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Invalid correction payload", invalid);
                }
            }
            return request;
        }

        private static Instant instant(JsonNode node, ObjectMapper mapper) throws IOException {
            return node == null || node.isNull() ? null : mapper.treeToValue(node, Instant.class);
        }

        private static String text(JsonNode node) {
            return node == null || node.isNull() ? null : node.asText();
        }

        private static Long longValue(JsonNode node) {
            return node == null || node.isNull() || !node.isNumber() ? null : node.longValue();
        }

        private static Double doubleValue(JsonNode node) {
            return node == null || node.isNull() || !node.isNumber() ? null : node.doubleValue();
        }

        private static UUID uuid(JsonNode node) {
            if (node == null || node.isNull() || node.asText().isBlank()) return null;
            try {
                return UUID.fromString(node.asText());
            } catch (IllegalArgumentException invalid) {
                throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Invalid correction group id", invalid);
            }
        }
    }
}
