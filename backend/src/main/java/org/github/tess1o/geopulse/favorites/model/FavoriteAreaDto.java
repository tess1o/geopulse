package org.github.tess1o.geopulse.favorites.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

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
    private transient PreparedGeometry preparedGeometry;

    /**
     * The polygon's envelope, kept because JTS's getEnvelopeInternal() returns a fresh copy per call.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.Getter(lombok.AccessLevel.NONE)
    @lombok.Setter(lombok.AccessLevel.NONE)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private transient Envelope bounds;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public PreparedGeometry preparedGeometry() {
        if (preparedGeometry == null) {
            preparedGeometry = PreparedGeometryFactory.prepare(
                    AreaGeometry.resolve(geometry, geometry == null ? northEastLat : null,
                            geometry == null ? northEastLon : null, geometry == null ? southWestLat : null,
                            geometry == null ? southWestLon : null));
        }
        return preparedGeometry;
    }

    /**
     * Whether (lon, lat) lies in the polygon's bounding box, edges included. A point the polygon
     * covers always does, so this is an allocation-free pre-check before {@code covers}.
     */
    public boolean boundsCover(double lon, double lat) {
        if (bounds == null) {
            bounds = preparedGeometry().getGeometry().getEnvelopeInternal();
        }
        return bounds.covers(lon, lat);
    }
}
