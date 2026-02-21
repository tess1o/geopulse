package org.github.tess1o.geopulse.coverage;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.coverage.model.CoverageCell;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingState;
import org.github.tess1o.geopulse.coverage.model.CoverageSettingsRequest;
import org.github.tess1o.geopulse.coverage.model.CoverageSummary;
import org.github.tess1o.geopulse.coverage.model.CoverageStatus;

@RegisterForReflection(targets = {
        CoverageCell.class,
        CoverageProcessingState.class,
        CoverageSettingsRequest.class,
        CoverageSummary.class,
        CoverageStatus.class,
})
public class CoverageNativeConfig {
}
