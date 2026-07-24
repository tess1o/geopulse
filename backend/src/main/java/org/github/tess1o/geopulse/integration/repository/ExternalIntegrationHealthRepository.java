package org.github.tess1o.geopulse.integration.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthEntity;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class ExternalIntegrationHealthRepository implements PanacheRepository<ExternalIntegrationHealthEntity> {

    @Inject
    EntityManager entityManager;

    public Optional<ExternalIntegrationHealthEntity> findByIntegrationAndProvider(
            ExternalIntegrationType integrationType,
            String providerKey) {
        return find("integrationType = ?1 and providerKey = ?2", integrationType, providerKey)
                .firstResultOptional();
    }

    public ExternalIntegrationHealthEntity getOrCreate(ExternalIntegrationType integrationType, String providerKey) {
        return findByIntegrationAndProvider(integrationType, providerKey)
                .orElseGet(() -> createIfMissing(integrationType, providerKey));
    }

    private ExternalIntegrationHealthEntity createIfMissing(ExternalIntegrationType integrationType, String providerKey) {
        Instant now = Instant.now();
        entityManager.createNativeQuery("""
                INSERT INTO external_integration_health (
                    integration_type,
                    provider_key,
                    status,
                    last_success_at,
                    created_at,
                    updated_at
                )
                VALUES (?1, ?2, ?3, ?4, ?4, ?4)
                ON CONFLICT ON CONSTRAINT uq_external_integration_health_type_provider DO NOTHING
                """)
                .setParameter(1, integrationType.name())
                .setParameter(2, providerKey)
                .setParameter(3, ExternalIntegrationHealthStatus.HEALTHY.name())
                .setParameter(4, now)
                .executeUpdate();
        return findByIntegrationAndProvider(integrationType, providerKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to create external integration health row for "
                                + integrationType.name() + "/" + providerKey));
    }
}
