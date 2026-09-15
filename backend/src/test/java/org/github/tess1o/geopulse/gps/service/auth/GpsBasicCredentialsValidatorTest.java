package org.github.tess1o.geopulse.gps.service.auth;

import io.quarkus.cache.CacheManager;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GpsBasicCredentialsValidatorTest {

    @Inject
    GpsBasicCredentialsValidator validator;

    @Inject
    CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        cacheManager.getCache(GpsBasicCredentialsValidator.CACHE_NAME).orElseThrow()
                .invalidateAll().await().indefinitely();
    }

    @Test
    void cachesValidCredentialsButNotInvalidCredentials() {
        SecurePasswordUtils passwordUtils = mock(SecurePasswordUtils.class);
        QuarkusMock.installMockForType(passwordUtils, SecurePasswordUtils.class);
        when(passwordUtils.isPasswordValid("valid", "hash")).thenReturn(true);
        when(passwordUtils.isPasswordValid("invalid", "hash")).thenReturn(false);

        assertTrue(validator.isValid("valid", "hash"));
        assertTrue(validator.isValid("valid", "hash"));
        assertFalse(validator.isValid("invalid", "hash"));
        assertFalse(validator.isValid("invalid", "hash"));

        verify(passwordUtils, times(1)).isPasswordValid("valid", "hash");
        verify(passwordUtils, times(2)).isPasswordValid("invalid", "hash");
    }

    @Test
    void passwordHashChangeForcesRevalidation() {
        SecurePasswordUtils passwordUtils = mock(SecurePasswordUtils.class);
        QuarkusMock.installMockForType(passwordUtils, SecurePasswordUtils.class);
        when(passwordUtils.isPasswordValid("valid", "old-hash")).thenReturn(true);
        when(passwordUtils.isPasswordValid("valid", "new-hash")).thenReturn(true);

        assertTrue(validator.isValid("valid", "old-hash"));
        assertTrue(validator.isValid("valid", "new-hash"));

        verify(passwordUtils).isPasswordValid("valid", "old-hash");
        verify(passwordUtils).isPasswordValid("valid", "new-hash");
    }
}
