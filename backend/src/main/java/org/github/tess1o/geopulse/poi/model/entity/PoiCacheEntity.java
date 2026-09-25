package org.github.tess1o.geopulse.poi.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A place worth visiting, cached from an external provider (Wikidata).
 *
 * <p>Global rather than user-scoped on purpose: a place in Paris is the same fact for
 * every user, so sharing it keeps outbound provider traffic down.
 */
@Entity
@Table(name = "poi_cache")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PoiCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "external_id", nullable = false, length = 80)
    private String externalId;

    @Column(name = "name", nullable = false, length = 400)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "image_file", length = 400)
    private String imageFile;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
