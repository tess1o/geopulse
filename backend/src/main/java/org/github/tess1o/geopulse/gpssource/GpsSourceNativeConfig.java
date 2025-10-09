package org.github.tess1o.geopulse.gpssource;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.gpssource.model.*;

@RegisterForReflection(targets = {
        GpsSourceConfigEntity.class,
        UpdateGpsSourceConfigStatusDto.class,
        CreateGpsSourceConfigDto.class,
        GpsSourceConfigDTO.class,
        UpdateGpsSourceConfigDto.class,
})
public class GpsSourceNativeConfig {
}
