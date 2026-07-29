package org.github.tess1o.geopulse.admin.service;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.Optional;

@ApplicationScoped
@Slf4j
public class AdminBootstrapService {

    private static final long ADMIN_BOOTSTRAP_LOCK_ID = 0x47454F50554C5345L; // "GEOPULSE"
    private static final long SINGLE_USER_COUNT = 1L;

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @ConfigProperty(name = "geopulse.admin.email")
    @StaticInitSafe
    Optional<String> adminEmail;

    @ConfigProperty(name = "geopulse.admin.first-user-admin.enabled", defaultValue = "true")
    @StaticInitSafe
    boolean firstUserAdminEnabled;

    @Inject
    public AdminBootstrapService(UserRepository userRepository, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        bootstrapExistingUsers();
    }

    @Transactional
    public void bootstrapExistingUsers() {
        Optional<String> configuredAdminEmail = configuredAdminEmail();
        if (configuredAdminEmail.isPresent()) {
            acquireBootstrapLock();
            promoteConfiguredAdminEmail(configuredAdminEmail.get());
            return;
        }

        if (!firstUserAdminEnabled) {
            return;
        }

        acquireBootstrapLock();

        if (userRepository.count("role", Role.ADMIN) > 0) {
            return;
        }

        long userCount = userRepository.count();
        if (userCount == 0) {
            return;
        }

        if (userCount == SINGLE_USER_COUNT) {
            UserEntity user = userRepository.findAll().firstResult();
            if (user != null) {
                user.setRole(Role.ADMIN);
                log.info("Promoted existing only user {} to ADMIN role (first-user admin bootstrap)", user.getEmail());
            }
            return;
        }

        log.warn("GeoPulse has {} users and no ADMIN role. First-user admin bootstrap will not guess an owner. " +
                "Set GEOPULSE_ADMIN_EMAIL and restart, or run `geopulse admin reset-password --email <email> --promote`.",
                userCount);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public Role determineInitialRole(String email) {
        Optional<String> configuredAdminEmail = configuredAdminEmail();
        if (configuredAdminEmail.isPresent()) {
            if (configuredAdminEmail.get().equalsIgnoreCase(email)) {
                log.info("Promoting user {} to ADMIN role (matches admin email)", email);
                return Role.ADMIN;
            }
            return Role.USER;
        }

        if (!firstUserAdminEnabled) {
            return Role.USER;
        }

        acquireBootstrapLock();

        if (userRepository.count("role", Role.ADMIN) == 0 && userRepository.count() == 0) {
            log.info("Promoting user {} to ADMIN role (first-user admin bootstrap)", email);
            return Role.ADMIN;
        }

        return Role.USER;
    }

    @Transactional
    public void ensureAdminForAuthenticatedUser(UserEntity user) {
        if (user == null || user.getRole() == Role.ADMIN) {
            return;
        }

        Optional<String> configuredAdminEmail = configuredAdminEmail();
        if (configuredAdminEmail.isEmpty() || !configuredAdminEmail.get().equalsIgnoreCase(user.getEmail())) {
            return;
        }

        UserEntity managedUser = userRepository.findById(user.getId());
        if (managedUser == null) {
            return;
        }

        managedUser.setRole(Role.ADMIN);
        user.setRole(Role.ADMIN);
        log.info("Promoted existing user {} to ADMIN role (matches admin email)", user.getEmail());
    }

    private Optional<String> configuredAdminEmail() {
        return adminEmail
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }

    private void promoteConfiguredAdminEmail(String email) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getRole() != Role.ADMIN)
                .ifPresent(user -> {
                    user.setRole(Role.ADMIN);
                    log.info("Promoted existing user {} to ADMIN role (matches admin email)", user.getEmail());
                });
    }

    private void acquireBootstrapLock() {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
                .setParameter(1, ADMIN_BOOTSTRAP_LOCK_ID)
                .getSingleResult();
    }
}
