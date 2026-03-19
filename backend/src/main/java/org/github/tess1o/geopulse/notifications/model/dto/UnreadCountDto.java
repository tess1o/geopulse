package org.github.tess1o.geopulse.notifications.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountDto {
    private long count;
    private Long latestUnreadId;
}
