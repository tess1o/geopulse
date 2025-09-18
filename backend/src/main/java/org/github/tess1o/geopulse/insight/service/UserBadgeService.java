package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.insight.model.BadgeProcessingResult;
import org.github.tess1o.geopulse.insight.model.UserBadgeEntity;
import org.github.tess1o.geopulse.insight.repository.UserBadgeRepository;
import org.github.tess1o.geopulse.insight.service.badge.BadgeCalculator;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing user badges - creation, updates, and ensuring completeness.
 * Extracted from BadgeCalculationJob for better separation of concerns.
 */
@ApplicationScoped
@Slf4j
public class UserBadgeService {

    private final UserBadgeRepository userBadgeRepository;
    private final BadgeCalculatorRegistry calculatorRegistry;

    public UserBadgeService(UserBadgeRepository userBadgeRepository, 
                           BadgeCalculatorRegistry calculatorRegistry) {
        this.userBadgeRepository = userBadgeRepository;
        this.calculatorRegistry = calculatorRegistry;
    }

    /**
     * Ensure all required badge types exist for a user.
     * Creates missing badge entries for new badge calculators.
     */
    @Transactional
    public BadgeProcessingResult ensureBadgesExist(UUID userId, Set<String> requiredBadgeIds) {
        log.debug("Ensuring all badges exist for user: {}", userId);

        // Get existing badge IDs for this user
        List<String> existingBadgeIds = userBadgeRepository.findBadgeIdsByUserId(userId);
        
        // Find missing badge types
        List<String> missingBadgeIds = requiredBadgeIds.stream()
                .filter(badgeId -> !existingBadgeIds.contains(badgeId))
                .toList();
        
        if (missingBadgeIds.isEmpty()) {
            log.debug("No missing badges for user: {}", userId);
            return BadgeProcessingResult.EMPTY;
        }

        log.info("Creating {} missing badges for user {}: {}", 
                missingBadgeIds.size(), userId, missingBadgeIds);
        
        int createdCount = 0;
        for (String badgeId : missingBadgeIds) {
            if (createBadgeForUser(userId, badgeId) != null) {
                createdCount++;
            }
        }
        
        return BadgeProcessingResult.created(createdCount);
    }

    /**
     * Update all incomplete badges for a user.
     * Only processes badges where earned = false.
     */
    @Transactional
    public BadgeProcessingResult updateIncompleteBadges(UUID userId) {
        log.debug("Updating incomplete badges for user: {}", userId);

        List<UserBadgeEntity> incompleteBadges = userBadgeRepository.findIncompleteBadgesByUserId(userId);
        
        if (incompleteBadges.isEmpty()) {
            log.debug("No incomplete badges found for user: {}", userId);
            return BadgeProcessingResult.EMPTY;
        }

        int totalUpdated = 0;
        int newlyEarned = 0;

        for (UserBadgeEntity existingBadge : incompleteBadges) {
            try {
                BadgeCalculator calculator = calculatorRegistry.getCalculator(existingBadge.getBadgeId());
                if (calculator == null) {
                    log.warn("No calculator found for badge: {}", existingBadge.getBadgeId());
                    continue;
                }

                // Recalculate the badge
                Badge updatedBadge = calculator.calculateBadge(userId);
                if (updatedBadge == null) {
                    log.warn("Calculator returned null for badge: {}", existingBadge.getBadgeId());
                    continue;
                }

                // Update the existing entity with new values
                boolean wasEarned = existingBadge.isEarned();
                existingBadge.updateFromBadge(updatedBadge);
                existingBadge.setLastCalculated(Instant.now());
                
                userBadgeRepository.persist(existingBadge);
                totalUpdated++;

                // Check if badge was newly earned
                if (!wasEarned && existingBadge.isEarned()) {
                    newlyEarned++;
                    log.info("User {} earned badge: {} - {}", userId, 
                            existingBadge.getBadgeId(), existingBadge.getTitle());
                }

            } catch (Exception e) {
                log.error("Failed to update badge {} for user {}: {}", 
                         existingBadge.getBadgeId(), userId, e.getMessage(), e);
            }
        }

        log.debug("Updated {} badges for user {}, {} newly earned", totalUpdated, userId, newlyEarned);
        return new BadgeProcessingResult(totalUpdated, newlyEarned, 0);
    }

    /**
     * Create a badge entry for a user by calculating it.
     * Returns the created entity or null if creation failed.
     */
    @Transactional
    public UserBadgeEntity createBadgeForUser(UUID userId, String badgeId) {
        try {
            BadgeCalculator calculator = calculatorRegistry.getCalculator(badgeId);
            if (calculator == null) {
                log.warn("No calculator found for badge: {}", badgeId);
                return null;
            }
            
            Badge badge = calculator.calculateBadge(userId);
            if (badge == null) {
                log.warn("Calculator returned null for badge: {}", badgeId);
                return null;
            }
            
            UserBadgeEntity newBadge = UserBadgeEntity.fromBadge(badge, userId);
            newBadge.setLastCalculated(Instant.now());
            userBadgeRepository.persist(newBadge);
            
            log.debug("Created badge {} for user {}", badgeId, userId);
            return newBadge;
            
        } catch (Exception e) {
            log.error("Failed to create badge {} for user {}: {}", 
                     badgeId, userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Process all badges for a user (ensure existence + update incomplete).
     * Convenience method that combines both operations.
     */
    @Transactional
    public BadgeProcessingResult processAllBadges(UUID userId) {
        // Ensure all badge types exist
        BadgeProcessingResult ensureResult = ensureBadgesExist(userId, calculatorRegistry.getAllBadgeIds());

        // Update incomplete badges
        BadgeProcessingResult updateResult = updateIncompleteBadges(userId);

        return ensureResult.add(updateResult);
    }
}