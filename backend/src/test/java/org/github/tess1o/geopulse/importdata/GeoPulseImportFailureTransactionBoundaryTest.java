package org.github.tess1o.geopulse.importdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.dto.ExportMetadataDto;
import org.github.tess1o.geopulse.export.dto.FavoritesDataDto;
import org.github.tess1o.geopulse.export.dto.ReverseGeocodingDataDto;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingService;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.model.ImportStatus;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@TestProfile(GeoPulseImportFailureTransactionBoundaryTest.SchedulerEnabledProfile.class)
@SerializedDatabaseTest
class GeoPulseImportFailureTransactionBoundaryTest {

    private static final Instant EXPORT_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Inject
    ImportDataService importDataService;

    @Inject
    ImportJobService importJobService;

    @Inject
    CacheGeocodingService cacheGeocodingService;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    private UUID testUserId;
    private TestCoordinates.Scope coordinateScope;

    @BeforeEach
    @Transactional
    void setUp() {
        coordinateScope = TestCoordinates.newScope();

        UserEntity testUser = new UserEntity();
        testUser.setEmail(TestIds.uniqueEmail("geopulse-failure-boundary"));
        testUser.setPasswordHash("test-hash");
        testUser.setEmailVerified(true);
        testUser.setActive(true);
        testUser.setRole(Role.USER);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        userRepository.persist(testUser);
        entityManager.flush();

        testUserId = testUser.getId();
    }

    @Test
    void geoPulseFailureAfterReverseGeocodingCommitsReverseOnlyAndRollsBackFavorites() throws Exception {
        Point geocodedPoint = coordinateScope.point(-122.431297, 37.773972);
        String providerName = shortUniqueValue("rg");
        String displayName = TestIds.uniqueValue("Failure Geocoding");
        String favoriteName = TestIds.uniqueValue("Rollback Favorite");

        ImportJob job = new ImportJob(
                testUserId,
                geoPulseOptions(
                        ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION,
                        ExportImportConstants.DataTypes.FAVORITES,
                        ExportImportConstants.DataTypes.USER_INFO),
                "geopulse-failure.zip",
                failingAfterReverseGeocodingZip(geocodedPoint, providerName, displayName, favoriteName));

        Exception failure = assertThrows(Exception.class, () -> importDataService.processImportData(job));
        assertTrue(hasCause(failure, IOException.class),
                "Malformed user info should fail the main import transaction");

        assertEquals(1, countReverseGeocodingRows(providerName, displayName),
                "Reverse geocoding import must commit in its own transaction");
        assertTrue(cacheGeocodingService.getCachedGeocodingResultId(testUserId, geocodedPoint).isPresent(),
                "Imported reverse geocoding should be visible through cache lookup");
        assertEquals(0, countFavoriteRows(favoriteName),
                "Favorites imported in the main import transaction must roll back after later failure");
    }

    @Test
    void processImportDataRejectsActiveCallerTransactionBeforeTouchingDatabase() throws Exception {
        Point geocodedPoint = coordinateScope.point(-73.985111, 40.758911);
        String providerName = shortUniqueValue("atx");
        String displayName = TestIds.uniqueValue("Active Tx Geocoding");
        ImportJob job = new ImportJob(
                testUserId,
                geoPulseOptions(ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION),
                "active-transaction.zip",
                reverseGeocodingOnlyZip(geocodedPoint, providerName, displayName));

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().call(() -> {
                    importDataService.processImportData(job);
                    return null;
                }));

