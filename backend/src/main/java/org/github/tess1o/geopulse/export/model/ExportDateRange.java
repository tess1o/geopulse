package org.github.tess1o.geopulse.export.model;

import lombok.Data;

import java.time.Instant;

@Data
public class ExportDateRange {
    private Instant startDate;
    private Instant endDate;
}