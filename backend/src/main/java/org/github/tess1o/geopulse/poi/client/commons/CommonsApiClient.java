package org.github.tess1o.geopulse.poi.client.commons;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
/**
 * Wikimedia Commons API, used to resolve a file's licence and author.
 *
 * <p>Commons licences are per-file, so this is required before any image may be shown.
 *
 * <p>Intentionally not {@code @RegisterRestClient}: the base URL is admin-configurable, so
 * the client is built per call by {@link org.github.tess1o.geopulse.poi.client.PoiRestClientFactory}.
 */
@Path("/w/api.php")
public interface CommonsApiClient {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Uni<CommonsImageInfoResponse> imageInfo(
            @QueryParam("action") String action,
            @QueryParam("format") String format,
            @QueryParam("prop") String prop,
            @QueryParam("titles") String titles,
            @QueryParam("iiprop") String iiProp,
            @QueryParam("iiurlwidth") Integer thumbWidth,
            @HeaderParam("User-Agent") String userAgent,
            @HeaderParam("Accept") String accept);
}
