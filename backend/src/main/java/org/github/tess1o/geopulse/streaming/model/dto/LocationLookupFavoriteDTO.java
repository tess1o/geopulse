package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A saved favorite that contains or is close to the queried map point. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationLookupFavoriteDTO {
    private Long id;
    private String name;
    private String type;
    private String relation;
    private Double distanceMeters;
}
