package org.github.tess1o.geopulse.user.service;

import jakarta.enterprise.event.Event;
import org.github.tess1o.geopulse.admin.service.AdminBootstrapService;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.auth.config.AuthConfigurationService;
import org.github.tess1o.geopulse.geofencing.service.DefaultNotificationTemplateService;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingConfiguration;
import org.github.tess1o.geopulse.streaming.events.TimelinePreferencesUpdatedEvent;
import org.github.tess1o.geopulse.streaming.events.TimelineStructureUpdatedEvent;
import org.github.tess1o.geopulse.streaming.events.TravelClassificationUpdatedEvent;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.shared.map.MapRenderMode;
import org.github.tess1o.geopulse.user.model.TimelineDisplayCapabilities;
import org.github.tess1o.geopulse.user.model.TimelineDisplayPreferences;
import org.github.tess1o.geopulse.user.model.TimelineDisplaySettings;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserAvatarRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class UserServiceMapMatchingDisplayPreferencesTest {

    @Mock UserRepository userRepository;
    @Mock UserAvatarRepository userAvatarRepository;
    @Mock SecurePasswordUtils securePasswordUtils;
    @Mock TimelinePreferencesUpdater preferencesUpdater;
    @Mock Event<TimelinePreferencesUpdatedEvent> preferencesUpdatedEvent;
    @Mock Event<TravelClassificationUpdatedEvent> classificationUpdatedEvent;
    @Mock Event<TimelineStructureUpdatedEvent> structureUpdatedEvent;
    @Mock AuthConfigurationService authConfigurationService;
    @Mock AsyncTimelineGenerationService asyncTimelineGenerationService;
    @Mock DefaultNotificationTemplateService defaultNotificationTemplateService;
    @Mock SystemSettingsService systemSettingsService;
    @Mock AdminBootstrapService adminBootstrapService;
    @Mock MapMatchingConfiguration mapMatchingConfiguration;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                userAvatarRepository,
                securePasswordUtils,
                preferencesUpdater,
                preferencesUpdatedEvent,
                classificationUpdatedEvent,
                structureUpdatedEvent,
                authConfigurationService,
                asyncTimelineGenerationService,
                defaultNotificationTemplateService,
                systemSettingsService,
                adminBootstrapService,
                mapMatchingConfiguration);
    }

    private UserEntity userWith(UUID userId, TimelineDisplayPreferences stored) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTimelineDisplayPreferences(stored);
        when(userRepository.findById(userId)).thenReturn(user);
        return user;
    }

    @Test
    void timelineDisplayPreferencesExposeMapMatchingAsDisabledWhenUnavailable() {
        UUID userId = UUID.randomUUID();
        userWith(userId, TimelineDisplayPreferences.builder().mapMatchingEnabled(true).build());
        when(mapMatchingConfiguration.isAvailable()).thenReturn(false);

        TimelineDisplaySettings settings = userService.getTimelineDisplaySettings(userId);

        assertFalse(settings.getCapabilities().isMapMatchingAvailable());
        assertFalse(settings.getPreferences().getMapMatchingEnabled());
    }

    @Test
    void updateRejectsMapMatchingOptInWhenUnavailable() {
        UUID userId = UUID.randomUUID();
        userWith(userId, new TimelineDisplayPreferences());
        when(mapMatchingConfiguration.isAvailable()).thenReturn(false);

        TimelineDisplayPreferences patch = TimelineDisplayPreferences.builder()
                .mapMatchingEnabled(true)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateTimelineDisplayPreferences(userId, patch));
    }

    @Test
    void updateAllowsDisablingMapMatchingWhenUnavailable() {
        UUID userId = UUID.randomUUID();
        UserEntity user = userWith(userId, TimelineDisplayPreferences.builder().mapMatchingEnabled(true).build());

        userService.updateTimelineDisplayPreferences(userId, TimelineDisplayPreferences.builder()
                .mapMatchingEnabled(false)
                .build());

        assertFalse(user.getTimelineDisplayPreferences().getMapMatchingEnabled());
    }

    @Test
    void updateAllowsMapMatchingOptInWhenAvailable() {
        UUID userId = UUID.randomUUID();
        UserEntity user = userWith(userId, new TimelineDisplayPreferences());
        when(mapMatchingConfiguration.isAvailable()).thenReturn(true);

        userService.updateTimelineDisplayPreferences(userId, TimelineDisplayPreferences.builder()
                .mapMatchingEnabled(true)
                .build());

        assertTrue(user.getTimelineDisplayPreferences().getMapMatchingEnabled());
    }

    @Test
    void mapMatchingMovementTypeExclusionsDefaultToEmpty() {
        UUID userId = UUID.randomUUID();
        userWith(userId, new TimelineDisplayPreferences());

        assertEquals(List.of(), userService.getTimelineDisplaySettings(userId)
                .getPreferences().getMapMatchingExcludedMovementTypes());
    }

    @Test
    void ordersAndDeduplicatesMapMatchingMovementTypeExclusions() {
        UUID userId = UUID.randomUUID();
        UserEntity user = userWith(userId, new TimelineDisplayPreferences());

        userService.updateTimelineDisplayPreferences(userId, TimelineDisplayPreferences.builder()
                .mapMatchingExcludedMovementTypes(List.of(TripType.CAR, TripType.WALK, TripType.CAR))
                .build());

        assertEquals(List.of(TripType.WALK, TripType.CAR),
                user.getTimelineDisplayPreferences().getMapMatchingExcludedMovementTypes());
    }

    @Test
    void nullLeavesMapMatchingMovementTypeExclusionsUnchangedAndEmptyClearsThem() {
        UUID userId = UUID.randomUUID();
        UserEntity user = userWith(userId, TimelineDisplayPreferences.builder()
                .mapMatchingExcludedMovementTypes(List.of(TripType.WALK))
                .build());

        userService.updateTimelineDisplayPreferences(userId, TimelineDisplayPreferences.builder().build());
        assertEquals(List.of(TripType.WALK), user.getTimelineDisplayPreferences().getMapMatchingExcludedMovementTypes());

        userService.updateTimelineDisplayPreferences(userId, TimelineDisplayPreferences.builder()
                .mapMatchingExcludedMovementTypes(List.of())
                .build());
        assertEquals(List.of(), user.getTimelineDisplayPreferences().getMapMatchingExcludedMovementTypes());
    }

    @Test
    void rejectsUnsupportedMapMatchingMovementTypeExclusions() {
        UUID userId = UUID.randomUUID();
        userWith(userId, new TimelineDisplayPreferences());

        assertThrows(IllegalArgumentException.class, () -> userService.updateTimelineDisplayPreferences(
                userId,
                TimelineDisplayPreferences.builder()
                        .mapMatchingExcludedMovementTypes(List.of(TripType.TRAIN))
                        .build()));
    }

    @Test
    void persists3dBuildingsDefaultAsADisplayPreference() {
        UUID userId = UUID.randomUUID();
        UserEntity user = userWith(userId, new TimelineDisplayPreferences());

        userService.updateTimelineDisplayPreferences(userId, TimelineDisplayPreferences.builder()
                .enable3dBuildingsByDefault(true)
                .build());

        assertTrue(user.getTimelineDisplayPreferences().getEnable3dBuildingsByDefault());
        assertTrue(userService.getTimelineDisplaySettings(userId).getPreferences().getEnable3dBuildingsByDefault());
    }

    @Test
    void updateLeavesUnspecifiedPreferencesUnchanged() {
        UUID userId = UUID.randomUUID();
        UserEntity user = userWith(userId, TimelineDisplayPreferences.builder()
                .pathMaxPoints(250)
                .defaultPathColor("#112233")
                .build());

        userService.updateTimelineDisplayPreferences(userId, TimelineDisplayPreferences.builder()
                .activePathColor(" #aabbcc ")
                .build());

        TimelineDisplayPreferences stored = user.getTimelineDisplayPreferences();
        assertEquals(250, stored.getPathMaxPoints());
        assertEquals("#112233", stored.getDefaultPathColor());
        assertEquals("#aabbcc", stored.getActivePathColor());
    }

    @Test
    void emptyStringResetsATextPreferenceToItsDefault() {
        UUID userId = UUID.randomUUID();
        UserEntity user = userWith(userId, TimelineDisplayPreferences.builder()
                .defaultPathColor("#112233")
                .customMapTileUrl("https://tiles.example.com/{z}/{x}/{y}.png")
                .build());

        userService.updateTimelineDisplayPreferences(userId, TimelineDisplayPreferences.builder()
                .defaultPathColor("")
                .customMapTileUrl("  ")
                .build());

        assertNull(user.getTimelineDisplayPreferences().getDefaultPathColor());
        assertNull(user.getTimelineDisplayPreferences().getCustomMapTileUrl());
    }

    @Test
    void storesOnlyValuesTheUserSetAndResolvesDefaultsOnRead() {
        UUID userId = UUID.randomUUID();
        UserEntity user = userWith(userId, new TimelineDisplayPreferences());

        userService.updateTimelineDisplayPreferences(userId, TimelineDisplayPreferences.builder()
                .pathSimplificationTolerance(30.0)
                .build());

        assertNull(user.getTimelineDisplayPreferences().getPathSimplificationEnabled());
        TimelineDisplayPreferences effective = userService.getTimelineDisplaySettings(userId).getPreferences();
        assertEquals(30.0, effective.getPathSimplificationTolerance());
        assertTrue(effective.getPathSimplificationEnabled());
        assertEquals(MapRenderMode.VECTOR, effective.getMapRenderMode());
    }

    @Test
    void timelineDisplaySettingsExposePanoramaxConfiguration() {
        UUID userId = UUID.randomUUID();
        userWith(userId, new TimelineDisplayPreferences());
        when(systemSettingsService.getBoolean("panoramax.enabled")).thenReturn(true);
        when(systemSettingsService.getString("panoramax.endpoint")).thenReturn("https://api.panoramax.xyz/api");

        TimelineDisplayCapabilities capabilities = userService.getTimelineDisplaySettings(userId).getCapabilities();

        assertTrue(capabilities.isPanoramaxAvailable());
        assertTrue(capabilities.getPanoramaxEndpoint().contains("panoramax.xyz"));
    }
}
