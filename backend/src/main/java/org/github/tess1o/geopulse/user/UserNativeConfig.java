package org.github.tess1o.geopulse.user;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.immich.model.ImmichPreferences;
import org.github.tess1o.geopulse.notes.model.MemosPreferences;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;
import org.github.tess1o.geopulse.shared.map.MapRenderMode;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
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
        UserSearchDTO.class,
        UpdateTimelineDisplayPreferencesRequest.class,
        TimelineDisplayPreferences.class,
        RefreshTokenResponse.class,
        DistanceUnit.class,
        TemperatureUnit.class,
        MapRenderMode.class,
        TripType.class,
        UserAvatarEntity.class
})
public class UserNativeConfig {
}

@RegisterForReflection(serialization = true, targets = {
        TimelinePreferences.class,
        ImmichPreferences.class,
        MemosPreferences.class,
        NotificationPreferences.class
})
class UserJsonPreferencesNativeConfig {
}
