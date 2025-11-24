package org.github.tess1o.geopulse.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InvitationRegisterRequest {
    @NotBlank(message = "Token is required")
    private String token;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    @Size(max = 254)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 3, max = 128)
    private String password;

    @Size(min = 1, max = 100)
    private String fullName;

    @Size(max = 255)
    private String timezone;
}
