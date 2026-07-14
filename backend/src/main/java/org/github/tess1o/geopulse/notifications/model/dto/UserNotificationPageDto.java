package org.github.tess1o.geopulse.notifications.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationPageDto {
    private List<UserNotificationDto> items;
    private long totalCount;
    private int page;
    private int pageSize;
}
