package org.github.tess1o.geopulse.user.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigFieldRegistry;
import org.github.tess1o.geopulse.user.mapper.TimelinePreferencesMapper;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UpdateTimelinePreferencesRequest;

/**
 * Enhanced TimelinePreferencesUpdater using MapStruct for conversions.
 * This eliminates code duplication and provides type-safe mapping.
 * <p>
 * Benefits of MapStruct approach:
 * 1. Eliminates manual conversion code
 * 2. Compile-time safety (no runtime mapping errors)
 * 3. High performance (no reflection at runtime)
 * 4. Automatic null handling
 * 5. Easy to maintain and extend
 */
@ApplicationScoped
public class TimelinePreferencesUpdater {

    @Inject
    TimelineConfigFieldRegistry fieldRegistry;

    @Inject
    TimelinePreferencesMapper mapper;

    /**
     * Update timeline preferences from request using MapStruct + registry pattern.
     */
    public void updatePreferences(TimelinePreferences preferences, UpdateTimelinePreferencesRequest request) {
        TimelineConfig prefsAsConfig = mapper.preferencesToConfig(preferences);
        TimelineConfig requestAsConfig = mapper.requestToConfig(request);

        fieldRegistry.getRegistry().updateConfiguration(prefsAsConfig, requestAsConfig);
        mapper.updatePreferencesFromConfig(prefsAsConfig, preferences);
    }
}