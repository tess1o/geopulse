package org.github.tess1o.geopulse.friends.invitation.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.friends.invitation.model.InvitationStatus;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FriendInvitationRepository implements PanacheRepository<FriendInvitationEntity> {

    /**
     * Find all pending invitations received by a user.
     *
     * @param receiverId The ID of the user who received the invitations
     * @return A list of pending invitation entities
     */
    public List<FriendInvitationEntity> findPendingInvitationsByReceiverId(UUID receiverId) {
        return list("receiver.id = ?1 AND status = ?2", receiverId, InvitationStatus.PENDING);
    }

    /**
     * Find all pending invitations sent by a user.
     *
     * @param senderId The ID of the user who sent the invitations
     * @return A list of pending invitation entities
     */
    public List<FriendInvitationEntity> findPendingInvitationsBySenderId(UUID senderId) {
        return list("sender.id = ?1 AND status = ?2", senderId, InvitationStatus.PENDING);
    }

    /**
     * Find an invitation by sender and receiver IDs.
     *
     * @param senderId   The ID of the user who sent the invitation
     * @param receiverId The ID of the user who received the invitation
     * @return An Optional containing the invitation if found, or empty if not found
     */
    public Optional<FriendInvitationEntity> findBySenderAndReceiver(UUID senderId, UUID receiverId) {
        return find("sender.id = ?1 AND receiver.id = ?2", senderId, receiverId).firstResultOptional();
    }

    /**
     * Check if a pending invitation exists between two users.
     *
     * @param senderId   The ID of the user who sent the invitation
     * @param receiverId The ID of the user who received the invitation
     * @return true if a pending invitation exists, false otherwise
     */
    public boolean existsPendingInvitation(UUID senderId, UUID receiverId) {
        return count("sender.id = ?1 AND receiver.id = ?2 AND status = ?3",
                senderId, receiverId, InvitationStatus.PENDING) > 0;
    }
}