package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Simple test to verify GPS point deduplication using unique index.
 * Tests that inserting the same point twice results in only one point in the database.
 */
@QuarkusTest
@Slf4j
@QuarkusTestResource(PostgisTestResource.class)
public class GpsPointDeduplicationTest {

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("dedup-test-" + UUID.randomUUID() + "@test.com");
        testUser.setPasswordHash("test-hash");
        userRepository.persist(testUser);
        entityManager.flush();

        log.info("Created test user: {}", testUser.getId());
    }

    @AfterEach
    @Transactional
    void tearDown() {
        if (testUser != null) {
            gpsPointRepository.delete("user.id", testUser.getId());
            userRepository.delete(testUser);
        }
    }

    /**
     * Test basic deduplication: insert point, commit, insert same point again, verify only 1 exists.
     */
    @Test
    @Transactional
    void testBasicDeduplication() {
        log.info("=== Testing Basic GPS Point Deduplication ===");
        // STEP 1: Insert first point
        Instant timestamp = Instant.parse("2025-01-15T10:00:00Z");
        double lat = 37.7749;
        double lon = -122.4194;

        String insertSql = """
                INSERT INTO gps_points
                (user_id, device_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at)
                VALUES (?::uuid, ?, ST_GeomFromText(?, 4326), ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, timestamp, coordinates) DO NOTHING
                """;

        var query1 = entityManager.createNativeQuery(insertSql);
        query1.setParameter(1, testUser.getId().toString());
        query1.setParameter(2, "test-device");
        query1.setParameter(3, String.format("POINT(%f %f)", lon, lat));
        query1.setParameter(4, timestamp);
        query1.setParameter(5, 10.0);
        query1.setParameter(6, 95.0);
        query1.setParameter(7, 15.0);
        query1.setParameter(8, 100.0);
        query1.setParameter(9, GpsSourceType.OWNTRACKS.name());
        query1.setParameter(10, Instant.now());

        int rows1 = query1.executeUpdate();
        entityManager.flush();
        log.info("First insert affected {} rows", rows1);

        // Verify 1 point exists
        long count1 = gpsPointRepository.count("user.id = ?1", testUser.getId());
        assertEquals(1, count1, "Should have exactly 1 GPS point after first insert");
        log.info("✓ After first insert: {} points in database", count1);

        // STEP 2: Insert same point again (should be skipped due to unique index)
        var query2 = entityManager.createNativeQuery(insertSql);
        query2.setParameter(1, testUser.getId().toString());
        query2.setParameter(2, "test-device");
        query2.setParameter(3, String.format("POINT(%f %f)", lon, lat));
        query2.setParameter(4, timestamp);
        query2.setParameter(5, 10.0);
        query2.setParameter(6, 95.0);
        query2.setParameter(7, 15.0);
        query2.setParameter(8, 100.0);
        query2.setParameter(9, GpsSourceType.OWNTRACKS.name());
        query2.setParameter(10, Instant.now());

        int rows2 = query2.executeUpdate();
        entityManager.flush();
        log.info("Second insert affected {} rows (duplicate should be skipped)", rows2);

        // CRITICAL: Verify still only 1 point exists
        long count2 = gpsPointRepository.count("user.id = ?1", testUser.getId());
        assertEquals(1, count2, "Should still have exactly 1 GPS point after duplicate insert");
        log.info("✓ After duplicate insert: {} points in database (duplicate correctly skipped)", count2);

        // STEP 3: Insert different point (should succeed)
        var query3 = entityManager.createNativeQuery(insertSql);
        query3.setParameter(1, testUser.getId().toString());
        query3.setParameter(2, "test-device");
        query3.setParameter(3, String.format("POINT(%f %f)", lon + 0.001, lat + 0.001)); // Different coordinates
        query3.setParameter(4, timestamp.plusSeconds(60)); // Different timestamp
        query3.setParameter(5, 10.0);
        query3.setParameter(6, 95.0);
        query3.setParameter(7, 15.0);
        query3.setParameter(8, 100.0);
        query3.setParameter(9, GpsSourceType.OWNTRACKS.name());
        query3.setParameter(10, Instant.now());

        int rows3 = query3.executeUpdate();
        entityManager.flush();
        log.info("Third insert (different point) affected {} rows", rows3);

        // Verify 2 points exist now
        long count3 = gpsPointRepository.count("user.id = ?1", testUser.getId());
        assertEquals(2, count3, "Should have 2 GPS points after inserting different point");
        log.info("✓ After different point insert: {} points in database", count3);

        log.info("=== Basic Deduplication Test PASSED ===");
    }
}
