package org.github.tess1o.geopulse.periods.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreatePeriodTagResponseDto {
    private PeriodTagDto periodTag;
    private boolean hasOverlap;
    private List<PeriodTagDto> overlappingTags;
}
