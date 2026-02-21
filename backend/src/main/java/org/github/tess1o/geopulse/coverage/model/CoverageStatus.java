package org.github.tess1o.geopulse.coverage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageStatus {
    private boolean userEnabled;
    private boolean processing;
    private boolean hasCells;
    private Instant lastProcessed;
    private Instant processingStartedAt;
}
