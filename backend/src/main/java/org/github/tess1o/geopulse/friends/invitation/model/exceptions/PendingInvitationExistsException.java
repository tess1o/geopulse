package org.github.tess1o.geopulse.friends.invitation.model.exceptions;

import org.github.tess1o.geopulse.friends.exceptions.FriendsException;

public class PendingInvitationExistsException extends FriendsException {
    public PendingInvitationExistsException(String message) {
        super(message);
    }
}
