package org.github.tess1o.geopulse.streaming.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.shared.DataGapStayOverrideLocationStrategy;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapStayOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class DataGapStayOverrideIntegrationTest {

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineDataGapStayOverrideRepository overrideRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Test
    @Transactional
    void shouldConvertGapToStayUsingLatestPointAndPersistOverride() {
        UserEntity user = createUser("latest-point");
        FavoritesEntity home = createFavorite(user, "Home", 50.4501, 30.5234);

        Instant start = Instant.parse("2026-01-10T10:00:00Z");
        Instant end = Instant.parse("2026-01-10T12:00:00Z");
        createGpsPoint(user, start, 50.4501, 30.5234);
        createGpsPoint(user, end, 50.4502, 30.5233);

        TimelineDataGapEntity gap = createDataGap(user, start, end);

        var result = dataGapStayOverrideService.convertGapToStay(user.getId(), gap.getId(), new DataGapStayOverrideRequest());

        assertThat(result).isPresent();
        assertThat(result.get().locationStrategy()).isEqualTo("LATEST_POINT");
        assertThat(dataGapRepository.findByIdAndUserId(gap.getId(), user.getId())).isEmpty();

        List<TimelineStayEntity> stays = stayRepository.findByUserAndDateRange(user.getId(), start.minusSeconds(60), end.plusSeconds(60));
        assertThat(stays).hasSize(1);
        assertThat(stays.getFirst().getFavoriteLocation()).isNotNull();
        assertThat(stays.getFirst().getFavoriteLocation().getId()).isEqualTo(home.getId());

        var overrides = overrideRepository.findByUserId(user.getId());
        assertThat(overrides).hasSize(1);
        assertThat(overrides.getFirst().getStay()).isNotNull();
        assertThat(overrides.getFirst().getLocationStrategy()).isEqualTo(DataGapStayOverrideLocationStrategy.LATEST_POINT);
    }

    @Test
    @Transactional
    void shouldConvertGapToStayUsingSelectedFavoriteLocation() {
        UserEntity user = createUser("selected-location");
        createFavorite(user, "Home", 50.4501, 30.5234);
        FavoritesEntity office = createFavorite(user, "Office", 50.4600, 30.5300);

        Instant start = Instant.parse("2026-01-11T08:00:00Z");
        Instant end = Instant.parse("2026-01-11T10:00:00Z");
        createGpsPoint(user, start, 50.4501, 30.5234);
        createGpsPoint(user, end, 50.4500, 30.5235);

        TimelineDataGapEntity gap = createDataGap(user, start, end);

        DataGapStayOverrideRequest request = new DataGapStayOverrideRequest();
        request.setLocationStrategy("SELECTED_LOCATION");
        request.setFavoriteId(office.getId());

        var result = dataGapStayOverrideService.convertGapToStay(user.getId(), gap.getId(), request);

        assertThat(result).isPresent();
        assertThat(result.get().locationStrategy()).isEqualTo("SELECTED_LOCATION");

        List<TimelineStayEntity> stays = stayRepository.findByUserAndDateRange(user.getId(), start.minusSeconds(60), end.plusSeconds(60));
        assertThat(stays).hasSize(1);
        assertThat(stays.getFirst().getFavoriteLocation()).isNotNull();
        assertThat(stays.getFirst().getFavoriteLocation().getId()).isEqualTo(office.getId());
        assertThat(stays.getFirst().getLocationName()).isEqualTo("Office");
    }

    @Test
    @Transactional
    void shouldAutoMergeBothAdjacentSameLocationStaysOnConversion() {
        UserEntity user = createUser("merge-both-sides");
        FavoritesEntity home = createFavorite(user, "Home", 50.4501, 30.5234);

        Instant previousStart = Instant.parse("2026-01-12T08:00:00Z");
        Instant gapStart = Instant.parse("2026-01-12T10:00:00Z");
        Instant gapEnd = Instant.parse("2026-01-12T12:00:00Z");
        Instant nextEnd = Instant.parse("2026-01-12T13:00:00Z");

        createGpsPoint(user, gapStart, 50.4501, 30.5234);
        createGpsPoint(user, gapEnd, 50.4501, 30.5234);

        createStay(user, home, previousStart, DurationSeconds.between(previousStart, gapStart), 50.4501, 30.5234);
        createStay(user, home, gapEnd, DurationSeconds.between(gapEnd, nextEnd), 50.4501, 30.5234);

        TimelineDataGapEntity gap = createDataGap(user, gapStart, gapEnd);

        dataGapStayOverrideService.convertGapToStay(user.getId(), gap.getId(), new DataGapStayOverrideRequest());

        List<TimelineStayEntity> stays = stayRepository.findByUserAndDateRange(user.getId(), previousStart.minusSeconds(60), nextEnd.plusSeconds(60));
        assertThat(stays).hasSize(1);

        TimelineStayEntity merged = stays.getFirst();
        assertThat(merged.getTimestamp()).isEqualTo(previousStart);
        assertThat(merged.getStayDuration()).isEqualTo(DurationSeconds.between(previousStart, nextEnd));
    }

    @Test
    @Transactional
    void shouldReapplyOverrideAfterGapReappears() {
        UserEntity user = createUser("reapply");
        createFavorite(user, "Home", 50.4501, 30.5234);

        Instant sourceStart = Instant.parse("2026-01-13T10:00:00Z");
        Instant sourceEnd = Instant.parse("2026-01-13T12:00:00Z");
        createGpsPoint(user, sourceStart, 50.4501, 30.5234);
        createGpsPoint(user, sourceEnd, 50.4501, 30.5234);

        TimelineDataGapEntity sourceGap = createDataGap(user, sourceStart, sourceEnd);
        dataGapStayOverrideService.convertGapToStay(user.getId(), sourceGap.getId(), new DataGapStayOverrideRequest());
        entityManager.flush();
        entityManager.clear();

        stayRepository.delete("user.id = ?1", user.getId());
        entityManager.flush();

        Instant regeneratedStart = sourceStart.plusSeconds(300);
        Instant regeneratedEnd = sourceEnd.plusSeconds(300);
        createGpsPoint(user, regeneratedStart, 50.4502, 30.5234);
        createGpsPoint(user, regeneratedEnd, 50.4502, 30.5234);
        TimelineDataGapEntity regeneratedGap = createDataGap(user, regeneratedStart, regeneratedEnd);

        int applied = dataGapStayOverrideService.reapplyManualOverrides(user.getId());

        assertThat(applied).isEqualTo(1);
        assertThat(dataGapRepository.findByIdOptional(regeneratedGap.getId())).isEmpty();

        List<TimelineStayEntity> stays = stayRepository.findByUserAndDateRange(user.getId(), regeneratedStart.minusSeconds(60), regeneratedEnd.plusSeconds(60));
        assertThat(stays).hasSize(1);

        var overrides = overrideRepository.findByUserId(user.getId());
        assertThat(overrides).hasSize(1);
        assertThat(overrides.getFirst().getStay()).isNotNull();
    }

    @Test
    @Transactional
    void shouldRejectConversionForOngoingGap() {
        UserEntity user = createUser("ongoing");
        createFavorite(user, "Home", 50.4501, 30.5234);

        Instant latestPoint = Instant.parse("2026-01-14T10:00:00Z");
        Instant ongoingEnd = Instant.parse("2026-01-14T12:00:00Z");
        createGpsPoint(user, latestPoint, 50.4501, 30.5234);

        TimelineDataGapEntity ongoingGap = createDataGap(user, latestPoint, ongoingEnd);

        assertThatThrownBy(() -> dataGapStayOverrideService.convertGapToStay(user.getId(), ongoingGap.getId(), new DataGapStayOverrideRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ongoing data gap");
    }

    private UserEntity createUser(String suffix) {
        UserEntity user = new UserEntity();
        user.setEmail(TestIds.uniqueEmail("gap-override-" + suffix));
        user.setFullName("Gap Override " + suffix);
        user.setPasswordHash("test");
        userRepository.persist(user);
        return user;
    }

    private FavoritesEntity createFavorite(UserEntity user, String name, double lat, double lon) {
        FavoritesEntity favorite = FavoritesEntity.builder()
                .user(user)
                .name(name)
                .type(FavoriteLocationType.POINT)
                .geometry(GeoUtils.createPoint(lon, lat))
                .mergeImpact(Boolean.FALSE)
                .build();
        favoritesRepository.persist(favorite);
        entityManager.flush();
        return favorite;
    }

    private GpsPointEntity createGpsPoint(UserEntity user, Instant timestamp, double lat, double lon) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(user);
        point.setTimestamp(timestamp);
        point.setCoordinates(GeoUtils.createPoint(lon, lat));
        point.setAccuracy(5.0);
        point.setVelocity(0.0);
        gpsPointRepository.persist(point);
        return point;
    }

    private TimelineDataGapEntity createDataGap(UserEntity user, Instant start, Instant end) {
        TimelineDataGapEntity gap = TimelineDataGapEntity.builder()
                .user(user)
                .startTime(start)
                .endTime(end)
                .durationSeconds(DurationSeconds.between(start, end))
                .build();
        dataGapRepository.persist(gap);
        entityManager.flush();
        return gap;
    }

    private TimelineStayEntity createStay(UserEntity user,
                                          FavoritesEntity favorite,
                                          Instant start,
                                          long durationSeconds,
                                          double lat,
                                          double lon) {
        TimelineStayEntity stay = TimelineStayEntity.builder()
                .user(user)
                .timestamp(start)
                .stayDuration(durationSeconds)
                .location(GeoUtils.createPoint(lon, lat))
                .locationName(favorite.getName())
                .favoriteLocation(favorite)
                .locationSource(LocationSource.FAVORITE)
                .build();
        stayRepository.persist(stay);
        entityManager.flush();
        return stay;
    }

    private static final class DurationSeconds {
        private static long between(Instant start, Instant end) {
            return java.time.Duration.between(start, end).getSeconds();
        }
    }
}
