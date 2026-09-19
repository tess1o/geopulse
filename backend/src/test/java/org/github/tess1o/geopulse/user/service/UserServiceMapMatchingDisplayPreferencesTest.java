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
import org.github.tess1o.geopulse.user.model.TimelineDisplayPreferences;
import org.github.tess1o.geopulse.user.model.UpdateTimelineDisplayPreferencesRequest;
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

    @Test
    void timelineDisplayPreferencesExposeMapMatchingAsDisabledWhenUnavailable() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTimelineDisplayMapMatchingEnabled(true);
        when(userRepository.findById(userId)).thenReturn(user);
        when(mapMatchingConfiguration.isAvailable()).thenReturn(false);

        TimelineDisplayPreferences preferences = userService.getTimelineDisplayPreferences(userId);

        assertFalse(preferences.getMapMatchingAvailable());
        assertFalse(preferences.getMapMatchingEnabled());
    }

    @Test
    void updateRejectsMapMatchingOptInWhenUnavailable() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(user);
        when(mapMatchingConfiguration.isAvailable()).thenReturn(false);

        UpdateTimelineDisplayPreferencesRequest request = UpdateTimelineDisplayPreferencesRequest.builder()
                .mapMatchingEnabled(true)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateTimelineDisplayPreferences(userId, request));
    }

    @Test
    void updateAllowsDisablingMapMatchingWhenUnavailable() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTimelineDisplayMapMatchingEnabled(true);
        when(userRepository.findById(userId)).thenReturn(user);

        UpdateTimelineDisplayPreferencesRequest request = UpdateTimelineDisplayPreferencesRequest.builder()
                .mapMatchingEnabled(false)
                .build();

        userService.updateTimelineDisplayPreferences(userId, request);

        assertFalse(user.getTimelineDisplayMapMatchingEnabled());
    }

    @Test
    void updateAllowsMapMatchingOptInWhenAvailable() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(user);
        when(mapMatchingConfiguration.isAvailable()).thenReturn(true);

        UpdateTimelineDisplayPreferencesRequest request = UpdateTimelineDisplayPreferencesRequest.builder()
                .mapMatchingEnabled(true)
                .build();

        userService.updateTimelineDisplayPreferences(userId, request);

        assertTrue(user.getTimelineDisplayMapMatchingEnabled());
    }

    @Test
    void mapMatchingMovementTypeExclusionsDefaultToEmpty() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTimelineDisplayMapMatchingExcludedMovementTypes(null);
        when(userRepository.findById(userId)).thenReturn(user);

        assertEquals(List.of(), userService.getTimelineDisplayPreferences(userId)
                .getMapMatchingExcludedMovementTypes());
    }

    @Test
    void normalizesAndDeduplicatesMapMatchingMovementTypeExclusions() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(user);

        userService.updateTimelineDisplayPreferences(userId, UpdateTimelineDisplayPreferencesRequest.builder()
                .mapMatchingExcludedMovementTypes(List.of(" car ", "walk", "CAR"))
                .build());

        assertEquals(List.of(TripType.WALK, TripType.CAR),
                user.getTimelineDisplayMapMatchingExcludedMovementTypes());
    }

    @Test
    void nullLeavesMapMatchingMovementTypeExclusionsUnchangedAndEmptyClearsThem() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTimelineDisplayMapMatchingExcludedMovementTypes(List.of(TripType.WALK));
        when(userRepository.findById(userId)).thenReturn(user);

        userService.updateTimelineDisplayPreferences(userId, UpdateTimelineDisplayPreferencesRequest.builder().build());
        assertEquals(List.of(TripType.WALK), user.getTimelineDisplayMapMatchingExcludedMovementTypes());

        userService.updateTimelineDisplayPreferences(userId, UpdateTimelineDisplayPreferencesRequest.builder()
                .mapMatchingExcludedMovementTypes(List.of())
                .build());
        assertEquals(List.of(), user.getTimelineDisplayMapMatchingExcludedMovementTypes());
    }

    @Test
    void rejectsUnsupportedMapMatchingMovementTypeExclusions() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(user);

        assertThrows(IllegalArgumentException.class, () -> userService.updateTimelineDisplayPreferences(
                userId,
                UpdateTimelineDisplayPreferencesRequest.builder()
                        .mapMatchingExcludedMovementTypes(List.of("TRAIN"))
                        .build()));
    }

    @Test
    void persists3dBuildingsDefaultAsADisplayPreference() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(user);

        userService.updateTimelineDisplayPreferences(userId, UpdateTimelineDisplayPreferencesRequest.builder()
                .enable3dBuildingsByDefault(true)
                .build());

        assertTrue(user.getTimelineDisplayEnable3dBuildingsByDefault());
        assertTrue(userService.getTimelineDisplayPreferences(userId).getEnable3dBuildingsByDefault());
    }

    @Test
    void timelineDisplayPreferencesExposePanoramaxConfiguration() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(user);
        when(systemSettingsService.getBoolean("panoramax.enabled")).thenReturn(true);
        when(systemSettingsService.getString("panoramax.endpoint")).thenReturn("https://api.panoramax.xyz/api");

        TimelineDisplayPreferences preferences = userService.getTimelineDisplayPreferences(userId);

        assertTrue(preferences.getPanoramaxAvailable());
        assertTrue(preferences.getPanoramaxEndpoint().contains("panoramax.xyz"));
    }
}
