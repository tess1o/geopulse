package org.github.tess1o.geopulse.auth.rest;

import org.github.tess1o.geopulse.auth.config.AuthConfigurationService;
import org.github.tess1o.geopulse.auth.model.LoginRequest;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.auth.service.BrowserAuthResponseMapper;
import org.github.tess1o.geopulse.auth.service.CookieService;
import org.github.tess1o.geopulse.auth.service.DemoModeService;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.PASSWORD_LOGIN_DISABLED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class AuthenticationResourceTest {

    @Test
    void passwordDisabledRemainsAClassifiedForbiddenFailure() {
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        AuthConfigurationService configurationService = mock(AuthConfigurationService.class);
        when(configurationService.isPasswordLoginEnabledForUser("user@example.com")).thenReturn(false);
        AuthenticationResource resource = new AuthenticationResource(
                authenticationService,
                mock(CookieService.class),
                mock(BrowserAuthResponseMapper.class),
                configurationService,
                mock(DemoModeService.class),
                mock(UserService.class));

        assertThatThrownBy(() -> resource.loginUser(new LoginRequest("user@example.com", "password")))
                .isInstanceOfSatisfying(GeoPulseException.class, error -> {
                    org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo(PASSWORD_LOGIN_DISABLED);
                    org.assertj.core.api.Assertions.assertThat(error.code().statusCode()).isEqualTo(403);
                });
        verifyNoInteractions(authenticationService);
    }
}
