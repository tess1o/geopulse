package org.github.tess1o.geopulse.gps.service.auth;

import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;

@ApplicationScoped
class GpsBasicCredentialsCache {

    private final SecurePasswordUtils passwordUtils;

    GpsBasicCredentialsCache(SecurePasswordUtils passwordUtils) {
        this.passwordUtils = passwordUtils;
    }

    @CacheResult(cacheName = GpsBasicCredentialsValidator.CACHE_NAME)
    public boolean verify(@CacheKey String credentialKey, String password, String storedHash) {
        if (!passwordUtils.isPasswordValid(password, storedHash)) {
            throw new InvalidPasswordException("Invalid GPS source password");
        }
        return true;
    }
}
