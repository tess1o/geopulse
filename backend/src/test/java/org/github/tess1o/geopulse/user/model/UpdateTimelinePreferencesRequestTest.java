package org.github.tess1o.geopulse.user.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class UpdateTimelinePreferencesRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPublicTransportAndRejectsUnknownPreferredMotorizedTypes() {
        UpdateTimelinePreferencesRequest valid = UpdateTimelinePreferencesRequest.builder()
                .preferredMotorizedType("PUBLIC_TRANSPORT")
                .build();
        UpdateTimelinePreferencesRequest invalid = UpdateTimelinePreferencesRequest.builder()
                .preferredMotorizedType("BUS")
                .build();

        assertEquals(0, validator.validate(valid).size());
        assertEquals(1, validator.validate(invalid).size());
    }

    @Test
    void acceptsFlightPeakSpeedDownTo150() {
        assertEquals(0, validator.validate(UpdateTimelinePreferencesRequest.builder()
                .flightMinMaxSpeed(150.0)
                .build()).size());
        assertEquals(1, validator.validate(UpdateTimelinePreferencesRequest.builder()
                .flightMinMaxSpeed(149.0)
                .build()).size());
    }

    @Test
    void validatesFlightMinDistanceRange() {
        assertEquals(0, validator.validate(UpdateTimelinePreferencesRequest.builder()
                .flightMinDistanceKm(0.0)
                .build()).size());
        assertEquals(0, validator.validate(UpdateTimelinePreferencesRequest.builder()
                .flightMinDistanceKm(500.0)
                .build()).size());
        assertEquals(1, validator.validate(UpdateTimelinePreferencesRequest.builder()
                .flightMinDistanceKm(-1.0)
                .build()).size());
        assertEquals(1, validator.validate(UpdateTimelinePreferencesRequest.builder()
                .flightMinDistanceKm(501.0)
                .build()).size());
    }

    @Test
    void validatesFlightAltitudeRanges() {
        assertEquals(0, validator.validate(UpdateTimelinePreferencesRequest.builder()
                .flightAltitudeDetectionEnabled(true)
                .flightAltitudeMinHeightMeters(50.0)
                .flightAltitudeMinVerticalSpeed(0.3)
                .build()).size());
        assertEquals(0, validator.validate(UpdateTimelinePreferencesRequest.builder()
                .flightAltitudeMinHeightMeters(5000.0)
                .flightAltitudeMinVerticalSpeed(10.0)
                .build()).size());
        assertEquals(2, validator.validate(UpdateTimelinePreferencesRequest.builder()
                .flightAltitudeMinHeightMeters(49.0)
                .flightAltitudeMinVerticalSpeed(0.2)
                .build()).size());
        assertEquals(2, validator.validate(UpdateTimelinePreferencesRequest.builder()
                .flightAltitudeMinHeightMeters(5001.0)
                .flightAltitudeMinVerticalSpeed(10.1)
                .build()).size());
    }
}
