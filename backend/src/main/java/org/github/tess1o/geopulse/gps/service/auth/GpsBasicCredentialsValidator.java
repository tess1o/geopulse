package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.shared.security.CryptoUtils;

import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class GpsBasicCredentialsValidator {

    static final String CACHE_NAME = "gps-basic-auth";

    private final GpsBasicCredentialsCache credentialsCache;
    private final byte[] cacheKey = CryptoUtils.randomBytes(32);

    public GpsBasicCredentialsValidator(GpsBasicCredentialsCache credentialsCache) {
        this.credentialsCache = credentialsCache;
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
        // storedHash, a zero byte, then the password: the separator keeps (hash, password) pairs unambiguous.
        return CryptoUtils.toUrlSafe(CryptoUtils.hmacSha256(cacheKey,
                (storedHash + '\0' + password).getBytes(StandardCharsets.UTF_8)));
    }
}
