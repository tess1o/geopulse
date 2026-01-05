package org.github.tess1o.geopulse.friends;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationDTO;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationEntity;
import org.github.tess1o.geopulse.friends.invitation.model.InvitationStatus;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionDTO;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionEntity;
import org.github.tess1o.geopulse.friends.rest.FriendResource;

@RegisterForReflection(targets = {
        FriendInvitationEntity.class,
        UserFriendEntity.class,
        InvitationStatus.class,
        FriendInfoDTO.class,
        FriendInvitationDTO.class,
        UserFriendPermissionDTO.class,
        UserFriendPermissionDTO.UserFriendPermissionDTOBuilder.class,
        UserFriendPermissionEntity.class,
        FriendResource.UpdatePermissionRequest.class,
        FriendResource.UpdateLiveLocationRequest.class
})
public class FriendsNativeConfig {
}
