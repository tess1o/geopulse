package org.github.tess1o.geopulse.streaming.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.github.tess1o.geopulse.user.mapper.TimelinePreferencesMapper;
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

    //TODO: Why do we use repository here? Use service!
    private final UserRepository userRepository;
    private final GlobalTimelineConfig globalDefaults;
    private final TimelineConfigFieldRegistry fieldRegistry;
    private final TimelinePreferencesMapper timelinePreferencesMapper;

    @Inject
    public TimelineConfigurationProvider(UserRepository userRepository,
                                         GlobalTimelineConfig globalDefaults,
                                         TimelineConfigFieldRegistry fieldRegistry,
                                         TimelinePreferencesMapper timelinePreferencesMapper) {
        this.userRepository = userRepository;
        this.globalDefaults = globalDefaults;
        this.fieldRegistry = fieldRegistry;
        this.timelinePreferencesMapper = timelinePreferencesMapper;
    }

    /**
     * Get the effective timeline configuration for a user.
     * Merges global defaults with user-specific preferences.
     *
     * @param userId the user identifier
     * @return effective timeline configuration
     */
    public TimelineConfig getConfigurationForUser(UUID userId) {
        log.debug("Getting timeline configuration for user {}", userId);

        UserEntity user = userRepository.findById(userId);

        if (user == null) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        TimelineConfig baseConfig = globalDefaults.getDefaultTimelineConfig();

        if (user.timelinePreferences != null) {
            TimelineConfig userPrefsAsConfig = convertPreferencesToConfig(user.timelinePreferences);
            fieldRegistry.getRegistry().mergeUserPreferences(baseConfig, userPrefsAsConfig);
        }

        return baseConfig;
    }

    /**
     * Get the global default timeline configuration.
     *
     * @return global default configuration
     */
    public TimelineConfig getGlobalDefaults() {
        return globalDefaults.getDefaultTimelineConfig();
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