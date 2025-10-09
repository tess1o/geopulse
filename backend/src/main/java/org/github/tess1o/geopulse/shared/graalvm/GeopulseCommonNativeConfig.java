package org.github.tess1o.geopulse.shared.graalvm;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

@RegisterForReflection(targets = {
        ApiResponse.class,
        GpsSourceType.class,
})
public class GeopulseCommonNativeConfig {
}
