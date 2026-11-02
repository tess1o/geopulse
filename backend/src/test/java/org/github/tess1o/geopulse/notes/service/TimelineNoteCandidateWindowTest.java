package org.github.tess1o.geopulse.notes.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TimelineNoteCandidateWindowTest {
    @Test
    void queryWindowMatchesExistingWholeSecondScoringAtFractionalBoundaries() {
        var service = new TimelineNoteAnchorReattachmentService();
        service.maxTimestampDeltaSeconds = 21600;
        Instant anchor = Instant.parse("2026-01-01T12:00:00.123456Z");
        for (long seconds : new long[]{-21601, -21600, -21599, 0, 21599, 21600, 21601}) {
            for (long nanos : new long[]{0, 1, 999999999}) {
                Instant candidate = anchor.plusSeconds(seconds).plusNanos(nanos);
                boolean eligible = Math.abs(Duration.between(anchor, candidate).getSeconds()) <= 21600;
                boolean inQuery = !candidate.isBefore(anchor.minusSeconds(21600))
                        && candidate.isBefore(service.candidateEnd(anchor));
                assertThat(inQuery).as("candidate %s", candidate).isEqualTo(eligible);
            }
        }
    }
}
