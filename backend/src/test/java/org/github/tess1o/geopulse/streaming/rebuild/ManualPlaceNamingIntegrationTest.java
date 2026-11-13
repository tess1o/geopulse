package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Issue 667: a hand-picked point can be given a name through the ordinary place edit. The name is the stay's location
 * name, not a visit label, needs no Favorite or geocoding record, and survives rebuilds.
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
        label.getPayload().put("visitName", "Parking spot");
        edits.create(user, label);
        harness.fullRebuild(user);

        assertNamed(user, "Car Park", "Parking spot");
        TimelineStayEntity other = QuarkusTransaction.requiringNew().call(() -> stays
                .find("user.id = ?1 and timestamp = ?2", user, at("16:00")).singleResult());
        assertThat(other.getLocationName()).isEqualTo("Other place");
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
        request.getPayload().put("activityCorrection", true);
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
