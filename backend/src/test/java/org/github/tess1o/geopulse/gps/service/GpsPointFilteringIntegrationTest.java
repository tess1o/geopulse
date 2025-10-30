package org.github.tess1o.geopulse.gps.service;

import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantBattery;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantGpsData;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantLocation;
import org.github.tess1o.geopulse.gps.integrations.overland.model.Geometry;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.overland.model.Properties;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.service.filter.GpsDataFilteringService;
import org.github.tess1o.geopulse.gps.service.filter.GpsFilterResult;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for GPS filtering with mapper.
 * Tests that unit conversions and filtering work together correctly.
 */
@Slf4j
class GpsPointFilteringIntegrationTest {

    private GpsPointMapper mapper;
    private GpsDataFilteringService filteringService;
    private GpsSourceConfigEntity config;
    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        mapper = new GpsPointMapper();
        filteringService = new GpsDataFilteringService();

        testUser = new UserEntity();
        testUser.setId(UUID.randomUUID());

        config = new GpsSourceConfigEntity();
        config.setId(UUID.randomUUID());
        config.setFilterInaccurateData(true);
        config.setMaxAllowedAccuracy(100); // 100 meters
        config.setMaxAllowedSpeed(250); // 250 km/h
    }

    @Test
    void testOwnTracks_VelocityAlreadyInKmh_FilteredCorrectly() {
        // Given: OwnTracks message with velocity in km/h
        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .lat(40.7128)
                .lon(-74.0060)
                .acc(50.0) // 50m accuracy - should pass
                .vel(120.0) // 120 km/h - should pass
                .tst(Instant.now().getEpochSecond())
                .build();

        // When: Map and filter
        GpsPointEntity entity = mapper.toEntity(message, testUser, "test-device", GpsSourceType.OWNTRACKS);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted
        assertTrue(result.isAccepted());
        assertEquals(120.0, entity.getVelocity()); // Stored in km/h
    }

    @Test
    void testOwnTracks_ExcessiveSpeed_Rejected() {
        // Given: OwnTracks message with excessive speed
        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .lat(40.7128)
                .lon(-74.0060)
                .acc(50.0)
                .vel(300.0) // 300 km/h - exceeds 250 km/h threshold
                .tst(Instant.now().getEpochSecond())
                .build();

        // When: Map and filter
        GpsPointEntity entity = mapper.toEntity(message, testUser, "test-device", GpsSourceType.OWNTRACKS);
        GpsFilterResult result = filteringService.filter(entity, config);

        log.info("Result: {}", result);
        // Then: Point is rejected
        assertTrue(result.isRejected());
        assertTrue(result.getRejectionReason().toLowerCase(Locale.ROOT).contains("speed"));
    }

    @Test
    void testOverland_SpeedConvertedFromMsToKmh() {
        // Given: Overland message with speed in m/s
        OverlandLocationMessage message = createOverlandMessage(
                50.0,  // 50m accuracy - should pass
                30.0   // 30 m/s = 108 km/h - should pass
        );

        // When: Map and filter
        GpsPointEntity entity = mapper.toEntity(message, testUser, GpsSourceType.OVERLAND);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted and speed is converted
        assertTrue(result.isAccepted());
        assertEquals(108.0, entity.getVelocity(), 0.1); // 30 m/s * 3.6 = 108 km/h
    }

    @Test
    void testOverland_ExcessiveSpeedInMs_RejectedAfterConversion() {
        // Given: Overland message with excessive speed in m/s
        OverlandLocationMessage message = createOverlandMessage(
                50.0,  // 50m accuracy - OK
                80.0   // 80 m/s = 288 km/h - exceeds 250 km/h threshold
        );

        // When: Map and filter
        GpsPointEntity entity = mapper.toEntity(message, testUser, GpsSourceType.OVERLAND);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is rejected after conversion
        assertTrue(result.isRejected());
        assertEquals(288.0, entity.getVelocity(), 0.1); // 80 m/s * 3.6 = 288 km/h
        assertTrue(result.getRejectionReason().toLowerCase(Locale.ROOT).contains("speed"));
        assertTrue(result.getRejectionReason().contains("288"));
    }

    @Test
    void testHomeAssistant_SpeedConvertedAndFiltered() {
        // Given: HomeAssistant message with speed in m/s
        HomeAssistantGpsData data = createHomeAssistantMessage(
                50.0,  // 50m accuracy - should pass
                20.0   // 20 m/s = 72 km/h - should pass
        );

        // When: Map and filter
        GpsPointEntity entity = mapper.toEntity(data, testUser, GpsSourceType.HOME_ASSISTANT);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted and speed is converted
        assertTrue(result.isAccepted());
        assertEquals(72.0, entity.getVelocity(), 0.1); // 20 m/s * 3.6 = 72 km/h
    }

    @Test
    void testHomeAssistant_PoorAccuracy_Rejected() {
        // Given: HomeAssistant message with poor accuracy
        HomeAssistantGpsData data = createHomeAssistantMessage(
                150.0, // 150m accuracy - exceeds 100m threshold
                20.0   // 20 m/s - speed is OK
        );

        // When: Map and filter
        GpsPointEntity entity = mapper.toEntity(data, testUser, GpsSourceType.HOME_ASSISTANT);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is rejected due to accuracy
        assertTrue(result.isRejected());
        assertTrue(result.getRejectionReason().toLowerCase(Locale.ROOT).contains("accuracy"));
        assertTrue(result.getRejectionReason().contains("150"));
    }

    @Test
    void testMultipleSources_ConsistentFiltering() {
        // Given: Messages from different sources with same effective speed
        // 30 m/s = 108 km/h (should all pass 250 km/h threshold)

        OwnTracksLocationMessage ownTracks = OwnTracksLocationMessage.builder()
                .lat(40.7128).lon(-74.0060)
                .acc(50.0)
                .vel(108.0) // Already in km/h
                .tst(Instant.now().getEpochSecond())
                .build();

        OverlandLocationMessage overland = createOverlandMessage(50.0, 30.0); // 30 m/s
        HomeAssistantGpsData homeAssistant = createHomeAssistantMessage(50.0, 30.0); // 30 m/s

        // When: Map and filter all three
        GpsPointEntity ownTracksEntity = mapper.toEntity(ownTracks, testUser, "device", GpsSourceType.OWNTRACKS);
        GpsPointEntity overlandEntity = mapper.toEntity(overland, testUser, GpsSourceType.OVERLAND);
        GpsPointEntity homeAssistantEntity = mapper.toEntity(homeAssistant, testUser, GpsSourceType.HOME_ASSISTANT);

        GpsFilterResult ownTracksResult = filteringService.filter(ownTracksEntity, config);
        GpsFilterResult overlandResult = filteringService.filter(overlandEntity, config);
        GpsFilterResult homeAssistantResult = filteringService.filter(homeAssistantEntity, config);

        // Then: All are accepted and have same speed
        assertTrue(ownTracksResult.isAccepted());
        assertTrue(overlandResult.isAccepted());
        assertTrue(homeAssistantResult.isAccepted());

        assertEquals(108.0, ownTracksEntity.getVelocity(), 0.1);
        assertEquals(108.0, overlandEntity.getVelocity(), 0.1);
        assertEquals(108.0, homeAssistantEntity.getVelocity(), 0.1);
    }

    @Test
    void testNullValues_NotRejected() {
        // Given: Messages with null accuracy and speed
        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .lat(40.7128)
                .lon(-74.0060)
                .acc(null) // No accuracy data
                .vel(null) // No velocity data
                .tst(Instant.now().getEpochSecond())
                .build();

        // When: Map and filter
        GpsPointEntity entity = mapper.toEntity(message, testUser, "device", GpsSourceType.OWNTRACKS);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted (null means unavailable, not bad)
        assertTrue(result.isAccepted());
    }

    @Test
    void testFilteringDisabled_AllPointsAccepted() {
        // Given: Filtering is disabled
        config.setFilterInaccurateData(false);

        // When: Point with terrible accuracy and speed
        OwnTracksLocationMessage message = OwnTracksLocationMessage.builder()
                .lat(40.7128)
                .lon(-74.0060)
                .acc(500.0) // Very bad accuracy
                .vel(500.0) // Unrealistic speed
                .tst(Instant.now().getEpochSecond())
                .build();

        GpsPointEntity entity = mapper.toEntity(message, testUser, "device", GpsSourceType.OWNTRACKS);
        GpsFilterResult result = filteringService.filter(entity, config);

        // Then: Point is accepted because filtering is disabled
        assertTrue(result.isAccepted());
    }

    // Helper methods

    private OverlandLocationMessage createOverlandMessage(Double accuracy, Double speedMs) {
        OverlandLocationMessage message = new OverlandLocationMessage();
        message.setType("Feature");

        Geometry geometry = new Geometry();
        geometry.setCoordinates(new double[]{-74.0060, 40.7128});
        message.setGeometry(geometry);

        Properties properties = new Properties();
        properties.setHorizontalAccuracy(accuracy);
        properties.setSpeed(speedMs);
        properties.setTimestamp(Instant.now());
        properties.setDeviceId("test-device");
        properties.setBatteryLevel(0.8);
        properties.setAltitude(10);
        message.setProperties(properties);

        return message;
    }

    private HomeAssistantGpsData createHomeAssistantMessage(Double accuracy, Double speedMs) {
        HomeAssistantGpsData data = new HomeAssistantGpsData();
        data.setDeviceId("test-device");
        data.setTimestamp(Instant.now());

        HomeAssistantLocation location = new HomeAssistantLocation();
        location.setLatitude(40.7128);
        location.setLongitude(-74.0060);
        location.setAccuracy(accuracy);
        location.setSpeed(speedMs);
        location.setAltitude(10.0);
        data.setLocation(location);

        HomeAssistantBattery battery = new HomeAssistantBattery();
        battery.setLevel(80);
        data.setBattery(battery);

        return data;
    }
}
