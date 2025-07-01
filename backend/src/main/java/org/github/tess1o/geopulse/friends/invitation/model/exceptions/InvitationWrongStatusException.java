package org.github.tess1o.geopulse.friends.invitation.model.exceptions;

import org.github.tess1o.geopulse.friends.exceptions.FriendsException;

public class InvitationWrongStatusException extends FriendsException {
    public InvitationWrongStatusException(String message) {
        super(message);
    }
}
