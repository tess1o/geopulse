package org.github.tess1o.geopulse.immich;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.immich.model.*;

@RegisterForReflection(targets = {
        ImmichPreferences.class,
        ImmichSearchRequest.class,
        ImmichSearchResponse.class,
        ImmichSearchResponse.ImmichSearchAssets.class,
        ImmichAsset.class,
        ImmichPhotoDto.class,
        ImmichPhotoSearchRequest.class,
        ImmichPhotoSearchResponse.class,
        ImmichConfigResponse.class,
        UpdateImmichConfigRequest.class,
        ImmichExifInfo.class,
        TestImmichConnectionRequest.class,
        TestImmichConnectionResponse.class
})
public class ImmichNativeConfig {
}
