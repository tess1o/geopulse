package org.github.tess1o.geopulse.notes.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.notes.model.MemosCreateMemoRequest;

@Path("/api/v1/memos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MemosRestClient {

    @GET
    Response listMemos(
            @QueryParam("pageSize") int pageSize,
            @QueryParam("pageToken") String pageToken,
            @QueryParam("filter") String filter,
            @QueryParam("orderBy") String orderBy
    );

    @POST
    Response createMemo(MemosCreateMemoRequest request);
}
