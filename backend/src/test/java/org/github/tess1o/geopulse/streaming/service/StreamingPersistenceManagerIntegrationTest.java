package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.domain.*;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter;
import org.github.tess1o.geopulse.streaming.service.trips.*;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class StreamingPersistenceManagerIntegrationTest {
    @Inject StreamingPersistenceManager writer;
    @Inject StreamingTimelineConverter converter;
    @Inject EntityManager em;
    @Inject TestUserFactory users;

    @Test
    void jdbcOutputMatchesOrmIncludingStatisticsRoundingNullsAndExplanations() {
        UUID user = users.createUser("jdbc-equivalence").user().getId();
        Instant at = Instant.parse("2026-01-01T10:11:12.123456Z");
        var trip = trip(at);
        trip.setMovementTypeSource(MovementTypeSource.MANUAL);
        trip.setStatistics(new TripGpsStatistics(4.1, 9.3, 2.5, 7));
        trip.setWaterStatistics(new TripWaterStatistics(40.0, .5, 20.0, 6, true));
        trip.setAltitudeStatistics(new TripAltitudeStatistics(301.5, 1.25, 15));
        trip.setExplanation(new TimelineExplanation(TimelineReason.of("FIRST_POINT"),
                List.of(TimelineReason.of("TEST", "text", "quotes \" and Unicode Київ")), TimelineReason.of("LAST_POINT")));
        var missingStats = trip(at.plusSeconds(600));
        missingStats.setTripType(null);
        missingStats.setMovementTypeSource(null);
        var stay = Stay.builder().startTime(at.plusSeconds(1000)).duration(Duration.ofSeconds(601))
                .latitude(50.123456789).longitude(30.987654321).locationName("Place")
                .visitName("My visit").favoriteId(-1L).geocodingId(0L).build();
        var gap = DataGap.fromTimeRange(at.plusSeconds(2000), at.plusSeconds(2011).minusNanos(200000000));
        RawTimeline timeline = RawTimeline.builder().trips(List.of(trip, missingStats)).stays(List.of(stay))
                .dataGaps(List.of(gap)).build();
        List<?> expected = QuarkusTransaction.requiringNew().call(() -> {
            var ref = converter.createUserReference(user);
            for (var t : timeline.getTrips()) em.persist(converter.convertStreamingTripToEntity(t, ref));
            em.persist(converter.convertStayToEntityWithBatchData(stay, ref, Map.of(), Map.of()));
            em.persist(converter.convertGapToEntity(gap, ref));
            em.flush();
            return snapshot(user);
        });
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                    INSERT INTO reverse_geocoding_location (id,request_coordinates,result_coordinates,
                        display_name,provider_name,created_at,last_accessed_at)
                    VALUES (0,ST_SetSRID(ST_MakePoint(0,0),4326),ST_SetSRID(ST_MakePoint(0,0),4326),
                        :name,'test',now(),now()) ON CONFLICT (id) DO NOTHING
                    """).setParameter("name", "sentinel-" + user).executeUpdate();
            for (String table : List.of("timeline_trips", "timeline_stays", "timeline_data_gaps"))
                em.createNativeQuery("DELETE FROM " + table + " WHERE user_id=:u").setParameter("u", user).executeUpdate();
            writer.persistRawTimeline(user, timeline, null);
            assertThat(snapshot(user)).isEqualTo(expected);
            em.createNativeQuery("DELETE FROM reverse_geocoding_location WHERE id=0 AND display_name=:name")
                    .setParameter("name", "sentinel-" + user).executeUpdate();
        });
    }

    @Test
    void laterBatchFailureRollsBackPriorBatchesAndCallerChanges() {
        UUID user = users.createUser("jdbc-rollback").user().getId();
        String name = QuarkusTransaction.requiringNew().call(() -> em.find(UserEntity.class, user).getFullName());
        List<Trip> trips = new ArrayList<>();
        for (int i = 0; i < 501; i++) trips.add(trip(Instant.EPOCH.plusSeconds(i * 1000L)));
        trips.getLast().setStartPoint(null);
        assertThatThrownBy(() -> QuarkusTransaction.requiringNew().run(() -> {
            em.find(UserEntity.class, user).setFullName("must roll back");
            writer.persistRawTimeline(user, RawTimeline.builder().trips(trips).build(), null);
        })).isInstanceOf(RuntimeException.class);
        QuarkusTransaction.requiringNew().run(() -> {
            assertThat(((Number) em.createNativeQuery("SELECT count(*) FROM timeline_trips WHERE user_id=:u")
                    .setParameter("u", user).getSingleResult()).longValue()).isZero();
            assertThat(em.find(UserEntity.class, user).getFullName()).isEqualTo(name);
        });
    }

    @Test
    void successfulBatchesStayOutOfPersistenceContextAndKeepCallerManaged() {
        UUID user = users.createUser("jdbc-session-bound").user().getId();
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity caller = em.find(UserEntity.class, user);
            var statistics = em.unwrap(Session.class).getStatistics();
            int before = statistics.getEntityCount();
            List<Trip> trips = new ArrayList<>();
            for (int i = 0; i < 1003; i++) trips.add(trip(Instant.EPOCH.plusSeconds(i * 1000L)));
            writer.persistRawTimeline(user, RawTimeline.builder().trips(trips).build(), null);
            assertThat(statistics.getEntityCount()).isEqualTo(before);
            assertThat(em.contains(caller)).isTrue();
            caller.setFullName("changed after JDBC");
        });
        QuarkusTransaction.requiringNew().run(() -> {
            assertThat(em.find(UserEntity.class, user).getFullName()).isEqualTo("changed after JDBC");
            assertThat(((Number) em.createNativeQuery("SELECT count(*) FROM timeline_trips WHERE user_id=:u")
                    .setParameter("u", user).getSingleResult()).longValue()).isEqualTo(1003);
        });
    }

    private List<?> snapshot(UUID user) {
        List<Object> result = new ArrayList<>();
        for (String table : List.of("timeline_trips", "timeline_stays", "timeline_data_gaps"))
            result.addAll(em.createNativeQuery("SELECT (to_jsonb(t)-'id'-'created_at'-'last_updated')::text FROM "
                            + table + " t WHERE user_id=:u ORDER BY 1")
                    .setParameter("u", user).getResultList());
        return result;
    }

    private Trip trip(Instant start) {
        return Trip.builder().startTime(start).duration(Duration.ofSeconds(300)).distanceMeters(1234.9)
                .tripType(TripType.CAR).startPoint(new GPSPoint(50.123456789, 30.987654321, 0, 0))
                .endPoint(new GPSPoint(50.234567891, 30.876543219, 0, 0)).build();
    }
}
