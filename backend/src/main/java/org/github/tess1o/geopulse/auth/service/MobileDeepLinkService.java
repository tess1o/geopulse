package org.github.tess1o.geopulse.auth.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.MobileAuthCodeEntity;
import org.github.tess1o.geopulse.auth.model.MobileAuthInitResponse;
import org.github.tess1o.geopulse.auth.repository.MobileAuthCodeRepository;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;

import java.time.Instant;
import java.util.Base64;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class MobileDeepLinkService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Inject
    MobileAuthCodeRepository mobileAuthCodeRepository;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    UserService userService;

    @ConfigProperty(name = "geopulse.auth.mobile.deeplink.url", defaultValue = "")
    @StaticInitSafe
    Optional<String> deeplinkUrl;

    @ConfigProperty(name = "geopulse.auth.mobile.code-expiry-seconds", defaultValue = "5")
    @StaticInitSafe
    int codeExpirySeconds;

    @Transactional
    public MobileAuthInitResponse generateAuthenticationLink(UUID userId) {
        var response = MobileAuthInitResponse.builder();

        if (deeplinkUrl.isEmpty()) {
            log.warn("Deeplink url is empty, skipping authentication link");
            return response.build();
        }

        String code = generateRandomCode();
        Instant now = Instant.now();

        mobileAuthCodeRepository.persist(MobileAuthCodeEntity.builder()
                .code(code)
                .userId(userId)
                .createdAt(now)
                .expiresAt(now.plusSeconds(codeExpirySeconds))
                .build());

        response.code(code);
        response.deeplinkUrl(deeplinkUrl.get());
        return response.build();
    }

    @Transactional
    public Optional<MobileAuthCodeEntity> consumeAuthenticationCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        Optional<MobileAuthCodeEntity> storedCode = mobileAuthCodeRepository.findByCode(code);
        if (storedCode.isEmpty()) {
            return Optional.empty();
        }

        MobileAuthCodeEntity mobileAuthCode = storedCode.get();
        if (!now.isBefore(mobileAuthCode.getExpiresAt())) {
            mobileAuthCodeRepository.delete(mobileAuthCode, now);
            return Optional.empty();
        }

        mobileAuthCodeRepository.delete(mobileAuthCode, now);
        return Optional.of(mobileAuthCode);
    }

    @Transactional
    public Optional<AuthResponse> exchangeSessionCode(String sessionCode) {
        Optional<MobileAuthCodeEntity> consumedCode = consumeAuthenticationCode(sessionCode);
        if (consumedCode.isEmpty()) {
            return Optional.empty();
        }

        UUID userId = consumedCode.get().getUserId();
        UserEntity user = userService.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return Optional.of(authenticationService.createAuthResponse(user));
    }

    private String generateRandomCode() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
