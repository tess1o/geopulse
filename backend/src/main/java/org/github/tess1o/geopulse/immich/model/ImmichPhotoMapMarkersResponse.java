package org.github.tess1o.geopulse.immich.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImmichPhotoMapMarkersResponse {
    private List<ImmichPhotoMapMarkerDto> markers;
    private Integer totalPhotos;
    private Integer geotaggedPhotos;
}
