package org.github.tess1o.geopulse.user.rest;

import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.service.boat.BoatSetupService;
import org.github.tess1o.geopulse.user.mapper.UserMapper;
import org.github.tess1o.geopulse.user.model.UpdateTimelineDisplayPreferencesRequest;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_PREFERENCES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@Tag("unit")
class UserResourceTimelineDisplayPreferencesTest {

    @Test
    void invalidMovementTypeUsesTimelinePreferencesErrorContract() {
        UserService userService = mock(UserService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        UUID userId = UUID.randomUUID();
        UpdateTimelineDisplayPreferencesRequest request = UpdateTimelineDisplayPreferencesRequest.builder()
                .mapMatchingExcludedMovementTypes(List.of("TRAIN"))
                .build();
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        doThrow(new IllegalArgumentException("unsupported"))
                .when(userService).updateTimelineDisplayPreferences(userId, request);

        UserResource resource = new UserResource(
                userService,
                mock(UserMapper.class),
                currentUserService,
                mock(BoatSetupService.class));

        GeoPulseException error = assertThrows(GeoPulseException.class,
                () -> resource.updateTimelineDisplayPreferences(request));

        assertEquals(INVALID_TIMELINE_PREFERENCES, error.code());
        assertEquals(400, error.code().statusCode());
    }
}
