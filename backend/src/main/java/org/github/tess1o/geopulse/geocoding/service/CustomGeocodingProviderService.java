package org.github.tess1o.geopulse.geocoding.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderRequest;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderResponse;
import org.github.tess1o.geopulse.geocoding.model.CustomGeocodingProviderEntity;
import org.github.tess1o.geopulse.geocoding.repository.CustomGeocodingProviderRepository;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class CustomGeocodingProviderService {

    private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() {};

    private final CustomGeocodingProviderRepository repository;
    private final AIEncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final SystemSettingsService settingsService;

    @Inject
    public CustomGeocodingProviderService(CustomGeocodingProviderRepository repository,
                                          AIEncryptionService encryptionService,
                                          ObjectMapper objectMapper,
                                          SystemSettingsService settingsService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
    }

    public List<CustomGeocodingProviderResponse> list() {
        return repository.listAllSorted().stream()
                .map(provider -> toResponse(provider, true))
                .toList();
    }

    public List<CustomGeocodingProviderEntity> listEnabledEntities() {
        return repository.listEnabled();
    }

    public Optional<CustomGeocodingProviderEntity> findEnabledByName(String name) {
        return repository.findByName(name)
                .filter(CustomGeocodingProviderEntity::isEnabled);
    }

    public Optional<CustomGeocodingProviderEntity> findByName(String name) {
        return repository.findByName(name);
    }

    @Transactional
    public CustomGeocodingProviderResponse create(CustomGeocodingProviderRequest request) {
        validateRequest(request, null);
        CustomGeocodingProviderEntity entity = new CustomGeocodingProviderEntity();
        apply(entity, request, true);
        repository.persist(entity);
        return toResponse(entity, true);
    }

    @Transactional
    public CustomGeocodingProviderResponse update(String name, CustomGeocodingProviderRequest request) {
        CustomGeocodingProviderEntity entity = repository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Custom geocoding provider not found: " + name));
        validateRequest(request, entity.getName());
        if (!Boolean.TRUE.equals(request.getEnabled())) {
            assertNotSelected(entity.getName(), "disable");
        }
        apply(entity, request, false);
        repository.persist(entity);
        return toResponse(entity, true);
    }

    @Transactional
    public void delete(String name) {
        CustomGeocodingProviderEntity entity = repository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Custom geocoding provider not found: " + name));
        assertNotSelected(entity.getName(), "delete");
        repository.delete(entity);
    }

    public Map<String, String> decryptHeaders(CustomGeocodingProviderEntity entity) {
        if (entity == null || entity.getHeadersJson() == null || entity.getHeadersJson().isBlank()) {
            return Map.of();
        }
        try {
            String json = encryptionService.decrypt(entity.getHeadersJson(), entity.getHeadersKeyId());
            Map<String, String> headers = objectMapper.readValue(json, HEADERS_TYPE);
            return sanitizeHeaders(headers);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt custom geocoding provider headers", e);
        }
    }

    public CustomGeocodingProviderResponse toResponse(CustomGeocodingProviderEntity entity, boolean maskHeaders) {
        Map<String, String> headers = decryptHeaders(entity);
        if (maskHeaders) {
            headers = mask(headers);
        }
        return CustomGeocodingProviderResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .displayName(entity.getDisplayName())
                .type(entity.getType())
                .url(entity.getUrl())
                .enabled(entity.isEnabled())
                .language(entity.getLanguage())
                .headers(headers)
                .delayMs(entity.getDelayMs())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void validateRequest(CustomGeocodingProviderRequest request, String existingName) {
        String normalizedName = normalizeName(request.getName());
        if (isBuiltInProvider(normalizedName)) {
            throw new IllegalArgumentException("Custom provider name cannot match a built-in provider: " + normalizedName);
        }
        if ((existingName == null || !existingName.equalsIgnoreCase(normalizedName))
                && repository.existsByName(normalizedName)) {
            throw new IllegalArgumentException("Custom geocoding provider already exists: " + normalizedName);
        }
        URI uri;
        try {
            uri = URI.create(request.getUrl().trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Provider URL is invalid");
        }
        if (uri.getScheme() == null || uri.getHost() == null
                || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Provider URL must be an http(s) URL");
        }
        if (request.getDelayMs() != null && request.getDelayMs() < 0) {
            throw new IllegalArgumentException("Delay must be zero or greater");
        }
    }

    private void apply(CustomGeocodingProviderEntity entity, CustomGeocodingProviderRequest request, boolean includeHeaders) {
        entity.setName(normalizeName(request.getName()));
        entity.setDisplayName(request.getDisplayName().trim());
        entity.setType(request.getType().trim().toLowerCase(Locale.ROOT));
        entity.setUrl(trimTrailingSlash(request.getUrl().trim()));
        entity.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        entity.setLanguage(normalizeOptional(request.getLanguage()));
        entity.setDelayMs(request.getDelayMs());

        if (includeHeaders || request.getHeaders() != null) {
            setEncryptedHeaders(entity, sanitizeHeaders(request.getHeaders()));
        }
    }

    private void setEncryptedHeaders(CustomGeocodingProviderEntity entity, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            entity.setHeadersJson(null);
            entity.setHeadersKeyId(null);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(headers);
            entity.setHeadersJson(encryptionService.encrypt(json));
            entity.setHeadersKeyId(encryptionService.getCurrentKeyId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt custom provider headers", e);
        }
    }

    private Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String trimmedKey = key.trim();
            String trimmedValue = value.trim();
            if (!trimmedKey.isEmpty() && !trimmedValue.isEmpty()) {
                sanitized.put(trimmedKey, trimmedValue);
            }
        });
        return sanitized;
    }

    private Map<String, String> mask(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> masked = new LinkedHashMap<>();
        headers.forEach((key, value) -> masked.put(key, "********"));
        return masked;
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String trimTrailingSlash(String url) {
        while (url.endsWith("/") && url.length() > 1) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private boolean isBuiltInProvider(String name) {
        return List.of("nominatim", "photon", "googlemaps", "mapbox", "geoapify", "chibigeo")
                .contains(name);
    }

    private void assertNotSelected(String providerName, String action) {
        String primary = settingsService.getString("geocoding.primary-provider");
        String fallback = settingsService.getString("geocoding.fallback-provider");
        if (providerName.equalsIgnoreCase(primary)) {
            throw new IllegalArgumentException("Cannot " + action + " custom provider '" + providerName + "' while it is the primary provider");
        }
        if (fallback != null && !fallback.isBlank() && providerName.equalsIgnoreCase(fallback)) {
            throw new IllegalArgumentException("Cannot " + action + " custom provider '" + providerName + "' while it is the fallback provider");
        }
    }
}
