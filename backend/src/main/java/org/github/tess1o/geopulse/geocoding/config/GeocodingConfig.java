package org.github.tess1o.geopulse.geocoding.config;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import lombok.Getter;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

/**
 * Configuration for geocoding providers.
 * Uses @ConfigProperty for runtime-configurable properties in native mode.
 */
@ApplicationScoped
@StaticInitSafe
@Getter
@Slf4j
public class GeocodingConfig {

    // Provider Configuration
    @Inject
    @ConfigProperty(name = "geocoding.provider.primary")
    @StaticInitSafe
    String primary;

    @Inject
    @ConfigProperty(name = "geocoding.provider.fallback")
    @StaticInitSafe
    Optional<String> fallback;

    // Nominatim Configuration
    @Inject
    @ConfigProperty(name = "geocoding.provider.nominatim.enabled")
    @StaticInitSafe
    boolean nominatimEnabled;

    // Photon Configuration
    @Inject
    @ConfigProperty(name = "geocoding.provider.photon.enabled")
    @StaticInitSafe
    boolean photonEnabled;

    // Google Maps Configuration
    @Inject
    @ConfigProperty(name = "geocoding.provider.googlemaps.enabled")
    @StaticInitSafe
    boolean googleMapsEnabled;

    @Inject
    @ConfigProperty(name = "geocoding.googlemaps.api-key")
    @StaticInitSafe
    String googleMapsApiKey;

    // Mapbox Configuration
    @Inject
    @ConfigProperty(name = "geocoding.provider.mapbox.enabled")
    @StaticInitSafe
    boolean mapboxEnabled;

    @Inject
    @ConfigProperty(name = "geocoding.mapbox.access-token")
    @StaticInitSafe
    String mapboxAccessToken;

    @PostConstruct
    void init() {
        log.info("Geocoding configuration initialized - primary: {}, fallback: {}",
                primary, fallback.orElse("none"));
        log.info("Providers enabled - Nominatim: {}, Photon: {}, Google Maps: {}, Mapbox: {}",
                nominatimEnabled, photonEnabled, googleMapsEnabled, mapboxEnabled);
    }

    // Helper methods to match your original nested interface structure
    public Provider provider() {
        return new Provider(this);
    }

    public GoogleMaps googlemaps() {
        return new GoogleMaps(this);
    }

    public Mapbox mapbox() {
        return new Mapbox(this);
    }

    /**
     * Provider configuration wrapper to maintain API compatibility
     */
    @Getter
    public static class Provider {
        private final GeocodingConfig config;

        Provider(GeocodingConfig config) {
            this.config = config;
        }

        public String primary() {
            return config.getPrimary();
        }

        public Optional<String> fallback() {
            return config.getFallback();
        }

        public Nominatim nominatim() {
            return new Nominatim(config);
        }

        public GoogleMaps googlemaps() {
            return new GoogleMaps(config);
        }

        public Mapbox mapbox() {
            return new Mapbox(config);
        }

        public Photon photon() {
            return new Photon(config);
        }

        @Getter
        public static class Nominatim {
            private final GeocodingConfig config;

            Nominatim(GeocodingConfig config) {
                this.config = config;
            }

            public boolean enabled() {
                return config.isNominatimEnabled();
            }
        }

        @Getter
        public static class GoogleMaps {
            private final GeocodingConfig config;

            GoogleMaps(GeocodingConfig config) {
                this.config = config;
            }

            public boolean enabled() {
                return config.isGoogleMapsEnabled();
            }
        }

        @Getter
        public static class Mapbox {
            private final GeocodingConfig config;

            Mapbox(GeocodingConfig config) {
                this.config = config;
            }

            public boolean enabled() {
                return config.isMapboxEnabled();
            }
        }

        @Getter
        public static class Photon {
            private final GeocodingConfig config;

            Photon(GeocodingConfig config) {
                this.config = config;
            }

            public boolean enabled() {
                return config.isPhotonEnabled();
            }
        }
    }

    /**
     * Google Maps configuration wrapper
     */
    @Getter
    public static class GoogleMaps {
        private final GeocodingConfig config;

        GoogleMaps(GeocodingConfig config) {
            this.config = config;
        }

        public String apiKey() {
            return config.getGoogleMapsApiKey();
        }
    }

    /**
     * Mapbox configuration wrapper
     */
    @Getter
    public static class Mapbox {
        private final GeocodingConfig config;

        Mapbox(GeocodingConfig config) {
            this.config = config;
        }

        public String accessToken() {
            return config.getMapboxAccessToken();
        }
    }
}