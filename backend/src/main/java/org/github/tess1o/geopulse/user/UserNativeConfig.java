package org.github.tess1o.geopulse.user;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.user.model.*;

@RegisterForReflection(targets = {
        UserEntity.class,
        TimelinePreferences.class,
        TimelineStatus.class,
        UpdateUserPasswordRequest.class,
        UpdateProfileRequest.class,
        UpdateTimelinePreferencesRequest.class,
        UserRegistrationRequest.class,
        UserResponse.class,
        UserSearchDTO.class
})
public class UserNativeConfig {
}
