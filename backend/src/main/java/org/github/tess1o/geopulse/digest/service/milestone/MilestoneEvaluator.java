package org.github.tess1o.geopulse.digest.service.milestone;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.digest.model.Milestone;
import org.github.tess1o.geopulse.digest.service.calculation.ActiveDaysCalculator;
import org.github.tess1o.geopulse.digest.service.repository.DigestDataRepository;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Evaluates milestones using configuration-driven definitions.
 * Replaces 400+ lines of repetitive milestone code with a clean, data-driven approach.
 */
@ApplicationScoped
public class MilestoneEvaluator {

    @Inject
    MilestoneDefinitions milestoneDefinitions;

    @Inject
    DigestDataRepository dataRepository;

    @Inject
    ActiveDaysCalculator activeDaysCalculator;

    /**
     * Build all milestones for the period.
     *
     * @param userId     User ID
     * @param stats      User statistics
     * @param timeline   Timeline data
     * @param periodType "monthly" or "yearly"
     * @param start      Period start time
     * @param end        Period end time
     * @param zoneId     User's timezone
     * @return List of achieved milestones, sorted by tier
     */
    public List<Milestone> buildMilestones(UUID userId, UserStatistics stats, MovementTimelineDTO timeline,
                                          String periodType, Instant start, Instant end, ZoneId zoneId) {
        List<Milestone> milestones = new ArrayList<>();
        boolean isMonthly = "monthly".equals(periodType);

        // Calculate values for milestone evaluation
        double distanceKm = stats.getTotalDistanceMeters() / 1000.0;
        int placesCount = dataRepository.getUniquePlacesCount(userId, start, end);
        int tripCount = timeline.getTripsCount();
        int activeDaysCount = activeDaysCalculator.calculateActiveDaysCount(timeline, start, end, zoneId);

        // Find longest single trip
        double longestTripKm = 0;
        if (timeline.getTrips() != null && !timeline.getTrips().isEmpty()) {
            longestTripKm = timeline.getTrips().stream()
                    .mapToDouble(trip -> trip.getDistanceMeters() / 1000.0)
                    .max()
                    .orElse(0);
        }

        // Evaluate each milestone category
        Milestone distanceMilestone = evaluateCategory("distance", distanceKm, isMonthly);
        if (distanceMilestone != null) {
            milestones.add(distanceMilestone);
        }

        Milestone placesMilestone = evaluateCategory("places", placesCount, isMonthly);
        if (placesMilestone != null) {
            milestones.add(placesMilestone);
        }

        Milestone tripsMilestone = evaluateCategory("trips", tripCount, isMonthly);
        if (tripsMilestone != null) {
            milestones.add(tripsMilestone);
        }

        Milestone activeDaysMilestone = evaluateCategory("activeDays", activeDaysCount, isMonthly);
        if (activeDaysMilestone != null) {
            milestones.add(activeDaysMilestone);
        }

        Milestone epicJourneyMilestone = evaluateCategory("epicJourney", longestTripKm, isMonthly);
        if (epicJourneyMilestone != null) {
            milestones.add(epicJourneyMilestone);
        }

        // Sort by tier (Diamond > Gold > Silver > Bronze)
        milestones.sort((m1, m2) -> {
            int tier1 = getTierValue(m1.getTier());
            int tier2 = getTierValue(m2.getTier());
            return Integer.compare(tier2, tier1); // Descending order
        });

        return milestones;
    }

    /**
     * Evaluate a single milestone category.
     *
     * @param categoryName Category name (distance, places, trips, etc.)
     * @param value        Actual value to evaluate
     * @param isMonthly    Whether this is a monthly digest
     * @return Milestone if threshold is met, null otherwise
     */
    private Milestone evaluateCategory(String categoryName, double value, boolean isMonthly) {
        MilestoneDefinitions.MilestoneCategory category = milestoneDefinitions.getCategoryByName(categoryName);
        if (category == null) {
            return null;
        }

        // Get appropriate thresholds (monthly or yearly)
        List<MilestoneDefinitions.MilestoneThreshold> thresholds = isMonthly
                ? category.monthlyThresholds()
                : category.yearlyThresholds();

        // Find the highest threshold that the value meets
        // Thresholds are sorted from highest to lowest
        for (MilestoneDefinitions.MilestoneThreshold threshold : thresholds) {
            if (value >= threshold.threshold()) {
                // Format description with actual value
                // Use int for %d format, double for %.0f format
                String description;
                if (threshold.descriptionTemplate().contains("%d")) {
                    description = String.format(threshold.descriptionTemplate(), (int) value);
                } else {
                    description = String.format(threshold.descriptionTemplate(), value);
                }

                return Milestone.builder()
                        .id(threshold.id())
                        .title(threshold.title())
                        .description(description)
                        .icon(threshold.icon())
                        .tier(threshold.tier())
                        .category(categoryName)
                        .build();
            }
        }

        return null;
    }

    /**
     * Get numeric value for tier sorting.
     */
    private int getTierValue(String tier) {
        return switch (tier) {
            case "diamond" -> 4;
            case "gold" -> 3;
            case "silver" -> 2;
            case "bronze" -> 1;
            default -> 0;
        };
    }
}
