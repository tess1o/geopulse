package org.github.tess1o.geopulse.user.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.config.AuthConfigurationService;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.streaming.events.TimelinePreferencesUpdatedEvent;
import org.github.tess1o.geopulse.streaming.events.TravelClassificationUpdatedEvent;
import org.github.tess1o.geopulse.streaming.events.TimelineStructureUpdatedEvent;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.github.tess1o.geopulse.user.model.*;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.Map;
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
    private final Event<TravelClassificationUpdatedEvent> classificationUpdatedEvent;
    private final Event<TimelineStructureUpdatedEvent> structureUpdatedEvent;
    private final AuthConfigurationService authConfigurationService;
    private final AsyncTimelineGenerationService asyncTimelineGenerationService;

    // Regex pattern for validating avatar paths - only allows /avatars/avatar{1-20}.png
    private static final Pattern VALID_AVATAR_PATTERN = Pattern.compile("^/avatars/avatar(1[0-9]|20|[1-9])\\.png$");

    // Mapping for timezone names that differ between JavaScript and Java
    private static final Map<String, String> TIMEZONE_MAPPING = Map.of(
            "Europe/Kiev", "Europe/Kyiv"  // JavaScript may send old name, normalize to new name
    );

    @Inject
    public UserService(UserRepository userRepository,
                       SecurePasswordUtils securePasswordUtils,
                       TimelinePreferencesUpdater preferencesUpdater,
                       Event<TimelinePreferencesUpdatedEvent> preferencesUpdatedEvent,
                       Event<TravelClassificationUpdatedEvent> classificationUpdatedEvent,
                       Event<TimelineStructureUpdatedEvent> structureUpdatedEvent,
                       AuthConfigurationService authConfigurationService,
                       AsyncTimelineGenerationService asyncTimelineGenerationService) {
        this.userRepository = userRepository;
        this.securePasswordUtils = securePasswordUtils;
        this.preferencesUpdater = preferencesUpdater;
        this.preferencesUpdatedEvent = preferencesUpdatedEvent;
        this.classificationUpdatedEvent = classificationUpdatedEvent;
        this.structureUpdatedEvent = structureUpdatedEvent;
        this.authConfigurationService = authConfigurationService;
        this.asyncTimelineGenerationService = asyncTimelineGenerationService;
    }

    /**
     * Register a new user.
     *
     * @param email    The user email
     * @param password The password (will be hashed)
     * @param fullName The user's full name
     * @param timezone The user's timezone (IANA format)
     * @return The created user entity
     * @throws IllegalArgumentException if the user already exists
     */
    @Transactional
    public UserEntity registerUser(String email, String password, String fullName, String timezone) {
        if (!authConfigurationService.isPasswordRegistrationEnabled()) {
            throw new IllegalArgumentException("Registration is disabled");
        }
        // Check if the user already exists
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User with email " + email + " already exists");
        }

        // Validate and set timezone (defaults to UTC if null/invalid)
        String validatedTimezone = validateTimezone(timezone);

        // Create a new user entity
        UserEntity user = UserEntity.builder()
                .email(email)
                .fullName(fullName)
                .role("USER")
                .isActive(true)
                .emailVerified(false)
                .passwordHash(securePasswordUtils.hashPassword(password))
                .timezone(validatedTimezone)
                .measureUnit(MeasureUnit.METRIC)
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

    /**
     * Validates timezone to ensure it's a valid IANA timezone identifier.
     * Returns UTC as default if timezone is null or invalid.
     * Also handles timezone name mapping (e.g., Europe/Kiev -> Europe/Kyiv).
     *
     * @param timezone the timezone to validate
     * @return validated timezone or "UTC" as default
     */
    private String validateTimezone(String timezone) {
        if (timezone == null || timezone.trim().isEmpty()) {
            return "UTC";
        }

        String normalizedTimezone = timezone.trim();

        // Apply timezone mapping if needed
        normalizedTimezone = TIMEZONE_MAPPING.getOrDefault(normalizedTimezone, normalizedTimezone);

        try {
            java.time.ZoneId.of(normalizedTimezone);
            return normalizedTimezone;
        } catch (java.time.DateTimeException e) {
            log.warn("Invalid timezone provided: {}, defaulting to UTC", timezone);
            return "UTC";
        }
    }

    /**
     * Validates custom map tile URL to ensure it's a safe and valid tile URL.
     * Must contain {z}, {x}, {y} placeholders and use HTTP/HTTPS protocols.
     *
     * @param tileUrl the tile URL to validate
     * @throws IllegalArgumentException if the tile URL is invalid
     */
    private void validateCustomMapTileUrl(String tileUrl) {
        if (tileUrl == null || tileUrl.trim().isEmpty()) {
            return; // Allow null/empty to use default tiles
        }

        String normalizedUrl = tileUrl.trim().toLowerCase();

        // Security: Only allow http:// and https:// protocols
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            log.warn("Invalid tile URL protocol: {}", tileUrl);
            throw new IllegalArgumentException("Custom map tile URL must use HTTP or HTTPS protocol");
        }

        // Security: Prevent dangerous protocols
        if (normalizedUrl.contains("javascript:") || normalizedUrl.contains("data:") ||
                normalizedUrl.contains("file:") || normalizedUrl.contains("ftp:")) {
            log.warn("Dangerous protocol detected in tile URL: {}", tileUrl);
            throw new IllegalArgumentException("Invalid tile URL protocol");
        }

        // Validate required placeholders for tile coordinates
        if (!tileUrl.contains("{z}") || !tileUrl.contains("{x}") || !tileUrl.contains("{y}")) {
            log.warn("Tile URL missing required placeholders: {}", tileUrl);
            throw new IllegalArgumentException("Custom map tile URL must contain {z}, {x}, and {y} placeholders");
        }

        // Security: Basic path traversal check
        if (tileUrl.contains("..")) {
            log.warn("Path traversal attempt in tile URL: {}", tileUrl);
            throw new IllegalArgumentException("Invalid tile URL format");
        }

        // Optional: Validate URL length (already enforced by @Size, but double-check)
        if (tileUrl.length() > 1000) {
            throw new IllegalArgumentException("Custom map tile URL is too long (max 1000 characters)");
        }
    }

    /**
     * Validates default redirect URL to ensure it's a safe internal path.
     * Must start with / and not contain dangerous patterns.
     *
     * @param redirectUrl the redirect URL to validate
     * @throws IllegalArgumentException if the redirect URL is invalid
     */
    private void validateDefaultRedirectUrl(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.trim().isEmpty()) {
            return; // Allow null/empty to use default behavior
        }

        String normalizedUrl = redirectUrl.trim();

        // Security: Only allow relative paths (internal redirects)
        if (!normalizedUrl.startsWith("/")) {
            log.warn("Invalid redirect URL - must start with /: {}", redirectUrl);
            throw new IllegalArgumentException("Default redirect URL must be an internal path starting with /");
        }

        // Security: Prevent absolute URLs and external redirects
        if (normalizedUrl.startsWith("//") ||
            normalizedUrl.toLowerCase().contains("http:") ||
            normalizedUrl.toLowerCase().contains("https:") ||
            normalizedUrl.toLowerCase().contains("javascript:") ||
            normalizedUrl.toLowerCase().contains("data:")) {
            log.warn("Dangerous pattern detected in redirect URL: {}", redirectUrl);
            throw new IllegalArgumentException("Default redirect URL must be an internal path, not an external URL");
        }

        // Security: Basic path traversal check
        if (normalizedUrl.contains("..")) {
            log.warn("Path traversal attempt in redirect URL: {}", redirectUrl);
            throw new IllegalArgumentException("Invalid redirect URL format");
        }

        // Validate URL length (already enforced by @Size, but double-check)
        if (normalizedUrl.length() > 1000) {
            throw new IllegalArgumentException("Default redirect URL is too long (max 1000 characters)");
        }
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

        // Validate and update timezone
        if (request.getTimezone() != null) {
            String validatedTimezone = validateTimezone(request.getTimezone());
            user.setTimezone(validatedTimezone);
            log.debug("Updated timezone for user {} to {}", user.getId(), validatedTimezone);
        }

        if (request.getCustomMapTileUrl() != null) {
            validateCustomMapTileUrl(request.getCustomMapTileUrl());
            user.setCustomMapTileUrl(request.getCustomMapTileUrl().trim().isEmpty() ? null : request.getCustomMapTileUrl().trim());
            log.debug("Updated custom map tile URL for user {}", user.getId());
        }

        if (request.getMeasureUnit() != null) {
            user.setMeasureUnit(request.getMeasureUnit());
            log.debug("Updated measure unit for user {}", user.getId());
        }

        if (request.getDefaultRedirectUrl() != null) {
            validateDefaultRedirectUrl(request.getDefaultRedirectUrl());
            user.setDefaultRedirectUrl(request.getDefaultRedirectUrl().trim().isEmpty() ? null : request.getDefaultRedirectUrl().trim());
            log.debug("Updated default redirect URL for user {}", user.getId());
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

    /**
     * Update timeline preferences and return type of change made.
     * @return "classification" for classification-only changes, "structural" for full regeneration needed, null for no changes
     */
    @Transactional
    public String updateTimelinePreferences(UUID userId, UpdateTimelinePreferencesRequest update) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new UserNotFoundException("User not found");
        }

        if (user.timelinePreferences == null) {
            user.timelinePreferences = new TimelinePreferences();
        }

        // Use registry-based updater - eliminates all the manual if/else logic
        preferencesUpdater.updatePreferences(user.timelinePreferences, update);

        // Determine which type of event to fire based on parameter types
        boolean hasClassificationChanges = hasClassificationParameters(update);
        boolean hasStructuralChanges = hasStructuralParameters(update);

        if (hasClassificationChanges && !hasStructuralChanges) {
            // Synchronous trip type recalculation (fast, no job needed)
            log.info("Firing travel classification updated event for user {} (classification-only changes)", userId);
            classificationUpdatedEvent.fire(new TravelClassificationUpdatedEvent(
                    userId,
                    user.timelinePreferences,
                    false // wasResetToDefaults = false
            ));
            return "classification"; // Classification-only changes
        } else if (hasStructuralChanges) {
            // Structural changes require full timeline regeneration
            log.info("Structural timeline changes detected for user {}", userId);
            return "structural"; // Full regeneration needed
        } else {
            // No timeline changes needed
            log.info("No timeline regeneration needed for user {} (no parameter changes detected)", userId);
            return null;
        }
    }

    /**
     * Reset preferences to defaults.
     * @return true if timeline regeneration is needed, false otherwise
     */
    @Transactional
    public boolean resetTimelinePreferencesToDefaults(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new UserNotFoundException("User not found");
        }
        user.timelinePreferences = null;
        log.info("Reset timeline preferences to defaults for user {}", userId);
        return true; // Always need timeline regeneration when resetting
    }

    /**
     * Create async job for timeline regeneration.
     * This method is NOT transactional and should be called after the transaction commits.
     */
    public UUID createTimelineRegenerationJob(UUID userId) {
        try {
            UUID jobId = asyncTimelineGenerationService.regenerateTimelineAsync(userId);
            log.info("Created async timeline regeneration job {} for user {}", jobId, userId);
            return jobId;
        } catch (IllegalStateException e) {
            log.warn("Could not create regeneration job for user {}: {}", userId, e.getMessage());
            return null;
        }
    }


    /**
     * Check if the update contains travel classification parameters.
     */
    private boolean hasClassificationParameters(UpdateTimelinePreferencesRequest update) {
        return update.getWalkingMaxAvgSpeed() != null ||
                update.getWalkingMaxMaxSpeed() != null ||
                update.getCarMinAvgSpeed() != null ||
                update.getCarMinMaxSpeed() != null ||
                update.getShortDistanceKm() != null;
    }

    /**
     * Check if the update contains structural timeline parameters.
     */
    private boolean hasStructuralParameters(UpdateTimelinePreferencesRequest update) {
        return update.getStaypointVelocityThreshold() != null ||
                update.getStaypointRadiusMeters() != null ||
                update.getStaypointMinDurationMinutes() != null ||
                update.getTripDetectionAlgorithm() != null ||
                update.getUseVelocityAccuracy() != null ||
                update.getStaypointMaxAccuracyThreshold() != null ||
                update.getStaypointMinAccuracyRatio() != null ||
                update.getIsMergeEnabled() != null ||
                update.getMergeMaxDistanceMeters() != null ||
                update.getMergeMaxTimeGapMinutes() != null ||
                update.getPathSimplificationEnabled() != null ||
                update.getPathSimplificationTolerance() != null ||
                update.getPathMaxPoints() != null ||
                update.getPathAdaptiveSimplification() != null ||
                update.getDataGapThresholdSeconds() != null ||
                update.getDataGapMinDurationSeconds() != null;
    }
}