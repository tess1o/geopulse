package org.github.tess1o.geopulse.weather.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "open-meteo-weather-api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface OpenMeteoRestClient {

    @GET
    @Path("/v1/forecast")
    Response forecast(
            @QueryParam("latitude") double latitude,
            @QueryParam("longitude") double longitude,
            @QueryParam("current") String current,
            @QueryParam("hourly") String hourly,
            @QueryParam("start_hour") String startHour,
            @QueryParam("end_hour") String endHour,
            @QueryParam("timezone") String timezone,
            @QueryParam("apikey") String apiKey
    );

    @GET
    @Path("/v1/archive")
    Response archive(
            @QueryParam("latitude") double latitude,
            @QueryParam("longitude") double longitude,
            @QueryParam("hourly") String hourly,
            @QueryParam("start_date") String startDate,
            @QueryParam("end_date") String endDate,
            @QueryParam("timezone") String timezone,
            @QueryParam("apikey") String apiKey
    );
}
