package org.github.tess1o.geopulse.testsupport;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** Turns on Hibernate statistics, for tests that check how many entities a piece of work loads. */
public class HibernateStatisticsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.hibernate-orm.statistics", "true");
    }
}
