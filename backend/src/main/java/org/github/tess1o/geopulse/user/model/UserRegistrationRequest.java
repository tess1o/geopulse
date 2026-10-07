package org.github.tess1o.geopulse.user.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

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
    @Schema(examples = "jane@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 3, max = 128, message = "Password must be between 3 and 128 characters")
    @Schema(examples = "correct-horse-battery")
    private String password;

    @Size(min = 1, max = 100, message = "Full name must be between 1 and 100 characters")
    @Schema(examples = "Jane Doe")
    private String fullName;

    @Size(max = 255, message = "Timezone cannot exceed 255 characters")
    @Schema(examples = "Europe/Kyiv")
    private String timezone;

    @Pattern(regexp = SupportedLanguages.PATTERN, message = "Language must be one of: en, uk")
    @Size(max = 16, message = "Language cannot exceed 16 characters")
    @Schema(examples = "en")
    private String language;
}