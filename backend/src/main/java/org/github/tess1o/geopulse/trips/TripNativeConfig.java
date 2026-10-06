package org.github.tess1o.geopulse.trips;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.trips.model.dto.*;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorAccessRole;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlaceVisitMatchEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemOverrideState;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemPriority;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemTravelMode;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemVisitSource;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;

@RegisterForReflection(targets = {
        // Entities
        TripEntity.class,
        TripPlanItemEntity.class,
        TripPlaceVisitMatchEntity.class,
        TripCollaboratorEntity.class,

        // Entity enums
        TripStatus.class,
        TripPlanItemPriority.class,
        TripPlanItemTravelMode.class,
        TripPlanItemVisitSource.class,
        TripPlanItemOverrideState.class,
        TripCollaboratorAccessRole.class,

        // DTOs
        CreateTripDto.class,
        UpdateTripDto.class,
        TripCollaboratorDto.class,
        UpdateTripCollaboratorDto.class,
        TripDto.class,
        TripSummaryDto.class,
        CreateTripPlanItemDto.class,
        UpdateTripPlanItemDto.class,
        ReorderTripPlanItemsDto.class,
        ReorderTripPlanItemsDto.Entry.class,
        TripPlanRouteDto.class,
        TripPlanRouteDto.Leg.class,
        TripPlanItemDto.class,
        TripVisitSuggestionDto.class,
        TripVisitOverrideRequestDto.class,
        PlanSuggestionDto.class,
        PlanSearchResultDto.class,
        PlanSearchResponseDto.class,
        PlanSearchExternalStatus.class,
        TripReconstructionPreviewDto.class,
        TripReconstructionRequestDto.class,
        TripReconstructionSegmentDto.class,
        TripReconstructionWaypointDto.class,
        TripReconstructionCommitResponseDto.class
})
public class TripNativeConfig {
}
