package org.github.tess1o.geopulse.friends.invitation.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for friend invitation requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendFriendInvitationDTO {
    @NotBlank(message = "Receiver email cannot be empty")
    @Email(message = "Invalid email format")
    private String receiverEmail;
}