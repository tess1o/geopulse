package org.github.tess1o.geopulse.sharing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateShareLinkResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("expires_at")
    private Instant expiresAt;

    @JsonProperty("has_password")
    private boolean hasPassword;

    @JsonProperty("show_history")
    private boolean showHistory;

    @JsonProperty("created_at")
    private Instant createdAt;

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    @JsonProperty("is_active")
    private boolean isActive() {
        return Instant.now().isAfter(createdAt);
    }
}