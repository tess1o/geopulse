package org.github.tess1o.geopulse.mapmatching;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaLocation;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaRestClient;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaTraceRouteRequest;
import org.github.tess1o.geopulse.mapmatching.client.ValhallaTraceRouteResponse;
import org.github.tess1o.geopulse.mapmatching.dto.*;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingStatus;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingSource;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;

@RegisterForReflection(targets = {
        TimelineTripPathMatchEntity.class,
        MapMatchingStatus.class,
        MapMatchingSource.class,
        MapMatchedPointDTO.class,
        MapMatchingResolutionRequest.class,
        MapMatchingResolutionResponse.class,
        MapMatchingStatusRequest.class,
        MapMatchingAdminStatusDTO.class,
        MapMatchingTripResolutionDTO.class,
        ValhallaLocation.class,
        ValhallaTraceRouteRequest.class,
        ValhallaTraceRouteResponse.class,
        ValhallaTraceRouteResponse.ValhallaGeometry.class,
        ValhallaTraceRouteResponse.ValhallaFeature.class,
        ValhallaTraceRouteResponse.ValhallaTrip.class,
        ValhallaTraceRouteResponse.ValhallaLeg.class,
        ValhallaTraceRouteResponse.ValhallaAlternate.class,
        ValhallaRestClient.class
})
public class MapMatchingNativeConfig {
}
