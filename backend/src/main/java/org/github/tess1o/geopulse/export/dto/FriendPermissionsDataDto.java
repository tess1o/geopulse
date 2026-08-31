package org.github.tess1o.geopulse.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendPermissionsDataDto {
    private String dataType;
    private Instant exportDate;
    private List<FriendPermissionDto> permissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FriendPermissionDto {
        private Long id;
        private UUID userId;
        private UUID friendId;
        private String userEmail;
        private String friendEmail;
        private Boolean shareTimeline;
        private Boolean shareLiveLocation;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
