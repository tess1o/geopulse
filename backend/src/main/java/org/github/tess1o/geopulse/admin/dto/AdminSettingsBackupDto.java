package org.github.tess1o.geopulse.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.admin.model.ValueType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSettingsBackupDto {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String BACKUP_SCOPE = "admin-managed-portable-settings";
    public static final String EXCLUDED_CONFIGURATION_SUMMARY =
            "Deployment/runtime infrastructure such as database, JWT keys, cookie/CORS, local filesystem paths, encryption key locations, metrics, warmup, and scheduler tuning is not included.";

    private int schemaVersion;
    private String scope;
    private String excludedConfigurationSummary;
    private Instant exportedAt;
    private List<SettingBackupDto> settings;
    private List<OidcProviderBackupDto> oidcProviders;
    private List<CustomGeocodingProviderBackupDto> customGeocodingProviders;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettingBackupDto {
        private String key;
        private String category;
        private ValueType valueType;
        private String value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OidcProviderBackupDto {
        private String name;
        private String displayName;
        private boolean enabled;
        private String clientId;
        private String clientSecret;
        private String discoveryUrl;
        private String icon;
        private String scopes;
        private String authorizationEndpoint;
        private String tokenEndpoint;
        private String userinfoEndpoint;
        private String jwksUri;
        private String issuer;
        private Instant metadataCachedAt;
        private boolean metadataValid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomGeocodingProviderBackupDto {
        private String name;
        private String displayName;
        private String type;
        private String url;
        private Boolean enabled;
        private String language;
        private Map<String, String> headers;
        private Integer delayMs;
    }
}
