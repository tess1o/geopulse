package org.github.tess1o.geopulse.geofencing.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeofenceEventPageDto {
    private List<GeofenceEventDto> items;
    private long totalCount;
    private int page;
    private int pageSize;
}
