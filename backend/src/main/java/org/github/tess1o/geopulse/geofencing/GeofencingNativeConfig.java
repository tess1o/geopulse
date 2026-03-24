package org.github.tess1o.geopulse.geofencing;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.geofencing.client.AppriseClientResult;
import org.github.tess1o.geopulse.geofencing.model.dto.*;
import org.github.tess1o.geopulse.geofencing.model.entity.*;

@RegisterForReflection(targets = {
        GeofenceRuleEntity.class,
        GeofenceRuleSubjectEntity.class,
        GeofenceRuleSubjectId.class,
        GeofenceRuleStateEntity.class,
        GeofenceRuleStateId.class,
        GeofenceEventEntity.class,
        NotificationTemplateEntity.class,
        GeofenceRuleStatus.class,
        GeofenceEventType.class,
        GeofenceDeliveryStatus.class,
        GeofenceRuleDto.class,
        GeofenceRuleDto.GeofenceRuleDtoBuilder.class,
        GeofenceRuleSubjectDto.class,
        GeofenceRuleSubjectDto.GeofenceRuleSubjectDtoBuilder.class,
        CreateGeofenceRuleRequest.class,
        UpdateGeofenceRuleRequest.class,
        GeofenceEventDto.class,
        GeofenceEventDto.GeofenceEventDtoBuilder.class,
        GeofenceEventPageDto.class,
        GeofenceEventPageDto.GeofenceEventPageDtoBuilder.class,
        GeofenceEventQueryDto.class,
        GeofenceEventQueryDto.GeofenceEventQueryDtoBuilder.class,
        NotificationTemplateDto.class,
        NotificationTemplateDto.NotificationTemplateDtoBuilder.class,
        CreateNotificationTemplateRequest.class,
        UpdateNotificationTemplateRequest.class,
        AppriseTestRequest.class,
        AppriseClientResult.class,
        TemplateDeliveryCapabilitiesDto.class
})
public class GeofencingNativeConfig {
}
