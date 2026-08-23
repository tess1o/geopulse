package org.github.tess1o.geopulse.mapmatching.repository;

import org.github.tess1o.geopulse.mapmatching.model.MapMatchingStatus;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@Tag("unit")
class TimelineTripPathMatchRepositoryTest {

    @Test
    void directFailureIsTerminalAndCompleted() {
        TimelineTripPathMatchEntity target = TimelineTripPathMatchEntity.builder()
                .id(1L)
                .status(MapMatchingStatus.PROCESSING)
                .build();
        TimelineTripPathMatchRepository repository = repositoryReturning(target);

        repository.markFailed(1L, "deterministic failure");

        assertThat(target.getStatus()).isEqualTo(MapMatchingStatus.FAILED);
        assertThat(target.getCompletedAt()).isNotNull();
        assertThat(target.getLastError()).isEqualTo("deterministic failure");
    }

    @Test
    void exhaustedRetryIsTerminalAndCompleted() {
        TimelineTripPathMatchEntity target = TimelineTripPathMatchEntity.builder()
                .id(1L)
                .status(MapMatchingStatus.PROCESSING)
                .attempts(3)
                .build();
        TimelineTripPathMatchRepository repository = repositoryReturning(target);

        repository.markFailedOrRetry(1L, "transient failure", 3);

        assertThat(target.getStatus()).isEqualTo(MapMatchingStatus.FAILED);
        assertThat(target.getCompletedAt()).isNotNull();
    }

    private TimelineTripPathMatchRepository repositoryReturning(TimelineTripPathMatchEntity target) {
        TimelineTripPathMatchRepository repository = spy(new TimelineTripPathMatchRepository());
        doReturn(Optional.of(target)).when(repository).findByIdOptional(target.getId());
        return repository;
    }
}
