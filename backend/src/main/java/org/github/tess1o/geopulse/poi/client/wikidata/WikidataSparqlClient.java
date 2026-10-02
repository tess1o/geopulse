package org.github.tess1o.geopulse.poi.client.wikidata;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
/**
 * Wikidata Query Service.
 *
 * <p>Queries are POSTed as {@code application/x-www-form-urlencoded} because a bbox query
 * is far too long for a query string.
 *
 * <p>Intentionally not {@code @RegisterRestClient}: the base URL is admin-configurable, so
 * the client is built per call by {@link org.github.tess1o.geopulse.poi.client.PoiRestClientFactory}.
 */
@Path("")
public interface WikidataSparqlClient {

    @POST
    @Path("/sparql")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<SparqlResponse> query(@jakarta.ws.rs.FormParam("query") String query,
                              @HeaderParam("User-Agent") String userAgent,
                              @HeaderParam("Accept") String accept);
}
