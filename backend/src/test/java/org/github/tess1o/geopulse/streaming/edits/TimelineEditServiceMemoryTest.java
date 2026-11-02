package org.github.tess1o.geopulse.streaming.edits;

import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.repository.TimelineEditRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class TimelineEditServiceMemoryTest {
    @Test
    void noEditRebuildReusesOwnedEventsAndStillReturnsAnIndependentImmutableList() {
        var service = new TimelineEditService();
        service.editRepository = mock(TimelineEditRepository.class);
        service.applier = mock(TimelineEditApplier.class);
        when(service.editRepository.findActiveOverlapping(any(), any(), any())).thenReturn(List.of());
        Stay stay = new Stay();
        List<TimelineEvent> source = new ArrayList<>(List.of(stay));
        var result = service.apply(UUID.randomUUID(), source, Instant.EPOCH, Instant.now());
        source.clear();
        assertThat(result.events()).containsExactly(stay);
        assertThat(result.events().getFirst()).isSameAs(stay);
        assertThat(result.applications()).isEmpty();
        assertThat(result.unappliedEditIds()).isEmpty();
        verifyNoInteractions(service.applier);
    }
}