        assertTrue(containsMessage(failure, "must be called outside an active transaction"),
                "Import service should fail fast when called from an active transaction");
        assertEquals(0, countReverseGeocodingRows(providerName, displayName),
                "No import data should be written after the transaction guard rejects the call");
    }

    @Test
    void failedSchedulerImportIsNotRetriedAndPreservesReverseGeocodingOnce() throws Exception {
        Point geocodedPoint = coordinateScope.point(13.404954, 52.520008);
        String providerName = shortUniqueValue("sch");
        String displayName = TestIds.uniqueValue("Scheduler Failure Geocoding");
        String favoriteName = TestIds.uniqueValue("Scheduler Rollback Favorite");

        ImportJob job = importJobService.createImportJob(
                testUserId,
                geoPulseOptions(
                        ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION,
                        ExportImportConstants.DataTypes.FAVORITES,
                        ExportImportConstants.DataTypes.USER_INFO),
                "scheduler-failure.zip",
                failingAfterReverseGeocodingZip(geocodedPoint, providerName, displayName, favoriteName));

        importJobService.processImportJobs();

        assertEquals(ImportStatus.FAILED, job.getStatus());
        assertTrue(job.isDataProcessingCompleted(),
                "Failed jobs should be marked as processed so the scheduler does not retry them");
        assertEquals(1, countReverseGeocodingRows(providerName, displayName));
        assertEquals(0, countFavoriteRows(favoriteName));

        importJobService.processImportJobs();

        assertEquals(ImportStatus.FAILED, job.getStatus());
        assertEquals(1, countReverseGeocodingRows(providerName, displayName),
                "Failed job should not be reprocessed on a later scheduler pass");
        assertEquals(0, countFavoriteRows(favoriteName));
    }

    private byte[] failingAfterReverseGeocodingZip(Point geocodedPoint,
                                                  String providerName,
                                                  String displayName,
                                                  String favoriteName) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(ExportImportConstants.FileNames.METADATA, metadataBytes(List.of(
                ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION,
                ExportImportConstants.DataTypes.FAVORITES,
                ExportImportConstants.DataTypes.USER_INFO)));
        entries.put(ExportImportConstants.FileNames.REVERSE_GEOCODING,
                reverseGeocodingBytes(geocodedPoint, providerName, displayName));
        entries.put(ExportImportConstants.FileNames.FAVORITES, favoritesBytes(favoriteName));
        entries.put(ExportImportConstants.FileNames.USER_INFO,
                "{ malformed user info".getBytes(StandardCharsets.UTF_8));
        return zip(entries);
    }

    private byte[] reverseGeocodingOnlyZip(Point geocodedPoint,
                                           String providerName,
                                           String displayName) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(ExportImportConstants.FileNames.METADATA, metadataBytes(List.of(
                ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION)));
        entries.put(ExportImportConstants.FileNames.REVERSE_GEOCODING,
                reverseGeocodingBytes(geocodedPoint, providerName, displayName));
        return zip(entries);
    }

    private byte[] metadataBytes(List<String> dataTypes) throws IOException {
        ExportMetadataDto metadata = ExportMetadataDto.builder()
                .exportJobId(UUID.randomUUID())
                .userId(testUserId)
                .exportDate(EXPORT_TIME)
                .dataTypes(dataTypes)
                .format(ExportImportConstants.Formats.JSON)
                .version(ExportImportConstants.Versions.CURRENT)
                .build();
        return objectMapper.writeValueAsBytes(metadata);
    }

    private byte[] reverseGeocodingBytes(Point point, String providerName, String displayName) throws IOException {
        ReverseGeocodingDataDto geocodingData = ReverseGeocodingDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION)
                .exportDate(EXPORT_TIME)
                .locations(List.of(ReverseGeocodingDataDto.ReverseGeocodingLocationDto.builder()
                        .id(1L)
                        .requestLatitude(point.getY())
                        .requestLongitude(point.getX())
                        .resultLatitude(point.getY())
                        .resultLongitude(point.getX())
                        .displayName(displayName)
                        .providerName(providerName)
                        .createdAt(EXPORT_TIME)
                        .lastAccessedAt(EXPORT_TIME)
                        .city("Boundary City")
                        .country("Boundary Country")
                        .build()))
                .build();
        return objectMapper.writeValueAsBytes(geocodingData);
    }

    private byte[] favoritesBytes(String favoriteName) throws IOException {
        FavoritesDataDto favoritesData = FavoritesDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.FAVORITES)
                .exportDate(EXPORT_TIME)
                .points(List.of(FavoritesDataDto.FavoritePointDto.builder()
                        .id(1L)
                        .name(favoriteName)
                        .city("Boundary City")
                        .country("Boundary Country")
                        .latitude(37.773972)
                        .longitude(-122.431297)
                        .build()))
                .areas(List.of())
                .build();
        return objectMapper.writeValueAsBytes(favoritesData);
    }

    private byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zipOutput.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutput.write(entry.getValue());
                zipOutput.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private ImportOptions geoPulseOptions(String... dataTypes) {
        ImportOptions options = new ImportOptions();
        options.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        options.setDataTypes(List.of(dataTypes));
        return options;
    }

    private String shortUniqueValue(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private long countReverseGeocodingRows(String providerName, String displayName) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM reverse_geocoding_location
                        WHERE user_id = :userId
                          AND provider_name = :providerName
                          AND display_name = :displayName
                        """)
                .setParameter("userId", testUserId)
                .setParameter("providerName", providerName)
                .setParameter("displayName", displayName)
                .getSingleResult()).longValue());
    }

    private long countFavoriteRows(String favoriteName) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM favorite_locations
                        WHERE user_id = :userId
                          AND name = :favoriteName
                        """)
                .setParameter("userId", testUserId)
                .setParameter("favoriteName", favoriteName)
                .getSingleResult()).longValue());
    }

    private boolean containsMessage(Throwable throwable, String expected) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static class SchedulerEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "geopulse.import.scheduler.enabled", "true",
                    "quarkus.scheduler.enabled", "false"
            );
        }
    }
}
