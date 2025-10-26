package org.github.tess1o.geopulse.maps.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * REST resource for proxying map tiles from custom tile providers.
 * This avoids CORS/ORB issues when loading tiles from third-party services.
 */
@Path("/api/tiles")
@RequestScoped
@Slf4j
public class TileProxyResource {

    private final CurrentUserService currentUserService;
    private final UserService userService;
    private final HttpClient httpClient;

    @Inject
    public TileProxyResource(CurrentUserService currentUserService, UserService userService) {
        this.currentUserService = currentUserService;
        this.userService = userService;

        // Create HTTP client with reasonable timeouts
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Proxy map tiles for the current user.
     *
     * @param z         Zoom level
     * @param x         Tile X coordinate
     * @param y         Tile Y coordinate
     * @param subdomain Optional subdomain (a, b, c, etc.)
     * @return The proxied tile image
     */
    @GET
    @Path("/{z}/{x}/{y}.png")
    @RolesAllowed("USER")
    @Produces("image/png")
    public Response getTile(
            @PathParam("z") int z,
            @PathParam("x") int x,
            @PathParam("y") int y,
            @QueryParam("s") String subdomain) {

        UserEntity user = currentUserService.getCurrentUser();
        try {

            // Check if user has a custom tile URL configured
            if (user.getCustomMapTileUrl() == null || user.getCustomMapTileUrl().trim().isEmpty()) {
                log.warn("User {} attempted to fetch custom tiles without configured URL", user.getEmail());
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("No custom tile URL configured")
                        .build();
            }

            String customUrl = user.getCustomMapTileUrl();

            // Replace coordinate placeholders
            String tileUrl = customUrl
                    .replace("{z}", String.valueOf(z))
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y));

            // Only replace subdomain if the URL contains {s} placeholder and subdomain is provided
            if (customUrl.contains("{s}")) {
                String subdomainValue = (subdomain != null && !subdomain.isEmpty()) ? subdomain : "a";
                tileUrl = tileUrl.replace("{s}", subdomainValue);
                log.debug("Proxying tile request: z={}, x={}, y={}, subdomain={}, url={}", z, x, y, subdomainValue, tileUrl);
            } else {
                log.debug("Proxying tile request (no subdomain): z={}, x={}, y={}, url={}", z, x, y, tileUrl);
            }

            // Fetch the tile from the remote server
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tileUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "GeoPulse/1.0.5")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            // Check if request was successful
            if (response.statusCode() != 200) {
                log.warn("Failed to fetch tile from {}: status={}, user={}",
                        tileUrl, response.statusCode(), user.getEmail());

                // For 404, return a transparent tile instead of an error
                if (response.statusCode() == 404) {
                    return Response.status(Response.Status.OK)
                            .type("image/png")
                            .header("Cache-Control", "public, max-age=3600") // Cache 404s for 1 hour
                            .header("X-Tile-Source", "not-found")
                            // Return 1x1 transparent PNG
                            .entity(java.util.Base64.getDecoder().decode(
                                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
                            ))
                            .build();
                }

                return Response.status(response.statusCode())
                        .entity("Failed to fetch tile from provider")
                        .build();
            }

            // Determine content type from response or default to PNG
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("image/png");

            // Return the tile with appropriate caching headers
            return Response.ok(response.body())
                    .type(contentType)
                    .header("Cache-Control", "public, max-age=2592000, immutable") // 30 days, immutable
                    .header("X-Tile-Source", "proxy")
                    .header("Access-Control-Allow-Origin", "*") // Allow CORS
                    .build();

        } catch (java.net.http.HttpTimeoutException e) {
            log.error("Timeout fetching tile: z={}, x={}, y={}, user={}",
                    z, x, y, user.getEmail());
            return Response.status(Response.Status.GATEWAY_TIMEOUT)
                    .entity("Tile server timeout")
                    .build();
        } catch (java.net.ConnectException e) {
            log.error("Connection failed to tile server: z={}, x={}, y={}, user={}",
                    z, x, y, user.getEmail());
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity("Cannot connect to tile server")
                    .build();
        } catch (Exception e) {
            log.error("Error proxying tile request: z={}, x={}, y={}, user={}",
                    z, x, y, user.getEmail(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error fetching tile")
                    .build();
        }
    }

    /**
     * Health check endpoint to verify if user has custom tiles configured.
     *
     * @return Status of custom tile configuration
     */
    @GET
    @Path("/status")
    @RolesAllowed("USER")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTileStatus() {
        try {
            UserEntity user = currentUserService.getCurrentUser();
            boolean hasCustomTiles = user.getCustomMapTileUrl() != null
                    && !user.getCustomMapTileUrl().trim().isEmpty();

            return Response.ok()
                    .entity("{\"hasCustomTiles\": " + hasCustomTiles + "}")
                    .build();
        } catch (Exception e) {
            log.error("Error checking tile status", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }
}
