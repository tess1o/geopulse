package org.github.tess1o.geopulse.user.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserPasswordRequest {
    @Schema(examples = "correct-horse-battery")
    private String oldPassword;
    
    @NotBlank(message = "New password is required")
    @Schema(examples = "new-long-passphrase")
    private String newPassword;
}
