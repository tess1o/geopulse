package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.edits.*;
import org.github.tess1o.geopulse.streaming.model.domain.*;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.repository.*;
import org.github.tess1o.geopulse.streaming.service.StreamingPersistenceManager;
import org.github.tess1o.geopulse.streaming.service.trips.StreamingSingleTripAlgorithm;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class ZeroDurationTimelineEditIntegrationTest {

    private static final Instant AT = Instant.parse("2026-10-18T16:01:00Z");

    @Inject
    TimelineReplayHarness harness;
    @Inject
    TimelineEditService edits;
    @Inject
    TimelineTripRepository trips;
    @Inject
    TimelineStayRepository stays;
    @Inject
    TimelineDataGapRepository gaps;
    @Inject
    StreamingSingleTripAlgorithm algorithm;
    @Inject
    TimelineConfigurationProvider configuration;
    @Inject
    StreamingPersistenceManager persistence;

    @org.junit.jupiter.api.Test
    void defaultRebuildWindowIncludesThePointAnchorWithoutInventingTripDuration() {
        UUID user = harness.createUser("zero-trip-default-window");
        rebuild(user);
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_MOVEMENT_TYPE);
        request.setAnchorStart(AT);
        request.setAnchorEnd(AT);
        request.setPayload(new LinkedHashMap<>(Map.of("movementType", "WALK")));
        var preview = edits.preview(user, request);
        assertThat(preview.events()).filteredOn(row -> row.type().equals("TRIP")).singleElement()
                .satisfies(row -> assertThat(row.start()).isEqualTo(row.end()));
        var edit = edits.create(user, request);
        assertThat(edit.affectedTo()).isEqualTo(AT.plusSeconds(1));
        rebuild(user);
        assertThat(edits.list(user)).singleElement().satisfies(row -> assertThat(row.applied()).isTrue());
    }

    @ParameterizedTest
    @EnumSource(value = TimelineEditKind.class, names = {"SET_MOVEMENT_TYPE", "REMOVE_EVENT", "SET_TIMES"})
    void pointEditsSurvivePersistenceReplayUndoAndRestore(TimelineEditKind kind) {
        UUID user = harness.createUser("zero-trip-660");
        rebuild(user);
        var request = new TimelineEditRequest();
        request.setKind(kind);
        request.setAnchorStart(AT);
        request.setAnchorEnd(AT);
        request.setAffectedFrom(AT.minusSeconds(3600));
        request.setAffectedTo(AT.plusSeconds(3600));
        request.setPayload(new LinkedHashMap<>(switch (kind) {
            case SET_MOVEMENT_TYPE -> Map.of("movementType", "WALK");
            case REMOVE_EVENT -> Map.of("resolution", "DISMISS");
            case SET_TIMES -> Map.of("start", AT.minusSeconds(60).toString(), "end", AT.plusSeconds(60).toString());
            default -> throw new IllegalArgumentException();
        }));
        var preview = edits.preview(user, request);
        var edit = edits.create(user, request);
        assertThat(edit.beforeValues()).containsEntry("type", "TRIP").containsEntry("movementType", "UNKNOWN");
        for (int pass = 0; pass < 2; pass++) {
            rebuild(user);
            var current = edits.loadCurrentEvents(user, request.getAffectedFrom(), request.getAffectedTo());
            assertThat(current).hasSize(preview.events().size());
            assertThat(edits.list(user)).filteredOn(row -> row.id().equals(edit.id())).singleElement()
                    .satisfies(row -> {
                        assertThat(row.applied()).isTrue();
                        assertThat(row.target()).isNotNull();
                    });
            var trip = current.stream().filter(Trip.class::isInstance).map(Trip.class::cast).toList();
            if (kind == TimelineEditKind.REMOVE_EVENT) {
                assertThat(trip).isEmpty();
                assertThat(current.getFirst().getEndTime()).isEqualTo(AT);
                assertThat(current.getLast().getStartTime()).isEqualTo(AT);
            } else {
                assertThat(trip).singleElement().satisfies(row -> {
                    assertThat(row.getDuration()).isEqualTo(Duration.ofSeconds(kind == TimelineEditKind.SET_TIMES ? 120 : 0));
                    if (kind == TimelineEditKind.SET_MOVEMENT_TYPE)
                        assertThat(row.getTripType().name()).isEqualTo("WALK");
                });
            }
            assertThat(current).noneMatch(DataGap.class::isInstance);
        }
        edits.revert(user, edit.id()).orElseThrow();
        rebuild(user);
        assertThat(edits.loadCurrentEvents(user, AT, AT)).singleElement().isInstanceOfSatisfying(Trip.class,
                row -> assertThat(row.getTripType().name()).isEqualTo("UNKNOWN"));
        edits.restore(user, edit.id()).orElseThrow();
        rebuild(user);
        assertThat(edits.list(user)).filteredOn(row -> row.id().equals(edit.id())).singleElement()
                .satisfies(row -> assertThat(row.applied()).isTrue());

        if (kind == TimelineEditKind.SET_MOVEMENT_TYPE) {
            request.getPayload().put("movementType", "BICYCLE");
            var replacement = edits.create(user, request);
            rebuild(user);
            assertThat(edits.list(user)).filteredOn(row -> row.id().equals(edit.id())).singleElement()
                    .satisfies(row -> assertThat(row.status()).isEqualTo(TimelineEditStatus.SUPERSEDED));
            assertThat(edits.list(user)).filteredOn(row -> row.id().equals(replacement.id())).singleElement()
                    .satisfies(row -> assertThat(row.applied()).isTrue());
        }

        request.setKind(TimelineEditKind.FORCE_TRIP);
        assertThatThrownBy(() -> edits.preview(user, request)).hasMessageContaining("positive anchor interval");
        request.setKind(TimelineEditKind.REMOVE_EVENT);
        request.getPayload().put("resolution", "GAP");
        assertThatThrownBy(() -> edits.preview(user, request)).hasMessageContaining("without filling time");
    }

    // Regenerate the connector from adjoining stays, then use the production edit and persistence stages.
    private void rebuild(UUID user) {
        QuarkusTransaction.requiringNew().run(() -> {
            List<TimelineEvent> generated = algorithm.apply(user, List.of(
                            Stay.builder().startTime(AT.minusSeconds(3600)).duration(Duration.ofHours(1))
                                    .latitude(50).longitude(30).locationName("Home").build(),
                            Stay.builder().startTime(AT).duration(Duration.ofHours(1))
                                    .latitude(50.0005).longitude(30.0005).locationName("Shop").build()),
                    configuration.getConfigurationForUser(user), null);
            var result = edits.apply(user, generated, AT.minusSeconds(3600), AT.plusSeconds(3600));
            trips.delete("user.id", user);
            stays.delete("user.id", user);
            gaps.delete("user.id", user);
            persistence.persistRawTimeline(user, RawTimeline.fromEvents(user, result.events()), null);
            edits.recordApplications(user, result);
            edits.clearMissingApplications(user);
        });
    }
}
