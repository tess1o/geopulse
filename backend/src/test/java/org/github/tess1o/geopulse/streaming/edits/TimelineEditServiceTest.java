package org.github.tess1o.geopulse.streaming.edits;

import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineEditRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class TimelineEditServiceTest {
    private static final Instant FROM = Instant.parse("2026-10-01T10:00:00Z");
    private final UUID user = UUID.randomUUID();
    private final TimelineEditRepository repository = mock(TimelineEditRepository.class);
    private final TimelineEditService service = new TimelineEditService();

    TimelineEditServiceTest() {
        service.editRepository = repository;
        service.applier = new TimelineEditApplier();
    }

    @ParameterizedTest
    @EnumSource(value = TimelineEditKind.class, names = {"FORCE_STAY", "FORCE_TRIP"})
    void partialRegenerationSkipsAnInsertionWhoseAnchorPrecedesTheWindow(TimelineEditKind kind) {
        var edit = edit(kind, FROM.minusSeconds(1), FROM.plusSeconds(60));
        when(repository.findActiveOverlapping(user, FROM, FROM.plusSeconds(30))).thenReturn(List.of(edit));
        var result = service.apply(user, List.of(), FROM, FROM.plusSeconds(30));
        assertThat(result.events()).isEmpty();
        assertThat(result.appliedEditIds()).isEmpty();
        assertThat(result.unappliedEditIds()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = TimelineEditKind.class, names = {"FORCE_STAY", "FORCE_TRIP"})
    void anchorAtWindowStartAppliesEvenWhenTheEventEndsBeyondTheWindow(TimelineEditKind kind) {
        var edit = edit(kind, FROM, FROM.plusSeconds(60));
        when(repository.findActiveOverlapping(user, FROM, FROM.plusSeconds(30))).thenReturn(List.of(edit));
        var result = service.apply(user, List.of(), FROM, FROM.plusSeconds(30));
        assertThat(result.appliedEditIds()).containsExactly(1L);
        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.getStartTime()).isEqualTo(FROM);
            assertThat(event.getEndTime()).isEqualTo(FROM.plusSeconds(60));
        });
    }

    @Test
    void matchingEditsWithEarlierAnchorsAreNotFilteredOut() {
        var edit = edit(TimelineEditKind.SET_VISIT_NAME, FROM.minusSeconds(1), FROM.plusSeconds(60));
        var stay = Stay.builder().startTime(FROM).duration(Duration.ofSeconds(60))
                .locationName("Home").latitude(50).longitude(30).build();
        when(repository.findActiveOverlapping(user, FROM, FROM.plusSeconds(60))).thenReturn(List.of(edit));
        var result = service.apply(user, List.of(stay), FROM, FROM.plusSeconds(60));
        assertThat(result.appliedEditIds()).containsExactly(1L);
        assertThat(((Stay) result.events().getFirst()).getVisitName()).isEqualTo("Corrected");
    }

    private TimelineEditEntity edit(TimelineEditKind kind, Instant start, Instant end) {
        return TimelineEditEntity.builder().id(1L).seq(1).kind(kind).anchorStart(start).anchorEnd(end)
                .payload(new LinkedHashMap<>(Map.of("locationName", "Cafe", "latitude", 50.0, "longitude", 30.0,
                        "movementType", "WALK", "startLatitude", 50.0, "startLongitude", 30.0,
                        "endLatitude", 50.1, "endLongitude", 30.1, "visitName", "Corrected"))).build();
    }
}
