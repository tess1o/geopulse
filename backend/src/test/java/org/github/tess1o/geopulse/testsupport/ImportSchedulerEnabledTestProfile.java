package org.github.tess1o.geopulse.testsupport;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Turns on the import job scheduler while leaving Quarkus scheduling off.
 * <p>
 * Shared by the import transaction-boundary tests. Each of them used to declare its own nested,
 * byte-identical profile, but Quarkus caches the running application per profile <em>class</em>
 * rather than per config value, so two identical profiles cost two application boots.
 */
public class ImportSchedulerEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "geopulse.import.scheduler.enabled", "true",
                "quarkus.scheduler.enabled", "false"
        );
    }
}
