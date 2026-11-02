package org.github.tess1o.geopulse.streaming.edits;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.streaming.StreamingNativeConfig;
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TimelineEditNativeConfigTest {
    @Test
    void registersEveryEditTransportTypeIncludingNestedPreviewEvents() {
        assertThat(StreamingNativeConfig.class.getAnnotation(RegisterForReflection.class).targets())
                .contains(TimelineOverrideIds.class, TimelineEditRequest.class, TimelineEditDTO.class, TimelineEditTargetDTO.class,
                        TimelineEditMutationResponse.class, TimelineEditPreviewDTO.class,
                        TimelineEditPreviewDTO.Event.class, TimelineEditKind.class, TimelineEditStatus.class);
    }
    @Test
    void registersGpsPathProjection() {
        assertThat(org.github.tess1o.geopulse.gps.GpsNativeConfig.class
                .getAnnotation(RegisterForReflection.class).targets())
                .contains(org.github.tess1o.geopulse.gps.model.GpsPathRow.class);
    }

    @Test
    void registersMatchingInputAndSharedLocationProjections() {
        assertThat(org.github.tess1o.geopulse.mapmatching.MapMatchingNativeConfig.class
                .getAnnotation(RegisterForReflection.class).targets())
                .contains(org.github.tess1o.geopulse.mapmatching.dto.MapMatchingPointDTO.class);
        assertThat(org.github.tess1o.geopulse.sharing.SharingNativeConfig.class
                .getAnnotation(RegisterForReflection.class).targets())
                .contains(org.github.tess1o.geopulse.sharing.model.LocationHistoryResponse.CurrentLocationData.class,
                        org.github.tess1o.geopulse.sharing.model.LocationHistoryResponse.HistoricalLocationData.class);
    }

}
