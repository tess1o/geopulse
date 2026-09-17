package org.github.tess1o.geopulse.admin.dto;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.admin.model.InvitationStatus;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

@Data
@Builder
public class ValidateInvitationResponse {
    private boolean valid;
    private InvitationStatus status;
    private MessageDescriptor message;
}
