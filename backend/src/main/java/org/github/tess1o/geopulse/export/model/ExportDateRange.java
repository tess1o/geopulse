package org.github.tess1o.geopulse.export.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportDateRange {
    private Instant startDate;

    private Instant endDate;
}
