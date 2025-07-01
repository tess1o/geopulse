package org.github.tess1o.geopulse.user.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user registration requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationRequest {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 3, max = 128, message = "Password must be between 3 and 128 characters")
    private String password;

    @Size(min = 1, max = 100, message = "Full name must be between 1 and 100 characters")
    private String fullName;
}