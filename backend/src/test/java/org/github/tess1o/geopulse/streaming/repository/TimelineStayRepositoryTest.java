package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
@TestTransaction
class TimelineStayRepositoryTest {
    @Inject
    TimelineStayRepository repository;

    @Inject
    EntityManager entityManager;

    @Test
    void nearestStaysIncludeCloserStayBeyondPlanarLimitAndExcludeOtherUsers() {
        UserEntity user = createUser();
        for (int i = 1; i <= 5; i++) {
            createStay(user, "North " + i, 70.3 + i * 0.001, 20.0);
        }
        createStay(user, "East", 70.0, 20.5);
        createStay(createUser(), "Other user's stay", 70.0, 20.0);
        entityManager.flush();

        var rows = repository.findNearestLocationLookupStays(user.getId(), GeoUtils.createPoint(20.0, 70.0), 5);

        assertThat(rows).extracting(row -> row[3])
                .containsExactly("East", "North 1", "North 2", "North 3", "North 4");
        assertThat(((Number) rows.getFirst()[8]).doubleValue()).isBetween(19_000.0, 20_000.0);
    }

    @Test
    void nearestStaysCrossDateLine() {
        UserEntity user = createUser();
        createStay(user, "West", 0.0, 179.0);
        createStay(user, "Across date line", 0.0, -179.9);
        entityManager.flush();

        assertThat(repository.findNearestLocationLookupStays(user.getId(), GeoUtils.createPoint(179.9, 0.0), 1))
                .extracting(row -> row[3]).containsExactly("Across date line");
    }

    @Test
    void nearestStaysUseDisplayedSpheroidDistanceEvenWhenSphereOrderDiffers() {
        UserEntity user = createUser();
        createStay(user, "North", 1.0, 0.0);
        createStay(user, "East", 0.0, 0.998);
        entityManager.flush();

        // East is closer on a sphere, but North is closer on the WGS84 ellipsoid used for display.
        var rows = repository.findNearestLocationLookupStays(user.getId(), GeoUtils.createPoint(0.0, 0.0), 1);

        assertThat(rows).extracting(row -> row[3]).containsExactly("North");
        assertThat(((Number) rows.getFirst()[8]).doubleValue()).isBetween(110_500.0, 110_600.0);
    }

    @Test
    void nearestStaysHandleEmptyUsersAndFewerStaysThanLimit() {
        assertThat(repository.findNearestLocationLookupStays(UUID.randomUUID(), GeoUtils.createPoint(20.0, 70.0), 5))
                .isEmpty();
        UserEntity user = createUser();
        createStay(user, "Only stay", 70.0, 20.0);
        entityManager.flush();

        assertThat(repository.findNearestLocationLookupStays(user.getId(), GeoUtils.createPoint(20.0, 70.0), 5))
                .extracting(row -> row[3]).containsExactly("Only stay");
        assertThat(repository.findNearestLocationLookupStays(user.getId(), GeoUtils.createPoint(20.0, 70.0), 0))
                .isEmpty();
    }

    private UserEntity createUser() {
        UserEntity user = UserEntity.builder()
                .email(TestIds.uniqueEmail("nearest-stay"))
                .fullName("Nearest stay test")
                .timezone("UTC")
                .isActive(true)
                .build();
        entityManager.persist(user);
        return user;
    }

    private void createStay(UserEntity user, String name, double latitude, double longitude) {
        entityManager.persist(TimelineStayEntity.builder()
                .user(user)
                .timestamp(Instant.parse("2026-01-01T12:00:00Z"))
                .stayDuration(600)
                .location(GeoUtils.createPoint(longitude, latitude))
                .locationName(name)
                .build());
    }
}
