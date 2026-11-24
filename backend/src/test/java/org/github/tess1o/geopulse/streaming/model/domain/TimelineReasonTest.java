package org.github.tess1o.geopulse.streaming.model.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class TimelineReasonTest {

    @Test
    void paramsKeepTheirOrderAndValues() {
        TimelineReason reason = TimelineReason.of(TimelineReasons.ARRIVAL, "stopSeconds", 240L, "points", 3,
                "stopSpeedKmh", 9.5);

        assertThat(reason.params()).containsExactly(
                Map.entry("stopSeconds", 240L), Map.entry("points", 3), Map.entry("stopSpeedKmh", 9.5));
        assertThat(reason.params().get("points")).isEqualTo(3);
        assertThat(reason.params().get("missing")).isNull();
        assertThat(reason.params()).hasSize(3).containsKey("stopSpeedKmh").doesNotContainKey("missing");
    }

    @Test
    void aRepeatedNameKeepsItsFirstPositionAndLastValue() {
        TimelineReason reason = TimelineReason.of("CODE", "a", 1, "b", 2, "a", 3);

        assertThat(reason.params()).containsExactly(Map.entry("a", 3), Map.entry("b", 2));
    }

    @Test
    void nullValuesAreKept() {
        TimelineReason reason = TimelineReason.of("CODE", "toWater", null);

        assertThat(reason.params()).containsKey("toWater");
        assertThat(reason.params().get("toWater")).isNull();
    }

    @Test
    void paramsCannotBeChanged() {
        Map<String, Object> source = new LinkedHashMap<>(Map.of("distanceMeters", 120));
        TimelineReason reason = new TimelineReason(TimelineReasons.LEFT_STAY, source);
        source.put("distanceMeters", 999);

        assertThat(reason.params()).containsExactly(Map.entry("distanceMeters", 120));
        assertThatThrownBy(() -> reason.params().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> reason.params().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> reason.params().entrySet().iterator().next().setValue(1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equalsAndHashCodeFollowCodeAndParams() {
        TimelineReason built = TimelineReason.of(TimelineReasons.MOVED_AWAY, "distanceMeters", 178, "radiusMeters", 50);
        Map<String, Object> other = new HashMap<>();
        other.put("radiusMeters", 50);
        other.put("distanceMeters", 178);
        TimelineReason fromMap = new TimelineReason(TimelineReasons.MOVED_AWAY, other);

        assertThat(built).isEqualTo(fromMap).hasSameHashCodeAs(fromMap);
        assertThat(built.params()).isEqualTo(other);
        assertThat(built).isNotEqualTo(TimelineReason.of(TimelineReasons.MOVED_AWAY, "distanceMeters", 178));
        assertThat(TimelineReason.of(TimelineReasons.LAST_POINT)).isEqualTo(new TimelineReason(TimelineReasons.LAST_POINT, null));
    }

    @Test
    void aReasonBuiltFromAnotherSharesItsParams() {
        TimelineReason first = TimelineReason.of(TimelineReasons.MOVED_AWAY, "distanceMeters", 178);
        TimelineReason second = new TimelineReason(TimelineReasons.LEFT_STAY, first.params());

        assertThat(second.params()).isSameAs(first.params());
    }

    @Test
    void storedFormRoundTrips() {
        TimelineExplanation explanation = new TimelineExplanation(
                TimelineReason.of(TimelineReasons.ARRIVAL, "stopSeconds", 240, "points", 3),
                List.of(TimelineReason.of(TimelineReasons.CONFIRMED, "radiusMeters", 50, "minMinutes", 7)),
                TimelineReason.of(TimelineReasons.LAST_POINT));

        LinkedHashMap<String, Object> stored = explanation.toMap();

        assertThat(stored).isEqualTo(Map.of(
                "start", Map.of("code", "ARRIVAL", "params", Map.of("stopSeconds", 240, "points", 3)),
                "notes", List.of(Map.of("code", "CONFIRMED", "params", Map.of("radiusMeters", 50, "minMinutes", 7))),
                "end", Map.of("code", "LAST_POINT")));
        TimelineExplanation read = TimelineExplanation.fromMap(stored);
        assertThat(read.getStart()).isEqualTo(explanation.getStart());
        assertThat(read.getNotes()).isEqualTo(explanation.getNotes());
        assertThat(read.getEnd()).isEqualTo(explanation.getEnd());
    }

    @Test
    void serializesAsBefore() throws Exception {
        TimelineReason reason = TimelineReason.of(TimelineReasons.SHORT_STOP, "stopSeconds", 120, "minMinutes", 7);

        assertThat(new ObjectMapper().writeValueAsString(reason))
                .isEqualTo("{\"code\":\"SHORT_STOP\",\"params\":{\"stopSeconds\":120,\"minMinutes\":7}}");
        assertThat(reason.toString()).isEqualTo("TimelineReason[code=SHORT_STOP, params={stopSeconds=120, minMinutes=7}]");
    }

    @Test
    void parametersMustBePairs() {
        assertThatThrownBy(() -> TimelineReason.of("CODE", "lonely"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
