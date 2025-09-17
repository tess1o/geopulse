package org.github.tess1o.geopulse.user.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.streaming.events.TimelinePreferencesUpdatedEvent;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.github.tess1o.geopulse.user.model.*;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for user management operations.
 */
@ApplicationScoped
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final SecurePasswordUtils securePasswordUtils;
    private final TimelinePreferencesUpdater preferencesUpdater;
    private final Event<TimelinePreferencesUpdatedEvent> preferencesUpdatedEvent;

    // Regex pattern for validating avatar paths - only allows /avatars/avatar{1-20}.png
    private static final Pattern VALID_AVATAR_PATTERN = Pattern.compile("^/avatars/avatar(1[0-9]|20|[1-9])\\.png$");

    @Inject
    public UserService(UserRepository userRepository,
                       SecurePasswordUtils securePasswordUtils,
                       TimelinePreferencesUpdater preferencesUpdater,
                       Event<TimelinePreferencesUpdatedEvent> preferencesUpdatedEvent) {
        this.userRepository = userRepository;
        this.securePasswordUtils = securePasswordUtils;
        this.preferencesUpdater = preferencesUpdater;
        this.preferencesUpdatedEvent = preferencesUpdatedEvent;
    }

    /**
     * Register a new user.
     *
     * @param email    The user ID
     * @param password The password (will be hashed)
     * @return The created user entity
     * @throws IllegalArgumentException if the user already exists
     */
    @Transactional
    public UserEntity registerUser(String email, String password, String fullName) {
        // Check if the user already exists
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User with email " + email + " already exists");
        }

        // Create a new user entity
        UserEntity user = UserEntity.builder()
                .email(email)
                .fullName(fullName)
                .role("USER")
                .isActive(true)
                .emailVerified(false)
                .passwordHash(securePasswordUtils.hashPassword(password))
                .build();

        userRepository.persist(user);
        return user;
    }

    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<UserEntity> findById(UUID id) {
        return Optional.ofNullable(userRepository.findById(id));
    }

    public void persist(UserEntity user) {
        this.userRepository.persist(user);
    }

    @Transactional
    public void resetTimelinePreferencesToDefaults(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new UserNotFoundException("User not found");
        }
        user.timelinePreferences = null;

        // Fire event to trigger timeline invalidation
        preferencesUpdatedEvent.fire(new TimelinePreferencesUpdatedEvent(
                userId,
                null, // null indicates reset to defaults
                true  // wasResetToDefaults = true
        ));
    }

    /**
     * Validates avatar path to ensure it matches the expected format.
     * Only allows paths like /avatars/avatar1.png through /avatars/avatar20.png
     *
     * @param avatarPath the avatar path to validate
     * @throws IllegalArgumentException if the avatar path is invalid
     */
    private void validateAvatarPath(String avatarPath) {
        if (avatarPath == null || avatarPath.trim().isEmpty()) {
            return; // Allow null/empty to remove avatar
        }

        // Check against whitelist pattern
        if (!VALID_AVATAR_PATTERN.matcher(avatarPath).matches()) {
            log.warn("Invalid avatar path attempted: {}", avatarPath);
            throw new IllegalArgumentException("Invalid avatar path. Must be in format /avatars/avatar{1-20}.png");
        }

        // Additional security checks
        if (avatarPath.contains("..") || avatarPath.contains("//")) {
            log.warn("Path traversal attempt in avatar path: {}", avatarPath);
            throw new IllegalArgumentException("Invalid avatar path. Path traversal not allowed.");
        }
    }

    @Transactional
    public void updateProfile(UpdateProfileRequest request) {
        UserEntity user = userRepository.findById(request.getUserId());
        if (user == null) {
            throw new UserNotFoundException("User not found");
        }

        // Update full name
        user.setFullName(request.getFullName());

        // Validate and update avatar path
        if (request.getAvatar() != null) {
            validateAvatarPath(request.getAvatar());
            user.setAvatar(request.getAvatar());
            log.debug("Updated avatar for user {} to {}", user.getId(), request.getAvatar());
        }
    }

    @Transactional
    public void changePassword(UpdateUserPasswordRequest request) {
        UserEntity user = userRepository.findById(request.getUserId());
        if (user == null) {
            throw new UserNotFoundException("User not found");
        }

        // If user has an existing password, validate the old password
        // If oldPassword is null/blank, it means user is setting password for the first time (OIDC scenario)
        if (user.getPasswordHash() != null && !user.getPasswordHash().trim().isEmpty()) {
            if (request.getOldPassword() == null || request.getOldPassword().trim().isEmpty()) {
                throw new InvalidPasswordException("Current password is required to change password");
            }
            if (!securePasswordUtils.isPasswordValid(request.getOldPassword(), user.getPasswordHash())) {
                throw new InvalidPasswordException("Old password is incorrect");
            }
        }

        // Set new password using bcrypt
        user.setPasswordHash(securePasswordUtils.hashPassword(request.getNewPassword()));
    }

    @Transactional
    public void updateTimelinePreferences(UUID userId, UpdateTimelinePreferencesRequest update) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new UserNotFoundException("User not found");
        }

        if (user.timelinePreferences == null) {
            user.timelinePreferences = new TimelinePreferences();
        }

        // Use registry-based updater - eliminates all the manual if/else logic
        preferencesUpdater.updatePreferences(user.timelinePreferences, update);

        // Fire event to trigger timeline invalidation
        preferencesUpdatedEvent.fire(new TimelinePreferencesUpdatedEvent(
                userId,
                user.timelinePreferences,
                false // wasResetToDefaults = false
        ));
    }
}