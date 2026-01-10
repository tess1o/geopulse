package org.github.tess1o.geopulse.periods;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.periods.model.dto.*;
import org.github.tess1o.geopulse.periods.model.entity.PeriodTagEntity;

@RegisterForReflection(targets = {
        // Entity
        PeriodTagEntity.class,

        // DTOs
        CreatePeriodTagDto.class,
        UpdatePeriodTagDto.class,
        PeriodTagDto.class,
        CreatePeriodTagResponseDto.class,
})
public class PeriodsNativeConfig {
}
