package org.github.tess1o.geopulse.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.auth.dto.AuthStatusResponse;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.LoginRequest;
import org.github.tess1o.geopulse.auth.model.TokenRefreshRequest;
import org.github.tess1o.geopulse.auth.oidc.dto.*;
import org.github.tess1o.geopulse.auth.oidc.model.OidcSessionStateEntity;
import org.github.tess1o.geopulse.auth.oidc.model.UserOidcConnectionEntity;

@RegisterForReflection(
        targets = {
                UserOidcConnectionEntity.class,
                UserOidcConnectionResponse.class,
                OidcSessionStateEntity.class,
                InitiateOidcLinkingRequest.class,
                LinkAccountWithPasswordRequest.class,
                OidcAccountLinkingErrorResponse.class,
                OidcCallbackRequest.class,
                OidcLoginInitResponse.class,
                OidcProviderResponse.class,
                OidcUserInfo.class,
                OidcTokenResponse.class,
                UserOidcConnectionResponse.class,
                AuthResponse.class,
                LoginRequest.class,
                TokenRefreshRequest.class,
                AuthStatusResponse.class
        }
)
public class AuthNativeConfig {
}
