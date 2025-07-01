package org.github.tess1o.geopulse.favorites.model;

import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Geometry;

@Entity
@Table(name = "favorite_locations")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FavoritesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private UserEntity user;

    @Column(columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;

    private String name;

    @Enumerated(EnumType.STRING)
    private FavoriteLocationType type; // e.g., POINT or AREA

    /**
     * City name extracted from geocoding when the favorite was created
     */
    @Column(name = "city", length = 200)
    private String city;

    /**
     * Country name extracted from geocoding when the favorite was created
     */
    @Column(name = "country", length = 100)
    private String country;
}
