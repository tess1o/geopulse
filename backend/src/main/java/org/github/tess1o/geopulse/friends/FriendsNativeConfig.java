package org.github.tess1o.geopulse.friends;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationDTO;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationEntity;
import org.github.tess1o.geopulse.friends.invitation.model.InvitationStatus;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.model.FriendLocationTrailDTO;
import org.github.tess1o.geopulse.friends.model.UpdateLiveLocationPermissionRequest;
import org.github.tess1o.geopulse.friends.model.UpdateTimelinePermissionRequest;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionDTO;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionEntity;

@RegisterForReflection(targets = {
        FriendInvitationEntity.class,
        UserFriendEntity.class,
        InvitationStatus.class,
        FriendInfoDTO.class,
        FriendLocationTrailDTO.class,
        FriendInvitationDTO.class,
        UserFriendPermissionDTO.class,
        UserFriendPermissionDTO.UserFriendPermissionDTOBuilder.class,
        UserFriendPermissionEntity.class,
        UpdateTimelinePermissionRequest.class,
        UpdateLiveLocationPermissionRequest.class
})
public class FriendsNativeConfig {
}
