package org.github.tess1o.geopulse.poi.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Records that a bounding box has been fetched from the provider within its TTL, so a
 * repeat lookup for the same area is a local read instead of another SPARQL round trip
 * (which takes several seconds upstream).
 *
 * <p>Deliberately holds no user id — it must not be possible to reconstruct who looked
 * at which part of the world from this table.
 */
@Entity
@Table(name = "poi_area_query")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PoiAreaQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "area_hash", nullable = false, length = 64)
    private String areaHash;

    @Column(name = "poi_count", nullable = false)
    private Integer poiCount;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
