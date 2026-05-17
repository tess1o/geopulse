package org.github.tess1o.geopulse.geofencing.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.geofencing.client.AppriseClientResult;
import org.github.tess1o.geopulse.geofencing.client.AppriseHttpClient;
import org.github.tess1o.geopulse.geofencing.model.dto.AppriseTestRequest;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;

@ApplicationScoped
@Slf4j
public class AppriseNotificationService {

    public static final String KEY_ENABLED = "system.notifications.apprise.enabled";
    public static final String KEY_API_URL = "system.notifications.apprise.api-url";
    public static final String KEY_AUTH_TOKEN = "system.notifications.apprise.auth-token";
    public static final String KEY_TIMEOUT_MS = "system.notifications.apprise.timeout-ms";
    public static final String KEY_VERIFY_TLS = "system.notifications.apprise.verify-tls";

    private static final int DEFAULT_TIMEOUT_MS = 5000;

    private final SystemSettingsService settingsService;
    private final AppriseHttpClient appriseHttpClient;

    @Inject
    public AppriseNotificationService(SystemSettingsService settingsService,
                                      AppriseHttpClient appriseHttpClient) {
        this.settingsService = settingsService;
        this.appriseHttpClient = appriseHttpClient;
    }

    public boolean isEnabled() {
        return settingsService.getBoolean(KEY_ENABLED);
    }

    public boolean isConfigured() {
        return getApiUrl() != null && !getApiUrl().isBlank();
    }

    public boolean isEnabledAndConfigured() {
        return isEnabled() && isConfigured();
    }

    public AppriseClientResult testConnection(AppriseTestRequest request) {
        if (!isEnabled()) {
            return new AppriseClientResult(false, 0, "Apprise notifications are disabled");
        }

        String apiUrl = getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            return new AppriseClientResult(false, 0, "Apprise API URL is not configured");
        }

        if (!isVerifyTls()) {
            log.warn("Apprise TLS verification is disabled in settings");
        }

        String destination = request != null ? request.getDestination() : null;
        AppriseExternalRoutingMode routingMode = request != null && request.getExternalRoutingMode() != null
                ? request.getExternalRoutingMode()
                : AppriseExternalRoutingMode.URLS;

        if (routingMode == AppriseExternalRoutingMode.KEY_TAG) {
            String configKey = request != null ? safeTrim(request.getAppriseConfigKey()) : null;
            String tag = request != null ? safeTrim(request.getAppriseTag()) : null;
            if (configKey == null || configKey.isBlank()) {
                return new AppriseClientResult(false, 0, "Apprise config key is required for KEY_TAG mode");
            }
            String title = request == null || request.getTitle() == null || request.getTitle().isBlank()
                    ? "GeoPulse Apprise Test"
                    : request.getTitle();
            String body = request == null || request.getBody() == null || request.getBody().isBlank()
                    ? "Apprise test notification from GeoPulse"
                    : request.getBody();
            return appriseHttpClient.notifyByConfigKey(
                    apiUrl,
                    getAuthToken(),
                    getTimeoutMs(),
                    isVerifyTls(),
                    configKey,
                    tag,
                    title,
                    body
            );
        }

        if (destination != null && !destination.isBlank()) {
            String title = request.getTitle() == null || request.getTitle().isBlank()
                    ? "GeoPulse Apprise Test"
                    : request.getTitle();
            String body = request.getBody() == null || request.getBody().isBlank()
                    ? "Apprise test notification from GeoPulse"
                    : request.getBody();
            return appriseHttpClient.notify(apiUrl, getAuthToken(), getTimeoutMs(), isVerifyTls(), destination, title, body);
        }

        return appriseHttpClient.ping(apiUrl, getAuthToken(), getTimeoutMs(), isVerifyTls());
    }

    public AppriseClientResult sendToTemplate(NotificationTemplateEntity template, String title, String body) {
        if (template == null) {
            return new AppriseClientResult(false, 0, "No template configured");
        }
        log.info("Sending notification to Apprise template: mode={}, title={}, templateId={}",
                template.getExternalRoutingMode(),
                title,
                template.getId());
        AppriseExternalRoutingMode mode = template.getExternalRoutingMode() == null
                ? AppriseExternalRoutingMode.URLS
                : template.getExternalRoutingMode();

        if (mode == AppriseExternalRoutingMode.KEY_TAG) {
            return sendToConfigKey(template.getAppriseConfigKey(), template.getAppriseTag(), title, body);
        }
        return sendToDestination(template.getDestination(), title, body);
    }

    public AppriseClientResult sendToDestination(String destination, String title, String body) {
        if (!isEnabled()) {
            return new AppriseClientResult(false, 0, "Apprise notifications are disabled");
        }
        String apiUrl = getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            return new AppriseClientResult(false, 0, "Apprise API URL is not configured");
        }

        if (!isVerifyTls()) {
            log.warn("Apprise TLS verification is disabled in settings");
        }

        return appriseHttpClient.notify(apiUrl, getAuthToken(), getTimeoutMs(), isVerifyTls(), destination, title, body);
    }

    public AppriseClientResult sendToConfigKey(String configKey, String tag, String title, String body) {
        if (!isEnabled()) {
            return new AppriseClientResult(false, 0, "Apprise notifications are disabled");
        }
        String apiUrl = getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            return new AppriseClientResult(false, 0, "Apprise API URL is not configured");
        }

        if (!isVerifyTls()) {
            log.warn("Apprise TLS verification is disabled in settings");
        }

        String normalizedKey = safeTrim(configKey);
        if (normalizedKey == null || normalizedKey.isBlank()) {
            return new AppriseClientResult(false, 0, "Apprise config key is not configured");
        }

        log.info("Sending Apprise notification to Apprise API URL: {}, key = {}, tag = {}", apiUrl, configKey, tag);

        return appriseHttpClient.notifyByConfigKey(
                apiUrl,
                getAuthToken(),
                getTimeoutMs(),
                isVerifyTls(),
                normalizedKey,
                safeTrim(tag),
                title,
                body
        );
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String getApiUrl() {
        return settingsService.getString(KEY_API_URL);
    }

    private String getAuthToken() {
        return settingsService.getString(KEY_AUTH_TOKEN);
    }

    private int getTimeoutMs() {
        int timeout = settingsService.getInteger(KEY_TIMEOUT_MS);
        return timeout > 0 ? timeout : DEFAULT_TIMEOUT_MS;
    }

    private boolean isVerifyTls() {
        return settingsService.getBoolean(KEY_VERIFY_TLS);
    }
}
