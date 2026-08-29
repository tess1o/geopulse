package org.github.tess1o.geopulse.exportimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.service.ExportDataGenerator;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geofencing.model.entity.*;
import org.github.tess1o.geopulse.geofencing.repository.GeofenceRuleRepository;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportDataService;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingSource;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingStatus;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.github.tess1o.geopulse.mapmatching.repository.TimelineTripPathMatchRepository;
import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.NoteLocationSource;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.github.tess1o.geopulse.notes.repository.TimelineNoteRepository;
import org.github.tess1o.geopulse.periods.model.entity.PeriodTagEntity;
import org.github.tess1o.geopulse.periods.repository.PeriodTagRepository;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.entity.*;
import org.github.tess1o.geopulse.streaming.model.shared.DataGapStayOverrideLocationStrategy;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.trips.model.entity.*;
import org.github.tess1o.geopulse.trips.repository.TripCollaboratorRepository;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end coverage for GeoPulse native export/import.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@Slf4j
@SerializedDatabaseTest
class ExportImportIntegrationTest {
    private static final Instant BASE_TIME = Instant.parse("2026-02-01T12:00:00Z");

    @Inject
    ExportDataGenerator exportDataGenerator;
    @Inject
    ImportDataService importDataService;
    @Inject
    UserRepository userRepository;
    @Inject
    GpsPointRepository gpsPointRepository;
    @Inject
    TimelineStayRepository timelineStayRepository;
    @Inject
    TimelineTripRepository timelineTripRepository;
    @Inject
    TimelineDataGapRepository timelineDataGapRepository;
    @Inject
    FavoritesRepository favoritesRepository;
    @Inject
    ReverseGeocodingLocationRepository reverseGeocodingLocationRepository;
    @Inject
    GpsSourceRepository gpsSourceRepository;
    @Inject
    PeriodTagRepository periodTagRepository;
    @Inject
    TripRepository tripRepository;
    @Inject
    TripPlanItemRepository tripPlanItemRepository;
    @Inject
    TripCollaboratorRepository tripCollaboratorRepository;
    @Inject
    NotificationTemplateRepository notificationTemplateRepository;
    @Inject
    GeofenceRuleRepository geofenceRuleRepository;
    @Inject
    TimelineNoteRepository timelineNoteRepository;
    @Inject
    WeatherSampleRepository weatherSampleRepository;
    @Inject
    TimelineTripPathMatchRepository timelineTripPathMatchRepository;
    @Inject
    EntityManager entityManager;

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private UserEntity testUser;
    private UserEntity collaboratorUser;
    private FavoritesEntity testFavorite;
    private ReverseGeocodingLocationEntity testGeocodingLocation;
    private TimelineStayEntity testStay;
    private TimelineTripEntity testTimelineTrip;
    private TimelineDataGapEntity testDataGap;
    private GpsPointEntity testGpsPoint;
    private GpsSourceConfigEntity testGpsSource;
    private PeriodTagEntity testPeriodTag;
    private TimelineTripMovementOverrideEntity testTripOverride;
    private TimelineDataGapStayOverrideEntity testGapOverride;
    private TripEntity testTrip;
    private TripPlanItemEntity testPlanItem;
    private TripCollaboratorEntity testTripCollaborator;
    private NotificationTemplateEntity testEnterTemplate;
    private NotificationTemplateEntity testLeaveTemplate;
    private GeofenceRuleEntity testGeofenceRule;
    private TimelineNoteEntity testNote;
    private WeatherSampleEntity testWeatherSample;
    private TimelineTripPathMatchEntity testPathMatch;

