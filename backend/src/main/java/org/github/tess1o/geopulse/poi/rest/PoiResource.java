package org.github.tess1o.geopulse.poi.rest;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.poi.dto.PoiSearchResponseDto;
import org.github.tess1o.geopulse.poi.repository.PoiCacheRepository;
import org.github.tess1o.geopulse.poi.service.PoiDiscoveryService;
import org.github.tess1o.geopulse.poi.service.PoiImageService;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_POI_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.POI_UNAVAILABLE;

/**
 * Discovery of places worth visiting, plus their images.
 *
 * <p>Images are served from here rather than hotlinked so the browser only ever talks to
 * this origin, and so attribution is guaranteed to exist before any bytes are sent.
 */
@Path("/poi")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.PLACES_TO_VISIT)
public class PoiResource {

    private static final int THUMBNAIL_CACHE_SECONDS = 86_400;

    private final PoiDiscoveryService discoveryService;
    private final PoiImageService imageService;
    private final PoiCacheRepository poiCacheRepository;

    @Inject
    public PoiResource(PoiDiscoveryService discoveryService,
                       PoiImageService imageService,
                       PoiCacheRepository poiCacheRepository) {
        this.discoveryService = discoveryService;
        this.imageService = imageService;
        this.poiCacheRepository = poiCacheRepository;
    }

    @GET
    @Path("/search")
    @APIResponse(responseCode = "200", description = "Places around the given point")
    @Operation(summary = "Find places to visit nearby",
            description = "Returns notable places (sights, museums, landmarks) around a point, based on Wikidata, "
                    + "with a photo when available. Results are cached per area for all users.")
    public PoiSearchResponseDto search(
            @Parameter(description = "Latitude of the center, in decimal degrees. Required.", example = "41.9028")
            @QueryParam("latitude") Double latitude,
            @Parameter(description = "Longitude of the center, in decimal degrees. Required.", example = "12.4964")
            @QueryParam("longitude") Double longitude,
            @Parameter(description = "Search radius in meters, from 500 to 50000. Defaults to 8000.")
            @QueryParam("radiusMeters") @DefaultValue("8000") Integer radiusMeters,
            @Parameter(description = "Maximum number of places, up to 60.")
            @QueryParam("limit") Integer limit) {
        if (latitude == null || longitude == null) {
            throw new GeoPulseException(INVALID_POI_REQUEST, "latitude and longitude are required");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new GeoPulseException(INVALID_POI_REQUEST, "Invalid latitude or longitude");
        }

        try {
            return discoveryService.search(latitude, longitude,
                    radiusMeters == null ? 8000 : radiusMeters, limit);
        } catch (PoiDiscoveryService.PoiUnavailableException e) {
            // Distinguishable from an empty area on the client.
            throw new GeoPulseException(POI_UNAVAILABLE, e.getMessage(), e);
        }
    }

    @GET
    @Path("/images/{poiId}/thumbnail")
    @Produces({"image/jpeg", "image/png", "image/webp"})
    // Required: this method does a blocking HTTP fetch and JDBC work. Matches the other
    // binary endpoints in this codebase (see ImmichResource).
    @Blocking
    @APIResponse(responseCode = "200", description = "Cached thumbnail of the place's photo")
    @APIResponse(responseCode = "404", description = "No usable image for this place")
    @Operation(summary = "Get a place photo",
            description = "Returns the cached thumbnail of a place's photo. The `X-Image-License`, `X-Image-Author`, "
                    + "and `X-Image-Source` headers carry the attribution required by the image license.")
    public Response thumbnail(
            @Parameter(description = "Place ID from the search results.")
            @PathParam("poiId") Long poiId) {
        // Resolve the file through our own cache: the client never supplies a URL, so this
        // endpoint cannot be pointed at an arbitrary host.
        String imageFile = poiCacheRepository.findByIdOptional(poiId)
                .map(entity -> entity.getImageFile())
                .orElse(null);

        Optional<PoiImageService.CachedImage> image = imageService.getThumbnail(imageFile);
        if (image.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        PoiImageService.CachedImage cached = image.get();
        return Response.ok(cached.bytes())
                .type(cached.contentType())
                .header("Cache-Control", "private, max-age=" + THUMBNAIL_CACHE_SECONDS)
                // Licence metadata travels with the image so a client cannot render it
                // without the credit being available.
                .header("X-Image-License", cached.licenseName() == null ? "" : cached.licenseName())
                .header("X-Image-Author", cached.author() == null ? "" : cached.author())
                .header("X-Image-Source", cached.filePageUrl() == null ? "" : cached.filePageUrl())
                .build();
    }
}
