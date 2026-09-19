package org.github.tess1o.geopulse.gpssource.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt.MqttConfiguration;
import org.github.tess1o.geopulse.gpssource.model.CreateGpsSourceConfigDto;
import org.github.tess1o.geopulse.gpssource.model.GpsFilteringDefaultsDTO;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigDTO;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceTypeTelemetryConfigDTO;
import org.github.tess1o.geopulse.gpssource.model.GpsTelemetryMappingEntry;
import org.github.tess1o.geopulse.gpssource.model.OwnTracksMqttConfigDTO;
import org.github.tess1o.geopulse.gpssource.model.UpdateGpsSourceConfigDto;
import org.github.tess1o.geopulse.gpssource.model.UpdateGpsSourceConfigStatusDto;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceService;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceTypeTelemetryConfigService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GPS_SOURCE_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_GPS_SOURCE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TELEMETRY_MAPPING;

@Path(ApiPaths.GPS_SOURCES)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@RequestScoped
@Tag(name = "User: GPS Sources", description = "Manage GPS source configuration, telemetry mappings, and status.")
public class GpsSourceConfigResource {

    private final GpsSourceService gpsSourceService;
    private final GpsSourceTypeTelemetryConfigService telemetryConfigService;
    private final CurrentUserService currentUserService;
    private final MqttConfiguration mqttConfiguration;

    public GpsSourceConfigResource(GpsSourceService gpsSourceService,
                                   GpsSourceTypeTelemetryConfigService telemetryConfigService,
                                   CurrentUserService currentUserService,
                                   MqttConfiguration mqttConfiguration) {
        this.gpsSourceService = gpsSourceService;
        this.telemetryConfigService = telemetryConfigService;
        this.currentUserService = currentUserService;
        this.mqttConfiguration = mqttConfiguration;
    }

    @GET
    public List<GpsSourceConfigDTO> getGpsSourceConfigs() {
        return gpsSourceService.findGpsSourceConfigs(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/defaults")
    public GpsFilteringDefaultsDTO getDefaultFilteringValues() {
        return new GpsFilteringDefaultsDTO(
                gpsSourceService.isDefaultFilterInaccurateDataEnabled(),
                gpsSourceService.getDefaultMaxAllowedAccuracy(),
                gpsSourceService.getDefaultMaxAllowedSpeed(),
                gpsSourceService.isDefaultDuplicateDetectionEnabled(),
                gpsSourceService.getDefaultDuplicateDetectionThresholdMinutes());
    }

    @GET
    @Path("/owntracks/mqtt-config")
    public OwnTracksMqttConfigDTO getOwnTracksMqttConfig() {
        return OwnTracksMqttConfigDTO.builder()
                .mqttEnabled(mqttConfiguration.isMqttEnabled())
                .brokerHost(mqttConfiguration.getBrokerHost())
                .brokerPort(mqttConfiguration.getBrokerPort())
                .tlsEnabled(mqttConfiguration.isTlsEnabled())
                .build();
    }

    @GET
    @Path("/telemetry/{sourceType}")
    public GpsSourceTypeTelemetryConfigDTO getTelemetryMapping(@PathParam("sourceType") String sourceTypeValue) {
        try {
            return telemetryConfigService.getResolvedConfig(
                    currentUserService.getCurrentUserId(), parseSourceType(sourceTypeValue));
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_TELEMETRY_MAPPING, INVALID_TELEMETRY_MAPPING.title(),
                    Map.of("sourceType", String.valueOf(sourceTypeValue)), exception);
        }
    }

    @PUT
    @Path("/telemetry/{sourceType}")
    public GpsSourceTypeTelemetryConfigDTO upsertTelemetryMapping(
            @PathParam("sourceType") String sourceTypeValue,
            @NotNull List<@Valid GpsTelemetryMappingEntry> mapping) {
        try {
            return telemetryConfigService.upsertConfig(
                    currentUserService.getCurrentUserId(), parseSourceType(sourceTypeValue), mapping);
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_TELEMETRY_MAPPING, INVALID_TELEMETRY_MAPPING.title(),
                    Map.of("sourceType", String.valueOf(sourceTypeValue)), exception);
        }
    }

    @DELETE
    @Path("/telemetry/{sourceType}")
    @APIResponse(responseCode = "204", description = "Telemetry mapping reset")
    public RestResponse<Void> resetTelemetryMapping(@PathParam("sourceType") String sourceTypeValue) {
        try {
            telemetryConfigService.resetConfig(
                    currentUserService.getCurrentUserId(), parseSourceType(sourceTypeValue));
            return RestResponse.noContent();
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_TELEMETRY_MAPPING, INVALID_TELEMETRY_MAPPING.title(),
                    Map.of("sourceType", String.valueOf(sourceTypeValue)), exception);
        }
    }

    @POST
    @APIResponse(responseCode = "201", description = "GPS source created")
    public RestResponse<GpsSourceConfigDTO> addGpsSourceConfig(
            @NotNull @Valid CreateGpsSourceConfigDto config) {
        config.setUserId(currentUserService.getCurrentUserId());
        try {
            return RestResponse.status(Response.Status.CREATED, gpsSourceService.addGpsSourceConfig(config));
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_GPS_SOURCE, INVALID_GPS_SOURCE.title(), exception);
        }
    }

    @DELETE
    @Path("/{id}")
    @APIResponse(responseCode = "204", description = "GPS source deleted")
    public RestResponse<Void> deleteGpsSourceConfig(@PathParam("id") UUID configId) {
        boolean deleted = gpsSourceService.deleteGpsSourceConfig(
                configId, currentUserService.getCurrentUserId());
        if (!deleted) {
            throw new GeoPulseException(GPS_SOURCE_NOT_FOUND, "GPS source not found",
                    Map.of("sourceId", configId.toString()));
        }
        return RestResponse.noContent();
    }

    @PATCH
    @Path("/{id}/status")
    @APIResponse(responseCode = "204", description = "GPS source status updated")
    public RestResponse<Void> updateStatus(
            @PathParam("id") UUID configId,
            @NotNull @Valid UpdateGpsSourceConfigStatusDto newStatus) {
        boolean updated = gpsSourceService.updateGpsConfigSourceStatus(
                configId, currentUserService.getCurrentUserId(), newStatus.isStatus());
        if (!updated) {
            throw new GeoPulseException(GPS_SOURCE_NOT_FOUND, "GPS source not found",
                    Map.of("sourceId", configId.toString()));
        }
        return RestResponse.noContent();
    }

    @PUT
    @Path("/{id}")
    @APIResponse(responseCode = "204", description = "GPS source updated")
    public RestResponse<Void> updateGpsConfigSource(
            @PathParam("id") String configId,
            @NotNull @Valid UpdateGpsSourceConfigDto config) {
        config.setId(configId);
        try {
            boolean updated = gpsSourceService.updateGpsConfigSource(
                    config, currentUserService.getCurrentUserId());
            if (!updated) {
                throw new GeoPulseException(GPS_SOURCE_NOT_FOUND, "GPS source not found");
            }
            return RestResponse.noContent();
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_GPS_SOURCE, INVALID_GPS_SOURCE.title(), exception);
        }
    }

    private GpsSourceType parseSourceType(String sourceTypeValue) {
        if (sourceTypeValue == null || sourceTypeValue.isBlank()) {
            throw new IllegalArgumentException("Source type is required");
        }
        try {
            return GpsSourceType.valueOf(sourceTypeValue.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported source type: " + sourceTypeValue, exception);
        }
    }
}
