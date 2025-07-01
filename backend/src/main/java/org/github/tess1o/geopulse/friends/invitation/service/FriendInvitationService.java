package org.github.tess1o.geopulse.friends.invitation.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.friends.invitation.mapper.FriendInvitationMapper;
import org.github.tess1o.geopulse.friends.invitation.model.*;
import org.github.tess1o.geopulse.friends.invitation.model.exceptions.*;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.friends.exceptions.*;
import org.github.tess1o.geopulse.friends.invitation.repository.FriendInvitationRepository;
import org.github.tess1o.geopulse.user.exceptions.NotAuthorizedUserException;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class FriendInvitationService {

    private final FriendInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendInvitationMapper invitationMapper;
    private final EntityManager entityManager;

    public FriendInvitationService(FriendInvitationRepository invitationRepository, UserRepository userRepository,
                                   FriendshipRepository friendshipRepository, FriendInvitationMapper invitationMapper,
                                   EntityManager entityManager) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.invitationMapper = invitationMapper;
        this.entityManager = entityManager;
    }

    /**
     * Send a friend invitation from one user to another.
     *
     * @param senderId   The ID of the user sending the invitation
     * @param receiverId The ID of the user receiving the invitation
     * @return The created invitation entity
     * @throws WebApplicationException if the invitation cannot be sent
     */
    @Transactional
    public FriendInvitationDTO sendInvitation(UUID senderId, UUID receiverId) {
        if (senderId.equals(receiverId)) {
            throw new InvitationToMySelfException("Cannot send invitation to yourself");
        }

        if (!userRepository.existsById(receiverId)) {
            throw new UserNotFoundException("Receiver not found");
        }

        if (friendshipRepository.existsFriendship(senderId, receiverId)) {
            throw new AlreadyFriendsException("Already friends");
        }

        if (invitationRepository.existsPendingInvitation(senderId, receiverId)) {
            throw new InvitationAlreadySentException("Invitation already sent");
        }

        if (invitationRepository.existsPendingInvitation(receiverId, senderId)) {
            throw new PendingInvitationExistsException("There's a pending invitation from the receiver");
        }

        // Create and save the invitation
        FriendInvitationEntity invitation = new FriendInvitationEntity();
        invitation.setSender(entityManager.getReference(UserEntity.class, senderId));
        invitation.setReceiver(entityManager.getReference(UserEntity.class, receiverId));
        invitation.setStatus(InvitationStatus.PENDING);
        invitationRepository.persist(invitation);

        log.info("Friend invitation sent from {} to {}", senderId, receiverId);

        return convertToDto(invitation);
    }

    /**
     * Get all pending invitations received by a user.
     *
     * @param userId The ID of the user
     * @return A list of pending invitation entities
     */
    @Transactional
    public List<FriendInvitationDTO> getPendingInvitations(UUID userId) {
        List<FriendInvitationEntity> invitations = invitationRepository.findPendingInvitationsByReceiverId(userId);
        return convertToDtoList(invitations);
    }

    /**
     * Get all pending invitations sent by a user.
     *
     * @param userId The ID of the user
     * @return A list of pending invitation entities
     */
    @Transactional
    public List<FriendInvitationDTO> getSentInvitations(UUID userId) {
        List<FriendInvitationEntity> invitations = invitationRepository.findPendingInvitationsBySenderId(userId);
        return convertToDtoList(invitations);
    }

    /**
     * Accept a friend's invitation.
     *
     * @param invitationId The ID of the invitation
     * @param receiverId   The ID of the user accepting the invitation
     * @return The updated invitation entity
     * @throws WebApplicationException if the invitation cannot be accepted
     */
    @Transactional
    public FriendInvitationDTO acceptInvitation(Long invitationId, UUID receiverId) {
        FriendInvitationEntity invitation = getInvitation(invitationId, receiverId, false);

        // Update invitation status
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.persist(invitation);

        // Create friendship entries (bidirectional)
        createFriendship(invitation.getSender(), invitation.getReceiver());
        createFriendship(invitation.getReceiver(), invitation.getSender());

        log.info("Friend invitation accepted: {} -> {}", invitation.getSender().getId(), invitation.getReceiver().getId());
        return convertToDto(invitation);
    }

    /**
     * Reject a friend's invitation.
     *
     * @param invitationId The ID of the invitation
     * @param receiverId   The ID of the user rejecting the invitation
     * @return The updated invitation entity
     * @throws WebApplicationException if the invitation cannot be rejected
     */
    @Transactional
    public FriendInvitationDTO rejectInvitation(Long invitationId, UUID receiverId) {
        FriendInvitationEntity invitation = getInvitation(invitationId, receiverId, false);
        invitation.setStatus(InvitationStatus.REJECTED);
        invitationRepository.persist(invitation);

        log.info("Friend invitation rejected: {} -> {}", invitation.getSender().getId(), invitation.getReceiver().getId());
        return convertToDto(invitation);
    }

    /**
     * Cancel a friend's invitation.
     *
     * @param invitationId The ID of the invitation
     * @param senderId     The ID of the user cancelling the invitation
     * @return The updated invitation entity
     * @throws WebApplicationException if the invitation cannot be canceled
     */
    @Transactional
    public FriendInvitationDTO cancelInvitation(Long invitationId, UUID senderId) {
        FriendInvitationEntity invitation = getInvitation(invitationId, senderId, true);

        // Update invitation status
        invitation.setStatus(InvitationStatus.CANCELLED);
        invitationRepository.persist(invitation);

        log.info("Friend invitation cancelled: {} -> {}", invitation.getSender().getId(), invitation.getReceiver().getId());
        return convertToDto(invitation);
    }

    private FriendInvitationEntity getInvitation(Long invitationId, UUID userId, boolean checkSender) {
        FriendInvitationEntity invitation = invitationRepository.findByIdOptional(invitationId)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation not found"));

        if (checkSender && !invitation.getSender().getId().equals(userId)) {
            throw new NotAuthorizedUserException("You are not authorized to work with this invitation");
        }
        if (!checkSender && !invitation.getReceiver().getId().equals(userId)) {
            throw new NotAuthorizedUserException("You are not authorized to work with this invitation");
        }
        // Check if the invitation is pending
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvitationWrongStatusException("Invitation is not pending");
        }
        return invitation;
    }

    private void createFriendship(UserEntity user, UserEntity friend) {
        UserFriendEntity friendship = new UserFriendEntity();
        friendship.setUser(user);
        friendship.setFriend(friend);
        friendshipRepository.persist(friendship);
    }

    private List<FriendInvitationDTO> convertToDtoList(List<FriendInvitationEntity> invitations) {
        return invitations.stream()
                .map(this::convertToDto)
                .toList();
    }

    private FriendInvitationDTO convertToDto(FriendInvitationEntity entity) {
        return invitationMapper.toDTO(entity);
    }
}
