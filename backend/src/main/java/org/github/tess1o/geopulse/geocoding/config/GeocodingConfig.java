package org.github.tess1o.geopulse.geocoding.config;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Configuration mapping for geocoding providers.
 * Uses Quarkus @ConfigMapping for type-safe configuration.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
@ConfigMapping(prefix = "geocoding")
@StaticInitSafe
public interface GeocodingConfig {

    /**
     * Provider configuration.
     */
    Provider provider();

    /**
     * Google Maps configuration.
     */
    GoogleMaps googlemaps();

    /**
     * Mapbox configuration.
     */
    Mapbox mapbox();

    /**
     * Provider selection and enablement.
     */
    interface Provider {
        /**
         * Primary provider name.
         */
        @WithDefault("nominatim")
        String primary();

        /**
         * Fallback provider name (optional).
         */
        Optional<String> fallback();

        /**
         * Nominatim provider settings.
         */
        Nominatim nominatim();

        /**
         * Google Maps provider settings.
         */
        GoogleMaps googlemaps();

        /**
         * Mapbox provider settings.
         */
        Mapbox mapbox();

        interface Nominatim {
            @WithDefault("true")
            boolean enabled();
        }

        interface GoogleMaps {
            @WithDefault("false")
            boolean enabled();
        }

        interface Mapbox {
            @WithDefault("false")
            boolean enabled();
        }
    }

    /**
     * Google Maps API configuration.
     */
    interface GoogleMaps {
        /**
         * Google Maps API key.
         */
        @WithDefault("")
        String apiKey();
    }

    /**
     * Mapbox API configuration.
     */
    interface Mapbox {
        /**
         * Mapbox access token.
         */
        @WithDefault("")
        String accessToken();
    }
}