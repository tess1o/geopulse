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
public class FavoritePointDto {
    private long id;
    private double longitude;
    private double latitude;
    private String name;
    private UUID userId;
    private String type;
    private String city;
    private String country;
}
