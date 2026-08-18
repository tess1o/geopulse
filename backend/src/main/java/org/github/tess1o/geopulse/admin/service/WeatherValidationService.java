package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.admin.dto.UpdateSettingRequest;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class WeatherValidationService {

    @Inject
    SystemSettingsService settingsService;

    public String validateWeatherChanges(List<UpdateSettingRequest> changes) {
        Map<String, String> pendingMap = changes.stream()
                .collect(Collectors.toMap(
                        UpdateSettingRequest::getKey,
                        UpdateSettingRequest::getValue
                ));

        ValidationContext context = new ValidationContext(pendingMap, settingsService);
        for (UpdateSettingRequest change : changes) {
            String error = validateWeatherChange(change.getKey(), change.getValue(), context);
            if (error != null) {
                return error;
            }
        }

        return validateProviderRouting(context);
    }

    private String validateWeatherChange(String key, String newValue, ValidationContext context) {
        if (WeatherConfigurationService.PIRATE_ENABLED.equals(key) && "true".equalsIgnoreCase(newValue)) {
            if (blank(context.getValue(WeatherConfigurationService.PIRATE_API_KEY))) {
                return "Cannot enable Pirate Weather without providing an API key";
            }
        }

        if (WeatherConfigurationService.PRIMARY_PROVIDER.equals(key)) {
            String provider = normalize(newValue);
            if (provider.isBlank()) {
                return "Primary weather provider cannot be empty";
            }
            if (!isKnownProvider(provider)) {
                return "Unknown weather provider: " + newValue;
            }
        }

        if (WeatherConfigurationService.SECONDARY_PROVIDER.equals(key)) {
            String provider = normalize(newValue);
            if (!provider.isBlank() && !isKnownProvider(provider)) {
                return "Unknown weather provider: " + newValue;
            }
        }

        return null;
    }

    private String validateProviderRouting(ValidationContext context) {
        if (!context.getBoolean(WeatherConfigurationService.WEATHER_ENABLED)) {
            return null;
        }

        String primaryProvider = normalize(context.getValue(WeatherConfigurationService.PRIMARY_PROVIDER));
        if (primaryProvider.isBlank()) {
            primaryProvider = WeatherConfigurationService.PROVIDER_OPEN_METEO;
        }
        String secondaryProvider = normalize(context.getValue(WeatherConfigurationService.SECONDARY_PROVIDER));

        if (!isProviderEnabledAndConfigured(primaryProvider, context)) {
            return "Primary weather provider '" + primaryProvider + "' is not enabled or configured";
        }

        if (!secondaryProvider.isBlank()) {
            if (secondaryProvider.equals(primaryProvider)) {
                return "Secondary weather provider cannot be the same as primary provider";
            }
            if (!isProviderEnabledAndConfigured(secondaryProvider, context)) {
                return "Secondary weather provider '" + secondaryProvider + "' is not enabled or configured";
            }
        }

        return null;
    }

    private boolean isProviderEnabledAndConfigured(String provider, ValidationContext context) {
        return switch (provider) {
            case WeatherConfigurationService.PROVIDER_OPEN_METEO -> context.getBoolean(WeatherConfigurationService.OPEN_METEO_ENABLED)
                    && !blank(context.getValue(WeatherConfigurationService.FORECAST_URL))
                    && !blank(context.getValue(WeatherConfigurationService.ARCHIVE_URL));
            case WeatherConfigurationService.PROVIDER_PIRATE_WEATHER -> context.getBoolean(WeatherConfigurationService.PIRATE_ENABLED)
                    && !blank(context.getValue(WeatherConfigurationService.PIRATE_BASE_URL))
                    && !blank(context.getValue(WeatherConfigurationService.PIRATE_TIME_MACHINE_URL))
                    && !blank(context.getValue(WeatherConfigurationService.PIRATE_API_KEY));
            default -> false;
        };
    }

    private boolean isKnownProvider(String provider) {
        return WeatherConfigurationService.PROVIDER_OPEN_METEO.equals(provider)
                || WeatherConfigurationService.PROVIDER_PIRATE_WEATHER.equals(provider);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
