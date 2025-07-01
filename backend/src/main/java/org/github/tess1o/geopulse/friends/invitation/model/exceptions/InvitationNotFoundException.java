package org.github.tess1o.geopulse.friends.invitation.model.exceptions;

import org.github.tess1o.geopulse.friends.exceptions.FriendsException;

public class InvitationNotFoundException extends FriendsException {
    public InvitationNotFoundException(String message) {
        super(message);
    }
}
