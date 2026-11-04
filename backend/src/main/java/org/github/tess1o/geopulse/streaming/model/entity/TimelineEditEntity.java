package org.github.tess1o.geopulse.streaming.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.shared.persistence.JacksonJsonMutabilityPlan;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Mutability;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;

@Entity
@Table(name = "timeline_edits")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineEditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id")
    private java.util.UUID groupId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private UserEntity user;

    @Column(nullable = false)
    private long seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TimelineEditKind kind;

    @Column(name = "anchor_start", nullable = false)
    private Instant anchorStart;
    @Column(name = "anchor_end", nullable = false)
    private Instant anchorEnd;
    @Column(name = "affected_from", nullable = false)
    private Instant affectedFrom;
    @Column(name = "affected_to", nullable = false)
    private Instant affectedTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Mutability(JacksonJsonMutabilityPlan.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private LinkedHashMap<String, Object> payload = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Mutability(JacksonJsonMutabilityPlan.class)
    @Column(name = "before_values", columnDefinition = "jsonb")
    private LinkedHashMap<String, Object> beforeValues;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_favorite_id")
    @ToString.Exclude
    private FavoritesEntity placeFavorite;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_geocoding_id")
    @ToString.Exclude
    private ReverseGeocodingLocationEntity placeGeocoding;
    @Column(name = "place_latitude")
    private Double placeLatitude;
    @Column(name = "place_longitude")
    private Double placeLongitude;
    @Column(name = "place_name", length = 500)
    private String placeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TimelineEditStatus status = TimelineEditStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_by")
    @ToString.Exclude
    private TimelineEditEntity supersededBy;

    @OneToOne(mappedBy = "edit", fetch = FetchType.LAZY)
    @ToString.Exclude
    private TimelineEditApplicationEntity application;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void createTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
