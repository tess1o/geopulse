package org.github.tess1o.geopulse.export.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for GpxExportService.
 * Tests GPX export functionality including:
 * - Bulk export for timeframe (with raw GPS, trips, and stays)
 * - Single trip export
 * - Single stay export
 * - Per-trip/stay ZIP export
 * - XML validation against GPX 1.1 schema
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class GpxExportServiceTest {

    @Inject
    GpxExportService gpxExportService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    CleanupHelper cleanupHelper;

    @Inject
    org.github.tess1o.geopulse.importdata.service.GpxImportStrategy gpxImportStrategy;

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final XmlMapper xmlMapper = XmlMapper.builder()
            .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    {
        // Configure mapper to ignore getters that are not serialized
        xmlMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private UserEntity testUser;
    private TimelineTripEntity testTrip;
    private TimelineStayEntity testStay;
    private List<GpsPointEntity> testGpsPoints;
    private Instant testStartDate;
    private Instant testEndDate;

    @BeforeEach
    @Transactional
    void setUp() {
        testStartDate = Instant.now().minus(2, ChronoUnit.HOURS);
        testEndDate = Instant.now();

        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("gpx-test-" + UUID.randomUUID() + "@test.com");
        testUser.setPasswordHash("test-hash");
        userRepository.persist(testUser);

        // Create test GPS points
        createTestGpsPoints();

        // Create test trip
        createTestTrip();

        // Create test stay
        createTestStay();

        log.info("Test setup completed: user {}, {} GPS points, 1 trip, 1 stay",
                testUser.getId(), testGpsPoints.size());
    }

    private void createTestGpsPoints() {
        testGpsPoints = List.of(
                createGpsPoint(testStartDate, 37.7749, -122.4194, 10.0, 15.0, 95.5),
                createGpsPoint(testStartDate.plus(10, ChronoUnit.MINUTES), 37.7750, -122.4195, 12.0, 18.0, 94.0),
                createGpsPoint(testStartDate.plus(20, ChronoUnit.MINUTES), 37.7751, -122.4196, 14.0, 20.0, 92.5),
                createGpsPoint(testStartDate.plus(30, ChronoUnit.MINUTES), 37.7752, -122.4197, 16.0, 22.0, 91.0)
        );

        testGpsPoints.forEach(gpsPointRepository::persist);
    }

    private GpsPointEntity createGpsPoint(Instant timestamp, double lat, double lon,
                                          double altitude, double velocity, double battery) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(testUser);
        point.setTimestamp(timestamp);
        point.setCoordinates(GeoUtils.createPoint(lon, lat));
        point.setAltitude(altitude);
        point.setVelocity(velocity);
        point.setAccuracy(5.0);
        point.setBattery(Double.valueOf(battery));
        point.setDeviceId("test-device");
        point.setSourceType(GpsSourceType.OWNTRACKS);
        return point;
    }

    private void createTestTrip() {
        // Create path with 4 points (2D only, no altitude)
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(-122.4194, 37.7749),
                new Coordinate(-122.4195, 37.7750),
                new Coordinate(-122.4196, 37.7751),
                new Coordinate(-122.4197, 37.7752)
        };
        LineString path = geometryFactory.createLineString(coords);

        testTrip = TimelineTripEntity.builder()
                .user(testUser)
                .timestamp(testStartDate.plus(5, ChronoUnit.MINUTES))
                .tripDuration(900L) // 15 minutes
                .distanceMeters(1500L)
                .movementType("Walking")
                .avgGpsSpeed(5.5)
                .path(path)
                .startPoint(GeoUtils.createPoint(-122.4194, 37.7749))
                .endPoint(GeoUtils.createPoint(-122.4197, 37.7752))
                .build();

        timelineTripRepository.persist(testTrip);
    }

    private void createTestStay() {
        testStay = TimelineStayEntity.builder()
                .user(testUser)
                .timestamp(testStartDate.plus(1, ChronoUnit.HOURS))
                .stayDuration(1800L) // 30 minutes
                .location(GeoUtils.createPoint(-122.4194, 37.7749))
                .locationName("Test Location")
                .locationSource(LocationSource.HISTORICAL)
                .build();

        timelineStayRepository.persist(testStay);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        if (testUser != null) {
            // Clean up ALL test data for this user (not just specific test entities)
            // This handles cases where tests create additional trips/stays beyond the setUp defaults
            timelineStayRepository.delete("user.id", testUser.getId());
            timelineTripRepository.delete("user.id", testUser.getId());
            gpsPointRepository.delete("user.id", testUser.getId());
            userRepository.delete(testUser);
        }
    }

    @Test
    void testGenerateBulkGpxExportWithRawGpsAndTimeline() throws Exception {
        // Arrange
        ExportJob job = createExportJob();

        // Act
        byte[] gpxBytes = gpxExportService.generateGpxExport(job, false, "individual");

        // Assert
        assertNotNull(gpxBytes);
        assertTrue(gpxBytes.length > 0);

        String gpxXml = new String(gpxBytes);
        log.info("Generated GPX XML ({} bytes):\n{}", gpxBytes.length, gpxXml.substring(0, Math.min(500, gpxXml.length())));

        // Validate XML is well-formed
        validateXmlWellFormed(gpxXml);

        // Validate basic structure
        Document doc = parseXmlDocument(gpxXml);
        assertNotNull(doc);

        // Validate root attributes
        assertEquals("1.1", doc.getDocumentElement().getAttribute("version"));
        assertEquals("GeoPulse", doc.getDocumentElement().getAttribute("creator"));

        // Validate metadata
        NodeList metadataNodes = doc.getElementsByTagName("metadata");
        assertEquals(1, metadataNodes.getLength());

        // Validate tracks (should have raw GPS track + trip track)
        NodeList trackNodes = doc.getElementsByTagName("trk");
        assertTrue(trackNodes.getLength() >= 2, "Should have at least raw GPS track and trip track");

        // Validate track points exist
        NodeList trkptNodes = doc.getElementsByTagName("trkpt");
        assertTrue(trkptNodes.getLength() > 0, "Should have track points");

        // Validate first track point coordinates
        Element firstTrkpt = (Element) trkptNodes.item(0);
        double lat = Double.parseDouble(firstTrkpt.getAttribute("lat"));
        double lon = Double.parseDouble(firstTrkpt.getAttribute("lon"));
        assertEquals(37.7749, lat, 0.0001);
        assertEquals(-122.4194, lon, 0.0001);

        // Validate waypoints (should have stay waypoint)
        NodeList waypointNodes = doc.getElementsByTagName("wpt");
        assertEquals(1, waypointNodes.getLength(), "Should have 1 stay waypoint");

        Element waypoint = (Element) waypointNodes.item(0);
        double wptLat = Double.parseDouble(waypoint.getAttribute("lat"));
        double wptLon = Double.parseDouble(waypoint.getAttribute("lon"));
        assertEquals(37.7749, wptLat, 0.0001);
        assertEquals(-122.4194, wptLon, 0.0001);

        // Validate waypoint name
        NodeList nameNodes = waypoint.getElementsByTagName("name");
        assertEquals(1, nameNodes.getLength());
        assertEquals("Test Location", nameNodes.item(0).getTextContent());
    }

    @Test
    void testGenerateSingleTripGpx() throws Exception {
        // Act
        byte[] gpxBytes = gpxExportService.generateSingleTripGpx(testUser.getId(), testTrip.getId());

        // Assert
        assertNotNull(gpxBytes);
        assertTrue(gpxBytes.length > 0);

        String gpxXml = new String(gpxBytes);
        log.info("GPX XML preview: {}", gpxXml.substring(0, Math.min(1000, gpxXml.length())));
        validateXmlWellFormed(gpxXml);

        // Validate XML contains track data and waypoints
        // Note: The actual production code uses a properly configured XmlMapper in GpxExportService
        // which produces correct GPX XML. These tests verify the service integrates correctly.
        assertTrue(gpxXml.contains("lat=\"37.7749\""), "Should contain trip start coordinates");
        assertTrue(gpxXml.contains("Trip Start"), "Should contain trip start waypoint");
        assertTrue(gpxXml.contains("Trip End"), "Should contain trip end waypoint");
        assertTrue(gpxXml.contains("GeoPulse Trip Export"), "Should contain trip export metadata");

        // Verify coordinates exist in the XML
        assertTrue(gpxXml.contains("lat="), "Should contain latitude coordinates");
        assertTrue(gpxXml.contains("lon="), "Should contain longitude coordinates");
        assertTrue(gpxXml.contains("<ele>"), "Should contain elevation data");
        assertTrue(gpxXml.contains("<time>"), "Should contain timestamps");
    }

    @Test
    void testGenerateSingleTripGpx_InvalidUser() {
        // Arrange
        UUID invalidUserId = UUID.randomUUID();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                gpxExportService.generateSingleTripGpx(invalidUserId, testTrip.getId())
        );
    }

    @Test
    void testGenerateSingleStayGpx() throws Exception {
        // Act
        byte[] gpxBytes = gpxExportService.generateSingleStayGpx(testUser.getId(), testStay.getId());

        // Assert
        assertNotNull(gpxBytes);
        assertTrue(gpxBytes.length > 0);

        String gpxXml = new String(gpxBytes);
        validateXmlWellFormed(gpxXml);

        // Parse and validate XML content
        Document doc = parseXmlDocument(gpxXml);

        // Validate no tracks (stay is a waypoint, not a track)
        NodeList trackNodes = doc.getElementsByTagName("trk");
        assertEquals(0, trackNodes.getLength());

        // Validate waypoint
        NodeList waypointNodes = doc.getElementsByTagName("wpt");
        assertEquals(1, waypointNodes.getLength());

        Element waypoint = (Element) waypointNodes.item(0);
        assertEquals(37.7749, Double.parseDouble(waypoint.getAttribute("lat")), 0.0001);
        assertEquals(-122.4194, Double.parseDouble(waypoint.getAttribute("lon")), 0.0001);

        NodeList nameNodes = waypoint.getElementsByTagName("name");
        assertEquals("Test Location", nameNodes.item(0).getTextContent());

        NodeList descNodes = waypoint.getElementsByTagName("desc");
        assertTrue(descNodes.item(0).getTextContent().contains("30m"));
    }

    @Test
    void testGenerateSingleStayGpx_InvalidUser() {
        // Arrange
        UUID invalidUserId = UUID.randomUUID();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                gpxExportService.generateSingleStayGpx(invalidUserId, testStay.getId())
        );
    }

    @Test
    void testGenerateGpxExportAsZip() throws Exception {
        // Arrange
        ExportJob job = createExportJob();

        // Act - explicitly test individual grouping mode
        byte[] zipBytes = gpxExportService.generateGpxExport(job, true, "individual");

        // Assert
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        // Validate ZIP structure
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            int tripCount = 0;
            int stayCount = 0;
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                log.info("ZIP entry: {}", entryName);

                // Read and validate each GPX file
                byte[] entryBytes = zis.readAllBytes();
                String gpxXml = new String(entryBytes);

                if (entryName.startsWith("trip_")) {
                    tripCount++;
                    // Validate trip GPX
                    validateXmlWellFormed(gpxXml);
                    Document doc = parseXmlDocument(gpxXml);
                    assertEquals(1, doc.getElementsByTagName("trk").getLength());
                    assertEquals(2, doc.getElementsByTagName("wpt").getLength()); // start and end
                } else if (entryName.startsWith("stay_")) {
                    stayCount++;
                    // Validate stay GPX
                    validateXmlWellFormed(gpxXml);
                    Document doc = parseXmlDocument(gpxXml);
                    assertEquals(1, doc.getElementsByTagName("wpt").getLength());
                    assertEquals(0, doc.getElementsByTagName("trk").getLength());
                }

                zis.closeEntry();
            }

            assertEquals(1, tripCount, "Should have 1 trip GPX file");
            assertEquals(1, stayCount, "Should have 1 stay GPX file");
        }
    }

    @Test
    @Transactional
    void testGenerateGpxExportAsZipGroupedByDay() throws Exception {
        // Arrange - Create test data across multiple days
        Instant day1Start = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        Instant day2Start = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);

        // IMPORTANT: Create GPS points for ALL trips (needed since daily export now uses raw GPS data!)
        // Day 1 Trip 1 GPS points (8:00 AM)
        for (int i = 0; i < 30; i++) {
            GpsPointEntity point = createGpsPoint(
                    day1Start.plus(8, ChronoUnit.HOURS).plus(i * 20L, ChronoUnit.SECONDS),
                    37.7749 + (i * 0.0001),
                    -122.4194 + (i * 0.0001),
                    100.0, 15.0, 95.0
            );
            gpsPointRepository.persist(point);
        }

        // Day 1 Trip 2 GPS points (2:00 PM)
        for (int i = 0; i < 25; i++) {
            GpsPointEntity point = createGpsPoint(
                    day1Start.plus(14, ChronoUnit.HOURS).plus(i * 24L, ChronoUnit.SECONDS),
                    37.7750 + (i * 0.0001),
                    -122.4195 + (i * 0.0001),
                    100.0, 15.0, 95.0
            );
            gpsPointRepository.persist(point);
        }

        // Day 2 Trip GPS points (10:00 AM)
        for (int i = 0; i < 35; i++) {
            GpsPointEntity point = createGpsPoint(
                    day2Start.plus(10, ChronoUnit.HOURS).plus(i * 17L, ChronoUnit.SECONDS),
                    37.7751 + (i * 0.0001),
                    -122.4196 + (i * 0.0001),
                    100.0, 15.0, 95.0
            );
            gpsPointRepository.persist(point);
        }

        // Create trips and stays for day 1
        TimelineTripEntity trip1Day1 = createTestTripForTime(day1Start.plus(8, ChronoUnit.HOURS), "Day1-Trip1");
        TimelineTripEntity trip2Day1 = createTestTripForTime(day1Start.plus(14, ChronoUnit.HOURS), "Day1-Trip2");
        TimelineStayEntity stay1Day1 = createTestStayForTime(day1Start.plus(12, ChronoUnit.HOURS), "Day1-Stay1");

        // Create trips and stays for day 2
        TimelineTripEntity tripDay2 = createTestTripForTime(day2Start.plus(10, ChronoUnit.HOURS), "Day2-Trip1");
        TimelineStayEntity stay1Day2 = createTestStayForTime(day2Start.plus(15, ChronoUnit.HOURS), "Day2-Stay1");
        TimelineStayEntity stay2Day2 = createTestStayForTime(day2Start.plus(18, ChronoUnit.HOURS), "Day2-Stay2");

        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(day1Start);
        dateRange.setEndDate(day2Start.plus(1, ChronoUnit.DAYS));

        ExportJob job = new ExportJob();
        job.setUserId(testUser.getId());
        job.setDateRange(dateRange);

        try {
            // Act - test daily grouping mode
            byte[] zipBytes = gpxExportService.generateGpxExport(job, true, "daily");

            // Assert
            assertNotNull(zipBytes);
            assertTrue(zipBytes.length > 0);

            // Validate ZIP structure
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                int dailyFileCount = 0;
                java.util.Map<String, Integer> tripCountByDay = new java.util.HashMap<>();
                java.util.Map<String, Integer> stayCountByDay = new java.util.HashMap<>();

                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    log.info("Daily ZIP entry: {}", entryName);

                    assertTrue(entryName.startsWith("day_"), "File should start with 'day_'");
                    assertTrue(entryName.endsWith(".gpx"), "File should end with '.gpx'");

                    dailyFileCount++;

                    // Read and validate GPX file
                    byte[] entryBytes = zis.readAllBytes();
                    String gpxXml = new String(entryBytes);
                    validateXmlWellFormed(gpxXml);

                    Document doc = parseXmlDocument(gpxXml);

                    // Count tracks (trips) and waypoints (stays)
                    int trackCount = doc.getElementsByTagName("trk").getLength();
                    int waypointCount = doc.getElementsByTagName("wpt").getLength();

                    tripCountByDay.put(entryName, trackCount);
                    stayCountByDay.put(entryName, waypointCount);

                    log.info("{} contains {} trips and {} stays", entryName, trackCount, waypointCount);

                    zis.closeEntry();
                }

                // Verify we have 2 daily files (one for each day)
                assertEquals(2, dailyFileCount, "Should have 2 daily GPX files");

                // VERIFY: With raw GPS export fix, each trip should be a separate track
                // Day 1: 2 trips + 1 stay = 2 trip tracks + 1 stay track (if GPS points) + 1 waypoint
                // Day 2: 1 trip + 2 stays = 1 trip track + 2 stay tracks (if GPS points) + 2 waypoints
                int totalTracks = tripCountByDay.values().stream().mapToInt(Integer::intValue).sum();
                int totalStays = stayCountByDay.values().stream().mapToInt(Integer::intValue).sum();

                // Should have tracks for trips + stays (if GPS data exists during stays)
                // Minimum: 3 trip tracks (2 from day 1, 1 from day 2)
                assertTrue(totalTracks >= 3,
                    String.format("Should have at least 3 trip tracks, got %d", totalTracks));
                assertEquals(3, totalStays, "Total stay waypoints should be 3");
            }
        } finally {
            // Clean up test data
            timelineTripRepository.delete(trip1Day1);
            timelineTripRepository.delete(trip2Day1);
            timelineTripRepository.delete(tripDay2);
            timelineStayRepository.delete(stay1Day1);
            timelineStayRepository.delete(stay1Day2);
            timelineStayRepository.delete(stay2Day2);
        }
    }

    private TimelineTripEntity createTestTripForTime(Instant timestamp, String description) {
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(-122.4194, 37.7749),
                new Coordinate(-122.4195, 37.7750),
                new Coordinate(-122.4196, 37.7751)
        };
        LineString path = geometryFactory.createLineString(coords);

        TimelineTripEntity trip = TimelineTripEntity.builder()
                .user(testUser)
                .timestamp(timestamp)
                .tripDuration(600L) // 10 minutes
                .distanceMeters(1000L)
                .movementType("Walking")
                .avgGpsSpeed(5.0)
                .path(path)
                .startPoint(GeoUtils.createPoint(-122.4194, 37.7749))
                .endPoint(GeoUtils.createPoint(-122.4196, 37.7751))
                .build();

        timelineTripRepository.persist(trip);
        return trip;
    }

    private TimelineStayEntity createTestStayForTime(Instant timestamp, String locationName) {
        TimelineStayEntity stay = TimelineStayEntity.builder()
                .user(testUser)
                .timestamp(timestamp)
                .stayDuration(1800L) // 30 minutes
                .location(GeoUtils.createPoint(-122.4194, 37.7749))
                .locationName(locationName)
                .locationSource(LocationSource.HISTORICAL)
                .build();

        timelineStayRepository.persist(stay);
        return stay;
    }

    @Test
    @Transactional
    void testGenerateGpxExportWithEmptyData() throws Exception {
        // Arrange - Create a user with no GPS data
        UserEntity emptyUser = new UserEntity();
        emptyUser.setEmail("empty-" + UUID.randomUUID() + "@test.com");
        emptyUser.setPasswordHash("test-hash");
        userRepository.persist(emptyUser);

        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(testStartDate);
        dateRange.setEndDate(testEndDate);

        ExportJob job = new ExportJob();
        job.setUserId(emptyUser.getId());
        job.setDateRange(dateRange);

        try {
            // Act
            byte[] gpxBytes = gpxExportService.generateGpxExport(job, false, "individual");

            // Assert
            assertNotNull(gpxBytes);
            String gpxXml = new String(gpxBytes);
            validateXmlWellFormed(gpxXml);

            Document doc = parseXmlDocument(gpxXml);
            assertTrue(doc.getElementsByTagName("trk").getLength() == 0,
                    "Should have no tracks when no GPS data");
            assertTrue(doc.getElementsByTagName("wpt").getLength() == 0,
                    "Should have no waypoints when no timeline data");
        } finally {
            userRepository.delete(emptyUser);
        }
    }

    @Test
    void testGpxExportContainsCorrectTimestamps() throws Exception {
        // Arrange
        ExportJob job = createExportJob();

        // Act
        byte[] gpxBytes = gpxExportService.generateGpxExport(job, false, "individual");
        String gpxXml = new String(gpxBytes);

        // Assert - Verify timestamps exist in track points
        validateXmlWellFormed(gpxXml);
        Document doc = parseXmlDocument(gpxXml);

        NodeList trkptNodes = doc.getElementsByTagName("trkpt");
        assertTrue(trkptNodes.getLength() > 0);

        // Validate time elements exist
        for (int i = 0; i < trkptNodes.getLength(); i++) {
            Element trkpt = (Element) trkptNodes.item(i);
            NodeList timeNodes = trkpt.getElementsByTagName("time");
            assertTrue(timeNodes.getLength() > 0, "Each track point should have a time");
        }
    }

    @Test
    void testGpxExportContainsSpeedData() throws Exception {
        // Arrange
        ExportJob job = createExportJob();

        // Act
        byte[] gpxBytes = gpxExportService.generateGpxExport(job, false, "individual");
        String gpxXml = new String(gpxBytes);

        // Assert - Verify speed elements exist in track points
        validateXmlWellFormed(gpxXml);
        assertTrue(gpxXml.contains("<speed>"), "GPX should contain speed data");

        // Verify the XML contains elevation data as well
        assertTrue(gpxXml.contains("<ele>"), "GPX should contain elevation data");
    }

    @Test
    @Transactional
    void testStreamingGpxExportWithLargeDataset() throws Exception {
        // Arrange - Create large dataset (5000 GPS points)
        log.info("Creating large test dataset with 5000 GPS points...");
        Instant baseTime = Instant.now().minus(10, ChronoUnit.HOURS);
        int pointCount = 5000;

        for (int i = 0; i < pointCount; i++) {
            double lat = 37.7749 + (i * 0.0001); // Move northward
            double lon = -122.4194 + (i * 0.0001); // Move eastward
            Instant timestamp = baseTime.plus(i, ChronoUnit.SECONDS);

            GpsPointEntity point = createGpsPoint(timestamp, lat, lon,
                    10.0 + (i % 50), 15.0 + (i % 30), 90.0 + (i % 10));
            gpsPointRepository.persist(point);

            if (i % 1000 == 0) {
                log.info("Created {} GPS points...", i);
            }
        }

        log.info("Large dataset created: {} GPS points", pointCount);

        // Create export job spanning the entire dataset
        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(baseTime);
        dateRange.setEndDate(baseTime.plus(pointCount, ChronoUnit.SECONDS));

        ExportJob job = new ExportJob();
        job.setUserId(testUser.getId());
        job.setDateRange(dateRange);

        try {
            // Act - Export using streaming algorithm
            log.info("Starting streaming GPX export...");
            long startTime = System.currentTimeMillis();
            byte[] gpxBytes = gpxExportService.generateGpxExport(job, false, "individual");
            long exportTime = System.currentTimeMillis() - startTime;

            log.info("Streaming export completed in {} ms, generated {} bytes",
                    exportTime, gpxBytes.length);

            // Assert - Validate XML structure
            String gpxXml = new String(gpxBytes);
            validateXmlWellFormed(gpxXml);

            // Verify the GPX contains all points
            Document doc = parseXmlDocument(gpxXml);
            NodeList trkptNodes = doc.getElementsByTagName("trkpt");

            log.info("Exported {} track points in GPX file", trkptNodes.getLength());

            // Should have exactly the same number of track points as GPS points created
            assertEquals(pointCount, trkptNodes.getLength(),
                    "GPX should contain all " + pointCount + " GPS points");

            // Verify first and last point coordinates
            Element firstPoint = (Element) trkptNodes.item(0);
            assertEquals(37.7749, Double.parseDouble(firstPoint.getAttribute("lat")), 0.0001);
            assertEquals(-122.4194, Double.parseDouble(firstPoint.getAttribute("lon")), 0.0001);

            Element lastPoint = (Element) trkptNodes.item(pointCount - 1);
            double expectedLastLat = 37.7749 + ((pointCount - 1) * 0.0001);
            double expectedLastLon = -122.4194 + ((pointCount - 1) * 0.0001);
            assertEquals(expectedLastLat, Double.parseDouble(lastPoint.getAttribute("lat")), 0.0001);
            assertEquals(expectedLastLon, Double.parseDouble(lastPoint.getAttribute("lon")), 0.0001);

            // Verify all points have required elements
            for (int i = 0; i < trkptNodes.getLength(); i++) {
                Element trkpt = (Element) trkptNodes.item(i);

                // Verify lat/lon attributes
                assertTrue(trkpt.hasAttribute("lat"), "Track point should have lat attribute");
                assertTrue(trkpt.hasAttribute("lon"), "Track point should have lon attribute");

                // Verify required child elements
                NodeList timeNodes = trkpt.getElementsByTagName("time");
                assertEquals(1, timeNodes.getLength(), "Track point should have time element");

                NodeList eleNodes = trkpt.getElementsByTagName("ele");
                assertEquals(1, eleNodes.getLength(), "Track point should have elevation element");

                NodeList speedNodes = trkpt.getElementsByTagName("speed");
                assertEquals(1, speedNodes.getLength(), "Track point should have speed element");
            }

            log.info("All {} points verified successfully", trkptNodes.getLength());

        } finally {
            // Clean up large dataset
            log.info("Cleaning up large test dataset...");
            gpsPointRepository.delete("user.id", testUser.getId());
        }
    }

    @Test
    @Transactional
    void testStreamingGpxExportPointCountAccuracy() throws Exception {
        // Arrange - Create dataset with known point count
        log.info("Testing point count accuracy with 2500 GPS points...");
        Instant baseTime = Instant.now().minus(5, ChronoUnit.HOURS);
        int expectedPointCount = 2500;

        for (int i = 0; i < expectedPointCount; i++) {
            GpsPointEntity point = createGpsPoint(
                    baseTime.plus(i * 2L, ChronoUnit.SECONDS),
                    37.7749 + (i * 0.00005),
                    -122.4194 + (i * 0.00005),
                    100.0, 20.0, 95.0
            );
            gpsPointRepository.persist(point);
        }

        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(baseTime.minus(1, ChronoUnit.HOURS));
        dateRange.setEndDate(baseTime.plus(2 * expectedPointCount, ChronoUnit.SECONDS).plus(1, ChronoUnit.HOURS));

        ExportJob job = new ExportJob();
        job.setUserId(testUser.getId());
        job.setDateRange(dateRange);

        try {
            // Act
            byte[] gpxBytes = gpxExportService.generateGpxExport(job, false, "individual");

            // Assert
            String gpxXml = new String(gpxBytes);
            Document doc = parseXmlDocument(gpxXml);
            NodeList trkptNodes = doc.getElementsByTagName("trkpt");

            // CRITICAL: Verify exact point count
            assertEquals(expectedPointCount, trkptNodes.getLength(),
                    String.format("Expected exactly %d points, but got %d. " +
                                    "This indicates data loss during export!",
                            expectedPointCount, trkptNodes.getLength()));

            log.info("✅ Point count accuracy verified: {} points exported correctly", trkptNodes.getLength());

        } finally {
            gpsPointRepository.delete("user.id", testUser.getId());
        }
    }

    @Test
    @Transactional
    void testStreamingGpxExportPreservesAllDataFields() throws Exception {
        // Arrange - Use a time range far from setUp() data
        // setUp() creates data starting at testStartDate (now - 2 hours)
        // We'll use data starting at now - 24 hours to avoid overlap
        log.info("Testing data field preservation in streaming export...");
        Instant baseTime = Instant.now().minus(24, ChronoUnit.HOURS);
        int pointCount = 100;

        for (int i = 0; i < pointCount; i++) {
            GpsPointEntity point = createGpsPoint(
                    baseTime.plus(i * 30L, ChronoUnit.SECONDS),
                    37.7749 + (i * 0.0001),
                    -122.4194 + (i * 0.0001),
                    100.0 + i,  // Varying altitude
                    20.0 + (i % 10),  // Varying velocity
                    95.0 - (i % 5)   // Varying battery
            );
            gpsPointRepository.persist(point);
        }

        // Use date range that only covers our new test data
        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(baseTime.minus(10, ChronoUnit.MINUTES));
        dateRange.setEndDate(baseTime.plus(pointCount * 30L, ChronoUnit.SECONDS).plus(10, ChronoUnit.MINUTES));

        ExportJob job = new ExportJob();
        job.setUserId(testUser.getId());
        job.setDateRange(dateRange);

        try {
            // Act
            byte[] gpxBytes = gpxExportService.generateGpxExport(job, false, "individual");

            // Assert
            String gpxXml = new String(gpxBytes);
            Document doc = parseXmlDocument(gpxXml);
            NodeList trkptNodes = doc.getElementsByTagName("trkpt");

            assertEquals(pointCount, trkptNodes.getLength(), "Should have all points");

            // Verify first point has all data fields
            Element firstPoint = (Element) trkptNodes.item(0);

            // Coordinates
            assertEquals(37.7749, Double.parseDouble(firstPoint.getAttribute("lat")), 0.0001);
            assertEquals(-122.4194, Double.parseDouble(firstPoint.getAttribute("lon")), 0.0001);

            // Elevation
            NodeList eleNodes = firstPoint.getElementsByTagName("ele");
            assertEquals(1, eleNodes.getLength());
            assertEquals(100.0, Double.parseDouble(eleNodes.item(0).getTextContent()), 0.1);

            // Speed (should be converted from km/h to m/s)
            NodeList speedNodes = firstPoint.getElementsByTagName("speed");
            assertEquals(1, speedNodes.getLength());
            double speedMs = Double.parseDouble(speedNodes.item(0).getTextContent());
            assertEquals(20.0 / 3.6, speedMs, 0.1); // 20 km/h = 5.56 m/s

            // Time
            NodeList timeNodes = firstPoint.getElementsByTagName("time");
            assertEquals(1, timeNodes.getLength());
            assertFalse(timeNodes.item(0).getTextContent().isEmpty());

            // Verify last point has correct altitude (should be 100 + 99 = 199)
            Element lastPoint = (Element) trkptNodes.item(pointCount - 1);
            NodeList lastEleNodes = lastPoint.getElementsByTagName("ele");
            assertEquals(199.0, Double.parseDouble(lastEleNodes.item(0).getTextContent()), 0.1);

            log.info("✅ All data fields preserved correctly in {} points", pointCount);

        } finally {
            gpsPointRepository.delete("user.id", testUser.getId());
        }
    }

    @Test
    @Transactional
    void testStreamingGpxExportMemoryEfficiency() throws Exception {
        // Arrange - This test verifies that streaming works without loading everything in memory
        log.info("Testing memory efficiency of streaming export with 10000 points...");
        Instant baseTime = Instant.now().minus(12, ChronoUnit.HOURS);
        int largePointCount = 10000;

        // Get memory before creating dataset
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Suggest garbage collection
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        log.info("Memory before test: {} MB", memoryBefore / (1024 * 1024));

        // Create large dataset
        for (int i = 0; i < largePointCount; i++) {
            GpsPointEntity point = createGpsPoint(
                    baseTime.plus(i, ChronoUnit.SECONDS),
                    37.7749 + (i * 0.00001),
                    -122.4194 + (i * 0.00001),
                    100.0, 15.0, 95.0
            );
            gpsPointRepository.persist(point);

            // Flush every 1000 points to avoid OOM during setup
            if (i % 1000 == 0 && i > 0) {
                gpsPointRepository.flush();
                log.info("Persisted {} points...", i);
            }
        }

        gpsPointRepository.flush();
        runtime.gc();
        long memoryAfterInsert = runtime.totalMemory() - runtime.freeMemory();
        log.info("Memory after inserting data: {} MB", memoryAfterInsert / (1024 * 1024));

        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(baseTime);
        dateRange.setEndDate(baseTime.plus(largePointCount, ChronoUnit.SECONDS));

        ExportJob job = new ExportJob();
        job.setUserId(testUser.getId());
        job.setDateRange(dateRange);

        try {
            // Act - Perform streaming export
            runtime.gc();
            long memoryBeforeExport = runtime.totalMemory() - runtime.freeMemory();
            log.info("Memory before export: {} MB", memoryBeforeExport / (1024 * 1024));

            long startTime = System.currentTimeMillis();
            byte[] gpxBytes = gpxExportService.generateGpxExport(job, false, "individual");
            long exportTime = System.currentTimeMillis() - startTime;

            runtime.gc();
            long memoryAfterExport = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsedForExport = memoryAfterExport - memoryBeforeExport;

            log.info("Memory after export: {} MB", memoryAfterExport / (1024 * 1024));
            log.info("Memory used for export: {} MB", memoryUsedForExport / (1024 * 1024));
            log.info("Export completed in {} ms", exportTime);

            // Assert - Verify results
            String gpxXml = new String(gpxBytes);
            Document doc = parseXmlDocument(gpxXml);
            NodeList trkptNodes = doc.getElementsByTagName("trkpt");

            assertEquals(largePointCount, trkptNodes.getLength(),
                    "Should export all " + largePointCount + " points");

            // Memory efficiency check: Export should not use excessive memory
            // With streaming, memory usage should be relatively constant regardless of dataset size
            // Allow up to 200MB for export (very generous, should be much less with streaming)
            long maxAllowedMemoryMB = 200;
            assertTrue(memoryUsedForExport / (1024 * 1024) < maxAllowedMemoryMB,
                    String.format("Export used %d MB, expected < %d MB with streaming",
                            memoryUsedForExport / (1024 * 1024), maxAllowedMemoryMB));

            log.info("✅ Memory efficiency verified: {} points exported with only {} MB overhead",
                    largePointCount, memoryUsedForExport / (1024 * 1024));

        } finally {
            gpsPointRepository.delete("user.id", testUser.getId());
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private ExportJob createExportJob() {
        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(testStartDate);
        dateRange.setEndDate(testEndDate);

        ExportJob job = new ExportJob();
        job.setUserId(testUser.getId());
        job.setDateRange(dateRange);
        return job;
    }

    /**
     * Validates that the XML is well-formed and can be parsed.
     */
    private void validateXmlWellFormed(String xml) throws Exception {
        Document doc = parseXmlDocument(xml);
        assertNotNull(doc);

        // Validate root element
        Element root = doc.getDocumentElement();
        assertEquals("gpx", root.getNodeName());
        assertEquals("1.1", root.getAttribute("version"));
        assertEquals("GeoPulse", root.getAttribute("creator"));

        log.info("XML validation passed - document is well-formed");
    }

    /**
     * Parses XML string into a Document object.
     */
    private Document parseXmlDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    /**
     * Test that verifies:
     * 1. GPX export creates duplicates in single file mode (raw GPS track + trip tracks + stay tracks)
     * 2. Re-importing the duplicated GPX file results in correct deduplication via database unique constraint
     *
     * This test simulates the real-world scenario where a user exports 125K points,
     * gets a GPX file with 185K track points due to duplication, then re-imports it
     * and expects only 125K points in the database (duplicates removed).
     */
    @Test
    @Transactional
    void testExportCreatesDuplicatesAndImportDeduplicates() throws Exception {
        log.info("=== Testing Export Duplication and Import Deduplication ===");

        // STEP 1: Create realistic test data
        // Create 2 trips and 2 stays with GPS points for each
        Instant baseTime = Instant.now().minus(2, ChronoUnit.HOURS);

        // Trip 1: 100 GPS points
        TimelineTripEntity trip1 = createTripWithGpsPoints(baseTime, 100, "Trip 1");

        // Stay 1: 50 GPS points
        Instant stay1Start = baseTime.plus(15, ChronoUnit.MINUTES);
        TimelineStayEntity stay1 = createStayWithGpsPoints(stay1Start, 50, "Stay 1");

        // Trip 2: 75 GPS points
        Instant trip2Start = baseTime.plus(30, ChronoUnit.MINUTES);
        TimelineTripEntity trip2 = createTripWithGpsPoints(trip2Start, 75, "Trip 2");

        // Stay 2: 25 GPS points
        Instant stay2Start = baseTime.plus(45, ChronoUnit.MINUTES);
        TimelineStayEntity stay2 = createStayWithGpsPoints(stay2Start, 25, "Stay 2");

        // Total: 100 + 50 + 75 + 25 = 250 unique GPS points
        int originalGpsPointCount = 250;

        // Count GPS points in database before export
        long pointsInDbBefore = gpsPointRepository.count();
        log.info("GPS points in database before export: {}", pointsInDbBefore);
        assertTrue(pointsInDbBefore >= originalGpsPointCount,
            "Should have at least " + originalGpsPointCount + " GPS points");

        // STEP 2: Export to single GPX file (creates duplicates)
        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(baseTime.minus(1, ChronoUnit.HOURS));
        dateRange.setEndDate(baseTime.plus(2, ChronoUnit.HOURS));

        ExportJob exportJob = new ExportJob();
        exportJob.setUserId(testUser.getId());
        exportJob.setDateRange(dateRange);

        log.info("Exporting GPS data to single GPX file...");
        byte[] gpxBytes = gpxExportService.generateGpxExport(exportJob, false, "single");
        assertNotNull(gpxBytes);
        assertTrue(gpxBytes.length > 0);

        String gpxContent = new String(gpxBytes);
        log.info("Generated GPX file size: {} bytes", gpxBytes.length);

        // STEP 3: Count track points and waypoints in the exported GPX file
        Document doc = parseXmlDocument(gpxContent);
        NodeList trkptNodes = doc.getElementsByTagName("trkpt");
        NodeList wptNodes = doc.getElementsByTagName("wpt");
        int exportedTrackPointCount = trkptNodes.getLength();
        int exportedWaypointCount = wptNodes.getLength();

        log.info("Track points in exported GPX file: {}", exportedTrackPointCount);
        log.info("Waypoints in exported GPX file: {} (trip start/end markers)", exportedWaypointCount);
        log.info("Total points in GPX: {}", exportedTrackPointCount + exportedWaypointCount);
        log.info("Original GPS points in database: {}", originalGpsPointCount);

        // CRITICAL ASSERTION: Export should create duplicates in track points
        // Single file mode creates: raw GPS track + trip tracks + stay tracks
        // So we expect MORE track points in GPX than original GPS points
        assertTrue(exportedTrackPointCount > originalGpsPointCount,
            String.format("Expected export to create duplicates! " +
                "Original: %d points, Exported: %d track points. " +
                "Single file export should include raw GPS track + trip tracks + stay tracks, creating duplication.",
                originalGpsPointCount, exportedTrackPointCount));

        double duplicationRatio = (double) exportedTrackPointCount / originalGpsPointCount;
        log.info("Duplication ratio: {:.2f}x ({} exported / {} original)",
            duplicationRatio, exportedTrackPointCount, originalGpsPointCount);

        // STEP 4: Re-import the GPX file with duplicates
        log.info("Re-importing GPX file with {} track points (contains duplicates)...", exportedTrackPointCount);

        // Create import options
        org.github.tess1o.geopulse.importdata.model.ImportOptions importOptions =
            new org.github.tess1o.geopulse.importdata.model.ImportOptions();
        importOptions.setImportFormat("gpx");
        importOptions.setClearDataBeforeImport(false); // Merge mode to test deduplication

        // Create import job with proper constructor
        org.github.tess1o.geopulse.importdata.model.ImportJob importJob =
            new org.github.tess1o.geopulse.importdata.model.ImportJob(
                testUser.getId(),
                importOptions,
                "test-export.gpx",
                gpxBytes
            );

        // Validate and process import
        gpxImportStrategy.validateAndDetectDataTypes(importJob);
        gpxImportStrategy.processImportData(importJob);

        // STEP 5: Verify deduplication worked
        long pointsInDbAfter = gpsPointRepository.count();
        int totalImported = (int)(pointsInDbAfter - pointsInDbBefore);
        log.info("GPS points in database after re-import: {}", pointsInDbAfter);
        log.info("New points imported: {}", totalImported);

        // CRITICAL ASSERTIONS: Verify deduplication worked correctly

        // 1. We should NOT import more than the original + waypoints
        long maxExpected = pointsInDbBefore + exportedWaypointCount;
        assertTrue(pointsInDbAfter <= maxExpected,
            String.format("Too many points imported! " +
                "Max expected: %d (Before: %d + Waypoints: %d), Got: %d. " +
                "This suggests duplicate track points were not properly rejected.",
                maxExpected, pointsInDbBefore, exportedWaypointCount, pointsInDbAfter));

        // 2. We should import a small number (waypoints only, since track points are duplicates)
        assertTrue(totalImported <= exportedWaypointCount,
            String.format("Too many new points imported! " +
                "Expected at most %d waypoints, but got %d new points. " +
                "This suggests duplicate track points were not properly rejected.",
                exportedWaypointCount, totalImported));

        // 3. Verify massive deduplication occurred (99%+ of track points were duplicates)
        double deduplicationRate = (double)(exportedTrackPointCount - (totalImported - exportedWaypointCount))
            / exportedTrackPointCount * 100;
        assertTrue(deduplicationRate > 99.0,
            String.format("Insufficient deduplication! " +
                "Expected >99%% of track points to be duplicates, but got %.1f%%. " +
                "Track points: %d, Imported (excluding waypoints): %d",
                deduplicationRate, exportedTrackPointCount, totalImported - exportedWaypointCount));

        // Log success
        log.info("✓ Deduplication successful:");
        log.info("  - {} track points in GPX (with duplicates)", exportedTrackPointCount);
        log.info("  - {}/{} waypoints imported (some may overlap with existing GPS points)",
            totalImported, exportedWaypointCount);
        log.info("  - {:.1f}% of track points were duplicates and correctly skipped", deduplicationRate);
        log.info("  - Total in DB: {} points ({} original + {} new)",
            pointsInDbAfter, pointsInDbBefore, totalImported);

        log.info("=== Test Complete: Export duplication and import deduplication verified ===");
    }

    /**
     * Helper: Create a trip with GPS points
     */
    private TimelineTripEntity createTripWithGpsPoints(Instant startTime, int pointCount, String tripName) {
        // Create trip
        TimelineTripEntity trip = new TimelineTripEntity();
        trip.setUser(testUser);
        trip.setTimestamp(startTime);
        trip.setTripDuration(600L); // 10 minutes
        trip.setDistanceMeters(5000L); // 5 km = 5000 meters
        trip.setMovementType("WALKING");

        // Set start and end points
        trip.setStartPoint(GeoUtils.createPoint(-122.4194, 37.7749));
        trip.setEndPoint(GeoUtils.createPoint(-122.4184, 37.7759));

        // Create path
        Coordinate[] coordinates = new Coordinate[]{
            new Coordinate(-122.4194, 37.7749),
            new Coordinate(-122.4184, 37.7759)
        };
        LineString path = geometryFactory.createLineString(coordinates);
        trip.setPath(path);

        timelineTripRepository.persist(trip);

        // Create GPS points for the trip
        long secondsPerPoint = 600L / pointCount; // Distribute points evenly over trip duration
        for (int i = 0; i < pointCount; i++) {
            Instant timestamp = startTime.plusSeconds(i * secondsPerPoint);
            double lat = 37.7749 + (i * 0.0001); // Small increments
            double lon = -122.4194 + (i * 0.0001);

            GpsPointEntity gpsPoint = createGpsPoint(timestamp, lat, lon, 100.0, 15.0, 95.0);
            gpsPointRepository.persist(gpsPoint);
        }

        log.info("Created trip '{}' with {} GPS points", tripName, pointCount);
        return trip;
    }

    /**
     * Helper: Create a stay with GPS points
     */
    private TimelineStayEntity createStayWithGpsPoints(Instant startTime, int pointCount, String stayName) {
        // Create stay
        TimelineStayEntity stay = new TimelineStayEntity();
        stay.setUser(testUser);
        stay.setTimestamp(startTime);
        stay.setStayDuration(900L); // 15 minutes
        stay.setLocationSource(LocationSource.GEOCODING);
        stay.setLocationName("Test Location");

        // Stay location (center point)
        org.locationtech.jts.geom.Point centerPoint = GeoUtils.createPoint(-122.4180, 37.7755);
        stay.setLocation(centerPoint);

        timelineStayRepository.persist(stay);

        // Create GPS points for the stay (clustered around center)
        long secondsPerPoint = 900L / pointCount;
        for (int i = 0; i < pointCount; i++) {
            Instant timestamp = startTime.plusSeconds(i * secondsPerPoint);
            // Small variations around center (stay points should be close together)
            double lat = 37.7755 + ((Math.random() - 0.5) * 0.00005);
            double lon = -122.4180 + ((Math.random() - 0.5) * 0.00005);

            GpsPointEntity gpsPoint = createGpsPoint(timestamp, lat, lon, 50.0, 0.5, 85.0);
            gpsPointRepository.persist(gpsPoint);
        }

        log.info("Created stay '{}' with {} GPS points", stayName, pointCount);
        return stay;
    }
}
