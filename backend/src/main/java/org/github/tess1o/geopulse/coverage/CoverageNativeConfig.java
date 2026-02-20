package org.github.tess1o.geopulse.coverage;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.coverage.model.CoverageCell;
import org.github.tess1o.geopulse.coverage.model.CoverageSummary;

@RegisterForReflection(targets = {
        CoverageCell.class,
        CoverageSummary.class,
})
public class CoverageNativeConfig {
}
