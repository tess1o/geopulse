package org.github.tess1o.geopulse.geocoding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DistinctValuesDto {

    private List<String> cities;
    private List<String> countries;
}
