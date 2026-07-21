package org.github.tess1o.geopulse.importdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.dto.ExportMetadataDto;
import org.github.tess1o.geopulse.export.dto.FavoritesDataDto;
import org.github.tess1o.geopulse.export.dto.RawGpsDataDto;
import org.github.tess1o.geopulse.export.dto.ReverseGeocodingDataDto;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.BatchProcessor;
import org.github.tess1o.geopulse.importdata.service.ImportBatchPersistenceException;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GeoPulseImportClearModeFailureTransactionBoundaryTest {

    private static final Instant EXPORT_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant GPS_TIME = Instant.parse("2026-01-02T12:00:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Inject
    ImportDataService importDataService;

    @Inject
    GpsPointRepository gpsPointRepository;

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
        testUser.setEmail(TestIds.uniqueEmail("geopulse-clear-failure"));
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
    void clearModeFailureRestoresExistingGpsAndPreservesReverseGeocoding() throws Exception {
        installFailingBatchProcessor();

        seedGpsPoint("existing-device", GPS_TIME, coordinateScope.point(-0.127600, 51.507400));

        Point geocodedPoint = coordinateScope.point(-0.127700, 51.507500);
        Point importedGpsPoint = coordinateScope.point(-0.128000, 51.507800);
        String providerName = shortUniqueValue("clr");
        String displayName = TestIds.uniqueValue("Clear Failure Geocoding");
        String favoriteName = TestIds.uniqueValue("Clear Failure Favorite");

        ImportOptions options = geoPulseOptions(
                ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION,
                ExportImportConstants.DataTypes.FAVORITES,
                ExportImportConstants.DataTypes.RAW_GPS);
        options.setClearDataBeforeImport(true);

        ImportJob job = new ImportJob(
                testUserId,
                options,
                "clear-mode-failure.zip",
                clearModeFailureZip(geocodedPoint, importedGpsPoint, providerName, displayName, favoriteName));

        Exception failure = assertThrows(Exception.class, () -> importDataService.processImportData(job));

        assertTrue(hasCause(failure, ImportBatchPersistenceException.class),
                "Import should fail because batch persistence failed");
        assertEquals(1, countGpsRowsByDevice("existing-device"),
                "Clear-mode deletion should roll back when the import transaction fails");
        assertEquals(0, countGpsRowsByDevice("imported-device"),
                "GPS rows from the failed import must not persist");
        assertEquals(1, countReverseGeocodingRows(providerName, displayName),
                "Reverse geocoding data must survive the failed import transaction");
        assertEquals(0, countFavoriteRows(favoriteName),
                "Favorites imported in the failed transaction must roll back");
    }

    private void installFailingBatchProcessor() {
        BatchProcessor failingBatchProcessor = Mockito.mock(BatchProcessor.class);
        when(failingBatchProcessor.processInBatches(
                anyList(),
                anyInt(),
                anyBoolean(),
                any(ImportJob.class),
                anyInt(),
                anyInt()))
                .thenThrow(new ImportBatchPersistenceException(
                        "simulated batch persistence failure",
                        new RuntimeException("simulated database failure")));
        QuarkusMock.installMockForType(failingBatchProcessor, BatchProcessor.class);
    }

    private void seedGpsPoint(String deviceId, Instant timestamp, Point point) {
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = userRepository.findById(testUserId);
            GpsPointEntity gpsPoint = new GpsPointEntity();
            gpsPoint.setUser(user);
            gpsPoint.setDeviceId(deviceId);
            gpsPoint.setTimestamp(timestamp);
            gpsPoint.setCoordinates(point);
            gpsPoint.setAccuracy(10.0);
            gpsPoint.setSourceType(GpsSourceType.GPX);
            gpsPoint.setCreatedAt(Instant.now());
            gpsPointRepository.persist(gpsPoint);
        });
    }

    private byte[] clearModeFailureZip(Point geocodedPoint,
                                       Point importedGpsPoint,
                                       String providerName,
                                       String displayName,
                                       String favoriteName) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(ExportImportConstants.FileNames.METADATA, metadataBytes(List.of(
                ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION,
                ExportImportConstants.DataTypes.FAVORITES,
                ExportImportConstants.DataTypes.RAW_GPS)));
        entries.put(ExportImportConstants.FileNames.REVERSE_GEOCODING,
                reverseGeocodingBytes(geocodedPoint, providerName, displayName));
        entries.put(ExportImportConstants.FileNames.FAVORITES, favoritesBytes(favoriteName));
        entries.put(ExportImportConstants.FileNames.RAW_GPS_DATA, rawGpsBytes(importedGpsPoint));
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
                        .city("Clear Boundary City")
                        .country("Clear Boundary Country")
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
                        .city("Clear Boundary City")
                        .country("Clear Boundary Country")
                        .latitude(51.507500)
                        .longitude(-0.127700)
                        .build()))
                .areas(List.of())
                .build();
        return objectMapper.writeValueAsBytes(favoritesData);
    }

    private byte[] rawGpsBytes(Point importedGpsPoint) throws IOException {
        RawGpsDataDto rawGpsData = RawGpsDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.RAW_GPS)
                .exportDate(EXPORT_TIME)
                .startDate(GPS_TIME)
                .endDate(GPS_TIME)
                .points(List.of(RawGpsDataDto.GpsPointDto.builder()
                        .id(1L)
                        .timestamp(GPS_TIME)
                        .latitude(importedGpsPoint.getY())
                        .longitude(importedGpsPoint.getX())
                        .accuracy(5.0)
                        .source(GpsSourceType.GPX.name())
                        .deviceId("imported-device")
                        .build()))
                .build();
        return objectMapper.writeValueAsBytes(rawGpsData);
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

    private long countGpsRowsByDevice(String deviceId) {
        return QuarkusTransaction.requiringNew().call(() ->
                gpsPointRepository.count("user.id = ?1 and deviceId = ?2", testUserId, deviceId));
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
}
