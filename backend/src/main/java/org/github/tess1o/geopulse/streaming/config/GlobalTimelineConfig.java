package org.github.tess1o.geopulse.streaming.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.shared.configuration.PropertyBasedConfigLoader;

@ApplicationScoped
public class GlobalTimelineConfig {

    @Inject
    TimelineConfigFieldRegistry fieldRegistry;

    public TimelineConfig getDefaultTimelineConfig() {
        TimelineConfig config = new TimelineConfig();
        return PropertyBasedConfigLoader.loadFromProperties(
            fieldRegistry.getRegistry(), 
            config
        );
    }
}
