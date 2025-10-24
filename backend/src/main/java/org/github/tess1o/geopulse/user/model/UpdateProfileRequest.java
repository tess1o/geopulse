package org.github.tess1o.geopulse.user.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProfileRequest {
    @Size(min = 1, max = 100, message = "Full name must be between 1 and 100 characters")
    private String fullName;

    @Size(max = 500, message = "Avatar URL cannot exceed 500 characters")
    private String avatar;

    @Size(max = 255, message = "Timezone cannot exceed 255 characters")
    private String timezone;

    @Size(max = 1000, message = "Custom map tile URL cannot exceed 1000 characters")
    private String customMapTileUrl;

    private UUID userId;
}
