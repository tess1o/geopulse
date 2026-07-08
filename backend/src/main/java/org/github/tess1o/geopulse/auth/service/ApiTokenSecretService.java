package org.github.tess1o.geopulse.auth.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@ApplicationScoped
public class ApiTokenSecretService {
    public static final String TOKEN_PREFIX = "gp_sa_";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int RANDOM_BYTES = 48;
    private static final int TOKEN_SUFFIX_LENGTH = 8;

    public String generateToken() {
        byte[] randomBytes = new byte[RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return TOKEN_PREFIX + TOKEN_ENCODER.encodeToString(randomBytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public boolean hasTokenPrefix(String rawToken) {
        return rawToken != null && rawToken.startsWith(TOKEN_PREFIX);
    }

    public String suffix(String rawToken) {
        return rawToken.substring(rawToken.length() - TOKEN_SUFFIX_LENGTH);
    }
}
