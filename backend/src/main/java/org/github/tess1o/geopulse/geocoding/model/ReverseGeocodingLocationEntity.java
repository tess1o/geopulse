package org.github.tess1o.geopulse.geocoding.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.proxy.HibernateProxy;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;
import java.util.Objects;

/**
 * Ultra-simplified reverse geocoding location entity.
 * Stores only the essential information needed for display to end users.
 */
@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Table(name = "reverse_geocoding_location")
public class ReverseGeocodingLocationEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    /**
     * The original request coordinates (what was asked for)
     */
    @Column(name = "request_coordinates", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point requestCoordinates;

    /**
     * The actual coordinates returned by the provider (might be slightly different)
     */
    @Column(name = "result_coordinates", columnDefinition = "geometry(Point,4326)")
    private Point resultCoordinates;

    /**
     * Bounding box for the location (optional)
     */
    @Column(name = "bounding_box", columnDefinition = "geometry(Polygon, 4326)")
    private Polygon boundingBox;

    /**
     * Formatted display name for end users (e.g., "Empire State Building (350 5th Ave)")
     * This is the final result that gets displayed, calculated by the formatter at save time.
     */
    @Column(name = "display_name", nullable = false, length = 1000)
    private String displayName;

    /**
     * Source provider that generated this result
     */
    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    /**
     * When this entry was created
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * When this entry was last accessed (for LRU eviction)
     */
    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    /**
     * City name extracted from the geocoding result
     */
    @Column(name = "city", length = 200)
    private String city;

    /**
     * Country name extracted from the geocoding result
     */
    @Column(name = "country", length = 100)
    private String country;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastAccessedAt = Instant.now();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) {
            return false;
        }
        ReverseGeocodingLocationEntity that = (ReverseGeocodingLocationEntity) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ?
                ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() :
                getClass().hashCode();
    }
}