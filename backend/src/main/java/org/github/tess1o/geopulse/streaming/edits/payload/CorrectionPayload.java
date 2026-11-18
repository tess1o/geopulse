package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The typed content of a correction. The JSONB {@code payload} column stores these records directly; the
 * {@code payloadType} discriminator is written with every payload and is also added to legacy rows by the
 * V36.94.0 migration. The operation itself stays in {@code TimelineEditKind} because legacy {@code SPLIT_STAY}
 * rows keep their own semantics.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "payloadType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PlaceStayPayload.class, name = CorrectionPayloads.PLACE_STAY),
        @JsonSubTypes.Type(value = PlaceTripPayload.class, name = CorrectionPayloads.PLACE_TRIP),
        @JsonSubTypes.Type(value = ClearPayload.class, name = CorrectionPayloads.CLEAR),
        @JsonSubTypes.Type(value = ResizePayload.class, name = CorrectionPayloads.RESIZE),
        @JsonSubTypes.Type(value = SetPlacePayload.class, name = CorrectionPayloads.SET_PLACE),
        @JsonSubTypes.Type(value = SetTripTypePayload.class, name = CorrectionPayloads.SET_TRIP_TYPE),
        @JsonSubTypes.Type(value = SetLabelPayload.class, name = CorrectionPayloads.SET_LABEL),
        @JsonSubTypes.Type(value = SetRoutePayload.class, name = CorrectionPayloads.SET_ROUTE),
        @JsonSubTypes.Type(value = MergePayload.class, name = CorrectionPayloads.MERGE),
        @JsonSubTypes.Type(value = SplitStayPayload.class, name = CorrectionPayloads.SPLIT_STAY)
})
public sealed interface CorrectionPayload permits PlaceStayPayload, PlaceTripPayload, ClearPayload, ResizePayload,
        SetPlacePayload, SetTripTypePayload, SetLabelPayload, SetRoutePayload, MergePayload, SplitStayPayload {
}
