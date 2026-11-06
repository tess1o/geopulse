package org.github.tess1o.geopulse.streaming.integration;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineLoggingIntegrationTest {
    @Inject StreamingTimelineGenerationService generation;
    @Inject EntityManager em;
    @Inject TestUserFactory users;

    @Test
    void routineRegenerationKeepsStageSummariesWithoutPerEventWarnings() {
        UUID user = users.createUser("timeline-log-volume").user().getId();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                    UPDATE users SET timeline_preferences='{"staypointRadiusMeters":50,
                        "staypointMinDurationMinutes":7,"isMergeEnabled":false,"boatEnabled":false}'::jsonb WHERE id=:u
                    """).setParameter("u", user).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO gps_points (user_id,coordinates,timestamp,accuracy,velocity,altitude,source_type,created_at)
                    SELECT :u,ST_SetSRID(ST_MakePoint(
                        30 + CASE WHEN i%100<30 THEN 0 WHEN i%100>=70 THEN .02 ELSE (i%100-30)*.0005 END,
                        50 + CASE WHEN i%100<30 THEN 0 WHEN i%100>=70 THEN .01 ELSE (i%100-30)*.00025 END),4326),
                        '2010-01-01T00:00:00Z'::timestamptz+i*interval '30 seconds',5,
                        CASE WHEN i%100 BETWEEN 30 AND 69 THEN 36 ELSE 0 END,280,'OWNTRACKS',now()
                    FROM generate_series(0,999) points(i)
                    """).setParameter("u", user).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO reverse_geocoding_location
                        (id,request_coordinates,result_coordinates,display_name,provider_name,created_at,last_accessed_at)
                    SELECT nextval('reverse_geocoding_location_seq'),ST_SetSRID(ST_MakePoint(lon,lat),4326),
                        ST_SetSRID(ST_MakePoint(lon,lat),4326),name,'nominatim',now(),now()
                    FROM (VALUES (30.0,50.0,'Log test home'),(30.02,50.01,'Log test office')) locations(lon,lat,name)
                    ON CONFLICT DO NOTHING
                    """).executeUpdate();
        });
        var logger = LogContext.getLogContext().getLogger("org.github.tess1o.geopulse.streaming");
        List<String> warnings = new ArrayList<>();
        AtomicInteger summaries = new AtomicInteger();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue() && warnings.size() < 5)
                    warnings.add(record.getMessage());
                if (record.getLevel().intValue() == Level.INFO.intValue()) summaries.incrementAndGet();
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        try {
            assertThat(generation.regenerateFullTimeline(user)).isTrue();
        } finally {
            logger.setLevel(originalLevel);
            logger.removeHandler(handler);
        }
        assertThat(warnings).isEmpty();
        assertThat(summaries.get()).isBetween(1, 50);
        QuarkusTransaction.requiringNew().run(() -> {
            assertThat(count("timeline_stays", user)).isEqualTo(20);
            assertThat(count("timeline_trips", user)).isEqualTo(19);
        });
    }

    private long count(String table, UUID user) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM " + table + " WHERE user_id=:u")
                .setParameter("u", user).getSingleResult()).longValue();
    }
}
