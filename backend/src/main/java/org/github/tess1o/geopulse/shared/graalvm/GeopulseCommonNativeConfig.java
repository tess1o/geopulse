package org.github.tess1o.geopulse.shared.graalvm;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.shared.api.UpdatedCountResponse;
import org.github.tess1o.geopulse.shared.api.SliceResponse;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.shared.api.JobResponse;
import org.github.tess1o.geopulse.shared.api.CountResponse;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;
import org.github.tess1o.geopulse.health.rest.HealthResource;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

@RegisterForReflection(targets = {
        HealthResource.HealthStatus.class,
        HealthResource.HealthStatusResponse.class,
        ApiErrorCode.class,
        CountResponse.class,
        JobResponse.class,
        PageResponse.class,
        SliceResponse.class,
        UpdatedCountResponse.class,
        GpsPoint.class,
        GpsSourceType.class,
        MessageDescriptor.class,
})
public class GeopulseCommonNativeConfig {
}
