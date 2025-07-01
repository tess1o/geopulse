package org.github.tess1o.geopulse.export.dto;

import lombok.*;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserInfoDataDto {
    private String dataType;
    private Instant exportDate;
    private UserDto user;
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UserDto {
        private UUID userId;
        private String email;
        private String fullName;
        private Instant createdAt;
        private TimelinePreferences preferences;
    }
}