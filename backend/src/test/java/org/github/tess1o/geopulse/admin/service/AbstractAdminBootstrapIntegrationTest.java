package org.github.tess1o.geopulse.admin.service;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;

import java.util.Optional;

abstract class AbstractAdminBootstrapIntegrationTest {

    protected static final String CONFIGURED_ADMIN_EMAIL = "owner@example.com";

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    @Inject
    AdminBootstrapService adminBootstrapService;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    @Transactional
    void resetUsers() {
        entityManager.createNativeQuery("TRUNCATE TABLE users CASCADE").executeUpdate();
    }

    protected UserEntity registerUser(String prefix) {
        return userService.registerUser(TestIds.uniqueEmail(prefix), "password", "Test User", "UTC");
    }

    protected UserEntity existingUser(String email, Role role) {
        return existingUser(email, role, null);
    }

    protected UserEntity existingUser(String email, Role role, String passwordHash) {
        UserEntity user = UserEntity.builder()
                .email(email)
                .fullName("Existing User")
                .role(role)
                .isActive(true)
                .emailVerified(true)
                .passwordHash(passwordHash)
                .timezone("UTC")
                .build();
        userRepository.persistAndFlush(user);
        return user;
    }

    protected Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    protected long adminCount() {
        return userRepository.count("role", Role.ADMIN);
    }
}
