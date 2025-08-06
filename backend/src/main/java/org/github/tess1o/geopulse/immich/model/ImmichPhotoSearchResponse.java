package org.github.tess1o.geopulse.immich.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImmichPhotoSearchResponse {
    private List<ImmichPhotoDto> photos;
    private Integer totalCount;
}