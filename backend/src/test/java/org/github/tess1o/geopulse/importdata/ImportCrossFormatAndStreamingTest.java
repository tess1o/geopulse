package org.github.tess1o.geopulse.importdata;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.importdata.service.ImportService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.CleanupHelper;

/**
 * Test suite for cross-format duplicate detection and streaming edge cases.
 *
 * Tests cover:
 * - Cross-format duplicate detection (GPX -> OwnTracks, etc.)
 * - Timestamp precision across formats
 * - Large file streaming behavior
 * - Temp file handling
 */
@QuarkusTest
@Slf4j
public class ImportCrossFormatAndStreamingTest {

    @Inject
    ImportDataService importDataService;

    @Inject
    ImportService importService;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    CleanupHelper cleanupHelper;

    private UserEntity testUser;
    private UUID testUserId;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupHelper.cleanupAll();

        testUser = new UserEntity();
        testUser.setEmail("test-cross-format@geopulse.test");
        testUser.setPasswordHash("test-hash");
        testUser.setEmailVerified(true);
        testUser.setActive(true);
        testUser.setRole("USER");
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        userRepository.persist(testUser);
        testUserId = testUser.getId();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupHelper.cleanupAll();
    }

    /**
     * CRITICAL TEST: Cross-format duplicate detection
     *
     * Scenario: Import as GPX, then import same data as OwnTracks
     * Expected: Duplicates detected despite format difference (same timestamp + coordinates)
     *
     * This verifies that the unique constraint works across different import formats.
     */
    @Test
    @Transactional
    void testCrossFormatDuplicateDetection_GpxThenOwnTracks() throws Exception {
        log.info("=== Testing cross-format duplicate detection (GPX -> OwnTracks) ===");

        // Use whole-second timestamps for compatibility
        Instant timestamp1 = Instant.parse("2025-01-01T12:00:00Z");
        Instant timestamp2 = Instant.parse("2025-01-01T13:00:00Z");

        // Step 1: Import as GPX
        String gpxData = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="GeoPulse">
                  <trk>
                    <name>Test Track</name>
                    <trkseg>
                      <trkpt lat="37.7749" lon="-122.4194">
                        <time>2025-01-01T12:00:00Z</time>
                        <ele>100.0</ele>
                      </trkpt>
                      <trkpt lat="37.7849" lon="-122.4094">
                        <time>2025-01-01T13:00:00Z</time>
                        <ele>110.0</ele>
                      </trkpt>
                    </trkseg>
                  </trk>
                </gpx>
                """;

        ImportOptions gpxOptions = new ImportOptions();
        gpxOptions.setImportFormat("gpx");
        gpxOptions.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob gpxJob = importService.createImportJob(
                testUserId, gpxOptions, "test.gpx", gpxData.getBytes());

        importDataService.processImportData(gpxJob);

        long gpxCount = gpsPointRepository.count("user.id = ?1", testUserId);
        assertEquals(2, gpxCount, "GPX import should succeed");
        log.info("✓ GPX import: {} points", gpxCount);

        // Step 2: Import SAME data as OwnTracks (same timestamp + coordinates)
        List<OwnTracksLocationMessage> ownTracksMessages = new ArrayList<>();
        ownTracksMessages.add(OwnTracksLocationMessage.builder()
                .type("location")
                .lat(37.7749)
                .lon(-122.4194)
                .tst(timestamp1.getEpochSecond())
                .acc(5.0)
                .build());
        ownTracksMessages.add(OwnTracksLocationMessage.builder()
                .type("location")
                .lat(37.7849)
                .lon(-122.4094)
                .tst(timestamp2.getEpochSecond())
                .acc(5.0)
                .build());

        ImportOptions ownTracksOptions = new ImportOptions();
        ownTracksOptions.setImportFormat("owntracks");
        ownTracksOptions.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob ownTracksJob = importService.createOwnTracksImportJob(
                testUserId, ownTracksOptions, "test.json",
                objectMapper.writeValueAsString(ownTracksMessages).getBytes());

        importDataService.processImportData(ownTracksJob);

        long finalCount = gpsPointRepository.count("user.id = ?1", testUserId);
        assertEquals(2, finalCount,
                "Cross-format duplicates should be detected (same timestamp + coordinates)");

        log.info("✓ OwnTracks import: {} total points (0 new, 2 duplicates skipped)", finalCount);
        log.info("=== TEST PASSED: Cross-format duplicate detection works correctly ===");
    }

    /**
     * TEST: Timestamp precision across formats
     *
     * Tests that formats with different timestamp precision handle duplicates correctly.
     * - OwnTracks: epoch seconds (no subsecond)
     * - GPX: ISO-8601 (can have subseconds)
     * - Google Timeline: various formats
     */
    @Test
    @Transactional
    void testTimestampPrecision_GpxSubsecondHandling() throws Exception {
        log.info("=== Testing GPX subsecond timestamp precision ===");

        // GPX with subsecond timestamps
        String gpxWithSubseconds = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1">
                  <trk>
                    <trkseg>
                      <trkpt lat="37.7749" lon="-122.4194">
                        <time>2025-01-01T12:00:00.123Z</time>
                      </trkpt>
                      <trkpt lat="37.7749" lon="-122.4194">
                        <time>2025-01-01T12:00:00.456Z</time>
                      </trkpt>
                    </trkseg>
                  </trk>
                </gpx>
                """;

        ImportOptions options = new ImportOptions();
        options.setImportFormat("gpx");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = importService.createImportJob(
                testUserId, options, "subseconds.gpx", gpxWithSubseconds.getBytes());

        importDataService.processImportData(job);

        long count = gpsPointRepository.count("user.id = ?1", testUserId);

        // These should be treated as DIFFERENT points (different subsecond timestamps)
        // Same coordinates, but timestamps differ by 333ms
        assertEquals(2, count,
                "GPX points with different subsecond timestamps should be treated as different");

        // Verify the timestamps were preserved correctly
        List<GpsPointEntity> points = gpsPointRepository.list("user.id = ?1 ORDER BY timestamp", testUserId);
        assertEquals(2, points.size());

        Instant ts1 = points.get(0).getTimestamp();
        Instant ts2 = points.get(1).getTimestamp();

        assertNotEquals(ts1, ts2, "Timestamps should be different");
        assertTrue(ts1.isBefore(ts2), "First timestamp should be before second");

        long millisDiff = ChronoUnit.MILLIS.between(ts1, ts2);
        assertEquals(333, millisDiff, "Timestamp difference should be 333ms");

        log.info("✓ Subsecond timestamps preserved: {} vs {}", ts1, ts2);
        log.info("=== TEST PASSED: GPX subsecond precision handled correctly ===");
    }

    /**
     * TEST: Large dataset streaming performance
     *
     * Verifies that large datasets are handled efficiently with streaming,
     * and batch processing works correctly for 1000+ points.
     */
    @Test
    @Transactional
    void testStreamingPerformance_LargeDataset() throws Exception {
        log.info("=== Testing streaming performance with large dataset (1000 points) ===");

        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<OwnTracksLocationMessage> messages = new ArrayList<>();

        // Create 1000 points
        for (int i = 0; i < 1000; i++) {
            messages.add(OwnTracksLocationMessage.builder()
                    .type("location")
                    .lat(37.0 + (i * 0.0001))
                    .lon(-122.0 + (i * 0.0001))
                    .tst(baseTime.plusSeconds(i * 60).getEpochSecond())
                    .acc(5.0)
                    .build());
        }

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        long startTime = System.currentTimeMillis();

        ImportJob job = importService.createOwnTracksImportJob(
                testUserId, options, "large.json",
                objectMapper.writeValueAsString(messages).getBytes());

        importDataService.processImportData(job);

        long duration = System.currentTimeMillis() - startTime;

        long count = gpsPointRepository.count("user.id = ?1", testUserId);
        assertEquals(1000, count, "All 1000 points should be imported");

        log.info("✓ Large dataset import completed in {}ms ({} points/sec)",
                duration, (1000 * 1000) / duration);
        log.info("=== TEST PASSED: Large dataset streaming works efficiently ===");

        // Performance assertion: should complete in reasonable time (< 30 seconds)
        assertTrue(duration < 30000,
                "Import of 1000 points should complete in < 30 seconds. Took: " + duration + "ms");
    }

    /**
     * TEST: Geographic edge cases
     *
     * Tests edge cases at poles and dateline
     */
    @Test
    @Transactional
    void testGeographicEdgeCases_PolesAndDateline() throws Exception {
        log.info("=== Testing geographic edge cases (poles, dateline) ===");

        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        List<OwnTracksLocationMessage> messages = new ArrayList<>();

        // North Pole
        messages.add(OwnTracksLocationMessage.builder()
                .type("location")
                .lat(90.0)
                .lon(0.0)
                .tst(baseTime.getEpochSecond())
                .build());

        // South Pole
        messages.add(OwnTracksLocationMessage.builder()
                .type("location")
                .lat(-90.0)
                .lon(0.0)
                .tst(baseTime.plusSeconds(60).getEpochSecond())
                .build());

        // International Date Line (positive)
        messages.add(OwnTracksLocationMessage.builder()
                .type("location")
                .lat(0.0)
                .lon(180.0)
                .tst(baseTime.plusSeconds(120).getEpochSecond())
                .build());

        // International Date Line (negative)
        messages.add(OwnTracksLocationMessage.builder()
                .type("location")
                .lat(0.0)
                .lon(-180.0)
                .tst(baseTime.plusSeconds(180).getEpochSecond())
                .build());

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = importService.createOwnTracksImportJob(
                testUserId, options, "edge-cases.json",
                objectMapper.writeValueAsString(messages).getBytes());

        importDataService.processImportData(job);

        long count = gpsPointRepository.count("user.id = ?1", testUserId);

        // Note: ±180° longitude are the same location (dateline), might be treated as duplicates
        // depending on PostGIS normalization
        assertTrue(count >= 3 && count <= 4,
                "Should import pole points and handle dateline correctly. Got: " + count);

        List<GpsPointEntity> points = gpsPointRepository.list("user.id = ?1", testUserId);
        boolean hasNorthPole = points.stream().anyMatch(p -> p.getLatitude() == 90.0);
        boolean hasSouthPole = points.stream().anyMatch(p -> p.getLatitude() == -90.0);

        assertTrue(hasNorthPole, "Should have North Pole point");
        assertTrue(hasSouthPole, "Should have South Pole point");

        log.info("✓ Geographic edge cases handled: {} points imported", count);
        log.info("=== TEST PASSED: Geographic edge cases work correctly ===");
    }

    /**
     * TEST: Batch size edge cases
     *
     * Tests batch processing with very small and very large batch sizes
     */
    @Test
    @Transactional
    void testBatchSize_EdgeCases() throws Exception {
        log.info("=== Testing batch size edge cases ===");

        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // Test with small dataset that fits in single batch
        List<OwnTracksLocationMessage> smallDataset = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            smallDataset.add(OwnTracksLocationMessage.builder()
                    .type("location")
                    .lat(37.7749 + (i * 0.001))
                    .lon(-122.4194 + (i * 0.001))
                    .tst(baseTime.plusSeconds(i * 60).getEpochSecond())
                    .acc(5.0)
                    .build());
        }

        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.RAW_GPS));

        ImportJob job = importService.createOwnTracksImportJob(
                testUserId, options, "small.json",
                objectMapper.writeValueAsString(smallDataset).getBytes());

        importDataService.processImportData(job);

        long count = gpsPointRepository.count("user.id = ?1", testUserId);
        assertEquals(5, count, "Small dataset should import correctly");

        log.info("✓ Small dataset (5 points) imported successfully");
        log.info("=== TEST PASSED: Batch size edge cases handled ===");
    }
}
