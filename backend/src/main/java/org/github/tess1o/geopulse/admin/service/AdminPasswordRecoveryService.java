package org.github.tess1o.geopulse.admin.service;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;

import java.security.SecureRandom;
import java.util.Optional;

@ApplicationScoped
@Unremovable
@Slf4j
public class AdminPasswordRecoveryService {

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 16;
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final SecurePasswordUtils passwordUtils;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public AdminPasswordRecoveryService(UserRepository userRepository, SecurePasswordUtils passwordUtils) {
        this.userRepository = userRepository;
        this.passwordUtils = passwordUtils;
    }

    @Transactional
    public AdminPasswordRecoveryResult resetPassword(
            String email,
            Optional<String> requestedPassword,
            boolean promote,
            boolean activate
    ) {
        String normalizedEmail = validateEmail(email);
        UserEntity user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + normalizedEmail));

        boolean promoted = false;
        if (user.getRole() != Role.ADMIN) {
            if (!promote) {
                throw new IllegalStateException("Target user is not an admin. Pass --promote to promote and reset this account.");
            }
            user.setRole(Role.ADMIN);
            promoted = true;
        }

        String newPassword = requestedPassword.orElseGet(this::generateTemporaryPassword);
        validatePassword(newPassword);
        user.setPasswordHash(passwordUtils.hashPassword(newPassword));

        boolean activated = false;
        if (activate && !user.isActive()) {
            user.setActive(true);
            activated = true;
        }

        log.warn("Emergency password reset completed for user {} ({}) promoted={} activated={}",
                user.getId(), user.getEmail(), promoted, activated);

        return new AdminPasswordRecoveryResult(
                user.getId(),
                user.getEmail(),
                requestedPassword.isEmpty(),
                requestedPassword.isEmpty() ? newPassword : null,
                promoted,
                activated
        );
    }

    private String validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim();
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}
