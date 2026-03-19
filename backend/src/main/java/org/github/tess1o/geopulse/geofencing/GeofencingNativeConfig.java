package org.github.tess1o.geopulse.geofencing;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.geofencing.client.AppriseClientResult;
import org.github.tess1o.geopulse.geofencing.model.dto.*;
import org.github.tess1o.geopulse.geofencing.model.entity.*;

@RegisterForReflection(targets = {
        GeofenceRuleEntity.class,
        GeofenceRuleStateEntity.class,
        GeofenceEventEntity.class,
        NotificationTemplateEntity.class,
        GeofenceRuleStatus.class,
        GeofenceEventType.class,
        GeofenceDeliveryStatus.class,
        GeofenceRuleDto.class,
        GeofenceRuleDto.GeofenceRuleDtoBuilder.class,
        CreateGeofenceRuleRequest.class,
        UpdateGeofenceRuleRequest.class,
        GeofenceEventDto.class,
        GeofenceEventDto.GeofenceEventDtoBuilder.class,
        NotificationTemplateDto.class,
        NotificationTemplateDto.NotificationTemplateDtoBuilder.class,
        CreateNotificationTemplateRequest.class,
        UpdateNotificationTemplateRequest.class,
        AppriseTestRequest.class,
        AppriseClientResult.class
})
public class GeofencingNativeConfig {
}
