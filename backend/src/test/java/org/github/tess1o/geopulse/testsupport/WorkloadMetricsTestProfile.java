package org.github.tess1o.geopulse.testsupport;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** Turns the workload metrics on, which production leaves off unless Prometheus is enabled. */
public class WorkloadMetricsTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("geopulse.prometheus.enabled", "true", "geopulse.prometheus.workload.enabled", "true");
    }
}
