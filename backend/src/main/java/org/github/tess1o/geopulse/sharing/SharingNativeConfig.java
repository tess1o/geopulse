package org.github.tess1o.geopulse.sharing;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.sharing.model.*;

@RegisterForReflection(targets = {
        SharedLinkEntity.class,
        UpdateShareLinkDto.class,
        AccessTokenResponse.class,
        CreateShareLinkRequest.class,
        CreateShareLinkResponse.class,
        LocationHistoryResponse.class,
        SharedLinkDto.class,
        SharedLinksDto.class,
        SharedLocationInfo.class,
        ShareLinkResponse.class,
        UpdateShareLinkDto.class,
        VerifyPasswordRequest.class,
})
public class SharingNativeConfig {
}
