package org.github.tess1o.geopulse.sharing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShareLinkDto {

    @Size(max = 255, message = "Name cannot exceed 255 characters")
    @JsonProperty("name")
    private String name;

    @Future(message = "Expiration date must be in the future")
    @JsonProperty("expires_at")
    private Instant expiresAt;

    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    @JsonProperty("password")
    private String password;

    @JsonProperty("show_history")
    private boolean showHistory;

    public boolean isPasswordRemoval() {
        return password == null;
    }
}