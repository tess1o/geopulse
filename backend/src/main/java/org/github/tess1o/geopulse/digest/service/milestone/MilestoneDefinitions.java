package org.github.tess1o.geopulse.digest.service.milestone;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration-driven milestone definitions.
 * This approach eliminates 400+ lines of repetitive milestone code by using a data-driven model.
 */
@ApplicationScoped
public class MilestoneDefinitions {

    /**
     * Represents a single milestone threshold configuration.
     */
    public record MilestoneThreshold(
            double threshold,
            String id,
            String title,
            String icon,
            String tier,
            String descriptionTemplate // e.g., "Traveled %.0f km this month"
    ) {
    }

    /**
     * Represents a category of milestones (distance, places, trips, etc.)
     */
    public record MilestoneCategory(
            String name,
            List<MilestoneThreshold> monthlyThresholds,
            List<MilestoneThreshold> yearlyThresholds
    ) {
    }

    /**
     * Get all milestone category definitions.
     */
    public List<MilestoneCategory> getAllCategories() {
        List<MilestoneCategory> categories = new ArrayList<>();

        // Distance Milestones
        categories.add(new MilestoneCategory(
                "distance",
                List.of(
                        new MilestoneThreshold(2000, "distance_champion", "Distance Champion", "💎", "diamond", "Traveled %.0f km this month"),
                        new MilestoneThreshold(1000, "road_warrior", "Road Warrior", "🥇", "gold", "Traveled %.0f km this month"),
                        new MilestoneThreshold(500, "active_explorer", "Active Explorer", "🥈", "silver", "Traveled %.0f km this month"),
                        new MilestoneThreshold(100, "local_traveler", "Local Traveler", "🥉", "bronze", "Traveled %.0f km this month")
                ),
                List.of(
                        new MilestoneThreshold(25000, "epic_traveler", "Epic Traveler", "💎", "diamond", "Traveled %.0f km this year"),
                        new MilestoneThreshold(10000, "road_warrior", "Road Warrior", "🥇", "gold", "Traveled %.0f km this year"),
                        new MilestoneThreshold(5000, "active_explorer", "Active Explorer", "🥈", "silver", "Traveled %.0f km this year"),
                        new MilestoneThreshold(1000, "casual_traveler", "Casual Traveler", "🥉", "bronze", "Traveled %.0f km this year")
                )
        ));

        // Places Milestones
        categories.add(new MilestoneCategory(
                "places",
                List.of(
                        new MilestoneThreshold(30, "ultimate_explorer", "Ultimate Explorer", "💎", "diamond", "Visited %d unique places"),
                        new MilestoneThreshold(20, "city_explorer", "City Explorer", "🥇", "gold", "Visited %d unique places"),
                        new MilestoneThreshold(10, "local_navigator", "Local Navigator", "🥈", "silver", "Visited %d unique places"),
                        new MilestoneThreshold(5, "place_explorer", "Place Explorer", "🥉", "bronze", "Visited %d unique places")
                ),
                List.of(
                        new MilestoneThreshold(200, "world_explorer", "World Explorer", "💎", "diamond", "Visited %d unique places"),
                        new MilestoneThreshold(100, "globe_trotter", "Globe Trotter", "🥇", "gold", "Visited %d unique places"),
                        new MilestoneThreshold(50, "city_navigator", "City Navigator", "🥈", "silver", "Visited %d unique places"),
                        new MilestoneThreshold(25, "local_explorer", "Local Explorer", "🥉", "bronze", "Visited %d unique places")
                )
        ));

        // Trips Milestones
        categories.add(new MilestoneCategory(
                "trips",
                List.of(
                        new MilestoneThreshold(100, "road_regular", "Road Regular", "💎", "diamond", "Completed %d trips"),
                        new MilestoneThreshold(50, "frequent_flyer", "Frequent Flyer", "🥇", "gold", "Completed %d trips"),
                        new MilestoneThreshold(30, "regular_traveler", "Regular Traveler", "🥈", "silver", "Completed %d trips"),
                        new MilestoneThreshold(10, "getting_started", "Getting Started", "🥉", "bronze", "Completed %d trips")
                ),
                List.of(
                        new MilestoneThreshold(1000, "always_moving", "Always Moving", "💎", "diamond", "Completed %d trips"),
                        new MilestoneThreshold(500, "road_regular", "Road Regular", "🥇", "gold", "Completed %d trips"),
                        new MilestoneThreshold(300, "frequent_traveler", "Frequent Traveler", "🥈", "silver", "Completed %d trips"),
                        new MilestoneThreshold(100, "regular_traveler", "Regular Traveler", "🥉", "bronze", "Completed %d trips")
                )
        ));

        // Active Days Milestones
        categories.add(new MilestoneCategory(
                "activeDays",
                List.of(
                        new MilestoneThreshold(25, "full_month", "Full Month", "💎", "diamond", "Active %d days"),
                        new MilestoneThreshold(20, "mostly_active", "Mostly Active", "🥇", "gold", "Active %d days"),
                        new MilestoneThreshold(15, "half_month", "Half Month", "🥈", "silver", "Active %d days"),
                        new MilestoneThreshold(7, "active_week", "Active Week", "🥉", "bronze", "Active %d days")
                ),
                List.of(
                        new MilestoneThreshold(330, "year_round", "Year Round", "💎", "diamond", "Active %d days (90%%)"),
                        new MilestoneThreshold(270, "mostly_active", "Mostly Active", "🥇", "gold", "Active %d days (75%%)"),
                        new MilestoneThreshold(180, "half_year", "Half Year", "🥈", "silver", "Active %d days (50%%)"),
                        new MilestoneThreshold(90, "quarterly_active", "Quarterly Active", "🥉", "bronze", "Active %d days (25%%)")
                )
        ));

        // Epic Journey Milestones
        categories.add(new MilestoneCategory(
                "epicJourney",
                List.of(
                        new MilestoneThreshold(500, "epic_adventure", "Epic Adventure", "💎", "diamond", "Longest trip: %.0f km"),
                        new MilestoneThreshold(200, "road_trip", "Road Trip", "🥇", "gold", "Longest trip: %.0f km"),
                        new MilestoneThreshold(100, "long_distance", "Long Distance", "🥈", "silver", "Longest trip: %.0f km"),
                        new MilestoneThreshold(50, "day_trip", "Day Trip", "🥉", "bronze", "Longest trip: %.0f km")
                ),
                List.of(
                        new MilestoneThreshold(1000, "epic_voyage", "Epic Voyage", "💎", "diamond", "Longest trip: %.0f km"),
                        new MilestoneThreshold(500, "long_journey", "Long Journey", "🥇", "gold", "Longest trip: %.0f km"),
                        new MilestoneThreshold(300, "road_trip", "Road Trip", "🥈", "silver", "Longest trip: %.0f km"),
                        new MilestoneThreshold(100, "weekend_trip", "Weekend Trip", "🥉", "bronze", "Longest trip: %.0f km")
                )
        ));

        return categories;
    }

    /**
     * Get category by name.
     */
    public MilestoneCategory getCategoryByName(String categoryName) {
        return getAllCategories().stream()
                .filter(cat -> cat.name().equals(categoryName))
                .findFirst()
                .orElse(null);
    }
}
