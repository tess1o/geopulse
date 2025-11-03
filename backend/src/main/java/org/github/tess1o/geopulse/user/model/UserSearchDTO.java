package org.github.tess1o.geopulse.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserSearchDTO {
    private UUID userId;
    private String email;
    private String fullName;
    private String avatar;
}
