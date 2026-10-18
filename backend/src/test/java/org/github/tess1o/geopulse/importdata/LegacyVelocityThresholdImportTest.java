package org.github.tess1o.geopulse.importdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.dto.ExportMetadataDto;
import org.github.tess1o.geopulse.export.dto.UserInfoDataDto;
import org.github.tess1o.geopulse.importdata.model.DebugImportRequest;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.DebugImportService;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

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

/**
 * The stay velocity threshold used to be stored in m/s. Exports written before the switch to km/h must be converted
 * on import, so an old file keeps detecting stays the way it did when it was exported.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class LegacyVelocityThresholdImportTest {

    private static final Instant EXPORT_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Inject
    ImportDataService importDataService;

    @Inject
    DebugImportService debugImportService;

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    UserRepository userRepository;

    @Test
    void geoPulseExportBeforeVersion12IsConvertedFromMetersPerSecond() throws Exception {
        UUID userId = testUserFactory.createUser("legacy-velocity-geopulse").userId();

        importGeoPulseUserInfo(userId, ExportImportConstants.Versions.V1_1, 2.5);

        assertEquals(9.0, velocityThreshold(userId), 1e-9);
    }

    @Test
    void geoPulseExportVersion12IsAlreadyInKmh() throws Exception {
        UUID userId = testUserFactory.createUser("current-velocity-geopulse").userId();

        importGeoPulseUserInfo(userId, ExportImportConstants.Versions.CURRENT, 9.0);

        assertEquals(9.0, velocityThreshold(userId), 1e-9);
    }

    @Test
    void debugExportVersion10IsConvertedFromMetersPerSecond() throws Exception {
        UUID userId = testUserFactory.createUser("legacy-velocity-debug").userId();

        debugImportService.importDebugData(userId, debugExport("1.0", 2.5), new DebugImportRequest(true, true));

        assertEquals(9.0, velocityThreshold(userId), 1e-9);
    }

    @Test
    void debugExportVersion11IsAlreadyInKmh() throws Exception {
        UUID userId = testUserFactory.createUser("current-velocity-debug").userId();

        debugImportService.importDebugData(userId, debugExport("1.1", 9.0), new DebugImportRequest(true, true));

        assertEquals(9.0, velocityThreshold(userId), 1e-9);
    }

    private void importGeoPulseUserInfo(UUID userId, String version, double velocityThreshold) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(ExportImportConstants.FileNames.METADATA, objectMapper.writeValueAsBytes(ExportMetadataDto.builder()
                .exportJobId(UUID.randomUUID())
                .userId(userId)
                .exportDate(EXPORT_TIME)
                .dataTypes(List.of(ExportImportConstants.DataTypes.USER_INFO))
                .format(ExportImportConstants.Formats.JSON)
                .version(version)
                .build()));
        entries.put(ExportImportConstants.FileNames.USER_INFO, objectMapper.writeValueAsBytes(UserInfoDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.USER_INFO)
                .exportDate(EXPORT_TIME)
                .user(UserInfoDataDto.UserDto.builder()
                        .userId(userId)
                        .preferences(TimelinePreferences.builder()
                                .staypointVelocityThreshold(velocityThreshold)
                                .build())
                        .build())
                .build()));

        ImportOptions options = new ImportOptions();
        options.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.USER_INFO));
        importDataService.processImportData(new ImportJob(userId, options, "velocity.zip", zip(entries)));
    }

    private byte[] debugExport(String version, double velocityThreshold) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", objectMapper.writeValueAsBytes(Map.of(
                "export_version", version,
                "gps_point_count", 0,
                "favorite_locations_count", 0,
                "favorite_areas_count", 0)));
        entries.put("gps_data.json", "[]".getBytes(StandardCharsets.UTF_8));
        entries.put("timeline_config.json",
                objectMapper.writeValueAsBytes(Map.of("staypointVelocityThreshold", velocityThreshold)));
        entries.put("favorite_locations.json", "[]".getBytes(StandardCharsets.UTF_8));
        entries.put("favorite_areas.json", "[]".getBytes(StandardCharsets.UTF_8));
        return zip(entries);
    }

    private double velocityThreshold(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() ->
                userRepository.findById(userId).getTimelinePreferences().getStaypointVelocityThreshold());
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
}
