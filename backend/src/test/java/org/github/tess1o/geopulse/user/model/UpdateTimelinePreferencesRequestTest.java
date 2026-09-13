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
}
