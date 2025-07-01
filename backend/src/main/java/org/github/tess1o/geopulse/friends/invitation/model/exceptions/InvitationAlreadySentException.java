package org.github.tess1o.geopulse.friends.invitation.model.exceptions;

import org.github.tess1o.geopulse.friends.exceptions.FriendsException;

public class InvitationAlreadySentException extends FriendsException {
    public InvitationAlreadySentException(String message) {
        super(message);
    }
}
