package org.github.tess1o.geopulse.friends.invitation.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationDTO;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationEntity;

@ApplicationScoped
public class FriendInvitationMapper {

    public FriendInvitationDTO toDTO(FriendInvitationEntity entity) {
        return FriendInvitationDTO.builder()
                .id(entity.getId())
                .senderId(entity.getSender().getId())
                .receiverId(entity.getReceiver().getId())
                .senderName(entity.getSender().getEmail())
                .receiverName(entity.getReceiver().getEmail ())
                .invitationStatus(entity.getStatus().name())
                .build();
    }
}
