package org.github.tess1o.geopulse.friends.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for friend timeline sharing permissions.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserFriendPermissionDTO {
    private UUID userId;        // The user granting permission
    private UUID friendId;      // The friend receiving permission
    private Boolean shareTimeline;  // Permission to view full timeline
    private Boolean shareLocationLive;  // Permission to view live location (from UserEntity.shareLocationWithFriends)
}
