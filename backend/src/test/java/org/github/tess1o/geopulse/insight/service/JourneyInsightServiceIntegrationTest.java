package org.github.tess1o.geopulse.insight.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.insight.model.City;
import org.github.tess1o.geopulse.insight.model.Country;
import org.github.tess1o.geopulse.insight.model.DistanceTraveled;
import org.github.tess1o.geopulse.insight.model.JourneyInsights;
import org.github.tess1o.geopulse.insight.model.TimePatterns;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Lifetime insights computed from the seeded stays and trips below. The user is in UTC because the day, hour
 * and month patterns are computed in the database's timezone.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class JourneyInsightServiceIntegrationTest {

    @Inject
    JourneyInsightService service;

    @Inject
    TimelineSeeder seed;

    private UUID userId;
    private UUID emptyUserId;
    private String cityA;
    private String cityB;
    private String countryX;
    private String countryY;

    /*
     * Stays: cityA (countryX) on Wed Jan 7 10:00 and 10:20 and Mon Jan 5 08:00; cityB (countryY) on Fri Feb 6 15:00.
     * Trips: CAR 12.345 km on Wed Jan 7 10:40, CAR 2 km on Mon Jan 5 09:00, WALK 3.5 km on Sat Feb 7 18:00,
     *        BICYCLE 0.999 km on Sun Feb 8 19:00.
     * So Wednesday and 10:00 have the most activity (3 each) and January the most of any month (5).
     */
    @BeforeEach
    @Transactional
    void setUp() {
        String token = TestIds.uniqueValue("insight");
        cityA = "Alpha " + token;
        cityB = "Beta " + token;
        countryX = "Xland " + token;
        countryY = "Yland " + token;

        UserEntity user = seed.user("insights", "UTC");
        userId = user.getId();
        FavoritesEntity home = seed.favorite(user, "Home", cityA, countryX, 50.45, 30.52);
        FavoritesEntity office = seed.favorite(user, "Office", cityA, countryX, 50.44, 30.51);
        FavoritesEntity hotel = seed.favorite(user, "Hotel", cityB, countryY, 48.85, 2.35);

        seed.stay(user, home, Instant.parse("2026-01-07T10:00:00Z"), 600);
        seed.stay(user, office, Instant.parse("2026-01-07T10:20:00Z"), 600);
        seed.stay(user, home, Instant.parse("2026-01-05T08:00:00Z"), 600);
        seed.stay(user, hotel, Instant.parse("2026-02-06T15:00:00Z"), 600);
        seed.trip(user, Instant.parse("2026-01-07T10:40:00Z"), 12345, 900, "CAR");
        seed.trip(user, Instant.parse("2026-01-05T09:00:00Z"), 2000, 300, "CAR");
        seed.trip(user, Instant.parse("2026-02-07T18:00:00Z"), 3500, 2400, "WALK");
        seed.trip(user, Instant.parse("2026-02-08T19:00:00Z"), 999, 300, "BICYCLE");

        emptyUserId = seed.user("insights-empty", "UTC").getId();
    }

    @Test
    void geographicInsights_listCountriesAndCitiesWithVisits() {
        JourneyInsights insights = service.getJourneyInsights(userId);

        assertThat(insights.getGeographic().getCountries())
                .extracting(Country::getName)
                .containsExactlyInAnyOrder(countryX, countryY);
        assertThat(insights.getGeographic().getCities())
                .extracting(City::getName, City::getVisits)
                .containsExactly(tuple(cityA, 3), tuple(cityB, 1));
    }

    @Test
    void distanceTraveled_sumsWholeKilometersPerMovementType() {
        DistanceTraveled distance = service.getJourneyInsights(userId).getDistanceTraveled();

        assertThat(distance.getByCar()).as("12.345 + 2 km").isEqualTo(14);
        assertThat(distance.getByWalk()).isEqualTo(3);
        assertThat(distance.getByBicycle()).as("under 1 km rounds down").isZero();
        assertThat(distance.getByTrain()).isZero();
        assertThat(distance.getTotal()).isEqualTo(17);
    }

    @Test
    void timePatterns_findTheBusiestDayHourAndMonth() {
        TimePatterns patterns = service.getJourneyInsights(userId).getTimePatterns();

        assertThat(patterns.getBusiestDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY.getValue());
        assertThat(patterns.getMostActiveHour()).isEqualTo(10);
        assertThat(patterns.getMostActiveYearMonth()).isEqualTo("2026-01");
        assertThat(patterns.getDayInsight().key()).endsWith("dayInsight.midweek");
    }

    @Test
    void achievements_areInitialisedForTheUser() {
        assertThat(service.getJourneyInsights(userId).getAchievements().getBadges()).isNotEmpty();
    }

    @Test
    void userWithoutData_hasEmptyInsights() {
        JourneyInsights insights = service.getJourneyInsights(emptyUserId);

        assertThat(insights.getGeographic().getCountries()).isEmpty();
        assertThat(insights.getGeographic().getCities()).isEmpty();
        assertThat(insights.getDistanceTraveled().getTotal()).isZero();
        assertThat(insights.getTimePatterns().getBusiestDayOfWeek()).isNull();
        assertThat(insights.getTimePatterns().getMostActiveYearMonth()).isNull();
    }
}
