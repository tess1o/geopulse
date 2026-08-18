package org.github.tess1o.geopulse.weather.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "pirate-weather-api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface PirateWeatherRestClient {

    @GET
    @Path("/forecast/{apiKey}/{latitude},{longitude}")
    Response forecast(
            @PathParam("apiKey") String apiKey,
            @PathParam("latitude") double latitude,
            @PathParam("longitude") double longitude,
            @QueryParam("units") String units,
            @QueryParam("exclude") String exclude
    );

    @GET
    @Path("/forecast/{apiKey}/{latitude},{longitude},{time}")
    Response timeMachine(
            @PathParam("apiKey") String apiKey,
            @PathParam("latitude") double latitude,
            @PathParam("longitude") double longitude,
            @PathParam("time") long time,
            @QueryParam("units") String units,
            @QueryParam("exclude") String exclude
    );
}
