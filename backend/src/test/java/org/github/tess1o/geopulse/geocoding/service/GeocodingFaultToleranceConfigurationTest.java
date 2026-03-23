package org.github.tess1o.geopulse.geocoding.service;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.github.tess1o.geopulse.geocoding.service.external.GoogleMapsGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.external.MapboxGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.external.NominatimGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.external.PhotonGeocodingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class GeocodingFaultToleranceConfigurationTest {

    @Test
    void applicationProperties_haveExpectedGeocodingFaultToleranceDefaults() throws IOException {
        Properties properties = new Properties();
        try (var stream = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(stream);
        }

        assertThat(properties.getProperty("quarkus.fault-tolerance.global.retry.max-retries"))
                .isEqualTo("${GEOPULSE_GEOCODING_RETRY_MAX_RETRIES:5}");
        assertThat(properties.getProperty("quarkus.fault-tolerance.global.retry.delay"))
                .isEqualTo("${GEOPULSE_GEOCODING_RETRY_DELAY_MS:1250}");
        assertThat(properties.getProperty("quarkus.fault-tolerance.global.retry.jitter"))
                .isEqualTo("${GEOPULSE_GEOCODING_RETRY_JITTER_MS:250}");
        assertThat(properties.getProperty("quarkus.fault-tolerance.global.circuit-breaker.delay"))
                .isEqualTo("${GEOPULSE_GEOCODING_CB_DELAY_SECONDS:20}");
        assertThat(properties.getProperty("geocoding.reconcile.item.max-attempts"))
                .isEqualTo("${GEOPULSE_GEOCODING_RECONCILE_ITEM_MAX_ATTEMPTS:4}");
        assertThat(properties.getProperty("geocoding.reconcile.circuit-open-wait.ms"))
                .isEqualTo("${GEOPULSE_GEOCODING_RECONCILE_CIRCUIT_OPEN_WAIT_MS:20000}");
    }

    @Test
    void providerReverseGeocodeMethods_useDefaultRetryAndCircuitBreakerAnnotationValues() throws Exception {
        List<Class<?>> providers = List.of(
                NominatimGeocodingService.class,
                PhotonGeocodingService.class,
                GoogleMapsGeocodingService.class,
                MapboxGeocodingService.class
        );

        for (Class<?> providerClass : providers) {
            Method reverseGeocode = providerClass.getDeclaredMethod("reverseGeocode", org.locationtech.jts.geom.Point.class);
            Retry retry = reverseGeocode.getAnnotation(Retry.class);
            CircuitBreaker circuitBreaker = reverseGeocode.getAnnotation(CircuitBreaker.class);

            assertThat(retry)
                    .as("%s should declare @Retry", providerClass.getSimpleName())
                    .isNotNull();
            assertThat(circuitBreaker)
                    .as("%s should declare @CircuitBreaker", providerClass.getSimpleName())
                    .isNotNull();

            assertRetryUsesAnnotationDefaults(retry);
            assertCircuitBreakerUsesAnnotationDefaults(circuitBreaker);
        }
    }

    private void assertRetryUsesAnnotationDefaults(Retry retry) throws Exception {
        assertThat(retry.maxRetries()).isEqualTo(annotationDefault(Retry.class, "maxRetries", Integer.class));
        assertThat(retry.delay()).isEqualTo(annotationDefault(Retry.class, "delay", Long.class));
        assertThat(retry.delayUnit()).isEqualTo(annotationDefault(Retry.class, "delayUnit", java.time.temporal.ChronoUnit.class));
        assertThat(retry.jitter()).isEqualTo(annotationDefault(Retry.class, "jitter", Long.class));
        assertThat(retry.jitterDelayUnit()).isEqualTo(annotationDefault(Retry.class, "jitterDelayUnit", java.time.temporal.ChronoUnit.class));
        assertThat(retry.maxDuration()).isEqualTo(annotationDefault(Retry.class, "maxDuration", Long.class));
        assertThat(retry.durationUnit()).isEqualTo(annotationDefault(Retry.class, "durationUnit", java.time.temporal.ChronoUnit.class));
        assertThat(retry.retryOn()).isEqualTo(annotationDefault(Retry.class, "retryOn", Class[].class));
        assertThat(retry.abortOn()).isEqualTo(annotationDefault(Retry.class, "abortOn", Class[].class));
    }

    private void assertCircuitBreakerUsesAnnotationDefaults(CircuitBreaker circuitBreaker) throws Exception {
        assertThat(circuitBreaker.delay()).isEqualTo(annotationDefault(CircuitBreaker.class, "delay", Long.class));
        assertThat(circuitBreaker.delayUnit()).isEqualTo(annotationDefault(CircuitBreaker.class, "delayUnit", java.time.temporal.ChronoUnit.class));
        assertThat(circuitBreaker.requestVolumeThreshold()).isEqualTo(annotationDefault(CircuitBreaker.class, "requestVolumeThreshold", Integer.class));
        assertThat(circuitBreaker.failureRatio()).isEqualTo(annotationDefault(CircuitBreaker.class, "failureRatio", Double.class));
        assertThat(circuitBreaker.successThreshold()).isEqualTo(annotationDefault(CircuitBreaker.class, "successThreshold", Integer.class));
        assertThat(circuitBreaker.failOn()).isEqualTo(annotationDefault(CircuitBreaker.class, "failOn", Class[].class));
        assertThat(circuitBreaker.skipOn()).isEqualTo(annotationDefault(CircuitBreaker.class, "skipOn", Class[].class));
    }

    @SuppressWarnings("unchecked")
    private static <T> T annotationDefault(Class<? extends Annotation> annotationClass,
                                           String methodName,
                                           Class<T> returnType) throws Exception {
        Method method = annotationClass.getMethod(methodName);
        Object defaultValue = method.getDefaultValue();
        return (T) returnType.cast(defaultValue);
    }
}
