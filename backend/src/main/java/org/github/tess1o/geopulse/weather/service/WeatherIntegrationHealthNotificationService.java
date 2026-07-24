package org.github.tess1o.geopulse.weather.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.integration.event.ExternalIntegrationHealthEvent;
import org.github.tess1o.geopulse.integration.event.ExternalIntegrationHealthEventType;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;

import java.time.Instant;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class WeatherIntegrationHealthNotificationService {

    private static final String NOTIFICATION_TARGET_ROUTE = "/app/admin/dashboard";

    @Inject
    EntityManager entityManager;

    @Transactional
    public void onIntegrationHealthEvent(@Observes(during = TransactionPhase.AFTER_SUCCESS) ExternalIntegrationHealthEvent event) {
        if (event.integrationType() != ExternalIntegrationType.WEATHER
                || !WeatherConfigurationService.PROVIDER_OPEN_METEO.equals(event.providerKey())) {
            return;
        }

        if (event.eventType() == ExternalIntegrationHealthEventType.QUOTA_REACHED) {
            publishQuotaReached(event);
        } else if (event.eventType() == ExternalIntegrationHealthEventType.QUOTA_RESTORED) {
            publishQuotaRestored(event);
        }
    }

    private void publishQuotaReached(ExternalIntegrationHealthEvent event) {
        publishAdminWeatherQuotaNotification(
                NotificationType.WEATHER_QUOTA_REACHED,
                event,
                event.currentStatus(),
                event.currentStatus() == ExternalIntegrationHealthStatus.INTERNAL_QUOTA_EXCEEDED
                        ? "Weather internal quota reached"
                        : "Weather provider quota reached",
                event.currentStatus() == ExternalIntegrationHealthStatus.INTERNAL_QUOTA_EXCEEDED
                        ? "GeoPulse has reached the configured daily weather request limit."
                        : "Open-Meteo reported quota or rate-limit exhaustion.",
                Map.of(
                        "circuitOpenUntil", stringValue(event.circuitOpenUntil()),
                        "nextProbeAt", stringValue(event.nextProbeAt())
                )
        );
    }

    private void publishQuotaRestored(ExternalIntegrationHealthEvent event) {
        publishAdminWeatherQuotaNotification(
                NotificationType.WEATHER_QUOTA_RESTORED,
                event,
                event.previousStatus(),
                "Weather provider quota restored",
                "Weather collection has resumed for " + event.providerKey() + ".",
                Map.of("restoredAt", stringValue(event.occurredAt()))
        );
    }

    private void publishAdminWeatherQuotaNotification(NotificationType type,
                                                     ExternalIntegrationHealthEvent event,
                                                     ExternalIntegrationHealthStatus status,
                                                     String title,
                                                     String message,
                                                     Map<String, Object> extraMetadata) {
        String incidentKey = event.incidentStartedAt() == null ? "unknown" : event.incidentStartedAt().toString();
        String dedupePrefix = "integration-health:%s:%s:%s:%s"
                .formatted(type.name().toLowerCase(), event.integrationType().name(), event.providerKey(), incidentKey);
        String metadataSql = """
                jsonb_build_object(
                    'integrationType', :integrationType,
                    'providerKey', :providerKey,
                    'status', :status,
                    'incidentStartedAt', :incidentStartedAt,
                    'targetRoute', :targetRoute
                ) || CAST(:extraMetadata AS jsonb)
                """;

        int inserted = entityManager.createNativeQuery("""
                INSERT INTO user_notifications (
                    owner_user_id,
                    source,
                    type,
                    title,
                    message,
                    occurred_at,
                    object_ref,
                    metadata,
                    dedupe_key,
                    created_at
                )
                SELECT u.id,
                       :source,
                       :type,
                       :title,
                       :message,
                       NOW(),
                       :objectRef,
                       %s,
                       CONCAT(:dedupePrefix, ':admin:', u.id::TEXT),
                       NOW()
                FROM users u
                WHERE u.role = 'ADMIN'
                  AND u.is_active = TRUE
                ON CONFLICT (dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING
                """.formatted(metadataSql))
                .setParameter("source", NotificationSource.WEATHER.name())
                .setParameter("type", type.name())
                .setParameter("title", title)
                .setParameter("message", message)
                .setParameter("objectRef", event.integrationType().name() + ":" + event.providerKey())
                .setParameter("integrationType", event.integrationType().name())
                .setParameter("providerKey", event.providerKey())
                .setParameter("status", status.name())
                .setParameter("incidentStartedAt", incidentKey)
                .setParameter("targetRoute", NOTIFICATION_TARGET_ROUTE)
                .setParameter("extraMetadata", toJson(extraMetadata))
                .setParameter("dedupePrefix", dedupePrefix)
                .executeUpdate();

        log.info("Published {} weather health notifications for {}", inserted, type);
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escapeJson(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                builder.append("null");
            } else {
                builder.append('"').append(escapeJson(String.valueOf(value))).append('"');
            }
        }
        return builder.append('}').toString();
    }

    private String escapeJson(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String stringValue(Instant value) {
        return value == null ? "" : value.toString();
    }
}
