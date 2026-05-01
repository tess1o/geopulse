package org.github.tess1o.geopulse.geocoding.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

@Entity
@Table(name = "user_location_normalization_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLocationNormalizationRuleEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 32)
    private NormalizationRuleType ruleType;

    @Column(name = "source_country", length = 100)
    private String sourceCountry;

    @Column(name = "source_city", length = 200)
    private String sourceCity;

    @Column(name = "target_country", length = 100)
    private String targetCountry;

    @Column(name = "target_city", length = 200)
    private String targetCity;

    @Column(name = "source_country_norm", length = 100)
    private String sourceCountryNorm;

    @Column(name = "source_city_norm", length = 200)
    private String sourceCityNorm;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