    @BeforeEach
    @Transactional
    void setUp() {
        testUser = new UserEntity();
        testUser.setEmail(TestIds.uniqueEmail("it-user"));
        testUser.setFullName("Export Import Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(BASE_TIME.minus(30, ChronoUnit.DAYS));
        userRepository.persist(testUser);

        collaboratorUser = new UserEntity();
        collaboratorUser.setEmail(TestIds.uniqueEmail("trip-collaborator"));
        collaboratorUser.setFullName("Trip Collaborator");
        collaboratorUser.setPasswordHash("test-hash");
        collaboratorUser.setCreatedAt(BASE_TIME.minus(29, ChronoUnit.DAYS));
        userRepository.persist(collaboratorUser);

        createClassicExportData();
        createNewGeoPulseExportData();
        entityManager.flush();
    }

    @Test
    void testCompleteExportImportCycleRestoresAllNativeSections() throws Exception {
        OriginalData originalData = captureOriginalData();
        byte[] exportedData = exportAllNativeData();

        Map<String, byte[]> zipEntries = unzip(exportedData);
        assertZipContainsAllExpectedNativeFiles(zipEntries);
        assertExportPayloadsContainExpectedData(zipEntries);
        assertDetectedDataTypes(exportedData);

        deleteAndVerifyUserDomainData();

        importAllNativeData(exportedData);

        verifyImportedDataMatchesOriginal(originalData);
    }

    @Test
    void testImportIntoExistingNativeDataUpdatesWithoutDuplicates() throws Exception {
        OriginalData originalData = captureOriginalData();
        byte[] exportedData = exportAllNativeData();

        importAllNativeData(exportedData);
        mutateExistingImportTargets();
        importAllNativeData(exportedData);

        assertNativeSectionCounts(testUser.getId(), 1);
        verifyImportedDataMatchesOriginal(originalData);
    }

    @Test
    void testExportWithoutTimelineDependencies() throws Exception {
        ExportJob exportJob = new ExportJob();
        exportJob.setUserId(testUser.getId());
        exportJob.setDataTypes(List.of(
                ExportImportConstants.DataTypes.FAVORITES,
                ExportImportConstants.DataTypes.USER_INFO));
        exportJob.setFormat(ExportImportConstants.Formats.JSON);
        exportJob.setDateRange(testDateRange());

        byte[] exportedData = generateGeoPulseNativeExport(exportJob);

        ImportOptions validateOptions = new ImportOptions();
        validateOptions.setDataTypes(List.of(
                ExportImportConstants.DataTypes.FAVORITES,
                ExportImportConstants.DataTypes.USER_INFO,
                ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION));
        validateOptions.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        ImportJob validateJob = new ImportJob(testUser.getId(), validateOptions, "test-export.zip", exportedData);

        List<String> detectedDataTypes = importDataService.validateAndDetectDataTypes(validateJob);

        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.FAVORITES));
        assertTrue(detectedDataTypes.contains(ExportImportConstants.DataTypes.USER_INFO));
        assertFalse(detectedDataTypes.contains(ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION));
    }

    private void createClassicExportData() {
        testGeocodingLocation = new ReverseGeocodingLocationEntity();
        testGeocodingLocation.setUser(testUser);
        testGeocodingLocation.setRequestCoordinates(GeoUtils.createPoint(-122.4194, 37.7749));
        testGeocodingLocation.setResultCoordinates(GeoUtils.createPoint(-122.4194, 37.7749));
        testGeocodingLocation.setDisplayName("San Francisco, CA, USA");
        testGeocodingLocation.setProviderName("test-provider");
        testGeocodingLocation.setCreatedAt(BASE_TIME.minus(1, ChronoUnit.HOURS));
        testGeocodingLocation.setLastAccessedAt(BASE_TIME);
        testGeocodingLocation.setCity("San Francisco");
        testGeocodingLocation.setCountry("USA");
        reverseGeocodingLocationRepository.persist(testGeocodingLocation);

        testFavorite = new FavoritesEntity();
        testFavorite.setUser(testUser);
        testFavorite.setName("Home");
        testFavorite.setCity("San Francisco");
        testFavorite.setCountry("USA");
        testFavorite.setType(FavoriteLocationType.POINT);
        testFavorite.setGeometry(GeoUtils.createPoint(-122.4194, 37.7749));
        favoritesRepository.persist(testFavorite);

        testStay = TimelineStayEntity.builder()
                .user(testUser)
                .timestamp(BASE_TIME.minus(2, ChronoUnit.HOURS))
                .location(GeoUtils.createPoint(-122.4194, 37.7749))
                .stayDuration(60)
                .locationName("Home Location")
                .locationSource(LocationSource.HISTORICAL)
                .favoriteLocation(testFavorite)
                .geocodingLocation(testGeocodingLocation)
                .build();
        timelineStayRepository.persist(testStay);

        Coordinate[] pathCoordinates = new Coordinate[]{
                new Coordinate(-122.4194, 37.7749),
                new Coordinate(-122.4150, 37.7770),
                new Coordinate(-122.4120, 37.7800),
                new Coordinate(-122.4094, 37.7849)
        };
        LineString tripPath = geometryFactory.createLineString(pathCoordinates);
        testTimelineTrip = TimelineTripEntity.builder()
                .user(testUser)
                .timestamp(BASE_TIME.minus(90, ChronoUnit.MINUTES))
                .startPoint(GeoUtils.createPoint(-122.4194, 37.7749))
                .endPoint(GeoUtils.createPoint(-122.4094, 37.7849))
                .distanceMeters(1500)
                .tripDuration(1800)
                .movementType("WALKING")
                .movementTypeSource(MovementTypeSource.MANUAL)
                .path(tripPath)
                .avgGpsSpeed(1.3)
                .maxGpsSpeed(2.1)
                .speedVariance(0.4)
                .lowAccuracyPointsCount(2)
                .waterDistanceMeters(25.0)
                .waterDistanceRatio(0.02)
                .longestWaterSegmentMeters(12.5)
                .waterSampleCount(4)
                .waterEvidenceAvailable(true)
                .build();
        timelineTripRepository.persist(testTimelineTrip);

        testDataGap = TimelineDataGapEntity.builder()
                .user(testUser)
                .startTime(BASE_TIME.minus(4, ChronoUnit.HOURS))
                .endTime(BASE_TIME.minus(210, ChronoUnit.MINUTES))
                .durationSeconds(1800)
                .createdAt(BASE_TIME.minus(2, ChronoUnit.HOURS))
                .build();
        timelineDataGapRepository.persist(testDataGap);

        testGpsPoint = new GpsPointEntity();
        testGpsPoint.setUser(testUser);
        testGpsPoint.setTimestamp(BASE_TIME.minus(3, ChronoUnit.HOURS));
        testGpsPoint.setCoordinates(GeoUtils.createPoint(-122.4194, 37.7749));
        testGpsPoint.setAccuracy(5.0);
        testGpsPoint.setAltitude(100.0);
        testGpsPoint.setVelocity(0.0);
        testGpsPoint.setBattery(85.0);
        testGpsPoint.setDeviceId("test-device");
        testGpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
        testGpsPoint.setCreatedAt(BASE_TIME);
        gpsPointRepository.persist(testGpsPoint);

        testGpsSource = new GpsSourceConfigEntity();
        testGpsSource.setUser(testUser);
        testGpsSource.setUsername("test-user");
        testGpsSource.setSourceType(GpsSourceType.OWNTRACKS);
        testGpsSource.setActive(true);
        gpsSourceRepository.persist(testGpsSource);
    }

    private void createNewGeoPulseExportData() {
        testPeriodTag = PeriodTagEntity.builder()
                .user(testUser)
                .tagName("Winter Travel")
                .startTime(BASE_TIME.minus(1, ChronoUnit.DAYS))
                .endTime(BASE_TIME.plus(1, ChronoUnit.DAYS))
                .source("manual")
                .isActive(true)
                .color("#2f80ed")
                .showAsPreset(false)
                .createdAt(BASE_TIME.minus(2, ChronoUnit.DAYS))
                .updatedAt(BASE_TIME.minus(1, ChronoUnit.DAYS))
                .build();
        periodTagRepository.persist(testPeriodTag);

        testTripOverride = TimelineTripMovementOverrideEntity.builder()
                .user(testUser)
                .trip(testTimelineTrip)
                .movementType("TRAIN")
                .sourceTripTimestamp(testTimelineTrip.getTimestamp())
                .sourceTripDurationSeconds(testTimelineTrip.getTripDuration())
                .sourceDistanceMeters(testTimelineTrip.getDistanceMeters())
                .sourceStartLatitude(37.7749)
                .sourceStartLongitude(-122.4194)
                .sourceEndLatitude(37.7849)
                .sourceEndLongitude(-122.4094)
                .createdAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .updatedAt(BASE_TIME.minus(30, ChronoUnit.MINUTES))
                .build();
        entityManager.persist(testTripOverride);

        testGapOverride = TimelineDataGapStayOverrideEntity.builder()
                .user(testUser)
                .dataGap(testDataGap)
                .stay(testStay)
                .locationStrategy(DataGapStayOverrideLocationStrategy.SELECTED_LOCATION)
                .selectedFavoriteId(testFavorite.getId())
                .selectedGeocodingId(testGeocodingLocation.getId())
                .selectedLatitude(37.7749)
                .selectedLongitude(-122.4194)
                .selectedLocationName("Home Location")
                .sourceGapStartTime(testDataGap.getStartTime())
                .sourceGapEndTime(testDataGap.getEndTime())
                .sourceGapDurationSeconds(testDataGap.getDurationSeconds())
                .sourceBeforeLatitude(37.7740)
                .sourceBeforeLongitude(-122.4200)
                .sourceAfterLatitude(37.7750)
                .sourceAfterLongitude(-122.4180)
                .createdAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .updatedAt(BASE_TIME.minus(30, ChronoUnit.MINUTES))
                .build();
        entityManager.persist(testGapOverride);

        testTrip = TripEntity.builder()
                .user(testUser)
                .periodTag(testPeriodTag)
                .name("Conference Trip")
                .startTime(BASE_TIME.minus(1, ChronoUnit.DAYS))
                .endTime(BASE_TIME.plus(3, ChronoUnit.DAYS))
                .status(TripStatus.ACTIVE)
                .color("#22a06b")
                .notes("Meet customers and map the route.")
                .createdAt(BASE_TIME.minus(2, ChronoUnit.DAYS))
                .updatedAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .build();
        tripRepository.persist(testTrip);
        entityManager.flush();

        testPlanItem = TripPlanItemEntity.builder()
                .trip(testTrip)
                .title("Visit pier")
                .notes("Check the waterfront venue.")
                .latitude(37.8080)
                .longitude(-122.4177)
                .plannedDay(LocalDate.of(2026, 2, 2))
                .priority(TripPlanItemPriority.MUST)
                .orderIndex(3)
                .isVisited(true)
                .visitConfidence(0.92)
                .visitSource(TripPlanItemVisitSource.AUTO)
                .visitedAt(BASE_TIME.minus(20, ChronoUnit.MINUTES))
                .manualOverrideState(TripPlanItemOverrideState.CONFIRMED)
                .createdAt(BASE_TIME.minus(1, ChronoUnit.DAYS))
                .updatedAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .build();
        tripPlanItemRepository.persist(testPlanItem);

        testTripCollaborator = TripCollaboratorEntity.builder()
                .trip(testTrip)
                .collaborator(collaboratorUser)
                .accessRole(TripCollaboratorAccessRole.EDIT)
                .createdAt(BASE_TIME.minus(1, ChronoUnit.DAYS))
                .updatedAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .build();
        tripCollaboratorRepository.persist(testTripCollaborator);

        testEnterTemplate = NotificationTemplateEntity.builder()
                .user(testUser)
                .name("Arrived")
                .destination("mailto:arrive@example.com")
                .externalRoutingMode(AppriseExternalRoutingMode.URLS)
                .titleTemplate("Arrived at {{geofence.name}}")
                .bodyTemplate("Entered {{geofence.name}}")
                .defaultForEnter(true)
                .defaultForLeave(false)
                .enabled(true)
                .sendInApp(true)
                .createdAt(BASE_TIME.minus(1, ChronoUnit.DAYS))
                .updatedAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .build();
        notificationTemplateRepository.persist(testEnterTemplate);

        testLeaveTemplate = NotificationTemplateEntity.builder()
                .user(testUser)
                .name("Left")
                .destination("mailto:leave@example.com")
                .externalRoutingMode(AppriseExternalRoutingMode.KEY_TAG)
                .appriseConfigKey("ops")
                .appriseTag("travel")
                .titleTemplate("Left {{geofence.name}}")
                .bodyTemplate("Exited {{geofence.name}}")
                .defaultForEnter(false)
                .defaultForLeave(true)
                .enabled(true)
                .sendInApp(false)
                .createdAt(BASE_TIME.minus(1, ChronoUnit.DAYS))
                .updatedAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .build();
        notificationTemplateRepository.persist(testLeaveTemplate);
        entityManager.flush();

        testGeofenceRule = GeofenceRuleEntity.builder()
                .ownerUser(testUser)
                .name("Office")
                .northEastLat(37.7900)
                .northEastLon(-122.4000)
                .southWestLat(37.7700)
                .southWestLon(-122.4300)
                .monitorEnter(true)
                .monitorLeave(false)
                .cooldownSeconds(300)
                .enterTemplate(testEnterTemplate)
                .leaveTemplate(testLeaveTemplate)
                .status(GeofenceRuleStatus.ACTIVE)
                .createdAt(BASE_TIME.minus(1, ChronoUnit.DAYS))
                .updatedAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .build();
        geofenceRuleRepository.persist(testGeofenceRule);
        entityManager.flush();

        GeofenceRuleSubjectEntity subject = GeofenceRuleSubjectEntity.builder()
                .id(new GeofenceRuleSubjectId(testGeofenceRule.getId(), collaboratorUser.getId()))
                .rule(testGeofenceRule)
                .subjectUser(collaboratorUser)
                .createdAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .build();
        entityManager.persist(subject);
        testGeofenceRule.getSubjectAssignments().add(subject);

        testNote = TimelineNoteEntity.builder()
                .user(testUser)
                .title("Route note")
                .contentMarkdown("Remember the north entrance.")
                .snippet("Remember the north entrance.")
                .eventTime(BASE_TIME.minus(90, ChronoUnit.MINUTES))
                .location(GeoUtils.createPoint(-122.4094, 37.7849))
                .locationSource(NoteLocationSource.DERIVED_TRIP_GPS)
                .anchorType(NoteAnchorType.TRIP)
                .trip(testTimelineTrip)
                .sourceItemStartTime(testTimelineTrip.getTimestamp())
                .sourceItemDurationSeconds(testTimelineTrip.getTripDuration())
                .sourceStartLatitude(37.7749)
                .sourceStartLongitude(-122.4194)
                .sourceEndLatitude(37.7849)
                .sourceEndLongitude(-122.4094)
                .sourceDistanceMeters(testTimelineTrip.getDistanceMeters())
                .createdAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .updatedAt(BASE_TIME.minus(30, ChronoUnit.MINUTES))
                .build();
        timelineNoteRepository.persist(testNote);

        testWeatherSample = WeatherSampleEntity.builder()
                .user(testUser)
                .provider("OPEN_METEO")
                .source(WeatherTargetSource.HISTORICAL_BACKFILL)
                .requestedLatitude(37.7749)
                .requestedLongitude(-122.4194)
                .providerLatitude(37.77)
                .providerLongitude(-122.42)
                .latitudeBucket(37.77)
                .longitudeBucket(-122.42)
                .observedAt(BASE_TIME.minus(3, ChronoUnit.HOURS))
                .fetchedAt(BASE_TIME.minus(2, ChronoUnit.HOURS))
                .timezone("America/Los_Angeles")
                .weatherCode(3)
                .temperature(12.4)
                .apparentTemperature(11.9)
                .humidity(67.0)
                .precipitation(0.1)
                .rain(0.1)
                .snowfall(0.0)
                .cloudCover(75.0)
                .windSpeed(5.5)
                .windGust(9.2)
                .windDirection(240.0)
                .pressure(1012.0)
                .rawData(Map.of("quality", "fixture", "hour", 9))
                .createdAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .updatedAt(BASE_TIME.minus(30, ChronoUnit.MINUTES))
                .build();
        weatherSampleRepository.persist(testWeatherSample);

        testPathMatch = TimelineTripPathMatchEntity.builder()
                .user(testUser)
                .trip(testTimelineTrip)
                .provider("valhalla")
                .profile("pedestrian")
                .configHash("config-hash")
                .inputHash("input-hash")
                .status(MapMatchingStatus.MATCHED)
                .attempts(1)
                .nextAttemptAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .lastAttemptAt(BASE_TIME.minus(50, ChronoUnit.MINUTES))
                .completedAt(BASE_TIME.minus(45, ChronoUnit.MINUTES))
                .matchedSegmentsJson("[[{\"lat\":37.7749,\"lon\":-122.4194},{\"lat\":37.7849,\"lon\":-122.4094}]]")
                .source(MapMatchingSource.ON_DEMAND.name())
                .priority(MapMatchingSource.ON_DEMAND.priority())
                .createdAt(BASE_TIME.minus(1, ChronoUnit.HOURS))
                .updatedAt(BASE_TIME.minus(45, ChronoUnit.MINUTES))
                .build();
        timelineTripPathMatchRepository.persist(testPathMatch);
    }

    private OriginalData captureOriginalData() {
        return QuarkusTransaction.requiringNew().call(() -> {
            TimelineTripEntity timelineTrip = timelineTripRepository.findById(testTimelineTrip.getId());
            assertNotNull(timelineTrip.getPath());
            assertEquals(4, timelineTrip.getPath().getNumPoints());

            return new OriginalData(
                    testGeocodingLocation.getRequestCoordinates().getX(),
                    testGeocodingLocation.getRequestCoordinates().getY(),
                    testGeocodingLocation.getDisplayName(),
                    testGeocodingLocation.getCity(),
                    testGeocodingLocation.getCountry(),
                    testGpsPoint.getTimestamp(),
                    testGpsPoint.getLatitude(),
                    testGpsPoint.getAccuracy(),
                    testGpsSource.getUsername(),
                    testPeriodTag.getTagName(),
                    testPeriodTag.getStartTime(),
                    testPeriodTag.getColor(),
                    testTripOverride.getMovementType(),
                    testGapOverride.getSelectedLocationName(),
                    testTrip.getName(),
                    testTrip.getNotes(),
                    testPlanItem.getTitle(),
                    testPlanItem.getNotes(),
                    collaboratorUser.getEmail(),
                    testEnterTemplate.getName(),
                    testEnterTemplate.getDestination(),
                    testLeaveTemplate.getName(),
                    testGeofenceRule.getName(),
                    testGeofenceRule.getCooldownSeconds(),
                    testNote.getTitle(),
                    testNote.getContentMarkdown(),
                    testWeatherSample.getProvider(),
                    testWeatherSample.getTemperature(),
                    testPathMatch.getProvider(),
                    testPathMatch.getProfile(),
                    testPathMatch.getConfigHash(),
                    testPathMatch.getInputHash(),
                    testPathMatch.getMatchedSegmentsJson());
        });
    }

    private byte[] exportAllNativeData() throws Exception {
        ExportJob exportJob = new ExportJob(
                testUser.getId(),
                allNativeDataTypes(),
                testDateRange(),
                ExportImportConstants.Formats.JSON);
        return generateGeoPulseNativeExport(exportJob);
    }

    private byte[] generateGeoPulseNativeExport(ExportJob exportJob) throws Exception {
        return QuarkusTransaction.requiringNew().call(() -> {
            exportDataGenerator.generateGeoPulseNativeExport(exportJob);
            assertNotNull(exportJob.getTempFilePath());
            byte[] exportedData = Files.readAllBytes(Paths.get(exportJob.getTempFilePath()));
            assertTrue(exportedData.length > 0);
            return exportedData;
        });
    }

    private void importAllNativeData(byte[] exportedData) throws Exception {
        ImportOptions importOptions = new ImportOptions();
        importOptions.setDataTypes(allNativeImportDataTypes());
        importOptions.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        ImportJob importJob = new ImportJob(testUser.getId(), importOptions, "test-export.zip", exportedData);
        importDataService.processImportData(importJob);
    }

    private void assertDetectedDataTypes(byte[] exportedData) throws Exception {
        ImportOptions validateOptions = new ImportOptions();
        validateOptions.setDataTypes(allNativeImportDataTypes());
        validateOptions.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        ImportJob validateJob = new ImportJob(testUser.getId(), validateOptions, "test-export.zip", exportedData);

        List<String> detectedDataTypes = importDataService.validateAndDetectDataTypes(validateJob);

        assertFalse(detectedDataTypes.contains(ExportImportConstants.DataTypes.TIMELINE));
        assertFalse(detectedDataTypes.contains(ExportImportConstants.DataTypes.DATA_GAPS));
        for (String dataType : allNativeImportDataTypes()) {
            assertTrue(detectedDataTypes.contains(dataType), "Detected data type missing: " + dataType);
        }
    }

    private void assertZipContainsAllExpectedNativeFiles(Map<String, byte[]> entries) {
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.METADATA));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.TIMELINE_DATA));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.DATA_GAPS));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.RAW_GPS_DATA));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.FAVORITES));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.USER_INFO));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.LOCATION_SOURCES));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.REVERSE_GEOCODING));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.PERIOD_TAGS));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.TIMELINE_OVERRIDES));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.TRIP_WORKSPACE));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.NOTIFICATION_TEMPLATES));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.GEOFENCING));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.NOTES));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.WEATHER_SAMPLES));
        assertTrue(entries.containsKey(ExportImportConstants.FileNames.MAP_MATCHING));
    }

    private void assertExportPayloadsContainExpectedData(Map<String, byte[]> entries) throws Exception {
        ExportMetadataDto metadata = read(entries, ExportImportConstants.FileNames.METADATA, ExportMetadataDto.class);
        assertEquals(ExportImportConstants.Versions.CURRENT, metadata.getVersion());
        assertTrue(metadata.getDataTypes().containsAll(allNativeDataTypes()));

        RawGpsDataDto rawGps = read(entries, ExportImportConstants.FileNames.RAW_GPS_DATA, RawGpsDataDto.class);
        assertEquals(1, rawGps.getPoints().size());
        assertEquals("test-device", rawGps.getPoints().get(0).getDeviceId());

        TimelineDataDto timeline = read(entries, ExportImportConstants.FileNames.TIMELINE_DATA, TimelineDataDto.class);
        assertEquals(1, timeline.getStays().size());
        assertEquals(1, timeline.getTrips().size());
        assertEquals(1, timeline.getDataGaps().size());
        TimelineDataDto.TripDto exportedTrip = timeline.getTrips().get(0);
        assertEquals("MANUAL", exportedTrip.getMovementTypeSource());
        assertEquals(1.3, exportedTrip.getAvgGpsSpeed(), 0.001);
        assertEquals(25.0, exportedTrip.getWaterDistanceMeters(), 0.001);
        assertTrue(exportedTrip.getWaterEvidenceAvailable());

        PeriodTagsDataDto periodTags = read(entries, ExportImportConstants.FileNames.PERIOD_TAGS, PeriodTagsDataDto.class);
        assertEquals(1, periodTags.getPeriodTags().size());
        assertEquals("Winter Travel", periodTags.getPeriodTags().get(0).getTagName());

        TimelineOverridesDataDto overrides = read(entries, ExportImportConstants.FileNames.TIMELINE_OVERRIDES, TimelineOverridesDataDto.class);
        assertEquals(1, overrides.getTripMovementOverrides().size());
        assertEquals("TRAIN", overrides.getTripMovementOverrides().get(0).getMovementType());
        assertEquals(1, overrides.getDataGapStayOverrides().size());
        assertEquals("Home Location", overrides.getDataGapStayOverrides().get(0).getSelectedLocationName());

        TripWorkspaceDataDto tripWorkspace = read(entries, ExportImportConstants.FileNames.TRIP_WORKSPACE, TripWorkspaceDataDto.class);
        assertEquals(1, tripWorkspace.getTrips().size());
        assertEquals("Conference Trip", tripWorkspace.getTrips().get(0).getName());
        assertEquals(1, tripWorkspace.getTrips().get(0).getPlanItems().size());
        assertEquals("Visit pier", tripWorkspace.getTrips().get(0).getPlanItems().get(0).getTitle());
        assertEquals(1, tripWorkspace.getTrips().get(0).getCollaborators().size());
        assertEquals(collaboratorUser.getEmail(), tripWorkspace.getTrips().get(0).getCollaborators().get(0).getEmail());

        NotificationTemplatesDataDto templates = read(entries, ExportImportConstants.FileNames.NOTIFICATION_TEMPLATES, NotificationTemplatesDataDto.class);
        assertEquals(2, templates.getTemplates().size());
        assertTrue(templates.getTemplates().stream().anyMatch(template -> "Arrived".equals(template.getName())));

        GeofencingDataDto geofencing = read(entries, ExportImportConstants.FileNames.GEOFENCING, GeofencingDataDto.class);
        assertEquals(1, geofencing.getRules().size());
        assertEquals("Office", geofencing.getRules().get(0).getName());
        assertEquals(1, geofencing.getRules().get(0).getSubjects().size());
        assertEquals(collaboratorUser.getEmail(), geofencing.getRules().get(0).getSubjects().get(0).getEmail());

        NotesDataDto notes = read(entries, ExportImportConstants.FileNames.NOTES, NotesDataDto.class);
        assertEquals(1, notes.getNotes().size());
        assertEquals("Route note", notes.getNotes().get(0).getTitle());
        assertEquals("TRIP", notes.getNotes().get(0).getAnchorType());

        WeatherSamplesDataDto weather = read(entries, ExportImportConstants.FileNames.WEATHER_SAMPLES, WeatherSamplesDataDto.class);
        assertEquals(1, weather.getSamples().size());
        assertEquals("OPEN_METEO", weather.getSamples().get(0).getProvider());
        assertEquals(12.4, weather.getSamples().get(0).getTemperature(), 0.001);

        MapMatchingDataDto mapMatching = read(entries, ExportImportConstants.FileNames.MAP_MATCHING, MapMatchingDataDto.class);
        assertEquals(1, mapMatching.getPathMatches().size());
        MapMatchingDataDto.PathMatchDto pathMatch = mapMatching.getPathMatches().get(0);
        assertEquals("valhalla", pathMatch.getProvider());
        assertEquals("pedestrian", pathMatch.getProfile());
        assertEquals("config-hash", pathMatch.getConfigHash());
        assertEquals("input-hash", pathMatch.getInputHash());
        assertEquals(MapMatchingStatus.MATCHED.name(), pathMatch.getStatus());
        assertEquals(testTimelineTrip.getTimestamp(), pathMatch.getTripTimestamp());
        assertTrue(pathMatch.getMatchedSegmentsJson().contains("37.7749"));
    }

    private <T> T read(Map<String, byte[]> entries, String fileName, Class<T> clazz) throws Exception {
        return objectMapper.readValue(entries.get(fileName), clazz);
    }

    private Map<String, byte[]> unzip(byte[] data) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
                zis.closeEntry();
            }
        }
        return entries;
    }

    private void deleteAndVerifyUserDomainData() {
        QuarkusTransaction.requiringNew().run(() -> {
            deleteUserDomainData(testUser.getId());
            entityManager.flush();
            entityManager.clear();
            assertNativeSectionCounts(testUser.getId(), 0);
            assertEquals(0, gpsPointRepository.count("user.id = ?1", testUser.getId()));
            assertEquals(0, gpsSourceRepository.count("user.id = ?1", testUser.getId()));
            assertEquals(0, favoritesRepository.count("user.id = ?1", testUser.getId()));
            assertEquals(0, reverseGeocodingLocationRepository.count("user.id = ?1", testUser.getId()));
        });
    }

    private void deleteUserDomainData(UUID userId) {
        entityManager.createQuery("""
                DELETE FROM GeofenceRuleSubjectEntity subject
                WHERE subject.rule.id IN (
                    SELECT rule.id FROM GeofenceRuleEntity rule WHERE rule.ownerUser.id = :userId
                )
                """).setParameter("userId", userId).executeUpdate();
        entityManager.createQuery("DELETE FROM GeofenceRuleEntity rule WHERE rule.ownerUser.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM NotificationTemplateEntity template WHERE template.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM TripCollaboratorEntity collaborator WHERE collaborator.trip.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM TripPlanItemEntity item WHERE item.trip.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM TripEntity trip WHERE trip.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM TimelineNoteEntity note WHERE note.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM WeatherSampleEntity sample WHERE sample.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM TimelineTripMovementOverrideEntity override WHERE override.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM TimelineDataGapStayOverrideEntity override WHERE override.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM TimelineTripPathMatchEntity pathMatch WHERE pathMatch.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM TimelineStayEntity stay WHERE stay.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM TimelineTripEntity trip WHERE trip.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM TimelineDataGapEntity gap WHERE gap.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM PeriodTagEntity tag WHERE tag.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM GpsPointEntity point WHERE point.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM GpsSourceConfigEntity source WHERE source.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM FavoritesEntity favorite WHERE favorite.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
        entityManager.createQuery("DELETE FROM ReverseGeocodingLocationEntity location WHERE location.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private void mutateExistingImportTargets() {
        QuarkusTransaction.requiringNew().run(() -> {
            periodTagRepository.findByUserId(testUser.getId()).get(0).setColor("#000000");
            findTripOverride().setMovementType("DRIVING");
            findGapOverride().setSelectedLocationName("Changed Location");
            notificationTemplateRepository.findByUser(testUser.getId()).stream()
                    .filter(template -> "Arrived".equals(template.getName()))
                    .findFirst()
                    .orElseThrow()
                    .setDestination("mailto:changed@example.com");
            TripEntity trip = tripRepository.findByUserId(testUser.getId()).get(0);
            trip.setNotes("Changed notes");
            tripPlanItemRepository.findByTripId(trip.getId()).get(0).setNotes("Changed plan item notes");
            geofenceRuleRepository.findByOwner(testUser.getId()).get(0).setCooldownSeconds(5);
            timelineNoteRepository.findByUserIdAndTimeRange(
                    testUser.getId(),
                    BASE_TIME.minus(1, ChronoUnit.DAYS),
                    BASE_TIME.plus(1, ChronoUnit.DAYS)).get(0).setTitle("Changed note title");
            weatherSampleRepository.findByUserAndRange(
                    testUser.getId(),
                    BASE_TIME.minus(1, ChronoUnit.DAYS),
                    BASE_TIME.plus(1, ChronoUnit.DAYS),
                    null, null, null, null).get(0).setTemperature(-99.0);
            TimelineTripPathMatchEntity pathMatch = findPathMatch();
            pathMatch.setMatchedSegmentsJson("[]");
            pathMatch.setAttempts(99);
        });
    }

    private void verifyImportedDataMatchesOriginal(OriginalData originalData) {
        QuarkusTransaction.requiringNew().run(() -> {
            ReverseGeocodingLocationEntity importedGeocodingLocation = reverseGeocodingLocationRepository.findByRequestCoordinates(
                    testUser.getId(),
                    GeoUtils.createPoint(originalData.geocodingLongitude(), originalData.geocodingLatitude()),
                    25.0);
            assertNotNull(importedGeocodingLocation);
            assertEquals(originalData.geocodingDisplayName(), importedGeocodingLocation.getDisplayName());
            assertEquals(originalData.geocodingCity(), importedGeocodingLocation.getCity());
            assertEquals(originalData.geocodingCountry(), importedGeocodingLocation.getCountry());
            assertEquals(testUser.getId(), importedGeocodingLocation.getUser().getId());

            List<GpsPointEntity> gpsPoints = gpsPointRepository.findByUserAndDateRange(
                    testUser.getId(),
                    originalData.gpsTimestamp().minusSeconds(1),
                    originalData.gpsTimestamp().plusSeconds(1),
                    0, 10, "timestamp", "asc");
            assertEquals(1, gpsPoints.size());
            assertEquals(originalData.gpsLatitude(), gpsPoints.get(0).getLatitude(), 0.000001);
            assertEquals(originalData.gpsAccuracy(), gpsPoints.get(0).getAccuracy(), 0.001);

            GpsSourceConfigEntity importedGpsSource = gpsSourceRepository.findAll().firstResult();
            assertNotNull(importedGpsSource);
            assertEquals(originalData.gpsSourceUsername(), importedGpsSource.getUsername());

            PeriodTagEntity periodTag = periodTagRepository.findByUserId(testUser.getId()).get(0);
            assertEquals(originalData.periodTagName(), periodTag.getTagName());
            assertEquals(originalData.periodTagStartTime(), periodTag.getStartTime());
            assertEquals(originalData.periodTagColor(), periodTag.getColor());

            assertEquals(originalData.tripOverrideMovementType(), findTripOverride().getMovementType());
            assertEquals(originalData.gapOverrideLocationName(), findGapOverride().getSelectedLocationName());

            TripEntity trip = tripRepository.findByUserId(testUser.getId()).get(0);
            assertEquals(originalData.tripName(), trip.getName());
            assertEquals(originalData.tripNotes(), trip.getNotes());
            assertEquals(periodTag.getId(), trip.getPeriodTag().getId());

            TripPlanItemEntity planItem = tripPlanItemRepository.findByTripId(trip.getId()).get(0);
            assertEquals(originalData.planItemTitle(), planItem.getTitle());
            assertEquals(originalData.planItemNotes(), planItem.getNotes());
            assertEquals(TripPlanItemPriority.MUST, planItem.getPriority());
            assertTrue(planItem.getIsVisited());

            TripCollaboratorEntity collaborator = tripCollaboratorRepository.findByTripIdWithCollaborator(trip.getId()).get(0);
            assertEquals(originalData.collaboratorEmail(), collaborator.getCollaborator().getEmail());
            assertEquals(TripCollaboratorAccessRole.EDIT, collaborator.getAccessRole());

            NotificationTemplateEntity enterTemplate = notificationTemplateRepository.findByUser(testUser.getId()).stream()
                    .filter(template -> originalData.enterTemplateName().equals(template.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(originalData.enterTemplateDestination(), enterTemplate.getDestination());

            GeofenceRuleEntity geofenceRule = geofenceRuleRepository.findByOwner(testUser.getId()).get(0);
            assertEquals(originalData.geofenceName(), geofenceRule.getName());
            assertEquals(originalData.geofenceCooldownSeconds(), geofenceRule.getCooldownSeconds());
            assertEquals(enterTemplate.getId(), geofenceRule.getEnterTemplate().getId());
            assertEquals(originalData.leaveTemplateName(), geofenceRule.getLeaveTemplate().getName());
            assertEquals(1, geofenceRule.getSubjectAssignments().size());
            assertEquals(originalData.collaboratorEmail(),
                    geofenceRule.getSubjectAssignments().iterator().next().getSubjectUser().getEmail());

            TimelineNoteEntity note = timelineNoteRepository.findByUserIdAndTimeRange(
                    testUser.getId(),
                    BASE_TIME.minus(1, ChronoUnit.DAYS),
                    BASE_TIME.plus(1, ChronoUnit.DAYS)).get(0);
            assertEquals(originalData.noteTitle(), note.getTitle());
            assertEquals(originalData.noteContent(), note.getContentMarkdown());
            assertEquals(NoteAnchorType.TRIP, note.getAnchorType());
            assertNotNull(note.getLocation());

            WeatherSampleEntity weather = weatherSampleRepository.findByUserAndRange(
                    testUser.getId(),
                    BASE_TIME.minus(1, ChronoUnit.DAYS),
                    BASE_TIME.plus(1, ChronoUnit.DAYS),
                    null, null, null, null).get(0);
            assertEquals(originalData.weatherProvider(), weather.getProvider());
            assertEquals(originalData.weatherTemperature(), weather.getTemperature(), 0.001);
            assertEquals("fixture", weather.getRawData().get("quality"));

            TimelineTripPathMatchEntity pathMatch = timelineTripPathMatchRepository.findCurrent(
                            testUser.getId(),
                            originalData.mapMatchingProvider(),
                            originalData.mapMatchingProfile(),
                            originalData.mapMatchingConfigHash(),
                            originalData.mapMatchingInputHash())
                    .orElseThrow();
            assertEquals(MapMatchingStatus.MATCHED, pathMatch.getStatus());
            assertEquals(originalData.mapMatchingSegmentsJson(), pathMatch.getMatchedSegmentsJson());
            assertEquals(MapMatchingSource.ON_DEMAND.name(), pathMatch.getSource());
            assertEquals(1, pathMatch.getAttempts());
        });
    }

    private TimelineTripMovementOverrideEntity findTripOverride() {
        return entityManager.createQuery("""
                        SELECT override FROM TimelineTripMovementOverrideEntity override
                        WHERE override.user.id = :userId
                        """, TimelineTripMovementOverrideEntity.class)
                .setParameter("userId", testUser.getId())
                .getSingleResult();
    }

    private TimelineDataGapStayOverrideEntity findGapOverride() {
        return entityManager.createQuery("""
                        SELECT override FROM TimelineDataGapStayOverrideEntity override
                        WHERE override.user.id = :userId
                        """, TimelineDataGapStayOverrideEntity.class)
                .setParameter("userId", testUser.getId())
                .getSingleResult();
    }

    private TimelineTripPathMatchEntity findPathMatch() {
        return timelineTripPathMatchRepository.findCurrent(
                        testUser.getId(),
                        "valhalla",
                        "pedestrian",
                        "config-hash",
                        "input-hash")
                .orElseThrow();
    }

    private void assertNativeSectionCounts(UUID userId, long expected) {
        assertEquals(expected, periodTagRepository.count("user.id = ?1", userId), "period tags");
        assertEquals(expected, entityManager.createQuery(
                "SELECT COUNT(override) FROM TimelineTripMovementOverrideEntity override WHERE override.user.id = :userId",
                Long.class).setParameter("userId", userId).getSingleResult(), "trip overrides");
        assertEquals(expected, entityManager.createQuery(
                "SELECT COUNT(override) FROM TimelineDataGapStayOverrideEntity override WHERE override.user.id = :userId",
                Long.class).setParameter("userId", userId).getSingleResult(), "gap overrides");
        assertEquals(expected, tripRepository.count("user.id = ?1", userId), "trips");
        assertEquals(expected, entityManager.createQuery(
                "SELECT COUNT(item) FROM TripPlanItemEntity item WHERE item.trip.user.id = :userId",
                Long.class).setParameter("userId", userId).getSingleResult(), "trip plan items");
        assertEquals(expected, entityManager.createQuery(
                "SELECT COUNT(collaborator) FROM TripCollaboratorEntity collaborator WHERE collaborator.trip.user.id = :userId",
                Long.class).setParameter("userId", userId).getSingleResult(), "trip collaborators");
        assertEquals(expected * 2, notificationTemplateRepository.count("user.id = ?1", userId), "notification templates");
        assertEquals(expected, geofenceRuleRepository.count("ownerUser.id = ?1", userId), "geofence rules");
        assertEquals(expected, entityManager.createQuery(
                "SELECT COUNT(subject) FROM GeofenceRuleSubjectEntity subject WHERE subject.rule.ownerUser.id = :userId",
                Long.class).setParameter("userId", userId).getSingleResult(), "geofence subjects");
        assertEquals(expected, timelineNoteRepository.count("user.id = ?1", userId), "notes");
        assertEquals(expected, weatherSampleRepository.count("user.id = ?1", userId), "weather samples");
        assertEquals(expected, timelineTripPathMatchRepository.count("user.id = ?1", userId), "map matching path matches");
    }

    private ExportDateRange testDateRange() {
        ExportDateRange dateRange = new ExportDateRange();
        dateRange.setStartDate(BASE_TIME.minus(2, ChronoUnit.DAYS));
        dateRange.setEndDate(BASE_TIME.plus(4, ChronoUnit.DAYS));
        return dateRange;
    }

    private List<String> allNativeDataTypes() {
        return List.of(
                ExportImportConstants.DataTypes.TIMELINE,
                ExportImportConstants.DataTypes.DATA_GAPS,
                ExportImportConstants.DataTypes.RAW_GPS,
                ExportImportConstants.DataTypes.USER_INFO,
                ExportImportConstants.DataTypes.LOCATION_SOURCES,
                ExportImportConstants.DataTypes.FAVORITES,
                ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION,
                ExportImportConstants.DataTypes.PERIOD_TAGS,
                ExportImportConstants.DataTypes.TIMELINE_OVERRIDES,
                ExportImportConstants.DataTypes.TRIP_WORKSPACE,
                ExportImportConstants.DataTypes.NOTIFICATION_TEMPLATES,
                ExportImportConstants.DataTypes.GEOFENCING,
                ExportImportConstants.DataTypes.NOTES,
                ExportImportConstants.DataTypes.WEATHER_SAMPLES,
                ExportImportConstants.DataTypes.MAP_MATCHING
        );
    }

    private List<String> allNativeImportDataTypes() {
        return List.of(
                ExportImportConstants.DataTypes.RAW_GPS,
                ExportImportConstants.DataTypes.USER_INFO,
                ExportImportConstants.DataTypes.LOCATION_SOURCES,
                ExportImportConstants.DataTypes.FAVORITES,
                ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION,
                ExportImportConstants.DataTypes.PERIOD_TAGS,
                ExportImportConstants.DataTypes.TIMELINE_OVERRIDES,
                ExportImportConstants.DataTypes.TRIP_WORKSPACE,
                ExportImportConstants.DataTypes.NOTIFICATION_TEMPLATES,
                ExportImportConstants.DataTypes.GEOFENCING,
                ExportImportConstants.DataTypes.NOTES,
                ExportImportConstants.DataTypes.WEATHER_SAMPLES,
                ExportImportConstants.DataTypes.MAP_MATCHING
        );
    }

    private record OriginalData(
            double geocodingLongitude,
            double geocodingLatitude,
            String geocodingDisplayName,
            String geocodingCity,
            String geocodingCountry,
            Instant gpsTimestamp,
            double gpsLatitude,
            Double gpsAccuracy,
            String gpsSourceUsername,
            String periodTagName,
            Instant periodTagStartTime,
            String periodTagColor,
            String tripOverrideMovementType,
            String gapOverrideLocationName,
            String tripName,
            String tripNotes,
            String planItemTitle,
            String planItemNotes,
            String collaboratorEmail,
            String enterTemplateName,
            String enterTemplateDestination,
            String leaveTemplateName,
            String geofenceName,
            Integer geofenceCooldownSeconds,
            String noteTitle,
            String noteContent,
            String weatherProvider,
            Double weatherTemperature,
            String mapMatchingProvider,
            String mapMatchingProfile,
            String mapMatchingConfigHash,
            String mapMatchingInputHash,
            String mapMatchingSegmentsJson) {
    }
}
