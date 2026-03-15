package org.github.tess1o.geopulse.trips.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.favorites.model.FavoriteAreaDto;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.model.FavoritePointDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.service.GeocodingService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.trips.model.dto.PlanSuggestionDto;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

@Path("/api/trips/plan-suggestion")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class PlanSuggestionResource {

    private final CurrentUserService currentUserService;
    private final FavoriteLocationService favoriteLocationService;
    private final GeocodingService geocodingService;

    public PlanSuggestionResource(CurrentUserService currentUserService,
                                  FavoriteLocationService favoriteLocationService,
                                  GeocodingService geocodingService) {
        this.currentUserService = currentUserService;
        this.favoriteLocationService = favoriteLocationService;
        this.geocodingService = geocodingService;
    }

    @GET
    public Response getPlanSuggestion(@QueryParam("lat") Double latitude,
                                      @QueryParam("lon") Double longitude) {
        if (latitude == null || longitude == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("lat and lon are required"))
                    .build();
        }

        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid lat/lon values"))
                    .build();
        }

        try {
            UUID userId = currentUserService.getCurrentUserId();
            Point point = GeoUtils.createPoint(longitude, latitude);

            PlanSuggestionDto suggestion = PlanSuggestionDto.builder()
                    .title(defaultTitle(latitude, longitude))
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
                }
            }

            return Response.ok(ApiResponse.success(suggestion)).build();
        } catch (Exception e) {
            log.error("Failed to resolve plan suggestion for lat={}, lon={}", latitude, longitude, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to resolve plan suggestion"))
                    .build();
        }
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

    private String defaultTitle(double latitude, double longitude) {
        return String.format("Planned place (%.5f, %.5f)", latitude, longitude);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
