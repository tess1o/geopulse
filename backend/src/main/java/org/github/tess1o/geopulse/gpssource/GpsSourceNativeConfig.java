package org.github.tess1o.geopulse.gpssource;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.gpssource.model.*;

@RegisterForReflection(targets = {
        GpsSourceConfigEntity.class,
        UpdateGpsSourceConfigStatusDto.class,
        CreateGpsSourceConfigDto.class,
        GpsSourceConfigDTO.class,
        GpsFilteringDefaultsDTO.class,
        UpdateGpsSourceConfigDto.class,
        GpsTelemetryMappingEntry.class,
        GpsSourceTypeTelemetryConfigEntity.class,
        GpsSourceTypeTelemetryConfigDTO.class,
        OwnTracksMqttConfigDTO.class,
        GpsSourceIngestStatusDTO.class,
        CreateGpsSourceConfigDto.NewApiToken.class,
        ConnectedGpsSourceDTO.class,
        GpsSourceAuthFailureCode.class,
        GpsSourceIngestStatusEntity.class,
})
public class GpsSourceNativeConfig {
}
