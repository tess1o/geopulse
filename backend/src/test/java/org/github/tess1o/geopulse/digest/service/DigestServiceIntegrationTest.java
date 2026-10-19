package org.github.tess1o.geopulse.digest.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.digest.model.DigestMetrics;
import org.github.tess1o.geopulse.digest.model.PeriodComparison;
import org.github.tess1o.geopulse.digest.model.TimeDigest;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.statistics.model.TopPlace;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Monthly and yearly digests for a user in Europe/Kyiv (UTC+2 in winter), computed from the seeded stays and
 * trips below. Two stays sit on month boundaries that differ between UTC and local time.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class DigestServiceIntegrationTest {

    private static final String TIMEZONE = "Europe/Kyiv";

    @Inject
    DigestService digestService;

    @Inject
    TimelineSeeder seed;

    private UUID userId;
    private String cafeName;

    /*
     * January 2026: stay Home 600s on Jan 15, trip CAR 8.5 km / 1000s on Jan 15.
     * February 2026 (local):
     *   stays: Home 3600s at 2026-01-31T22:30Z (Feb 1 00:30 local), Office 7200s on Feb 10,
     *          Home 3600s on Feb 10, Cafe (another city) 1800s on Feb 20
     *   trips: CAR 10 km / 1800s and WALK 2 km / 1200s on Feb 10, WALK 5 km / 900s on Feb 20
     * March 2026 (local): Home 900s at 2026-02-28T22:30Z (Mar 1 00:30 local).
     */
    @BeforeEach
    @Transactional
    void setUp() {
        String token = TestIds.uniqueValue("digest");
        UserEntity user = seed.user("digest", TIMEZONE);
        userId = user.getId();
        FavoritesEntity home = seed.favorite(user, "Home", "Kyiv " + token, "Ukraine", 50.4501, 30.5234);
        FavoritesEntity office = seed.favorite(user, "Office", "Kyiv " + token, "Ukraine", 50.4400, 30.5100);
        cafeName = "Cafe " + token;
        ReverseGeocodingLocationEntity cafe = seed.geocoding(user, cafeName, "Lviv " + token, "Ukraine", 49.8397, 24.0297);

        seed.stay(user, home, Instant.parse("2026-01-15T12:00:00Z"), 600);
        seed.trip(user, Instant.parse("2026-01-15T10:00:00Z"), 8500, 1000, "CAR");

        seed.stay(user, home, Instant.parse("2026-01-31T22:30:00Z"), 3600);
        seed.stay(user, office, Instant.parse("2026-02-10T08:00:00Z"), 7200);
        seed.stay(user, home, Instant.parse("2026-02-10T18:00:00Z"), 3600);
        seed.stay(user, cafe, Instant.parse("2026-02-20T10:00:00Z"), 1800);
        seed.trip(user, Instant.parse("2026-02-10T07:00:00Z"), 10000, 1800, "CAR");
        seed.trip(user, Instant.parse("2026-02-10T17:00:00Z"), 2000, 1200, "WALK");
        seed.trip(user, Instant.parse("2026-02-20T09:00:00Z"), 5000, 900, "WALK");

        seed.stay(user, home, Instant.parse("2026-02-28T22:30:00Z"), 900);
    }

    @Test
    void monthlyDigest_countsTheLocalMonth() {
        TimeDigest digest = digestService.getMonthlyDigest(userId, 2026, 2, TIMEZONE);

        assertThat(digest.getPeriod().getYear()).isEqualTo(2026);
        assertThat(digest.getPeriod().getMonth()).isEqualTo(2);

        DigestMetrics metrics = digest.getMetrics();
        assertThat(metrics.getTotalDistance()).isEqualTo(17000.0);
        assertThat(metrics.getTripCount()).isEqualTo(3);
        assertThat(metrics.getStayCount()).as("Feb 1 00:30 local is in, Mar 1 00:30 local is out").isEqualTo(4);
        assertThat(metrics.getActiveDays()).as("Feb 1, Feb 10 and Feb 20").isEqualTo(3);
        assertThat(metrics.getCitiesVisited()).isEqualTo(2);
        assertThat(metrics.getCarDistance()).isCloseTo(10000.0, offset(0.01));
        assertThat(metrics.getWalkDistance()).isCloseTo(7000.0, offset(0.01));
        assertThat(metrics.getBicycleDistance()).isZero();
        assertThat(metrics.getTimeMoving()).isEqualTo(3900);
    }

    @Test
    void monthlyDigest_comparesWithThePreviousMonth() {
        PeriodComparison comparison = digestService.getMonthlyDigest(userId, 2026, 2, TIMEZONE).getComparison();

        assertThat(comparison.getTotalDistance()).as("January distance").isEqualTo(8500.0);
        assertThat(comparison.getPercentChange()).isEqualTo(100.0);
        assertThat(comparison.getDirection()).isEqualTo("increase");
    }

    @Test
    void monthlyDigest_ranksTopPlacesByVisits() {
        TimeDigest digest = digestService.getMonthlyDigest(userId, 2026, 2, TIMEZONE);

        assertThat(digest.getTopPlaces())
                .extracting(TopPlace::getName, TopPlace::getVisits, TopPlace::getDuration)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Home", 2L, 7200L),
                        org.assertj.core.groups.Tuple.tuple("Office", 1L, 7200L),
                        org.assertj.core.groups.Tuple.tuple(cafeName, 1L, 1800L));
    }

    @Test
    void monthlyDigest_previousMonthOnlyHasItsOwnData() {
        DigestMetrics january = digestService.getMonthlyDigest(userId, 2026, 1, TIMEZONE).getMetrics();

        assertThat(january.getTotalDistance()).isEqualTo(8500.0);
        assertThat(january.getTripCount()).isEqualTo(1);
        assertThat(january.getStayCount()).as("Jan 31 22:30 UTC belongs to February locally").isEqualTo(1);
        assertThat(january.getActiveDays()).isEqualTo(1);
    }

    @Test
    void yearlyDigest_coversTheWholeLocalYear() {
        TimeDigest digest = digestService.getYearlyDigest(userId, 2026, TIMEZONE);

        assertThat(digest.getPeriod().getMonth()).isNull();
        DigestMetrics metrics = digest.getMetrics();
        assertThat(metrics.getTotalDistance()).isEqualTo(25500.0);
        assertThat(metrics.getTripCount()).isEqualTo(4);
        assertThat(metrics.getStayCount()).isEqualTo(6);
        assertThat(metrics.getActiveDays()).as("Jan 15, Feb 1, Feb 10, Feb 20, Mar 1").isEqualTo(5);
        assertThat(metrics.getCitiesVisited()).isEqualTo(2);
        assertThat(metrics.getTimeMoving()).isEqualTo(4900);
        assertThat(digest.getComparison().getDirection()).as("nothing in 2025").isEqualTo("increase");
    }

    @Test
    void digestForAnEmptyPeriodIsZero() {
        DigestMetrics metrics = digestService.getMonthlyDigest(userId, 2025, 6, TIMEZONE).getMetrics();

        assertThat(metrics.getTotalDistance()).isZero();
        assertThat(metrics.getTripCount()).isZero();
        assertThat(metrics.getStayCount()).isZero();
        assertThat(metrics.getActiveDays()).isZero();
    }
}
