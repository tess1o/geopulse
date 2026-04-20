package org.github.tess1o.geopulse.version;

import io.quarkus.runtime.annotations.RegisterForReflection;


@RegisterForReflection(targets = {
        GitHubReleaseInfo.class,
        VersionStatusResponse.class
})
public class VersionNativeConfig {
}
