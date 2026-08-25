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

import java.util.UUID;

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
}
