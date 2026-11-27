package org.github.tess1o.geopulse.coverage;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.coverage.model.CoverageCells;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.coverage.model.CoverageSettingsRequest;
import org.github.tess1o.geopulse.coverage.model.CoverageSummary;
import org.github.tess1o.geopulse.coverage.model.CoverageStatus;
import org.github.tess1o.geopulse.coverage.model.CoverageStatusSnapshot;

@RegisterForReflection(targets = {
        CoverageCells.class,
        CoverageProcessingCursor.class,
        CoverageSettingsRequest.class,
        CoverageSummary.class,
        CoverageStatus.class,
        CoverageStatusSnapshot.class
})
public class CoverageNativeConfig {
}
