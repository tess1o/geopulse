package org.github.tess1o.geopulse.geocoding;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingService;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating the transaction rollback problem with geocoding cache.
 *
 * PROBLEM: When using @Transactional(TxType.REQUIRED), cache writes participate in the outer transaction.
 * If the outer transaction rolls back, all cache writes are lost, causing users to "go in circles"
 * by repeatedly geocoding the same coordinates.
 *
 * SOLUTION: Change to @Transactional(TxType.REQUIRES_NEW) so cache writes commit independently
 * and survive outer transaction rollbacks.
 *
 * This test simulates the real scenario:
 * 1. Outer transaction starts (timeline generation)
 * 2. Multiple coordinates get successfully geocoded and cached
 * 3. An error occurs later in the transaction (e.g., circuit breaker, validation failure)
 * 4. Outer transaction rolls back
 * 5. CURRENT BUG: Cache writes are lost
 * 6. EXPECTED: Cache writes should survive because they used REQUIRES_NEW
 */
@QuarkusTest
@Slf4j
class GeocodingCacheTransactionRollbackTest {

    @Inject
    CacheGeocodingService cacheGeocodingService;

    @Inject
    ReverseGeocodingLocationRepository geocodingRepository;

    @Inject
    UserTransaction userTransaction;

    private Point testPoint1;
    private Point testPoint2;
    private Point testPoint3;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up any existing test data
        if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
            userTransaction.rollback();
        }

        userTransaction.begin();
        try {
            geocodingRepository.deleteAll();
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        // Create test points
        testPoint1 = GeoUtils.createPoint(-73.9851, 40.7589); // NYC
        testPoint2 = GeoUtils.createPoint(-0.1276, 51.5074);  // London
        testPoint3 = GeoUtils.createPoint(2.3522, 48.8566);   // Paris
    }

    /**
     * This test demonstrates the CURRENT BUG where cache writes are lost on transaction rollback.
     *
     * With TxType.REQUIRED:
     * - Cache writes participate in outer transaction
     * - When outer transaction rolls back, cache writes are lost
     * - Test FAILS because cached data is gone
     *
     * With TxType.REQUIRES_NEW:
     * - Cache writes commit immediately in independent transactions
     * - When outer transaction rolls back, cache writes survive
     * - Test PASSES because cached data persists
     */
    @Test
    void testCacheWritesSurviveOuterTransactionRollback() throws Exception {
        log.info("=== Testing cache persistence during transaction rollback ===");

        // Start outer transaction (simulating timeline generation)
        userTransaction.begin();

        try {
            // Simulate successful geocoding and caching of multiple points
            log.info("Step 1: Caching geocoding results for 3 points...");

            FormattableGeocodingResult result1 = SimpleFormattableResult.builder()
                    .requestCoordinates(testPoint1)
                    .resultCoordinates(testPoint1)
                    .formattedDisplayName("New York, USA")
                    .providerName("test-provider")
                    .city("New York")
                    .country("USA")
                    .build();
            cacheGeocodingService.cacheGeocodingResult(result1);
            log.info("   ✓ Cached point 1: New York");

            FormattableGeocodingResult result2 = SimpleFormattableResult.builder()
                    .requestCoordinates(testPoint2)
                    .resultCoordinates(testPoint2)
                    .formattedDisplayName("London, UK")
                    .providerName("test-provider")
                    .city("London")
                    .country("UK")
                    .build();
            cacheGeocodingService.cacheGeocodingResult(result2);
            log.info("   ✓ Cached point 2: London");

            FormattableGeocodingResult result3 = SimpleFormattableResult.builder()
                    .requestCoordinates(testPoint3)
                    .resultCoordinates(testPoint3)
                    .formattedDisplayName("Paris, France")
                    .providerName("test-provider")
                    .city("Paris")
                    .country("France")
                    .build();
            cacheGeocodingService.cacheGeocodingResult(result3);
            log.info("   ✓ Cached point 3: Paris");

            // Simulate an error that causes the outer transaction to roll back
            // This could be: circuit breaker opening, validation failure, database constraint violation, etc.
            log.info("Step 2: Simulating error that causes transaction rollback...");
            throw new RuntimeException("Simulated error: Circuit breaker opened, validation failed, etc.");

        } catch (Exception e) {
            // Transaction rolls back (this is what happens in real scenario)
            log.info("Step 3: Rolling back outer transaction due to error: {}", e.getMessage());
            userTransaction.rollback();
        }

        // Now check if cache writes survived the rollback
        log.info("Step 4: Verifying cache data persistence after rollback...");

        // Start new transaction to read cache
        userTransaction.begin();
        try {
            Optional<FormattableGeocodingResult> cached1 = cacheGeocodingService.getCachedGeocodingResult(testPoint1);
            Optional<FormattableGeocodingResult> cached2 = cacheGeocodingService.getCachedGeocodingResult(testPoint2);
            Optional<FormattableGeocodingResult> cached3 = cacheGeocodingService.getCachedGeocodingResult(testPoint3);

            userTransaction.commit();

            // CRITICAL ASSERTIONS
            // With TxType.REQUIRED: These assertions FAIL (cache data is lost)
            // With TxType.REQUIRES_NEW: These assertions PASS (cache data survives)

            log.info("=== VERIFICATION RESULTS ===");

            if (cached1.isPresent()) {
                log.info("✓ Point 1 (New York) found in cache: {}", cached1.get().getFormattedDisplayName());
            } else {
                log.error("✗ Point 1 (New York) NOT found in cache - DATA LOST!");
            }

            if (cached2.isPresent()) {
                log.info("✓ Point 2 (London) found in cache: {}", cached2.get().getFormattedDisplayName());
            } else {
                log.error("✗ Point 2 (London) NOT found in cache - DATA LOST!");
            }

            if (cached3.isPresent()) {
                log.info("✓ Point 3 (Paris) found in cache: {}", cached3.get().getFormattedDisplayName());
            } else {
                log.error("✗ Point 3 (Paris) NOT found in cache - DATA LOST!");
            }

            // These assertions demonstrate the problem:
            // BEFORE FIX (TxType.REQUIRED): All three fail - cache data lost on rollback
            // AFTER FIX (TxType.REQUIRES_NEW): All three pass - cache data survives rollback
            assertTrue(cached1.isPresent(),
                "Point 1 should be cached even after outer transaction rollback (with REQUIRES_NEW)");
            assertTrue(cached2.isPresent(),
                "Point 2 should be cached even after outer transaction rollback (with REQUIRES_NEW)");
            assertTrue(cached3.isPresent(),
                "Point 3 should be cached even after outer transaction rollback (with REQUIRES_NEW)");

            // Verify the cached data is correct
            assertEquals("New York, USA", cached1.get().getFormattedDisplayName());
            assertEquals("London, UK", cached2.get().getFormattedDisplayName());
            assertEquals("Paris, France", cached3.get().getFormattedDisplayName());

            log.info("=== TEST PASSED: Cache data survived transaction rollback! ===");

        } catch (Exception e) {
            if (userTransaction.getStatus() == Status.STATUS_ACTIVE) {
                userTransaction.rollback();
            }
            throw e;
        }
    }

    /**
     * Complementary test: Verify that cache reads work correctly with independent transactions.
     */
    @Test
    void testCacheReadsWorkIndependentOfTransaction() throws Exception {
        log.info("=== Testing cache reads with transaction independence ===");

        // First, cache some data
        userTransaction.begin();
        FormattableGeocodingResult result = SimpleFormattableResult.builder()
                .requestCoordinates(testPoint1)
                .resultCoordinates(testPoint1)
                .formattedDisplayName("Test Location")
                .providerName("test-provider")
                .build();
        cacheGeocodingService.cacheGeocodingResult(result);
        userTransaction.commit();

        // Now read without an active transaction (simulates background job scenario)
        Optional<FormattableGeocodingResult> cached = cacheGeocodingService.getCachedGeocodingResult(testPoint1);

        assertTrue(cached.isPresent(), "Cache read should work without active transaction (with REQUIRES_NEW)");
        assertEquals("Test Location", cached.get().getFormattedDisplayName());

        log.info("=== TEST PASSED: Cache reads work independently of transactions! ===");
    }
}
