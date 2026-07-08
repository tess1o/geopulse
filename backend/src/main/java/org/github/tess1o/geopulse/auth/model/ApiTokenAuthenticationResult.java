package org.github.tess1o.geopulse.auth.model;

import org.github.tess1o.geopulse.user.model.UserEntity;

public record ApiTokenAuthenticationResult(
        UserApiTokenEntity apiToken,
        UserEntity user
) {
}
