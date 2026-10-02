package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class GpsBasicCredentialsValidator {

    static final String CACHE_NAME = "gps-basic-auth";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final GpsBasicCredentialsCache credentialsCache;
    private final byte[] cacheKey = new byte[32];

    public GpsBasicCredentialsValidator(GpsBasicCredentialsCache credentialsCache) {
        this.credentialsCache = credentialsCache;
        new SecureRandom().nextBytes(cacheKey);
    }

    public boolean isValid(String password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }
        try {
            return credentialsCache.verify(cacheKey(password, storedHash), password, storedHash);
        } catch (InvalidPasswordException e) {
            return false;
        }
    }

    private String cacheKey(String password, String storedHash) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(cacheKey, HMAC_ALGORITHM));
            mac.update(storedHash.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to build GPS credential cache key", e);
        }
    }
}
