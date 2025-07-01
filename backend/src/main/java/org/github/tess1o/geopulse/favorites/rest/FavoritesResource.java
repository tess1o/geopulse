package org.github.tess1o.geopulse.favorites.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.favorites.model.AddAreaToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.EditFavoriteDto;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationsDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.UUID;

@Path("/api/favorites")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("USER")
@Slf4j
public class FavoritesResource {

    private final FavoriteLocationService service;
    private final CurrentUserService currentUserService;

    @Inject
    public FavoritesResource(FavoriteLocationService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GET
    @Path("")
    public Response getFavorites() {
        try {
            UUID authenticatedUserId = currentUserService.getCurrentUserId();
            log.info("User {} is retrieving favorites", authenticatedUserId);
            FavoriteLocationsDto favorites = service.getFavorites(authenticatedUserId);
            return Response.ok(ApiResponse.success(favorites)).build();
        } catch (Exception e) {
            log.error("Failed to retrieve favorites", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve favorites: " + e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{favoriteId}")
    public Response updateFavorite(@PathParam("favoriteId") long favoriteId, @Valid EditFavoriteDto dto) {
        try {
            UUID authenticatedUserId = currentUserService.getCurrentUserId();
            log.info("User {} updating favorite {} with new name: {}", authenticatedUserId, favoriteId, dto.getName());
            service.renameFavorite(authenticatedUserId, favoriteId, dto.getName());
            return Response.ok(ApiResponse.success("Favorite updated successfully")).build();
        } catch (SecurityException e) {
            log.warn("User {} attempted to update favorite {} without authorization",
                    currentUserService.getCurrentUserId(), favoriteId);
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Not authorized to update this favorite"))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request to update favorite {}: {}", favoriteId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Favorite not found"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to update favorite {}", favoriteId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update favorite: " + e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{favoriteId}")
    public Response deleteFavorite(@PathParam("favoriteId") long favoriteId) {
        try {
            UUID authenticatedUserId = currentUserService.getCurrentUserId();
            log.info("User {} deleting favorite {}", authenticatedUserId, favoriteId);
            service.deleteFavorite(authenticatedUserId, favoriteId);
            return Response.ok(ApiResponse.success("Favorite deleted successfully")).build();
        } catch (SecurityException e) {
            log.warn("User {} attempted to delete favorite {} without authorization",
                    currentUserService.getCurrentUserId(), favoriteId);
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Not authorized to delete this favorite"))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request to delete favorite {}: {}", favoriteId, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Favorite not found"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete favorite {}", favoriteId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete favorite: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/point")
    @Transactional
    public Response addPointToFavorites(@Valid AddPointToFavoritesDto dto) {
        try {
            UUID authenticatedUserId = currentUserService.getCurrentUserId();
            log.info("User {} adding point favorite: {} at [{}, {}]",
                    authenticatedUserId, dto.getName(), dto.getLat(), dto.getLon());
            service.addFavorite(authenticatedUserId, dto);
            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success("Point favorite added successfully"))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid point favorite data: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid favorite data: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to add point favorite", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to add favorite: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/area")
    @Transactional
    public Response addAreaToFavorites(@Valid AddAreaToFavoritesDto dto) {
        try {
            UUID authenticatedUserId = currentUserService.getCurrentUserId();
            log.info("User {} adding area favorite: {} with bounds NE[{}, {}] SW[{}, {}]",
                    authenticatedUserId, dto.getName(),
                    dto.getNorthEastLat(), dto.getNorthEastLon(),
                    dto.getSouthWestLat(), dto.getSouthWestLon());
            service.addFavorite(authenticatedUserId, dto);
            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success("Area favorite added successfully"))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid area favorite data: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid favorite data: " + e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to add area favorite", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to add favorite: " + e.getMessage()))
                    .build();
        }
    }
}