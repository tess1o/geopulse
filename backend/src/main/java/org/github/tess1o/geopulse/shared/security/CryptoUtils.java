package org.github.tess1o.geopulse.shared.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Shared primitives for secrets and fingerprints: random tokens, SHA-256 digests and HMAC-SHA256.
 * Static so code that runs without CDI (backup tooling) can use it too.
 */
public final class CryptoUtils {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_SAFE = Base64.getUrlEncoder().withoutPadding();
    private static final String HMAC_SHA256 = "HmacSHA256";

    private CryptoUtils() {
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    /** {@code byteLength} random bytes, base64url without padding: safe in URLs, headers and cookies. */
    public static String randomUrlSafeToken(int byteLength) {
        return URL_SAFE.encodeToString(randomBytes(byteLength));
    }

    /** A random string of {@code length} characters drawn from {@code alphabet}, e.g. a temporary password. */
    public static String randomString(String alphabet, int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length())));
        }
        return value.toString();
    }

    /** Lowercase hex SHA-256 of the UTF-8 bytes of {@code value}. */
    public static String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    /** base64url without padding, the encoding used for every token above. */
    public static String toUrlSafe(byte[] value) {
        return URL_SAFE.encodeToString(value);
    }
}
