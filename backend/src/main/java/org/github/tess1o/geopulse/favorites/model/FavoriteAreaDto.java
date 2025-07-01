package org.github.tess1o.geopulse.favorites.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FavoriteAreaDto {
    private long id;
    private String name;
    private UUID userId;
    private String type;
    private double northEastLat;
    private double northEastLon;
    private double southWestLat;
    private double southWestLon;
}
