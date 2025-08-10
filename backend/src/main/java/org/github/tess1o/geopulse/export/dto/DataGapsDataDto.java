package org.github.tess1o.geopulse.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DataGapsDataDto {
    private String dataType;
    private Instant exportDate;
    private Instant startDate;
    private Instant endDate;
    private List<TimelineDataDto.DataGapDto> dataGaps;
}