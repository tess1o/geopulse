package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.set;

/**
 * A regeneration folds and records every active correction in its range. The statements it runs must not grow with
 * the number of corrections: on a large account a per-correction query turns every rebuild into thousands of round
 * trips.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class CorrectionRebuildQueryCountIntegrationTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 3, 2);
    private static final int FEW = 5;
    private static final int MANY = 45;

    @Inject TimelineReplayHarness harness;
    @Inject TimelineEditService edits;
    @Inject TimelineStayRepository stays;
    @Inject EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void mocks() {
        GeocodingTestMocks.install();
    }

    @Test
    void regenerationStatementsDoNotGrowWithTheNumberOfCorrections() {
        long few = statementsForRebuildWith(FEW);
        long many = statementsForRebuildWith(MANY);

        assertThat(many - few)
                .as("statements of a full rebuild with %d corrections (%d) minus with %d corrections (%d)",
                        MANY, many, FEW, few)
                .isLessThan(10);
    }

    private long statementsForRebuildWith(int corrections) {
        UUID user = harness.createUserWithFavoriteAreas("query-count-" + corrections, SyntheticDays.NAMED_PLACES);
        harness.seedRecording(user, SyntheticDays.generate(11, FIRST_DAY, 21));
        harness.fullRebuild(user);

        List<TimelineStayEntity> labelled = QuarkusTransaction.requiringNew().call(() ->
                stays.find("user.id = ?1 order by timestamp", user).page(0, corrections).list());
        assertThat(labelled).hasSize(corrections);
        for (int i = 0; i < labelled.size(); i++) {
            TimelineStayEntity stay = labelled.get(i);
            var request = new TimelineEditRequest();
            request.setKind(TimelineEditKind.SET_VISIT_NAME);
            request.setAnchorStart(stay.getTimestamp());
            request.setAnchorEnd(stay.getTimestamp().plusSeconds(stay.getStayDuration()));
            // A batch save folds once up front; the per-correction review is not what is measured here.
            request.setPending(true);
            set(request, new LinkedHashMap<>(Map.of("visitName", "Visit " + i)));
            QuarkusTransaction.requiringNew().run(() -> edits.create(user, request));
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            harness.fullRebuild(user);
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }
}
