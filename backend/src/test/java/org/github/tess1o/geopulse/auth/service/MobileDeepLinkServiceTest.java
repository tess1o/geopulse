package org.github.tess1o.geopulse.auth.service;

import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.MobileAuthCodeEntity;
import org.github.tess1o.geopulse.auth.model.MobileAuthInitResponse;
import org.github.tess1o.geopulse.auth.repository.MobileAuthCodeRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class MobileDeepLinkServiceTest {

    @Mock
    MobileAuthCodeRepository mobileAuthCodeRepository;

    @Mock
    AuthenticationService authenticationService;

    @Mock
    UserService userService;

    private MobileDeepLinkService service;

    @BeforeEach
    void setUp() {
        service = new MobileDeepLinkService();
        service.mobileAuthCodeRepository = mobileAuthCodeRepository;
        service.authenticationService = authenticationService;
        service.userService = userService;
        service.deeplinkUrl = Optional.of("app://auth/code/exchange");
        service.codeExpirySeconds = 600;
    }

    @Test
    void generateAuthenticationLink_returnsCodeAndPersistsConfiguredExpiration() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-05-10T12:00:00Z");
        ArgumentCaptor<MobileAuthCodeEntity> entityCaptor = ArgumentCaptor.forClass(MobileAuthCodeEntity.class);

        try (MockedStatic<Instant> instantMock = mockStatic(Instant.class)) {
            instantMock.when(Instant::now).thenReturn(now);

            MobileAuthInitResponse response = service.generateAuthenticationLink(userId);

            assertNotNull(response.getCode());
            assertEquals("app://auth/code/exchange", response.getDeeplinkUrl());

            verify(mobileAuthCodeRepository).persist(entityCaptor.capture());
            MobileAuthCodeEntity persistedCode = entityCaptor.getValue();
            assertEquals(response.getCode(), persistedCode.getCode());
            assertEquals(userId, persistedCode.getUserId());
            assertEquals(now, persistedCode.getCreatedAt());
            assertEquals(now.plusSeconds(600), persistedCode.getExpiresAt());
        }
    }

    @Test
    void consumeAuthenticationCode_returnsStoredCodeAndDeletesIt() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-05-10T12:00:00Z");
        MobileAuthCodeEntity storedCode = MobileAuthCodeEntity.builder()
                .id(UUID.randomUUID())
                .code("generated-code")
                .userId(userId)
                .createdAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300))
                .build();


        when(mobileAuthCodeRepository.findByCode("generated-code"))
                .thenReturn(Optional.of(storedCode), Optional.empty());

        try (MockedStatic<Instant> instantMock = mockStatic(Instant.class)) {
            instantMock.when(Instant::now).thenReturn(now);

            Optional<MobileAuthCodeEntity> consumedCode = service.consumeAuthenticationCode("generated-code");
            Optional<MobileAuthCodeEntity> consumedAgain = service.consumeAuthenticationCode("generated-code");

            assertTrue(consumedCode.isPresent());
            assertEquals(userId, consumedCode.get().getUserId());
            verify(mobileAuthCodeRepository).delete(storedCode, now);
            assertFalse(consumedAgain.isPresent());
        }
    }

    @Test
    void consumeAuthenticationCode_returnsEmptyWhenCodeBlank() {
        assertFalse(service.consumeAuthenticationCode(" ").isPresent());
        verifyNoInteractions(mobileAuthCodeRepository);
    }

    @Test
    void consumeAuthenticationCode_returnsEmptyWhenCodeExpiresExactlyNow() {
        Instant now = Instant.parse("2026-05-10T12:00:00Z");
        MobileAuthCodeEntity expiredCode = MobileAuthCodeEntity.builder()
                .id(UUID.randomUUID())
                .code("expired-now-code")
                .userId(UUID.randomUUID())
                .createdAt(now.minusSeconds(600))
                .expiresAt(now)
                .build();

        when(mobileAuthCodeRepository.findByCode("expired-now-code")).thenReturn(Optional.of(expiredCode));

        try (MockedStatic<Instant> instantMock = mockStatic(Instant.class)) {
            instantMock.when(Instant::now).thenReturn(now);

            Optional<MobileAuthCodeEntity> consumedCode = service.consumeAuthenticationCode("expired-now-code");

            assertFalse(consumedCode.isPresent());
            verify(mobileAuthCodeRepository).delete(expiredCode, now);
        }
    }

    @Test
    void consumeAuthenticationCode_returnsEmptyForExpiredCode() {
        Instant now = Instant.parse("2026-05-10T12:00:00Z");
        MobileAuthCodeEntity expiredCode = MobileAuthCodeEntity.builder()
                .id(UUID.randomUUID())
                .code("expired-code")
                .userId(UUID.randomUUID())
                .createdAt(now.minusSeconds(600))
                .expiresAt(now.minusSeconds(60))
                .build();

        when(mobileAuthCodeRepository.findByCode("expired-code")).thenReturn(Optional.of(expiredCode));

        try (MockedStatic<Instant> instantMock = mockStatic(Instant.class)) {
            instantMock.when(Instant::now).thenReturn(now);

            Optional<MobileAuthCodeEntity> consumedCode = service.consumeAuthenticationCode("expired-code");

            assertFalse(consumedCode.isPresent());
            verify(mobileAuthCodeRepository).delete(expiredCode, now);
        }
    }

    @Test
    void consumeAuthenticationCode_doesNotDeleteWhenCodeNotFound() {
        when(mobileAuthCodeRepository.findByCode("missing-code")).thenReturn(Optional.empty());

        assertFalse(service.consumeAuthenticationCode("missing-code").isPresent());

        verify(mobileAuthCodeRepository, never()).delete(any(MobileAuthCodeEntity.class), any(Instant.class));
    }

    @Test
    void exchangeSessionCode_returnsAuthResponseForValidCode() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-05-10T12:00:00Z");
        MobileAuthCodeEntity storedCode = MobileAuthCodeEntity.builder()
                .id(UUID.randomUUID())
                .code("valid-code")
                .userId(userId)
                .createdAt(now.minusSeconds(5))
                .expiresAt(now.plusSeconds(300))
                .build();
        UserEntity user = UserEntity.builder().id(userId).email("user@example.com").build();
        AuthResponse authResponse = AuthResponse.builder()
                .id(userId.toString())
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(1800)
                .build();

        when(mobileAuthCodeRepository.findByCode("valid-code")).thenReturn(Optional.of(storedCode));
        when(userService.findById(userId)).thenReturn(Optional.of(user));
        when(authenticationService.createAuthResponse(user)).thenReturn(authResponse);

        Optional<AuthResponse> exchangedResponse;
        try (MockedStatic<Instant> instantMock = mockStatic(Instant.class)) {
            instantMock.when(Instant::now).thenReturn(now);
            exchangedResponse = service.exchangeSessionCode("valid-code");
        }

        assertTrue(exchangedResponse.isPresent());
        assertEquals(authResponse, exchangedResponse.get());
        verify(mobileAuthCodeRepository).delete(storedCode, now);
        verify(authenticationService).createAuthResponse(user);
    }

    @Test
    void exchangeSessionCode_returnsEmptyWhenSessionCodeInvalid() {
        when(mobileAuthCodeRepository.findByCode("missing-code")).thenReturn(Optional.empty());

        Optional<AuthResponse> exchangedResponse = service.exchangeSessionCode("missing-code");

        assertFalse(exchangedResponse.isPresent());
        verifyNoInteractions(authenticationService, userService);
    }
}
