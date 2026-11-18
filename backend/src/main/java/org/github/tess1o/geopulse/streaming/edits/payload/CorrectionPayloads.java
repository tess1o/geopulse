package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;

import java.util.Map;

/**
 * Maps the stored {@link TimelineEditKind} to its typed payload. The discriminator written into every payload is
 * the user-facing operation where one exists, so stored rows, exports and the API share the same vocabulary.
 */
public final class CorrectionPayloads {
    public static final String PLACE_STAY = "PLACE_STAY";
    public static final String PLACE_TRIP = "PLACE_TRIP";
    public static final String CLEAR = "CLEAR";
    public static final String RESIZE = "RESIZE";
    public static final String SET_PLACE = "SET_PLACE";
    public static final String SET_TRIP_TYPE = "SET_TRIP_TYPE";
    public static final String SET_LABEL = "SET_LABEL";
    public static final String SET_ROUTE = "SET_ROUTE";
    public static final String MERGE = "MERGE";
    /** Legacy stored split; new corrections use PLACE_STAY over the second half. */
    public static final String SPLIT_STAY = "SPLIT_STAY";

    private static final Map<TimelineEditKind, Class<? extends CorrectionPayload>> TYPES = Map.of(
            TimelineEditKind.SET_MOVEMENT_TYPE, SetTripTypePayload.class,
            TimelineEditKind.SET_PLACE, SetPlacePayload.class,
            TimelineEditKind.SET_VISIT_NAME, SetLabelPayload.class,
            TimelineEditKind.FORCE_STAY, PlaceStayPayload.class,
            TimelineEditKind.FORCE_TRIP, PlaceTripPayload.class,
            TimelineEditKind.SET_TIMES, ResizePayload.class,
            TimelineEditKind.SET_ROUTE, SetRoutePayload.class,
            TimelineEditKind.SPLIT_STAY, SplitStayPayload.class,
            TimelineEditKind.MERGE, MergePayload.class,
            TimelineEditKind.REMOVE_EVENT, ClearPayload.class
    );

    private static final Map<TimelineEditKind, String> TYPE_NAMES = Map.of(
            TimelineEditKind.SET_MOVEMENT_TYPE, SET_TRIP_TYPE,
            TimelineEditKind.SET_PLACE, SET_PLACE,
            TimelineEditKind.SET_VISIT_NAME, SET_LABEL,
            TimelineEditKind.FORCE_STAY, PLACE_STAY,
            TimelineEditKind.FORCE_TRIP, PLACE_TRIP,
            TimelineEditKind.SET_TIMES, RESIZE,
            TimelineEditKind.SET_ROUTE, SET_ROUTE,
            TimelineEditKind.SPLIT_STAY, SPLIT_STAY,
            TimelineEditKind.MERGE, MERGE,
            TimelineEditKind.REMOVE_EVENT, CLEAR
    );

    public static Boolean reconstruction(CorrectionPayload payload) {
        return payload instanceof PlaceStayPayload p ? p.reconstruction() : payload instanceof PlaceTripPayload p ? p.reconstruction() : false;
    }

    public static Long tripId(CorrectionPayload payload) {
        return payload instanceof PlaceStayPayload p ? p.tripId() : payload instanceof PlaceTripPayload p ? p.tripId() : null;
    }

    private CorrectionPayloads() {
    }

    public static Class<? extends CorrectionPayload> typeFor(TimelineEditKind kind) {
        Class<? extends CorrectionPayload> type = TYPES.get(kind);
        if (type == null) {
            throw new IllegalArgumentException("No payload type for correction kind " + kind);
        }
        return type;
    }

    public static String typeName(TimelineEditKind kind) {
        String name = TYPE_NAMES.get(kind);
        if (name == null) {
            throw new IllegalArgumentException("No payload type for correction kind " + kind);
        }
        return name;
    }

    /** The typed payload of a stored row, rejecting a payload whose type does not match the row's kind. */
    public static CorrectionPayload require(TimelineEditKind kind, CorrectionPayload payload) {
        if (payload == null || !typeFor(kind).isInstance(payload)) {
            throw new IllegalArgumentException("Correction " + kind + " has a payload of the wrong type");
        }
        return payload;
    }

    public static <T extends CorrectionPayload> T require(TimelineEditKind kind, CorrectionPayload payload,
                                                          Class<T> expected) {
        require(kind, payload);
        if (!expected.isInstance(payload)) {
            throw new IllegalArgumentException("Correction " + kind + " requires payload " + expected.getSimpleName());
        }
        return expected.cast(payload);
    }

    /**
     * Reads a payload from an old row or export that has no discriminator yet. The discriminator is added from the
     * row's kind before binding, which is exactly what the V36.94.0 migration does for stored rows.
     */
    public static CorrectionPayload fromLegacyJson(TimelineEditKind kind, JsonNode node, ObjectMapper mapper) {
        try {
            ObjectNode object = node == null || node.isNull() ? mapper.createObjectNode() : (ObjectNode) node.deepCopy();
            object.put("payloadType", typeName(kind));
            return mapper.treeToValue(object, typeFor(kind));
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the payload of correction " + kind, e);
        }
    }
}
