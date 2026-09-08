package org.github.tess1o.geopulse.notifications.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;
import org.github.tess1o.geopulse.notifications.model.dto.NotificationPreferencesDto;
import org.github.tess1o.geopulse.notifications.model.dto.UpdateNotificationPreferencesRequest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class NotificationPreferencesService {
    private static final int MIN_SILENCE_MINUTES = 1;
    private static final int MAX_SILENCE_MINUTES = 10_080;

    private final UserRepository userRepository;

    public NotificationPreferencesService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public NotificationPreferencesDto get(UUID userId) {
        UserEntity user = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toDto(normalize(user.getNotificationPreferences()));
    }

    @Transactional
    public NotificationPreferencesDto update(UUID userId, UpdateNotificationPreferencesRequest request) {
        if (request == null || request.getGpsSilenceMinutes() < MIN_SILENCE_MINUTES
                || request.getGpsSilenceMinutes() > MAX_SILENCE_MINUTES) {
            throw new IllegalArgumentException("GPS silence threshold must be between 1 and 10080 minutes");
        }
        UserEntity user = userRepository.findByIdOptional(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        NotificationPreferences previous = normalize(user.getNotificationPreferences());
        NotificationPreferences next = NotificationPreferences.builder()
                .gpsHealthEnabled(request.isGpsHealthEnabled())
                .gpsSilenceMinutes(request.getGpsSilenceMinutes())
                .gpsHealth(copyChannel(request.getGpsHealth()))
                .rewindEnabled(request.isRewindEnabled())
                .rewind(copyChannel(request.getRewind()))
                .whatsNewEnabled(request.isWhatsNewEnabled())
                .gpsMonitoringStartedAt(request.isGpsHealthEnabled() && !previous.isGpsHealthEnabled() ? Instant.now() : previous.getGpsMonitoringStartedAt())
                .build();
        validateChannel(next.getGpsHealth(), "GPS health");
        validateChannel(next.getRewind(), "Rewind");
        user.setNotificationPreferences(next);
        return toDto(next);
    }

    public NotificationPreferences getEntityPreferences(UserEntity user) {
        return normalize(user.getNotificationPreferences());
    }

    private NotificationPreferences normalize(NotificationPreferences preferences) {
        if (preferences == null) {
            return NotificationPreferences.builder().build();
        }
        if (preferences.getGpsHealth() == null) preferences.setGpsHealth(new NotificationPreferences.Channel());
        if (preferences.getRewind() == null) preferences.setRewind(new NotificationPreferences.Channel());
        if (preferences.getWhatsNewEnabled() == null) preferences.setWhatsNewEnabled(true);
        if (preferences.getGpsSilenceMinutes() < MIN_SILENCE_MINUTES) preferences.setGpsSilenceMinutes(60);
        return preferences;
    }

    private NotificationPreferences.Channel copyChannel(NotificationPreferences.Channel input) {
        if (input == null) return new NotificationPreferences.Channel();
        return NotificationPreferences.Channel.builder()
                .inAppEnabled(input.isInAppEnabled())
                .appriseEnabled(input.isAppriseEnabled())
                .routingMode(input.getRoutingMode() == null ? AppriseExternalRoutingMode.URLS : input.getRoutingMode())
                .destination(trim(input.getDestination()))
                .appriseConfigKey(trim(input.getAppriseConfigKey()))
                .appriseTag(trim(input.getAppriseTag()))
                .build();
    }

    private void validateChannel(NotificationPreferences.Channel channel, String label) {
        if (!channel.isAppriseEnabled()) return;
        boolean configured = channel.getRoutingMode() == AppriseExternalRoutingMode.KEY_TAG
                ? channel.getAppriseConfigKey() != null
                : channel.getDestination() != null;
        if (!configured) throw new IllegalArgumentException(label + " Apprise destination is required when external delivery is enabled");
    }

    private NotificationPreferencesDto toDto(NotificationPreferences preferences) {
        return new NotificationPreferencesDto(preferences.isGpsHealthEnabled(), preferences.getGpsSilenceMinutes(),
                preferences.getGpsHealth(), preferences.isRewindEnabled(), preferences.getRewind(), preferences.getWhatsNewEnabled());
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
