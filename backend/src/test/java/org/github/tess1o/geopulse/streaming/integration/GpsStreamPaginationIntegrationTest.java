package org.github.tess1o.geopulse.streaming.integration;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.iterator.StreamingGpsIterable;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class GpsStreamPaginationIntegrationTest {
    @Inject GpsPointRepository gps;
    @Inject EntityManager em;
    @Inject TestUserFactory users;

    @Test
    void duplicateTimestampsAreNeitherSkippedNorRepeatedAcrossPageBoundaries() {
        UUID user = users.createUser("stream-duplicate-timestamps").user().getId();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                    INSERT INTO gps_points (user_id,coordinates,timestamp,accuracy,velocity,altitude,source_type,created_at)
                    SELECT :user,ST_SetSRID(ST_MakePoint(30+i*.001,50),4326),
                        '2026-01-01T00:00:00Z'::timestamptz + (i/4) * interval '1 second',
                        5,10,280,'OWNTRACKS',now() FROM generate_series(0,40) points(i)
                    """).setParameter("user", user).executeUpdate();
            for (Instant from : List.of(Instant.EPOCH, Instant.parse("2026-01-01T00:00:02Z"))) {
                List<Long> expected = em.createNativeQuery("SELECT id FROM gps_points WHERE user_id=:u "
                                + "AND timestamp >= :f ORDER BY timestamp,id")
                        .setParameter("u", user).setParameter("f", from).getResultList().stream()
                        .map(row -> ((Number) row).longValue()).toList();
                for (int pageSize : new int[]{1, 2, 7, 40, 100}) {
                    List<Long> actual = new ArrayList<>();
                    for (var point : new StreamingGpsIterable(gps, user, from, pageSize)) {
                        actual.add(point.getId());
                        assertThat(actual.size()).isLessThanOrEqualTo(expected.size());
                    }
                    assertThat(actual).as("page size %s from %s", pageSize, from).containsExactlyElementsOf(expected);
                }
            }
        });
    }
}
