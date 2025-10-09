package org.github.tess1o.geopulse.friends;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationDTO;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationEntity;
import org.github.tess1o.geopulse.friends.invitation.model.InvitationStatus;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;

@RegisterForReflection(targets = {
        FriendInvitationEntity.class,
        UserFriendEntity.class,
        UserFriendEntity.class,
        InvitationStatus.class,
        FriendInfoDTO.class,
        FriendInvitationDTO.class,
})
public class FriendsNativeConfig {
}
