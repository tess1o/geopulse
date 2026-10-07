package org.github.tess1o.geopulse.user.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProfileRequest {
    @Size(min = 1, max = 100, message = "Full name must be between 1 and 100 characters")
    @Schema(examples = "Jane Doe")
    private String fullName;

    @Size(max = 500, message = "Avatar URL cannot exceed 500 characters")
    @Schema(examples = "/avatars/avatar3.png")
    private String avatar;

    @Size(max = 255, message = "Timezone cannot exceed 255 characters")
    @Schema(examples = "Europe/Kyiv")
    private String timezone;

    /**
     * UI preferences to change. Null fields are left unchanged; an empty string resets a text preference
     * to its default.
     */
    @Valid
    private UserUiPreferences uiPreferences;
}
