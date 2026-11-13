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
import org.github.tess1o.geopulse.export.dto.TimelineDataDto;
import org.github.tess1o.geopulse.favorites.model.AreaGeometry;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.model.ImportPhase;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.importdata.service.TimelineImportHelper;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GeoPulseFavoritesImportIntegrationTest {

    private static final Instant EXPORT_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant GPS_TIME = Instant.parse("2026-01-02T12:00:00Z");
    private static final UUID TIMELINE_JOB_ID = UUID.randomUUID();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Inject
    ImportDataService importDataService;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    private UUID testUserId;
    private TestCoordinates.Scope coordinateScope;
    private AsyncTimelineGenerationService asyncTimelineGenerationService;
    private final List<Instant> gpsRegenerationStarts = new ArrayList<>();

    @BeforeEach
    @Transactional
    void setUp() {
        coordinateScope = TestCoordinates.newScope();

        UserEntity testUser = new UserEntity();
        testUser.setEmail(TestIds.uniqueEmail("favorites-replace"));
        testUser.setPasswordHash("test-hash");
        testUser.setEmailVerified(true);
        testUser.setActive(true);
        testUser.setRole(Role.USER);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        userRepository.persist(testUser);
        entityManager.flush();
        testUserId = testUser.getId();

        asyncTimelineGenerationService = Mockito.mock(AsyncTimelineGenerationService.class);
        when(asyncTimelineGenerationService.requestFullRegeneration(any(UUID.class)))
                .thenReturn(TIMELINE_JOB_ID);
        QuarkusMock.installMockForType(asyncTimelineGenerationService, AsyncTimelineGenerationService.class);

        TimelineImportHelper timelineImportHelper = Mockito.mock(TimelineImportHelper.class);
        when(timelineImportHelper.triggerTimelineGenerationForImportedGpsData(any(ImportJob.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    gpsRegenerationStarts.add(invocation.getArgument(1));
                    ImportJob job = invocation.getArgument(0);
                    job.setTimelineJobId(TIMELINE_JOB_ID);
                    job.setPhase(ImportPhase.TIMELINE_GENERATION);
                    return TIMELINE_JOB_ID;
                });
        QuarkusMock.installMockForType(timelineImportHelper, TimelineImportHelper.class);
    }

    @Test
    void clearDataBeforeImportReplacesFavoritesAsFullSnapshot() throws Exception {
        seedFavorite("Keep A", FavoriteLocationType.POINT, point(51.50, -0.12));
        seedFavorite("Resize B", FavoriteLocationType.AREA, box(30.0, 30.01, 50.0, 50.01));
        long localOnlyId = seedFavorite("Local Only C", FavoriteLocationType.POINT, point(52.0, -1.0));
        long stayId = seedStay(localOnlyId, "Local Only C", point(52.0, -1.0));

        ImportOptions options = geoPulseOptions(ExportImportConstants.DataTypes.FAVORITES);
        options.setClearDataBeforeImport(true);
        ImportJob job = new ImportJob(testUserId, options, "favorites-replace.zip", zip(Map.of(
                ExportImportConstants.FileNames.METADATA,
                metadataBytes(List.of(ExportImportConstants.DataTypes.FAVORITES)),
                ExportImportConstants.FileNames.FAVORITES,
                favoritesBytes(
                        List.of(pointDto(1L, "Keep A", 51.50, -0.12),
                                pointDto(2L, "New N", 48.85, 2.35)),
                        List.of(areaDto(3L, "Resize B", box(30.0, 30.02, 50.0, 50.02)))))));

        importDataService.processImportData(job);

        assertEquals(Set.of("Keep A", "New N", "Resize B"), favoriteNames());
        assertEquals(3, favoriteCount(), "a replaced set contains no stale or duplicate favorites");
        assertEquals(0.02 * 0.02, favoriteArea("Resize B"), 0.000001, "the resized area must replace the old one");
        assertFalse(stayExists(stayId), "stays referencing a removed favorite are deleted");
        verify(asyncTimelineGenerationService).requestFullRegeneration(testUserId);
        assertEquals(TIMELINE_JOB_ID, job.getTimelineJobId());
        assertEquals(ImportPhase.TIMELINE_GENERATION, job.getPhase());
    }

    @Test
    void mergeModeStillKeepsExistingFavorites() throws Exception {
        seedFavorite("Keep A", FavoriteLocationType.POINT, point(51.50, -0.12));
        seedFavorite("Local Only C", FavoriteLocationType.POINT, point(52.0, -1.0));

        ImportOptions options = geoPulseOptions(ExportImportConstants.DataTypes.FAVORITES);
        ImportJob job = new ImportJob(testUserId, options, "favorites-merge.zip", zip(Map.of(
                ExportImportConstants.FileNames.METADATA,
                metadataBytes(List.of(ExportImportConstants.DataTypes.FAVORITES)),
                ExportImportConstants.FileNames.FAVORITES,
                favoritesBytes(List.of(pointDto(1L, "New N", 48.85, 2.35)), List.of()))));

        importDataService.processImportData(job);

        assertEquals(Set.of("Keep A", "Local Only C", "New N"), favoriteNames());
        verify(asyncTimelineGenerationService, never()).requestFullRegeneration(any(UUID.class));
    }

    @Test
    void favoritesAndGpsImportRegeneratesFromEpoch() throws Exception {
        seedFavorite("Existing", FavoriteLocationType.POINT, point(51.50, -0.12));

        ImportOptions options = geoPulseOptions(
                ExportImportConstants.DataTypes.FAVORITES,
                ExportImportConstants.DataTypes.RAW_GPS);
        options.setClearDataBeforeImport(true);
        ImportJob job = new ImportJob(testUserId, options, "favorites-and-gps.zip", zip(Map.of(
                ExportImportConstants.FileNames.METADATA,
                metadataBytes(List.of(
                        ExportImportConstants.DataTypes.FAVORITES,
                        ExportImportConstants.DataTypes.RAW_GPS)),
                ExportImportConstants.FileNames.FAVORITES,
                favoritesBytes(List.of(pointDto(1L, "Archive N", 48.85, 2.35)), List.of()),
                ExportImportConstants.FileNames.RAW_GPS_DATA,
                rawGpsBytes())));

        importDataService.processImportData(job);

        assertEquals(Set.of("Archive N"), favoriteNames());
        assertEquals(List.of(StreamingTimelineGenerationService.DEFAULT_START_DATE), gpsRegenerationStarts,
                "replacing favorites requires a full regeneration");
        verify(asyncTimelineGenerationService, never()).requestFullRegeneration(any(UUID.class));
    }

    @Test
    void emptyFavoritesArchiveClearsAllFavorites() throws Exception {
        seedFavorite("Existing", FavoriteLocationType.POINT, point(51.50, -0.12));

        ImportOptions options = geoPulseOptions(ExportImportConstants.DataTypes.FAVORITES);
        options.setClearDataBeforeImport(true);
        ImportJob job = new ImportJob(testUserId, options, "favorites-empty.zip", zip(Map.of(
                ExportImportConstants.FileNames.METADATA,
                metadataBytes(List.of(ExportImportConstants.DataTypes.FAVORITES)),
                ExportImportConstants.FileNames.FAVORITES,
                favoritesBytes(List.of(), List.of()))));

        importDataService.processImportData(job);

        assertEquals(0, favoriteCount(), "an empty favorites archive replaces the whole set");
        verify(asyncTimelineGenerationService).requestFullRegeneration(testUserId);
    }

    @Test
    void snapshotRestoreDoesNotStartPostCommitRegeneration() throws Exception {
        seedFavorite("Existing", FavoriteLocationType.POINT, point(51.50, -0.12));

        ImportOptions options = geoPulseOptions(
                ExportImportConstants.DataTypes.FAVORITES,
                ExportImportConstants.DataTypes.TIMELINE);
        options.setClearDataBeforeImport(true);
        options.setSnapshotRestore(true);
        ImportJob job = new ImportJob(testUserId, options, "favorites-snapshot.zip", zip(Map.of(
                ExportImportConstants.FileNames.METADATA,
                metadataBytes(List.of(
                        ExportImportConstants.DataTypes.FAVORITES,
                        ExportImportConstants.DataTypes.TIMELINE)),
                ExportImportConstants.FileNames.FAVORITES,
                favoritesBytes(List.of(pointDto(1L, "Archive N", 48.85, 2.35)), List.of()),
                ExportImportConstants.FileNames.TIMELINE_DATA,
                timelineBytes())));

        importDataService.processImportData(job);

        assertEquals(Set.of("Archive N"), favoriteNames());
        verify(asyncTimelineGenerationService, never()).requestFullRegeneration(any(UUID.class));
    }

    private long seedFavorite(String name, FavoriteLocationType type, org.locationtech.jts.geom.Geometry geometry) {
        return QuarkusTransaction.requiringNew().call(() -> {
            FavoritesEntity favorite = FavoritesEntity.builder()
                    .user(userRepository.findById(testUserId))
                    .name(name)
                    .type(type)
                    .geometry(geometry)
                    .build();
            favoritesRepository.persist(favorite);
            entityManager.flush();
            return favorite.getId();
        });
    }

    private long seedStay(long favoriteId, String name, Point location) {
        return QuarkusTransaction.requiringNew().call(() -> {
            TimelineStayEntity stay = TimelineStayEntity.builder()
                    .user(userRepository.findById(testUserId))
                    .timestamp(GPS_TIME)
                    .stayDuration(600)
                    .location(location)
                    .locationName(name)
                    .favoriteLocation(favoritesRepository.findById(favoriteId))
                    .build();
            timelineStayRepository.persist(stay);
            entityManager.flush();
            return stay.getId();
        });
    }

    private Set<String> favoriteNames() {
        return QuarkusTransaction.requiringNew().call(() -> favoritesRepository.findByUserId(testUserId).stream()
                .map(FavoritesEntity::getName)
                .collect(Collectors.toSet()));
    }

    private long favoriteCount() {
        return QuarkusTransaction.requiringNew().call(() -> favoritesRepository.countByUserId(testUserId));
    }

    private double favoriteArea(String name) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery("""
                        SELECT ST_Area(geometry)
                        FROM favorite_locations
                        WHERE user_id = :userId AND name = :name
                        """)
                .setParameter("userId", testUserId)
                .setParameter("name", name)
                .getSingleResult()).doubleValue());
    }

    private boolean stayExists(long stayId) {
        return QuarkusTransaction.requiringNew().call(() -> timelineStayRepository.findById(stayId) != null);
    }

    private byte[] metadataBytes(List<String> dataTypes) throws IOException {
        ExportMetadataDto metadata = ExportMetadataDto.builder()
                .exportJobId(UUID.randomUUID())
                .userId(testUserId)
                .exportDate(EXPORT_TIME)
                .dataTypes(dataTypes)
                .format(ExportImportConstants.Formats.GEOPULSE)
                .version(ExportImportConstants.Versions.CURRENT)
                .build();
        return objectMapper.writeValueAsBytes(metadata);
    }

    private byte[] favoritesBytes(List<FavoritesDataDto.FavoritePointDto> points,
                                  List<FavoritesDataDto.FavoriteAreaDto> areas) throws IOException {
        return objectMapper.writeValueAsBytes(FavoritesDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.FAVORITES)
                .exportDate(EXPORT_TIME)
                .points(points)
                .areas(areas)
                .build());
    }

    private FavoritesDataDto.FavoritePointDto pointDto(long id, String name, double latitude, double longitude) {
        return FavoritesDataDto.FavoritePointDto.builder()
                .id(id)
                .name(name)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    private FavoritesDataDto.FavoriteAreaDto areaDto(long id, String name, Polygon polygon) {
        return FavoritesDataDto.FavoriteAreaDto.builder()
                .id(id)
                .name(name)
                .geometry(AreaGeometry.from(polygon))
                .build();
    }

    private byte[] rawGpsBytes() throws IOException {
        Point point = point(51.51, -0.13);
        return objectMapper.writeValueAsBytes(RawGpsDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.RAW_GPS)
                .exportDate(EXPORT_TIME)
                .startDate(GPS_TIME)
                .endDate(GPS_TIME)
                .points(List.of(RawGpsDataDto.GpsPointDto.builder()
                        .id(1L)
                        .timestamp(GPS_TIME)
                        .latitude(point.getY())
                        .longitude(point.getX())
                        .accuracy(5.0)
                        .source(GpsSourceType.GPX.name())
                        .deviceId("imported-device")
                        .build()))
                .build());
    }

    private byte[] timelineBytes() throws IOException {
        return objectMapper.writeValueAsBytes(TimelineDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.TIMELINE)
                .exportDate(EXPORT_TIME)
                .stays(List.of())
                .trips(List.of())
                .dataGaps(List.of())
                .build());
    }

    private Point point(double latitude, double longitude) {
        return coordinateScope.point(longitude, latitude);
    }

    private Polygon box(double south, double north, double west, double east) {
        return GeoUtils.buildBoundingBoxPolygon(south, north, west, east);
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
}
