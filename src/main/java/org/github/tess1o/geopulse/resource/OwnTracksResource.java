package org.github.tess1o.geopulse.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.model.dto.LocationMessage;
import org.github.tess1o.geopulse.service.LocationService;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.Map;

@Path("/")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class OwnTracksResource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    LocationService locationService;

    @POST
    @Path("/pub")
    public Response handleOwnTracks(Map<String, Object> payload,
                                    @RestHeader("X-Limit-U") String userId,
                                    @RestHeader("X-Limit-D") String deviceId) {
        log.info("Received payload: {}", payload);

        if (!"location".equals(payload.get("_type"))) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        LocationMessage locationMessage = MAPPER.convertValue(payload, LocationMessage.class);
        locationService.saveLocation(locationMessage, userId, deviceId);

        return Response.ok().build();
    }
}
