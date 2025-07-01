package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Service for generating timeline version hashes to detect when cached timeline data is stale.
 * Version hash includes all factors that could affect timeline generation:
 * - User's favorites (names, geometries, types)
 * - Timeline configuration preferences
 * - Timeline generation parameters
 */
@ApplicationScoped
@Slf4j
public class TimelineVersionService {

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    UserRepository userRepository;

    /**
     * Generate version hash based on user's favorites and timeline configuration.
     * This hash changes when any factor affecting timeline generation changes.
     *
     * @param userId       User ID
     * @param timelineDate Date of timeline (for potential future date-specific favorites)
     * @return SHA-256 hash representing current version state
     */
    public String generateTimelineVersion(UUID userId, Instant timelineDate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Add user ID
            digest.update(userId.toString().getBytes(StandardCharsets.UTF_8));
            
            // Add timeline date (formatted as UTC date)
            String dateString = timelineDate.atZone(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
            digest.update(dateString.getBytes(StandardCharsets.UTF_8));
            
            // Add user's favorites data
            addFavoritesData(digest, userId);
            
            // Add timeline configuration
            addTimelineConfiguration(digest, userId);
            
            // Convert to hex string
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
            
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback to timestamp-based version (will always be considered stale)
            return "fallback-" + System.currentTimeMillis();
        }
    }

    /**
     * Check if timeline data is still valid by comparing versions.
     *
     * @param userId        User ID
     * @param timelineDate  Timeline date
     * @param currentVersion Current version hash from cached data
     * @return true if timeline is still valid, false if stale
     */
    public boolean isTimelineValid(UUID userId, Instant timelineDate, String currentVersion) {
        if (currentVersion == null || currentVersion.isBlank()) {
            return false;
        }
        
        String latestVersion = generateTimelineVersion(userId, timelineDate);
        boolean isValid = latestVersion.equals(currentVersion);
        
        if (!isValid) {
            log.debug("Timeline version mismatch for user {} on {}: current={}, latest={}", 
                    userId, timelineDate, currentVersion, latestVersion);
        }
        
        return isValid;
    }

    /**
     * Add all user's favorites data to the hash calculation.
     */
    private void addFavoritesData(MessageDigest digest, UUID userId) {
        List<FavoritesEntity> favorites = favoritesRepository.findByUserId(userId);
        
        // Sort by ID to ensure consistent ordering
        favorites.sort((f1, f2) -> f1.getId().compareTo(f2.getId()));
        
        for (FavoritesEntity favorite : favorites) {
            // Add favorite ID
            digest.update(favorite.getId().toString().getBytes(StandardCharsets.UTF_8));
            
            // Add favorite name
            if (favorite.getName() != null) {
                digest.update(favorite.getName().getBytes(StandardCharsets.UTF_8));
            }
            
            // Add favorite type
            if (favorite.getType() != null) {
                digest.update(favorite.getType().name().getBytes(StandardCharsets.UTF_8));
            }
            
            // Add geometry (WKT representation for consistency)
            if (favorite.getGeometry() != null) {
                digest.update(favorite.getGeometry().toText().getBytes(StandardCharsets.UTF_8));
            }
            
            // Add city and country if present
            if (favorite.getCity() != null) {
                digest.update(favorite.getCity().getBytes(StandardCharsets.UTF_8));
            }
            if (favorite.getCountry() != null) {
                digest.update(favorite.getCountry().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Add timeline configuration preferences to the hash calculation.
     */
    private void addTimelineConfiguration(MessageDigest digest, UUID userId) {
        var user = userRepository.findById(userId);
        if (user == null || user.timelinePreferences == null) {
            // Use default configuration marker
            digest.update("default-config".getBytes(StandardCharsets.UTF_8));
            return;
        }
        
        TimelinePreferences prefs = user.timelinePreferences;
        
        // Add relevant configuration that affects timeline generation
        if (prefs.getStaypointDetectionAlgorithm() != null) {
            digest.update(prefs.getStaypointDetectionAlgorithm().getBytes(StandardCharsets.UTF_8));
        }
        
        if (prefs.getStaypointVelocityThreshold() != null) {
            digest.update(prefs.getStaypointVelocityThreshold().toString().getBytes(StandardCharsets.UTF_8));
        }
        
        if (prefs.getStaypointMaxAccuracyThreshold() != null) {
            digest.update(prefs.getStaypointMaxAccuracyThreshold().toString().getBytes(StandardCharsets.UTF_8));
        }
        
        if (prefs.getTripDetectionAlgorithm() != null) {
            digest.update(prefs.getTripDetectionAlgorithm().getBytes(StandardCharsets.UTF_8));
        }
        
        if (prefs.getTripMinDistanceMeters() != null) {
            digest.update(prefs.getTripMinDistanceMeters().toString().getBytes(StandardCharsets.UTF_8));
        }
        
        if (prefs.getTripMinDurationMinutes() != null) {
            digest.update(prefs.getTripMinDurationMinutes().toString().getBytes(StandardCharsets.UTF_8));
        }
        
        // Add merging preferences
        if (prefs.getIsMergeEnabled() != null) {
            digest.update(prefs.getIsMergeEnabled().toString().getBytes(StandardCharsets.UTF_8));
        }
        
        if (prefs.getMergeMaxDistanceMeters() != null) {
            digest.update(prefs.getMergeMaxDistanceMeters().toString().getBytes(StandardCharsets.UTF_8));
        }
        
        if (prefs.getMergeMaxTimeGapMinutes() != null) {
            digest.update(prefs.getMergeMaxTimeGapMinutes().toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Convert byte array to hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}