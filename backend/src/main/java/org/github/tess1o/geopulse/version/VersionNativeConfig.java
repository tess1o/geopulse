package org.github.tess1o.geopulse.version;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.version.dto.GeoPulseVersionResponse;
import org.github.tess1o.geopulse.version.dto.VersionStatusResponse;
import org.github.tess1o.geopulse.version.service.GitHubReleaseInfo;


@RegisterForReflection(targets = {
        GitHubReleaseInfo.class,
        VersionStatusResponse.class,
        GeoPulseVersionResponse.class
})
public class VersionNativeConfig {
}
