package org.github.tess1o.geopulse.friends.invitation.model;

/**
 * Represents the status of a friend invitation.
 */
public enum InvitationStatus {
    PENDING,    // Invitation has been sent but not yet accepted or rejected
    ACCEPTED,   // Invitation has been accepted by the receiver
    REJECTED,   // Invitation has been rejected by the receiver
    CANCELLED   // Invitation has been cancelled by the sender
}
