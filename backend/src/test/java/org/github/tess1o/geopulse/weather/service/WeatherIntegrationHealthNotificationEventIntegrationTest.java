package org.github.tess1o.geopulse.weather.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.integration.event.ExternalIntegrationHealthEvent;
import org.github.tess1o.geopulse.integration.event.ExternalIntegrationHealthEventType;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class WeatherIntegrationHealthNotificationEventIntegrationTest {

    @Inject
    Event<ExternalIntegrationHealthEvent> healthEvent;

    @Inject
    EntityManager entityManager;

    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("DELETE FROM user_notifications").executeUpdate();

            UserEntity admin = UserEntity.builder()
                    .email("weather-health-admin-" + UUID.randomUUID() + "@example.com")
                    .passwordHash("test")
                    .fullName("Weather Health Admin")
                    .createdAt(Instant.now())
                    .isActive(true)
                    .role(Role.ADMIN)
                    .timezone("UTC")
                    .timelineStatus(TimelineStatus.IDLE)
                    .build();
            entityManager.persist(admin);
            entityManager.flush();
            adminUserId = admin.getId();
        });
    }

    @Test
    void quotaEventAfterCommitCreatesAdminNotification() {
        Instant occurredAt = Instant.now();
        ExternalIntegrationHealthEvent event = new ExternalIntegrationHealthEvent(
                ExternalIntegrationHealthEventType.QUOTA_REACHED,
                ExternalIntegrationType.WEATHER,
                WeatherConfigurationService.PROVIDER_OPEN_METEO,
                ExternalIntegrationHealthStatus.HEALTHY,
                ExternalIntegrationHealthStatus.INTERNAL_QUOTA_EXCEEDED,
                occurredAt,
                occurredAt,
                "INTERNAL_QUOTA",
                "Integration test quota reached",
                occurredAt.plusSeconds(3600),
                occurredAt.plusSeconds(3600)
        );

        QuarkusTransaction.requiringNew().run(() -> healthEvent.fire(event));

        long notifications = QuarkusTransaction.requiringNew().call(() ->
                ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM user_notifications
                        WHERE owner_user_id = ?1
                          AND type = 'WEATHER_QUOTA_REACHED'
                        """)
                        .setParameter(1, adminUserId)
                        .getSingleResult()).longValue());

        assertThat(notifications).isOne();
    }
}
