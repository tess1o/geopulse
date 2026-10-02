package org.github.tess1o.geopulse.shared.graalvm;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

@RegisterForReflection(targets = {
        GpsSourceType.class,
})
public class GeopulseCommonNativeConfig {
}
