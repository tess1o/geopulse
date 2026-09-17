package org.github.tess1o.geopulse.trips.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.favorites.model.FavoriteAreaDto;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.model.FavoritePointDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.GeocodingService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.trips.model.dto.PlanSuggestionDto;
import org.locationtech.jts.geom.Point;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_SEARCH;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/trips/plan-suggestion")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Trips and Planning", description = "Suggest trip plans for a location.")
public class PlanSuggestionResource {

    private final CurrentUserService currentUserService;
    private final FavoriteLocationService favoriteLocationService;
    private final GeocodingService geocodingService;
    private final CacheGeocodingService cacheGeocodingService;

    public PlanSuggestionResource(CurrentUserService currentUserService,
                                  FavoriteLocationService favoriteLocationService,
                                  GeocodingService geocodingService,
                                  CacheGeocodingService cacheGeocodingService) {
        this.currentUserService = currentUserService;
        this.favoriteLocationService = favoriteLocationService;
        this.geocodingService = geocodingService;
        this.cacheGeocodingService = cacheGeocodingService;
    }

    @GET
    public PlanSuggestionDto getPlanSuggestion(@QueryParam("lat") Double latitude,
                                      @QueryParam("lon") Double longitude) {
        if (latitude == null || longitude == null) {
            throw problem(INVALID_TRIP_SEARCH, "lat and lon are required");
        }

        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw problem(INVALID_TRIP_SEARCH, "Invalid lat/lon values");
        }

        UUID userId = currentUserService.getCurrentUserId();
        Point point = GeoUtils.createPoint(longitude, latitude);

        PlanSuggestionDto suggestion = PlanSuggestionDto.builder()
                .latitude(latitude)
                .longitude(longitude)
                .sourceType("coordinates")
                .build();

        FavoriteLocationsDto favorite = favoriteLocationService.findByPoint(userId, point);
        FavoritePointDto favoritePoint = firstFavoritePoint(favorite);
        FavoriteAreaDto favoriteArea = firstFavoriteArea(favorite);

        if (favoritePoint != null && !isBlank(favoritePoint.getName())) {
            suggestion.setTitle(favoritePoint.getName());
            suggestion.setSourceType("favorite-point");
            suggestion.setFavoriteId(favoritePoint.getId());
            suggestion.setFavoriteType("point");
        } else if (favoriteArea != null && !isBlank(favoriteArea.getName())) {
            suggestion.setTitle(favoriteArea.getName());
            suggestion.setSourceType("favorite-area");
            suggestion.setFavoriteId(favoriteArea.getId());
            suggestion.setFavoriteType("area");
        } else {
            FormattableGeocodingResult geocoding = geocodingService.getLocationName(point);
            if (geocoding != null && !isBlank(geocoding.getFormattedDisplayName())) {
                suggestion.setTitle(geocoding.getFormattedDisplayName());
                suggestion.setSourceType("geocoding");
                cacheGeocodingService.getCachedGeocodingResultId(userId, point)
                        .ifPresent(suggestion::setGeocodingId);
            }
        }

        return suggestion;
    }

    private FavoritePointDto firstFavoritePoint(FavoriteLocationsDto favorite) {
        if (favorite == null || favorite.getPoints() == null || favorite.getPoints().isEmpty()) {
            return null;
        }
        return favorite.getPoints().getFirst();
    }

    private FavoriteAreaDto firstFavoriteArea(FavoriteLocationsDto favorite) {
        if (favorite == null || favorite.getAreas() == null || favorite.getAreas().isEmpty()) {
            return null;
        }
        return favorite.getAreas().getFirst();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
