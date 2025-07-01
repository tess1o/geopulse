package org.github.tess1o.geopulse.friends.invitation.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

@Entity
@Table(name = "friend_invitations")
@Getter
@Setter
@RequiredArgsConstructor
public class FriendInvitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserEntity sender;    // User who sent the invitation

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserEntity receiver;  // User who received the invitation
    
    @Enumerated(EnumType.STRING)
    private InvitationStatus status = InvitationStatus.PENDING; // Default status is PENDING

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;     // When the invitation was sent

    @PrePersist
    protected void onCreate() {
        sentAt = Instant.now();
    }
}