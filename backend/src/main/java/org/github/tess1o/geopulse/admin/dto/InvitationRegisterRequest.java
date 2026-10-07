package org.github.tess1o.geopulse.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.github.tess1o.geopulse.user.model.SupportedLanguages;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class InvitationRegisterRequest {
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    @Size(max = 254)
    @Schema(examples = "jane@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 3, max = 128)
    @Schema(examples = "correct-horse-battery")
    private String password;

    @Size(min = 1, max = 100)
    @Schema(examples = "Jane Doe")
    private String fullName;

    @Size(max = 255)
    @Schema(examples = "Europe/Kyiv")
    private String timezone;

    @Pattern(regexp = SupportedLanguages.PATTERN, message = "Language must be one of: en, uk")
    @Size(max = 16)
    @Schema(examples = "en")
    private String language;
}
