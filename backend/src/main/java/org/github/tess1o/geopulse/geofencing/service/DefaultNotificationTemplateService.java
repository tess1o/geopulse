package org.github.tess1o.geopulse.geofencing.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.geofencing.repository.NotificationTemplateRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.UUID;

@ApplicationScoped
public class DefaultNotificationTemplateService {

    private static final String DEFAULT_ENTER_NAME = "In-App Enter (Default)";
    private static final String DEFAULT_LEAVE_NAME = "In-App Leave (Default)";
    private static final String DEFAULT_ENTER_TITLE = "Geofence Alert: {{geofenceName}}";
    private static final String DEFAULT_LEAVE_TITLE = "Geofence Alert: {{geofenceName}}";
    private static final String DEFAULT_ENTER_BODY =
            "{{subjectName}} {{eventVerb}} {{geofenceName}} at {{timestamp}} ({{lat}}, {{lon}})";
    private static final String DEFAULT_LEAVE_BODY =
            "{{subjectName}} {{eventVerb}} {{geofenceName}} at {{timestamp}} ({{lat}}, {{lon}})";

    private final NotificationTemplateRepository templateRepository;
    private final EntityManager entityManager;

    @Inject
    public DefaultNotificationTemplateService(NotificationTemplateRepository templateRepository,
                                              EntityManager entityManager) {
        this.templateRepository = templateRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void ensureDefaultsForUser(UUID userId) {
        if (userId == null) {
            return;
        }

        UserEntity userRef = entityManager.getReference(UserEntity.class, userId);

        if (!templateRepository.existsDefaultEnterByUser(userId)) {
            NotificationTemplateEntity enterTemplate = NotificationTemplateEntity.builder()
                    .user(userRef)
                    .name(DEFAULT_ENTER_NAME)
                    .destination("")
                    .titleTemplate(DEFAULT_ENTER_TITLE)
                    .bodyTemplate(DEFAULT_ENTER_BODY)
                    .defaultForEnter(true)
                    .defaultForLeave(false)
                    .enabled(true)
                    .build();
            templateRepository.persist(enterTemplate);
        }

        if (!templateRepository.existsDefaultLeaveByUser(userId)) {
            NotificationTemplateEntity leaveTemplate = NotificationTemplateEntity.builder()
                    .user(userRef)
                    .name(DEFAULT_LEAVE_NAME)
                    .destination("")
                    .titleTemplate(DEFAULT_LEAVE_TITLE)
                    .bodyTemplate(DEFAULT_LEAVE_BODY)
                    .defaultForEnter(false)
                    .defaultForLeave(true)
                    .enabled(true)
                    .build();
            templateRepository.persist(leaveTemplate);
        }
    }
}
