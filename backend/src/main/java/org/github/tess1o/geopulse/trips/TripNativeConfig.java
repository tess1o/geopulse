package org.github.tess1o.geopulse.trips;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripDto;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.PlanSuggestionDto;
import org.github.tess1o.geopulse.trips.model.dto.TripDto;
import org.github.tess1o.geopulse.trips.model.dto.TripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripSummaryDto;
import org.github.tess1o.geopulse.trips.model.dto.TripVisitOverrideRequestDto;
import org.github.tess1o.geopulse.trips.model.dto.TripVisitSuggestionDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlaceVisitMatchEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemOverrideState;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemPriority;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemVisitSource;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;

@RegisterForReflection(targets = {
        // Entities
        TripEntity.class,
        TripPlanItemEntity.class,
        TripPlaceVisitMatchEntity.class,

        // Entity enums
        TripStatus.class,
        TripPlanItemPriority.class,
        TripPlanItemVisitSource.class,
        TripPlanItemOverrideState.class,

        // DTOs
        CreateTripDto.class,
        UpdateTripDto.class,
        TripDto.class,
        TripSummaryDto.class,
        CreateTripPlanItemDto.class,
        UpdateTripPlanItemDto.class,
        TripPlanItemDto.class,
        TripVisitSuggestionDto.class,
        TripVisitOverrideRequestDto.class,
        PlanSuggestionDto.class
})
public class TripNativeConfig {
}
