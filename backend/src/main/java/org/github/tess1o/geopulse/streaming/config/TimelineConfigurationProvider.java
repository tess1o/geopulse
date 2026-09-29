package org.github.tess1o.geopulse.streaming.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.github.tess1o.geopulse.user.mapper.TimelinePreferencesMapper;
import org.github.tess1o.geopulse.user.model.TimelineDisplayPreferences;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.UUID;

/**
 * Service responsible for providing timeline configuration.
 * Handles merging of global defaults with user-specific preferences
 * while maintaining clean separation between user domain and timeline domain.
 */
@ApplicationScoped
@Slf4j
public class TimelineConfigurationProvider {

    private final UserRepository userRepository;
    private final GlobalTimelineConfig globalDefaults;
    private final TimelineConfigFieldRegistry fieldRegistry;
    private final TimelinePreferencesMapper timelinePreferencesMapper;
    private final EntityManager entityManager;

    @Inject
    public TimelineConfigurationProvider(UserRepository userRepository,
                                         GlobalTimelineConfig globalDefaults,
                                         TimelineConfigFieldRegistry fieldRegistry,
                                         TimelinePreferencesMapper timelinePreferencesMapper,
                                         EntityManager entityManager) {
        this.userRepository = userRepository;
        this.globalDefaults = globalDefaults;
        this.fieldRegistry = fieldRegistry;
        this.timelinePreferencesMapper = timelinePreferencesMapper;
        this.entityManager = entityManager;
    }

    /**
     * Get the effective timeline configuration for a user.
     * Merges global defaults with user-specific preferences.
     *
     * NOTE: Path simplification settings are read from the timeline_display_preferences
     * JSONB instead of timeline_preferences, as they are display-only settings that
     * don't affect timeline generation.
     *
     * @param userId the user identifier
     * @return effective timeline configuration
     */
    public TimelineConfig getConfigurationForUser(UUID userId) {
        UserEntity user = userRepository.findById(userId);

        if (user == null) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        TimelineConfig baseConfig = globalDefaults.getDefaultTimelineConfig();

        if (user.timelinePreferences != null) {
            TimelineConfig userPrefsAsConfig = convertPreferencesToConfig(user.timelinePreferences);
            fieldRegistry.getRegistry().mergeUserPreferences(baseConfig, userPrefsAsConfig);
        }

        // Override path simplification settings from the display preferences; unset ones keep the global default
        TimelineDisplayPreferences display = user.getTimelineDisplayPreferences();
        if (display.getPathSimplificationEnabled() != null) {
            baseConfig.setPathSimplificationEnabled(display.getPathSimplificationEnabled());
        }
        if (display.getPathSimplificationTolerance() != null) {
            baseConfig.setPathSimplificationTolerance(display.getPathSimplificationTolerance());
        }
        if (display.getPathMaxPoints() != null) {
            baseConfig.setPathMaxPoints(display.getPathMaxPoints());
        }
        if (display.getPathAdaptiveSimplification() != null) {
            baseConfig.setPathAdaptiveSimplification(display.getPathAdaptiveSimplification());
        }

        return baseConfig;
    }

    /**
     * Reads only the effective boat-enabled flag without materializing UserEntity.
     * This keeps GPS point save flows from initializing lazy user associations.
     */
    public boolean isBoatEnabledForUser(UUID userId) {
        try {
            Object result = entityManager.createNativeQuery("""
                            SELECT (timeline_preferences ->> 'boatEnabled')::boolean
                            FROM users
                            WHERE id = :userId
                            """)
                    .setParameter("userId", userId)
                    .getSingleResult();

            if (result instanceof Boolean boatEnabled) {
                return boatEnabled;
            }

            return Boolean.TRUE.equals(globalDefaults.getDefaultTimelineConfig().getBoatEnabled());
        } catch (NoResultException e) {
            throw new UserNotFoundException("User not found: " + userId, e);
        }
    }

    /**
     * Convert timeline preferences to configuration format for merging.
     * This maintains module boundaries while enabling configuration reuse.
     *
     * @param preferences user timeline preferences
     * @return preferences converted to configuration format
     */
    private TimelineConfig convertPreferencesToConfig(TimelinePreferences preferences) {
        return timelinePreferencesMapper.preferencesToConfig(preferences);
    }
}
