package org.github.tess1o.geopulse.streaming.model.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class UserStateTest {
    @Test
    void incrementalCentroidExactlyMatchesOriginalSummationAndRounding() {
        UserState state = new UserState();
        List<GPSPoint> reference = new ArrayList<>();
        Random random = new Random(642);
        assertThat(state.calculateCentroid()).isNull();
        for (int i = 0; i < 1024; i++) {
            GPSPoint point = new GPSPoint(random.nextDouble() * 160 - 80, random.nextDouble() * 360 - 180, 0, 0);
            state.addActivePoint(point);
            reference.add(point);
            double latitude = 0;
            double longitude = 0;
            for (GPSPoint p : reference) {
                latitude += p.getLatitude();
                longitude += p.getLongitude();
            }
            assertThat(state.calculateCentroid().getLatitude()).isEqualTo(round(latitude / reference.size()));
            assertThat(state.calculateCentroid().getLongitude()).isEqualTo(round(longitude / reference.size()));
        }
    }

    @Test
    void readOnlyViewIsReusedAndSnapshotsSurviveClearing() {
        UserState state = new UserState();
        List<GPSPoint> view = state.getActivePoints();
        GPSPoint point = new GPSPoint(50, 30, 0, 0);
        state.addActivePoint(point);
        assertThat(state.getActivePoints()).isSameAs(view).containsExactly(point);
        assertThatThrownBy(() -> view.add(point)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> view.set(0, point)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> view.subList(0, 1).clear()).isInstanceOf(UnsupportedOperationException.class);
        List<GPSPoint> snapshot = state.copyActivePoints();
        state.clearActivePoints();
        assertThat(view).isEmpty();
        assertThat(snapshot).containsExactly(point);
        assertThat(snapshot.getFirst()).isSameAs(point);
        snapshot.clear();
        state.addActivePoint(new GPSPoint(-40, -120, 0, 0));
        assertThat(state.calculateCentroid().getLatitude()).isEqualTo(-40);
        assertThat(state.calculateCentroid().getLongitude()).isEqualTo(-120);
    }

    @Test
    void resetAfterALargeSegmentResetsSumsAndKeepsTheViewValid() {
        UserState state = new UserState();
        List<GPSPoint> view = state.getActivePoints();
        for (int i = 0; i < 10001; i++) state.addActivePoint(new GPSPoint(70, 160, 0, 0));
        state.setCurrentMode(ProcessorMode.CONFIRMED_STAY);
        state.reset();
        assertThat(state.calculateCentroid()).isNull();
        assertThat(state.getCurrentMode()).isEqualTo(ProcessorMode.UNKNOWN);
        assertThat(view).isEmpty();
        state.addActivePoint(new GPSPoint(10.123456785, -10.123456785, 0, 0));
        assertThat(view).hasSize(1);
        assertThat(state.calculateCentroid().getLatitude()).isEqualTo(round(10.123456785));
        assertThat(state.calculateCentroid().getLongitude()).isEqualTo(round(-10.123456785));
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).doubleValue();
    }
}
