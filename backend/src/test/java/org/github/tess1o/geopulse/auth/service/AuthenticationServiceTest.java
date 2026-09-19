package org.github.tess1o.geopulse.auth.service;

import org.github.tess1o.geopulse.admin.service.AdminBootstrapService;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_CREDENTIALS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock UserService userService;
    @Mock SecurePasswordUtils securePasswordUtils;
    @Mock AdminBootstrapService adminBootstrapService;
    @Mock DemoModeService demoModeService;

    AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(userService, securePasswordUtils, adminBootstrapService, demoModeService);
    }

    @Test
    void missingUserStillVerifiesAgainstDummyHash() {
        when(userService.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(securePasswordUtils.dummyPasswordHash()).thenReturn("dummy-hash");
        when(securePasswordUtils.isPasswordValid("wrong", "dummy-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("missing@example.com", "wrong"))
                .isInstanceOfSatisfying(GeoPulseException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo(INVALID_CREDENTIALS));

        verify(securePasswordUtils).isPasswordValid("wrong", "dummy-hash");
    }

    @Test
    void wrongPasswordVerifiesAgainstStoredHash() {
        UserEntity user = new UserEntity();
        user.setPasswordHash("stored-hash");
        when(userService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(securePasswordUtils.isPasswordValid("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("user@example.com", "wrong"))
                .isInstanceOfSatisfying(GeoPulseException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo(INVALID_CREDENTIALS));

        verify(securePasswordUtils).isPasswordValid("wrong", "stored-hash");
    }

    @Test
    void inactiveAccountIsCheckedOnlyAfterValidPassword() {
        UserEntity user = new UserEntity();
        user.setPasswordHash("stored-hash");
        user.setActive(false);
        when(userService.findByEmail("inactive@example.com")).thenReturn(Optional.of(user));
        when(securePasswordUtils.isPasswordValid("correct", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate("inactive@example.com", "correct"))
                .isInstanceOfSatisfying(GeoPulseException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo(ACCESS_DENIED));

        verify(securePasswordUtils).isPasswordValid("correct", "stored-hash");
    }
}
