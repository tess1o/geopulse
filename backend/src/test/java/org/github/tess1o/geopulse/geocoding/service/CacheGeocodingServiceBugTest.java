package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.*;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TEST CASE TO DEMONSTRATE THE BUG IN findByExactCoordinates().
 * <p>
 * This test SHOULD FAIL with the current implementation because:
 * - findByExactCoordinates() does not filter by user_id IS NULL
 * - When both original and user copy exist at same coordinates,
 * it might return the user copy instead of the original
 * - This causes duplicate originals to be created
 * <p>
 * Expected Failure: assertFalse(duplicateOriginalsCreated) will FAIL
 * <p>
 * DO NOT FIX THE CODE - This test demonstrates the bug!
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CacheGeocodingServiceBugTest {

    @Inject
    CacheGeocodingService cacheService;

    @Inject
    ReverseGeocodingLocationRepository repository;

    @Inject
    EntityManager entityManager;

    @Inject
    CleanupHelper cleanupHelper;

    @Inject
    jakarta.transaction.UserTransaction userTransaction;

    private static UUID USER_A_ID;
    private static final double TEST_LAT = 40.7589;
    private static final double TEST_LON = -73.9851;

    @BeforeEach
    @Transactional
    void setupUser() {
        UserEntity userA = UserEntity.builder()
                .email("user-bug-test@example.com")
                .fullName("User Bug Test")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(userA);
        entityManager.flush();
        USER_A_ID = userA.getId();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        cleanupHelper.cleanupAll();
    }

    @Test
    @Order(1)
    @DisplayName("🐛 BUG TEST: Duplicate originals created when user copy exists at same coordinates")
    void testDuplicateOriginalsCreatedDueToFindByExactCoordinatesBug() throws Exception {
        /*
         * SETUP THE BUG SCENARIO
         * =====================
         *
         * Step 1: Create original at coordinates (40.7589, -73.9851)
         * Step 2: Create user copy at EXACT same coordinates
         * Step 3: Call cacheGeocodingResult() with same coordinates
         *
         * EXPECTED BEHAVIOR (correct):
         * - findByExactCoordinates() should return the original (user_id=NULL)
         * - existing != null && existing.getUser() == null → TRUE
         * - Update existing original → NO DUPLICATE
         *
         * ACTUAL BEHAVIOR (buggy):
         * - findByExactCoordinates() might return user copy (user_id=USER_A)
         * - existing != null && existing.getUser() == null → FALSE
         * - Falls into else block → CREATES DUPLICATE ORIGINAL! 🐛
         */

        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);

        Long originalId;
        Long userCopyId;

        // CRITICAL: Start and commit a transaction for test data setup
        // This ensures data is committed before cacheGeocodingResult() runs in REQUIRES_NEW
        userTransaction.begin();
        try {
            // STEP 1: Create original at coordinates
            ReverseGeocodingLocationEntity original = new ReverseGeocodingLocationEntity();
            original.setUser(null); // Original
            original.setRequestCoordinates(coords);
            original.setResultCoordinates(coords);
            original.setDisplayName("Starbucks Original");
            original.setProviderName("google");
            original.setCity("New York");
            original.setCountry("USA");
            original.setCreatedAt(Instant.now());
            original.setLastAccessedAt(Instant.now());
            repository.persist(original);
            entityManager.flush();

            originalId = original.getId();
            System.out.println("✅ Step 1: Created original with id=" + originalId);

            // STEP 2: Create user copy at EXACT same coordinates
            ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();
            userCopy.setUser(entityManager.getReference(UserEntity.class, USER_A_ID));
            userCopy.setRequestCoordinates(coords);
            userCopy.setResultCoordinates(coords);
            userCopy.setDisplayName("My Coffee Shop (User Copy)");
            userCopy.setProviderName("google");
            userCopy.setCity("New York");
            userCopy.setCountry("USA");
            userCopy.setCreatedAt(Instant.now());
            userCopy.setLastAccessedAt(Instant.now());
            repository.persist(userCopy);
            entityManager.flush();

            userCopyId = userCopy.getId();
            System.out.println("✅ Step 2: Created user copy with id=" + userCopyId);

            // Verify we have 2 entities (1 original + 1 user copy)
            long countBefore = repository.count();
            assertEquals(2, countBefore, "Should have 2 entities before cacheGeocodingResult()");

            // COMMIT the transaction so data is visible to REQUIRES_NEW transaction
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        // STEP 3: Call cacheGeocodingResult() with same coordinates (outside transaction)
        // This simulates external geocoding API being called again
        FormattableGeocodingResult newResult = SimpleFormattableResult.builder()
                .requestCoordinates(coords)
                .resultCoordinates(coords)
                .formattedDisplayName("Starbucks Updated")
                .providerName("google")
                .city("New York")
                .country("USA")
                .build();

        System.out.println("🔍 Step 3: Calling cacheGeocodingResult() - this is where the unique constraint prevents duplicates...");

        cacheService.cacheGeocodingResult(newResult);

        // VERIFICATION: Check if duplicate original was created
        // Run in new transaction to see committed results
        userTransaction.begin();
        try {
            long countAfter = repository.count();
            System.out.println("📊 Entities after: " + countAfter);

            // Query all originals (user_id IS NULL)
            List<ReverseGeocodingLocationEntity> originals = entityManager.createQuery(
                            "SELECT r FROM ReverseGeocodingLocationEntity r WHERE r.user IS NULL",
                            ReverseGeocodingLocationEntity.class)
                    .getResultList();

            System.out.println("📋 Number of originals (user_id=NULL): " + originals.size());
            originals.forEach(o -> System.out.println("   - id=" + o.getId() + ", displayName=" + o.getDisplayName()));

            // Query all user copies
            List<ReverseGeocodingLocationEntity> userCopies = entityManager.createQuery(
                            "SELECT r FROM ReverseGeocodingLocationEntity r WHERE r.user IS NOT NULL",
                            ReverseGeocodingLocationEntity.class)
                    .getResultList();

            System.out.println("📋 Number of user copies (user_id!=NULL): " + userCopies.size());
            userCopies.forEach(u -> System.out.println("   - id=" + u.getId() + ", displayName=" + u.getDisplayName()));

            // THE FIX VERIFICATION
            // ====================
            // EXPECTED: 1 original (updated) + 1 user copy = 2 total
            // The unique constraint prevents duplicates!

            boolean duplicateOriginalsCreated = originals.size() > 1;

            if (duplicateOriginalsCreated) {
                System.out.println("🐛 BUG STILL EXISTS! Found " + originals.size() + " originals (expected 1)");
                System.out.println("   The unique constraint should have prevented this!");
            } else {
                System.out.println("✅ FIX WORKS: Found 1 original (correct behavior)");
                System.out.println("   The unique constraint prevented duplicate creation");
            }

            // ASSERTIONS
            // ==========

            // This should PASS now that we have the fix
            assertFalse(duplicateOriginalsCreated,
                    "❌ FIX FAILED: Duplicate originals created! " +
                            "Found " + originals.size() + " originals instead of 1. " +
                            "The unique constraint should have prevented this.");

            // Additional verification
            assertEquals(1, originals.size(),
                    "Should have exactly 1 original (user_id=NULL)");

            assertEquals(1, userCopies.size(),
                    "Should still have exactly 1 user copy");

            assertEquals(2, countAfter,
                    "Total count should be 2 (1 original + 1 user copy)");

            // Verify the original was updated (not duplicated)
            ReverseGeocodingLocationEntity updatedOriginal = originals.get(0);
            assertEquals(originalId, updatedOriginal.getId(),
                    "Original should have been updated in-place (same ID)");
            assertEquals("Starbucks Updated", updatedOriginal.getDisplayName(),
                    "Original should have updated display name");

            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    @Order(2)
    @DisplayName("🐛 BUG TEST 2: Multiple originals accumulate over time")
    void testMultipleDuplicateOriginalsAccumulateOverTime() throws Exception {
        /*
         * This test demonstrates that the bug can cause MULTIPLE duplicate
         * originals to accumulate if cacheGeocodingResult() is called
         * repeatedly at the same coordinates where a user copy exists.
         */

        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);

        // Setup test data in a committed transaction
        userTransaction.begin();
        try {
            // Create original
            ReverseGeocodingLocationEntity original = new ReverseGeocodingLocationEntity();
            original.setUser(null);
            original.setRequestCoordinates(coords);
            original.setResultCoordinates(coords);
            original.setDisplayName("Original");
            original.setProviderName("google");
            original.setCity("New York");
            original.setCountry("USA");
            original.setCreatedAt(Instant.now());
            original.setLastAccessedAt(Instant.now());
            repository.persist(original);

            // Create user copy
            ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();
            userCopy.setUser(entityManager.getReference(UserEntity.class, USER_A_ID));
            userCopy.setRequestCoordinates(coords);
            userCopy.setResultCoordinates(coords);
            userCopy.setDisplayName("User Copy");
            userCopy.setProviderName("google");
            userCopy.setCity("New York");
            userCopy.setCountry("USA");
            userCopy.setCreatedAt(Instant.now());
            userCopy.setLastAccessedAt(Instant.now());
            repository.persist(userCopy);

            entityManager.flush();
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        // Call cacheGeocodingResult() MULTIPLE times
        for (int i = 1; i <= 3; i++) {
            FormattableGeocodingResult result = SimpleFormattableResult.builder()
                    .requestCoordinates(coords)
                    .resultCoordinates(coords)
                    .formattedDisplayName("Update " + i)
                    .providerName("google")
                    .city("New York")
                    .country("USA")
                    .build();

            cacheService.cacheGeocodingResult(result);

            System.out.println("🔄 Iteration " + i + " completed");
        }

        // Check originals count in new transaction
        userTransaction.begin();
        try {
            List<ReverseGeocodingLocationEntity> originals = entityManager.createQuery(
                            "SELECT r FROM ReverseGeocodingLocationEntity r WHERE r.user IS NULL",
                            ReverseGeocodingLocationEntity.class)
                    .getResultList();

            System.out.println("📊 After 3 cache calls: " + originals.size() + " originals found");
            originals.forEach(o -> System.out.println("   - id=" + o.getId() + ", name=" + o.getDisplayName()));

            // EXPECTED: 1 original (updated 3 times) - unique constraint prevents duplicates!
            // BEFORE FIX: Could be 1, 2, 3, or 4 originals depending on database ordering

            if (originals.size() > 1) {
                System.out.println("🐛 FIX FAILED: " + originals.size() + " duplicate originals accumulated!");
            } else {
                System.out.println("✅ FIX WORKS: Unique constraint prevented duplicates!");
            }

            assertEquals(1, originals.size(),
                    "Should have exactly 1 original even after multiple cache calls. " +
                            "Found " + originals.size() + " - unique constraint should have prevented duplicates!");

            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    @Order(3)
    @DisplayName("🔍 DIAGNOSTIC: What does findOriginalByExactCoordinates actually return?")
    void testWhatDoesFindOriginalByExactCoordinatesReturn() throws Exception {
        /*
         * This test directly probes findOriginalByExactCoordinates() to see
         * what it returns when both original and user copy exist.
         *
         * This will verify that the method has proper user_id IS NULL filtering.
         */

        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);

        Long originalId;
        Long userCopyId;

        // Create test data in committed transaction
        userTransaction.begin();
        try {
            // Create original FIRST
            ReverseGeocodingLocationEntity original = new ReverseGeocodingLocationEntity();
            original.setUser(null);
            original.setRequestCoordinates(coords);
            original.setResultCoordinates(coords);
            original.setDisplayName("I AM THE ORIGINAL");
            original.setProviderName("google");
            original.setCity("New York");
            original.setCountry("USA");
            original.setCreatedAt(Instant.now().minusSeconds(100)); // Created earlier
            original.setLastAccessedAt(Instant.now().minusSeconds(100));
            repository.persist(original);
            entityManager.flush();

            originalId = original.getId();

            // Create user copy SECOND (more recent)
            ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();
            userCopy.setUser(entityManager.getReference(UserEntity.class, USER_A_ID));
            userCopy.setRequestCoordinates(coords);
            userCopy.setResultCoordinates(coords);
            userCopy.setDisplayName("I AM A USER COPY");
            userCopy.setProviderName("google");
            userCopy.setCity("New York");
            userCopy.setCountry("USA");
            userCopy.setCreatedAt(Instant.now()); // Created later
            userCopy.setLastAccessedAt(Instant.now());
            repository.persist(userCopy);
            entityManager.flush();

            userCopyId = userCopy.getId();

            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }

        // Query in new transaction
        userTransaction.begin();
        try {
            // Call findOriginalByExactCoordinates()
            ReverseGeocodingLocationEntity result = repository.findOriginalByExactCoordinates(coords);

        System.out.println("🔍 DIAGNOSTIC RESULTS:");
        System.out.println("   Original ID:  " + originalId + " (user_id=NULL)");
        System.out.println("   User Copy ID: " + userCopyId + " (user_id=" + USER_A_ID + ")");
        System.out.println();
        System.out.println("   findOriginalByExactCoordinates() returned:");
        System.out.println("   - ID: " + (result != null ? result.getId() : "null"));
        System.out.println("   - Display Name: " + (result != null ? result.getDisplayName() : "null"));
        System.out.println("   - user_id: " + (result != null && result.getUser() != null ? result.getUser().getId() : "NULL"));
        System.out.println();

        if (result != null) {
            boolean isOriginal = result.getUser() == null;
            if (isOriginal) {
                System.out.println("✅ CORRECT: findOriginalByExactCoordinates() returned the original");
                System.out.println("   (has WHERE user_id IS NULL filter)");
            } else {
                System.out.println("❌ BUG CONFIRMED: findOriginalByExactCoordinates() returned user copy!");
                System.out.println("   (missing WHERE user_id IS NULL filter)");
            }
        }

            // ASSERTION: Should return the original, NOT the user copy
            assertNotNull(result, "findOriginalByExactCoordinates() should return an entity");
            assertTrue(result.isOriginal(),
                    "❌ BUG: findOriginalByExactCoordinates() returned user copy (id=" + result.getId() + ") " +
                            "instead of original (id=" + originalId + "). " +
                            "This proves the method does NOT filter by user_id IS NULL!");
            assertEquals(originalId, result.getId(),
                    "Should return the original ID, not the user copy ID");

            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }
}
