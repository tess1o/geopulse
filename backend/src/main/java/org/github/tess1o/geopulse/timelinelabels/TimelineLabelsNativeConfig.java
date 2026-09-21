package org.github.tess1o.geopulse.timelinelabels;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.timelinelabels.model.dto.*;
import org.github.tess1o.geopulse.timelinelabels.model.entity.TimelineLabelEntity;

@RegisterForReflection(targets = {
        // Entity
        TimelineLabelEntity.class,

        // DTOs
        CreateTimelineLabelDto.class,
        UpdateTimelineLabelDto.class,
        TimelineLabelDto.class,
})
public class TimelineLabelsNativeConfig {
}
