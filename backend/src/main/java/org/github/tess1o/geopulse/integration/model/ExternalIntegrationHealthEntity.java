package org.github.tess1o.geopulse.integration.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "external_integration_health",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_external_integration_health_type_provider",
                columnNames = {"integration_type", "provider_key"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalIntegrationHealthEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "integration_type", nullable = false, length = 40)
    private ExternalIntegrationType integrationType;

    @Column(name = "provider_key", nullable = false, length = 80)
    private String providerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private ExternalIntegrationHealthStatus status = ExternalIntegrationHealthStatus.HEALTHY;

    @Column(name = "incident_started_at")
    private Instant incidentStartedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    @Column(name = "circuit_open_until")
    private Instant circuitOpenUntil;

    @Column(name = "next_probe_at")
    private Instant nextProbeAt;

    @Column(name = "failure_count", nullable = false)
    @Builder.Default
    private int failureCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
