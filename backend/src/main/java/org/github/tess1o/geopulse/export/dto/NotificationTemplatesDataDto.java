package org.github.tess1o.geopulse.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationTemplatesDataDto {
    private String dataType;
    private Instant exportDate;
    private List<NotificationTemplateDto> templates;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class NotificationTemplateDto {
        private Long id;
        private String name;
        private String destination;
        private String externalRoutingMode;
        private String appriseConfigKey;
        private String appriseTag;
        private String titleTemplate;
        private String bodyTemplate;
        private Boolean defaultForEnter;
        private Boolean defaultForLeave;
        private Boolean enabled;
        private Boolean sendInApp;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
