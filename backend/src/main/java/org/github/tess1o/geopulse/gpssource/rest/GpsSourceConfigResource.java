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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GPS_SOURCE_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_GPS_SOURCE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TELEMETRY_MAPPING;

@Path(ApiPaths.GPS_SOURCES)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@RequestScoped
@Tag(name = ApiTags.GPS_SOURCES)
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
    @Operation(summary = "List GPS sources",
            description = "Returns the GPS sources of the signed-in user: type, credentials (username or token), "
                    + "device filter, active status, and point filtering settings.")
    public List<GpsSourceConfigDTO> getGpsSourceConfigs() {
        return gpsSourceService.findGpsSourceConfigs(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/defaults")
    @Operation(summary = "Get default point filters",
            description = "Returns the server defaults for filtering inaccurate points (maximum accuracy and speed) "
                    + "and detecting duplicates. New GPS sources start with these values.")
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
    @Operation(summary = "Get OwnTracks MQTT settings",
            description = "Returns whether the built-in MQTT broker for OwnTracks is enabled, and the host, port, "
                    + "and TLS setting to configure in the OwnTracks app.")
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
    @Operation(summary = "Get telemetry mapping",
            description = "Returns how extra fields sent by a GPS source type (such as battery level or charging "
                    + "state) are labeled, typed, and shown in GeoPulse. Returns the user's customized mapping, or "
                    + "the defaults when none is saved.")
    public GpsSourceTypeTelemetryConfigDTO getTelemetryMapping(
            @Parameter(description = "GPS source type, for example `OWNTRACKS`, `OVERLAND`, `TRACCAR`, "
                    + "`HOME_ASSISTANT`, `GPSLOGGER`, `COLOTA`, or `DAWARICH`. Case-insensitive.",
                    example = "OWNTRACKS")
            @PathParam("sourceType") String sourceTypeValue) {
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
    @Operation(summary = "Save telemetry mapping",
            description = "Replaces the telemetry mapping of a GPS source type for the signed-in user. Each entry "
                    + "maps a payload field (`key`) to a label, type (`boolean`, `number`, or `string`), unit, and "
                    + "where it is displayed.")
    public GpsSourceTypeTelemetryConfigDTO upsertTelemetryMapping(
            @Parameter(description = "GPS source type, for example `OWNTRACKS`, `OVERLAND`, `TRACCAR`, "
                    + "`HOME_ASSISTANT`, `GPSLOGGER`, `COLOTA`, or `DAWARICH`. Case-insensitive.",
                    example = "OWNTRACKS")
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
    @Operation(summary = "Reset telemetry mapping",
            description = "Deletes the customized telemetry mapping of a GPS source type so the defaults are used "
                    + "again.")
    public RestResponse<Void> resetTelemetryMapping(
            @Parameter(description = "GPS source type, for example `OWNTRACKS`, `OVERLAND`, `TRACCAR`, "
                    + "`HOME_ASSISTANT`, `GPSLOGGER`, `COLOTA`, or `DAWARICH`. Case-insensitive.",
                    example = "OWNTRACKS")
            @PathParam("sourceType") String sourceTypeValue) {
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
    @Operation(summary = "Create a GPS source",
            description = "Creates a GPS source that a tracker app can send data to. Basic-auth sources (OwnTracks, "
                    + "GPSLogger, Colota) need a username and password; token sources (Overland, Traccar, Home "
                    + "Assistant, Dawarich) need a token.")
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
    @Operation(summary = "Delete a GPS source",
            description = "Deletes a GPS source. Its credentials stop working immediately. GPS points already "
                    + "received are kept.")
    public RestResponse<Void> deleteGpsSourceConfig(
            @Parameter(description = "GPS source ID.")
            @PathParam("id") UUID configId) {
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
    @Operation(summary = "Enable or disable a GPS source",
            description = "Turns a GPS source on or off. Data sent to a disabled source is rejected.")
    public RestResponse<Void> updateStatus(
            @Parameter(description = "GPS source ID.")
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
    @Operation(summary = "Update a GPS source",
            description = "Updates the credentials, device filter, and point filtering settings of a GPS source.")
    public RestResponse<Void> updateGpsConfigSource(
            @Parameter(description = "GPS source ID.")
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
