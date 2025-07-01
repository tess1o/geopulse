package org.github.tess1o.geopulse.friends.invitation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendInvitationDTO {
    private UUID senderId;
    private String senderName;
    private UUID receiverId;
    private String receiverName;
    private long id;
    private String invitationStatus;
}
