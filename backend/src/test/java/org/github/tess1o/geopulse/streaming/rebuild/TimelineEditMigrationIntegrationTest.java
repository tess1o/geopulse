package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.*;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.repository.*;
import org.github.tess1o.geopulse.streaming.service.*;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.testsupport.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineEditMigrationIntegrationTest {
    @Inject TimelineReplayHarness harness;
    @Inject ManualEditsSupport manual;
    @Inject TripMovementTypeOverrideService movement;
    @Inject StayNameOverrideService names;
    @Inject DataGapStayOverrideService gapEdits;
    @Inject TimelineDataGapRepository gaps;
    @Inject TimelineStayRepository stays;
    @Inject TimelineTripRepository trips;
    @Inject TimelineEditService edits;
    @Inject EntityManager em;

    @Test
    void seededLegacyCorrectionsMigrateAndReplayWithoutApplyingTwice() throws Exception {
        GeocodingTestMocks.install();
        var day = new CommuteWithStops();
        UUID user = day.ingest(harness, "migration-equivalence");
        harness.ingest(user, new Track().stay(day.office, CommuteWithStops.at("16:00"), CommuteWithStops.at("18:00"), 2).messages());
        harness.fullRebuild(user);
        var source = manual.tripCovering(user, CommuteWithStops.at("08:16"), CommuteWithStops.at("08:20"));
        movement.setManualMovementType(user, source.getId(), TripType.TRAIN);
        manual.split(user, CommuteWithStops.at("08:16"), CommuteWithStops.at("08:20"), day.bakery, "Bakery");
        var gap = QuarkusTransaction.requiringNew().call(() -> gaps.findByUserId(user).stream()
                .filter(row -> row.getEndTime().equals(CommuteWithStops.at("16:00"))).findFirst().orElseThrow());
        var request = new DataGapStayOverrideRequest(); request.setLocationStrategy("SELECTED_LOCATION");
        request.setLatitude(day.pharmacy[0]); request.setLongitude(day.pharmacy[1]); request.setLocationName("Gym");
        gapEdits.convertGapToStay(user, gap.getId(), request);
        names.nameStay(user, CommuteWithStops.at("08:46"), CommuteWithStops.at("12:00"), "Planning day");
        long legacyCount = QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery("""
                select (select count(*) from timeline_trip_stay_split_overrides where user_id = ?1)
                     + (select count(*) from timeline_trip_movement_overrides where user_id = ?1)
                     + (select count(*) from timeline_data_gap_stay_overrides where user_id = ?1)
                     + (select count(*) from timeline_stay_name_overrides where user_id = ?1)
                """).setParameter(1, user).getSingleResult()).longValue());
        String sql;
        try (var input = getClass().getResourceAsStream("/db/migration/V36.81.0__Unify_timeline_edits.sql")) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Run the shipped data migration, scoped to this fixture; Flyway already created its tables.
        String dataMigration = sql.substring(sql.indexOf("WITH legacy AS"));
        for (String table : new String[]{"timeline_trip_stay_split_overrides", "timeline_trip_movement_overrides",
                "timeline_data_gap_stay_overrides", "timeline_stay_name_overrides"}) {
            dataMigration = dataMigration.replace("FROM " + table, "FROM " + table + " WHERE user_id = '" + user + "'")
                    .replace("UPDATE " + table + " SET migrated_to_timeline_edits = TRUE;",
                            "UPDATE " + table + " SET migrated_to_timeline_edits = TRUE WHERE user_id = '" + user + "';");
        }
        String migration = dataMigration;
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(migration).executeUpdate());
        assertThat(edits.list(user)).hasSize((int) legacyCount).extracting(row -> row.kind())
                .contains(TimelineEditKind.FORCE_STAY, TimelineEditKind.SET_MOVEMENT_TYPE, TimelineEditKind.SET_VISIT_NAME);
        harness.fullRebuild(user);
        QuarkusTransaction.requiringNew().run(() -> {
            java.util.List<org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity> migratedStays = stays.find("user.id", user).list();
            assertThat(migratedStays).anySatisfy(row -> {
                assertThat(row.getTimestamp()).isEqualTo(CommuteWithStops.at("08:16"));
                assertThat(row.getStayDuration()).isEqualTo(240); assertThat(row.getLocationName()).isEqualTo("Bakery");
            }).anySatisfy(row -> { assertThat(row.getTimestamp()).isEqualTo(gap.getStartTime());
                assertThat(row.getLocationName()).isEqualTo("Gym");
                assertThat(row.getStayDuration()).isEqualTo(gap.getDurationSeconds());
            }).anySatisfy(row -> { assertThat(row.getTimestamp()).isEqualTo(CommuteWithStops.at("08:46"));
                // Legacy one-visit names become visit labels, independent from the place's name.
                assertThat(row.getVisitName()).isEqualTo("Planning day"); });
            java.util.List<org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity> migratedTrips = trips.find("user.id", user).list();
            assertThat(migratedTrips).hasSize(2).allSatisfy(row -> {
                assertThat(row.getMovementType()).isEqualTo("TRAIN");
                assertThat(row.getMovementTypeSource().name()).isEqualTo("MANUAL");
            });
        });
        var migrated = harness.snapshot(user).content();
        harness.fullRebuild(user);
        assertThat(harness.snapshot(user).content()).containsExactlyElementsOf(migrated);
    }
}
