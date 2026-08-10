package org.github.tess1o.geopulse.auth.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.geofencing.service.DefaultNotificationTemplateService;
import org.github.tess1o.geopulse.shared.map.MapRenderMode;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.List;

@ApplicationScoped
@Slf4j
public class DemoUserProvisioningService {

    private final DemoModeService demoModeService;
    private final UserRepository userRepository;
    private final DefaultNotificationTemplateService defaultNotificationTemplateService;

    @Inject
    public DemoUserProvisioningService(DemoModeService demoModeService,
                                       UserRepository userRepository,
                                       DefaultNotificationTemplateService defaultNotificationTemplateService) {
        this.demoModeService = demoModeService;
        this.userRepository = userRepository;
        this.defaultNotificationTemplateService = defaultNotificationTemplateService;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        provisionConfiguredDemoUsers();
    }

    @Transactional
    public void provisionConfiguredDemoUsers() {
        List<DemoModeService.DemoPersonaConfig> personas = demoModeService.getProvisioningPersonas();
        if (personas.isEmpty()) {
            return;
        }

        int created = 0;
        int updated = 0;
        for (DemoModeService.DemoPersonaConfig persona : personas) {
            boolean createdUser = upsertDemoUser(persona);
            if (createdUser) {
                created++;
            } else {
                updated++;
            }
        }

        log.info("Demo user provisioning completed: created={}, updated={}", created, updated);
    }

    private boolean upsertDemoUser(DemoModeService.DemoPersonaConfig persona) {
        UserEntity user = userRepository.findByEmailIgnoreCase(persona.email())
                .orElse(null);

        boolean isNewUser = user == null;
        if (isNewUser) {
            user = UserEntity.builder()
                    .email(persona.email())
                    .emailVerified(true)
                    .role(Role.USER)
                    .isActive(true)
                    .mapRenderMode(MapRenderMode.VECTOR)
                    .coverageEnabled(false)
                    .build();
        }

        user.setEmail(persona.email());
        user.setFullName(persona.fullName());
        user.setTimezone(persona.timezone());
        user.setMeasureUnit(persona.measureUnit());
        user.setDateFormat(persona.dateFormat());
        user.setTimeFormat(persona.timeFormat());
        user.setActive(true);
        user.setEmailVerified(true);
        user.setRole(Role.USER);

        if (isNewUser) {
            userRepository.persistAndFlush(user);
        }
        defaultNotificationTemplateService.ensureDefaultsForUser(user.getId());

        return isNewUser;
    }
}
