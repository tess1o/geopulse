package org.github.tess1o.geopulse.streaming.rebuild;

import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.put;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.EditFavoriteDto;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingUpdateDTO;
import org.github.tess1o.geopulse.geocoding.service.ReverseGeocodingManagementService;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.payload.SetPlacePayload;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Issue 667: a hand-picked point can be given a name through the ordinary place edit. The name is the stay's location
 * name, not a visit label, needs no Favorite or geocoding record, and survives rebuilds.
 * <p>
 * Issue 677: a short visit added by hand and named after the nearby road can be assigned a place already known
 * elsewhere in the timeline, or a Favorite, through the same place edit. The stay then belongs to that place: it keeps
 * it through rebuilds and follows a later rename of the place.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class ManualPlaceNamingIntegrationTest {

    private static final double KNOWLE_LANE_LAT = 54.20377578050423;
    private static final double KNOWLE_LANE_LON = -1.7261908173302964;

    @Inject TimelineReplayHarness harness;
    @Inject TimelineEditService edits;
    @Inject TimelineStayRepository stays;
    @Inject ActivityCorrectionService corrections;
    @Inject FavoriteLocationService favoriteLocationService;
    @Inject FavoritesRepository favoritesRepository;
    @Inject ReverseGeocodingManagementService geocodingManagementService;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setup() {
        GeocodingTestMocks.install();
    }

    @Test
    void aCustomPointOnAForcedStayCanBeNamed() {
        var user = harness.createUser("place-name");
        forceStay(user, "14:24", "15:24", KNOWLE_LANE_LAT, KNOWLE_LANE_LON, "Knowle Lane");
        forceStay(user, "16:00", "16:30", 54.21, -1.72, "Other place");
        harness.fullRebuild(user);

        renamePlace(user, "Car Park");

        // The live pass must apply the new name; a full rebuild must keep it.
        harness.process(user);
        assertNamed(user, "Car Park", null);
        harness.fullRebuild(user);
        assertNamed(user, "Car Park", null);

        var label = new TimelineEditRequest();
        label.setKind(TimelineEditKind.SET_VISIT_NAME);
        label.setAnchorStart(at("14:24"));
        label.setAnchorEnd(at("15:24"));
        label.setAffectedFrom(at("14:24"));
        label.setAffectedTo(at("15:24"));
        put(label, "visitName", "Parking spot");
        edits.create(user, label);
        harness.fullRebuild(user);

        assertNamed(user, "Car Park", "Parking spot");
        TimelineStayEntity other = QuarkusTransaction.requiringNew().call(() -> stays
                .find("user.id = ?1 and timestamp = ?2", user, at("16:00")).singleResult());
        assertThat(other.getLocationName()).isEqualTo("Other place");
    }

    @Test
    void aManuallyAddedStayCanBeAssignedAPlaceKnownElsewhereInTheTimeline() {
        UUID user = day.ingest(harness, "assign-known-place");
        addShortVisitNamedAfterTheRoad(user);
        TimelineStayEntity office = stayStarting(user, at("08:46"));
        long officePlace = office.getGeocodingLocation().getId();
        String officeName = office.getLocationName();
        String homeName = stayStarting(user, at("06:00")).getLocationName();

        edits.saveGrouped(user, placeEdit(SetPlacePayload.builder().geocodingId(officePlace).build()));
        harness.fullRebuild(user);

        TimelineStayEntity visit = stayStarting(user, at("08:16"));
        assertThat(visit.getGeocodingLocation()).isNotNull();
        assertThat(visit.getGeocodingLocation().getId()).isEqualTo(officePlace);
        assertThat(visit.getFavoriteLocation()).isNull();
        assertThat(visit.getLocationName()).isEqualTo(officeName);
        assertThat(visit.getStayDuration()).as("the place edit keeps the visit's time").isEqualTo(240);
        assertThat(stayStarting(user, at("06:00")).getLocationName()).as("other stays are untouched")
                .isEqualTo(homeName);

        geocodingManagementService.updateGeocodingResult(user, officePlace,
                new ReverseGeocodingUpdateDTO("Corner Shop", null, null));
        harness.fullRebuild(user);
        assertThat(stayStarting(user, at("08:16")).getLocationName()).as("the visit follows the renamed place")
                .isEqualTo("Corner Shop");
    }

    @Test
    void aManuallyAddedStayCanBeAssignedAFavorite() {
        UUID user = day.ingest(harness, "assign-favorite");
        addShortVisitNamedAfterTheRoad(user);
        String homeName = stayStarting(user, at("06:00")).getLocationName();
        favoriteLocationService.addFavorite(user, new AddPointToFavoritesDto("Corner Bakery", day.bakery[0], day.bakery[1]));
        long favoriteId = QuarkusTransaction.requiringNew().call(() ->
                favoritesRepository.find("user.id = ?1 and name = ?2", user, "Corner Bakery").firstResult().getId());

        edits.saveGrouped(user, placeEdit(SetPlacePayload.builder().favoriteId(favoriteId).build()));
        harness.fullRebuild(user);

        TimelineStayEntity visit = stayStarting(user, at("08:16"));
        assertThat(visit.getFavoriteLocation()).isNotNull();
        assertThat(visit.getFavoriteLocation().getId()).isEqualTo(favoriteId);
        assertThat(visit.getGeocodingLocation()).isNull();
        assertThat(visit.getLocationName()).isEqualTo("Corner Bakery");
        assertThat(visit.getStayDuration()).isEqualTo(240);
        assertThat(stayStarting(user, at("06:00")).getLocationName()).isEqualTo(homeName);

        favoriteLocationService.updateFavorite(user, favoriteId,
                new EditFavoriteDto("The Bakery", null, null, null, null, null, null));
        harness.fullRebuild(user);
        assertThat(stayStarting(user, at("08:16")).getLocationName()).as("the visit follows the renamed favorite")
                .isEqualTo("The Bakery");
    }

    /** The reporter's case: a four-minute visit, below the stay threshold, added by hand with the road's name. */
    private void addShortVisitNamedAfterTheRoad(UUID user) {
        var visit = new ActivityCorrectionService.Activity();
        visit.setSegmentType("STAY");
        visit.setStartTime(at("08:16"));
        visit.setEndTime(at("08:20"));
        visit.setLatitude(day.bakery[0]);
        visit.setLongitude(day.bakery[1]);
        visit.setLocationName("Station Road");
        corrections.save(user, new ActivityCorrectionService.Request(null, List.of(visit), null, null));
        harness.fullRebuild(user);
        assertThat(stayStarting(user, at("08:16")).getLocationName()).isEqualTo("Station Road");
    }

    /** The drawer's Location action on the manually added visit. */
    private static TimelineEditRequest placeEdit(SetPlacePayload payload) {
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_PLACE);
        request.setAnchorStart(at("08:16"));
        request.setAnchorEnd(at("08:20"));
        request.setPayload(payload);
        return request;
    }

    private TimelineStayEntity stayStarting(UUID user, java.time.Instant start) {
        return QuarkusTransaction.requiringNew().call(() -> {
            TimelineStayEntity stay = stays.find("user.id = ?1 and timestamp = ?2", user, start)
                    .firstResultOptional()
                    .orElseThrow(() -> new AssertionError("No stay starts at " + start + ": "
                            + harness.snapshot(user).stays()));
            if (stay.getGeocodingLocation() != null) stay.getGeocodingLocation().getId();
            if (stay.getFavoriteLocation() != null) stay.getFavoriteLocation().getId();
            return stay;
        });
    }

    private void renamePlace(UUID user, String name) {
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_PLACE);
        request.setAnchorStart(at("14:24"));
        request.setAnchorEnd(at("15:24"));
        request.setAffectedFrom(at("14:24"));
        request.setAffectedTo(at("15:24"));
        request.setLatitude(KNOWLE_LANE_LAT);
        request.setLongitude(KNOWLE_LANE_LON);
        request.setPlaceName(name);
        edits.create(user, request);
    }

    private void forceStay(UUID user, String from, String to, double lat, double lon, String name) {
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.FORCE_STAY);
        request.setAnchorStart(at(from));
        request.setAnchorEnd(at(to));
        request.setAffectedFrom(at(from));
        request.setAffectedTo(at(to));
        request.setLatitude(lat);
        request.setLongitude(lon);
        request.setPlaceName(name);
        put(request, "activityCorrection", true);
        edits.create(user, request);
    }

    private void assertNamed(UUID user, String locationName, String visitName) {
        TimelineStayEntity stay = QuarkusTransaction.requiringNew().call(() -> stays
                .find("user.id = ?1 and timestamp = ?2", user, at("14:24")).singleResult());
        assertThat(stay.getLocationName()).isEqualTo(locationName);
        assertThat(stay.getVisitName()).isEqualTo(visitName);
        assertThat(stay.getFavoriteLocation()).isNull();
        assertThat(stay.getGeocodingLocation()).isNull();
        assertThat(stay.getLocation().getY()).isCloseTo(KNOWLE_LANE_LAT, within(1e-6));
        assertThat(stay.getLocation().getX()).isCloseTo(KNOWLE_LANE_LON, within(1e-6));
    }

    private static java.time.Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
