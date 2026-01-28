package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.admin.dto.UpdateSettingRequest;
import org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class GeocodingValidationService {

    @Inject
    GeocodingProviderFactory geocodingProviderFactory;

    @Inject
    SystemSettingsService settingsService;

    /**
     * Validate a batch of geocoding configuration changes with full context awareness.
     * <p>
     * This method validates all changes together, considering pending values from the
     * same request batch. This prevents order-dependent validation failures.
     * <p>
     * Example: Enabling Google Maps with an API key in same request:
     * <pre>
     * [
     *   {"key": "geocoding.googlemaps.api-key", "value": "xyz123"},
     *   {"key": "geocoding.googlemaps.enabled", "value": "true"}
     * ]
     * </pre>
     * Validation sees both pending changes, so enabling the provider succeeds.
     *
     * @param changes List of setting changes to validate
     * @return Error message if validation fails, null if all validations pass
     */
    public String validateGeocodingChanges(List<UpdateSettingRequest> changes) {
        // Build context with all pending changes
        Map<String, String> pendingMap = changes.stream()
                .collect(Collectors.toMap(
                        UpdateSettingRequest::getKey,
                        UpdateSettingRequest::getValue
                ));

        ValidationContext context = new ValidationContext(pendingMap, settingsService);

        // Validate each change with full context
        for (UpdateSettingRequest change : changes) {
            String error = validateGeocodingChange(change.getKey(), change.getValue(), context);
            if (error != null) {
                return error;
            }
        }

        return null;
    }

    /**
     * Validate a single geocoding configuration change.
     * <p>
     * This method is used when validating changes without batch context.
     * For bulk updates, use {@link #validateGeocodingChanges(List)} instead.
     *
     * @param key Setting key
     * @param newValue New value
     * @return Error message if validation fails, null if validation passes
     */
    public String validateGeocodingChange(String key, String newValue) {
        return validateGeocodingChange(key, newValue, null);
    }

    /**
     * Validate a geocoding configuration change with optional context.
     *
     * @param key Setting key
     * @param newValue New value
     * @param context Optional validation context with pending changes
     * @return Error message if validation fails, null if validation passes
     */
    private String validateGeocodingChange(String key, String newValue, ValidationContext context) {
        // Validate provider enable/disable changes
        if (key.endsWith(".enabled")) {
            return validateProviderToggle(key, newValue, context);
        }

        // Validate primary provider changes
        if (key.equals("geocoding.primary-provider")) {
            return validatePrimaryProviderChange(newValue, context);
        }

        // Validate fallback provider changes
        if (key.equals("geocoding.fallback-provider")) {
            return validateFallbackProviderChange(newValue, context);
        }

        return null;
    }

    /**
     * Validate enabling/disabling a geocoding provider.
     * Returns error message if validation fails, null if validation passes.
     */
    private String validateProviderToggle(String key, String newValue, ValidationContext context) {
        // If enabling a provider, no validation needed
        if ("true".equalsIgnoreCase(newValue)) {
            return null;
        }

        // If disabling a provider, validate it's safe to do so
        String providerName = extractProviderName(key);

        // Check if this would disable the last enabled provider
        List<String> remainingProviders = getEnabledProvidersAfterDisabling(providerName, context);
        if (remainingProviders.isEmpty()) {
            return "Cannot disable the last enabled geocoding provider. " +
                    "Please enable another provider first.";
        }

        // Check if this is the current primary provider
        String primaryProvider = context != null
                ? context.getValue("geocoding.primary-provider")
                : settingsService.getString("geocoding.primary-provider");
        if (providerName.equalsIgnoreCase(primaryProvider)) {
            return "Cannot disable the current primary geocoding provider ('" + providerName + "'). " +
                    "Please change the primary provider to one of: " + remainingProviders.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.joining(", "));
        }

        // Check if this is the current fallback provider
        String fallbackProvider = context != null
                ? context.getValue("geocoding.fallback-provider")
                : settingsService.getString("geocoding.fallback-provider");
        if (fallbackProvider != null && !fallbackProvider.isEmpty() && providerName.equalsIgnoreCase(fallbackProvider)) {
            return "Cannot disable the current fallback geocoding provider ('" + providerName + "'). " +
                    "Please change or clear the fallback provider first.";
        }

        return null;
    }

    /**
     * Validate changing the primary provider.
     * Returns error message if validation fails, null if validation passes.
     */
    private String validatePrimaryProviderChange(String newValue, ValidationContext context) {
        if (newValue == null || newValue.isBlank()) {
            return "Primary provider cannot be empty";
        }

        // Check if the provider is enabled and properly configured
        List<String> enabledProviders = context != null
                ? getEnabledProvidersWithContext(context)
                : geocodingProviderFactory.getEnabledProviders();

        boolean isEnabled = enabledProviders.stream()
                .anyMatch(p -> p.equalsIgnoreCase(newValue));

        if (!isEnabled) {
            return "Cannot set primary provider to '" + newValue + "': " +
                    "provider is not enabled or missing required credentials (e.g., API key). " +
                    "Available providers: " + enabledProviders.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.joining(", "));
        }

        return null;
    }

    /**
     * Validate changing the fallback provider.
     * Returns error message if validation fails, null if validation passes.
     */
    private String validateFallbackProviderChange(String newValue, ValidationContext context) {
        // Empty fallback is allowed (no fallback)
        if (newValue == null || newValue.isBlank()) {
            return null;
        }

        // Check that fallback is different from primary
        String primaryProvider = context != null
                ? context.getValue("geocoding.primary-provider")
                : settingsService.getString("geocoding.primary-provider");
        if (newValue.equalsIgnoreCase(primaryProvider)) {
            return "Fallback provider cannot be the same as primary provider ('" + primaryProvider + "')";
        }

        // Check if the provider is enabled and properly configured
        List<String> enabledProviders = context != null
                ? getEnabledProvidersWithContext(context)
                : geocodingProviderFactory.getEnabledProviders();

        boolean isEnabled = enabledProviders.stream()
                .anyMatch(p -> p.equalsIgnoreCase(newValue));

        if (!isEnabled) {
            return "Cannot set fallback provider to '" + newValue + "': " +
                    "provider is not enabled or missing required credentials (e.g., API key). " +
                    "Available providers: " + enabledProviders.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.joining(", "));
        }

        return null;
    }

    /**
     * Extract provider name from setting key.
     * Example: "geocoding.nominatim.enabled" -> "nominatim"
     */
    private String extractProviderName(String key) {
        String[] parts = key.split("\\.");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "";
    }

    /**
     * Get list of providers that would remain enabled after disabling the given provider.
     */
    private List<String> getEnabledProvidersAfterDisabling(String providerToDisable, ValidationContext context) {
        List<String> currentlyEnabled = context != null
                ? getEnabledProvidersWithContext(context)
                : geocodingProviderFactory.getEnabledProviders();
        return currentlyEnabled.stream()
                .filter(p -> !p.equalsIgnoreCase(providerToDisable))
                .collect(Collectors.toList());
    }

    /**
     * Get list of enabled providers considering pending changes in validation context.
     * <p>
     * This method replicates the logic from each provider's isEnabled() method,
     * but uses the ValidationContext to check pending values instead of current DB state.
     * <p>
     * Provider requirements:
     * - Nominatim: Only needs enabled flag
     * - Photon: Only needs enabled flag
     * - Google Maps: Needs BOTH enabled flag AND api-key
     * - Mapbox: Needs BOTH enabled flag AND access-token
     *
     * @param context Validation context with pending changes
     * @return List of provider names that would be enabled
     */
    private List<String> getEnabledProvidersWithContext(ValidationContext context) {
        List<String> enabled = new ArrayList<>();

        // Nominatim - only needs enabled flag
        if (context.getBoolean("geocoding.nominatim.enabled")) {
            enabled.add("Nominatim");
        }

        // Photon - only needs enabled flag
        if (context.getBoolean("geocoding.photon.enabled")) {
            enabled.add("Photon");
        }

        // Google Maps - needs BOTH enabled flag AND api-key
        if (context.getBoolean("geocoding.googlemaps.enabled")) {
            String apiKey = context.getValue("geocoding.googlemaps.api-key");
            if (apiKey != null && !apiKey.isBlank() && !apiKey.equals("********")) {
                enabled.add("GoogleMaps");
            }
        }

        // Mapbox - needs BOTH enabled flag AND access-token
        if (context.getBoolean("geocoding.mapbox.enabled")) {
            String token = context.getValue("geocoding.mapbox.access-token");
            if (token != null && !token.isBlank() && !token.equals("********")) {
                enabled.add("Mapbox");
            }
        }

        return enabled;
    }

}
