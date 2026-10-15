package org.github.tess1o.geopulse.auth;

import org.github.tess1o.geopulse.auth.dto.LogoutResponse;
import org.github.tess1o.geopulse.auth.session.model.SessionAuthMethod;
import org.github.tess1o.geopulse.auth.session.model.SessionClientType;
import org.github.tess1o.geopulse.auth.session.model.SessionRevocationReason;
import org.github.tess1o.geopulse.auth.session.model.UserSessionEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.auth.dto.*;
import org.github.tess1o.geopulse.auth.model.ApiTokenScope;
import org.github.tess1o.geopulse.auth.model.ApiTokenStatus;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.BrowserAuthResponse;
import org.github.tess1o.geopulse.auth.model.LoginRequest;
import org.github.tess1o.geopulse.auth.model.MobileAuthCodeEntity;
import org.github.tess1o.geopulse.auth.model.MobileAuthInitResponse;
import org.github.tess1o.geopulse.auth.model.MobileSessionExchangeRequest;
import org.github.tess1o.geopulse.auth.model.TokenRefreshRequest;
import org.github.tess1o.geopulse.auth.model.UserApiTokenEntity;
import org.github.tess1o.geopulse.auth.oidc.dto.*;
import org.github.tess1o.geopulse.auth.oidc.model.OidcDiscoveryDocument;
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
                OidcDiscoveryDocument.class,
                OidcTokenResponse.class,
                UserOidcConnectionResponse.class,
                AuthResponse.class,
                BrowserAuthResponse.class,
                LoginRequest.class,
                MobileAuthCodeEntity.class,
                MobileAuthInitResponse.class,
                MobileSessionExchangeRequest.class,
                TokenRefreshRequest.class,
                AuthStatusResponse.class,
                UserApiTokenEntity.class,
                ApiTokenStatus.class,
                ApiTokenScope.class,
                ApiTokenResponse.class,
                CreateApiTokenRequest.class,
                CreateApiTokenResponse.class,
                UpdateApiTokenRequest.class,
                DemoPersonaResponse.class,
                DemoLoginRequest.class,
                LogoutResponse.class,
                UserSessionEntity.class,
                SessionClientType.class,
                SessionAuthMethod.class,
                SessionRevocationReason.class,
        }
)
public class AuthNativeConfig {
}
