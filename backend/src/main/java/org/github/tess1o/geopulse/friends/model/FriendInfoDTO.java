package org.github.tess1o.geopulse.friends.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FriendInfoDTO {
    private UUID userId;
    private UUID friendId;
    private String avatar;
    private Double lastLongitude;
    private Double lastLatitude;
    private String fullName;
    private String email;
    private String lastSeen;
    private String lastLocation;
    private String latestActivityType;  //STAY or TRIP
    private int latestActivityDurationSeconds;

    // Permissions: what the friend shares WITH the current user
    private Boolean friendSharesLiveLocation;   // Does friend allow me to see their live location?
    private Boolean friendSharesTimeline;       // Does friend allow me to see their timeline?
}
