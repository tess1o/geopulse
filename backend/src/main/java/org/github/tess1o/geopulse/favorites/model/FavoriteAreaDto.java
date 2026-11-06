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
    private String city;
    private String country;
    private AreaGeometry geometry;
    private Double latitude;
    private Double longitude;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter(lombok.AccessLevel.NONE)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private transient org.locationtech.jts.geom.prep.PreparedGeometry preparedGeometry;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public org.locationtech.jts.geom.prep.PreparedGeometry preparedGeometry() {
        if (preparedGeometry == null) {
            preparedGeometry = org.locationtech.jts.geom.prep.PreparedGeometryFactory.prepare(
                    AreaGeometry.resolve(geometry, geometry == null ? northEastLat : null,
                            geometry == null ? northEastLon : null, geometry == null ? southWestLat : null,
                            geometry == null ? southWestLon : null));
        }
        return preparedGeometry;
    }
}
