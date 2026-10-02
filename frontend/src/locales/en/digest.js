/**
 * Time Digest milestones.
 *
 * The backend sends each milestone's title/description as a `MessageDescriptor`
 * ({key, parameters, fallback}, see `shared/api/MessageDescriptor.java`) and the frontend resolves it
 * via `formatMessageDescriptor()` (see DigestMilestones.vue). Keys here are
 * `digest.milestone.<id>.title` / `digest.milestone.<id>.description.<monthly|yearly>`, matching
 * `MilestoneEvaluator.java`. The same milestone id can appear in both the monthly and yearly
 * threshold lists (e.g. `road_warrior`), so the description is split per period; the title is not,
 * since it is identical across periods for every id that is reused.
 *
 * Generic page chrome (section titles, tier labels, empty states) lives under `analytics.digest.*`
 * instead -- this file only covers the per-milestone text.
 */
export default {
    milestone: {
        // Distance
        distance_champion: { title: 'Distance Champion', description: { monthly: 'Traveled {value} km this month' } },
        road_warrior: { title: 'Road Warrior', description: { monthly: 'Traveled {value} km this month', yearly: 'Traveled {value} km this year' } },
        active_explorer: { title: 'Active Explorer', description: { monthly: 'Traveled {value} km this month', yearly: 'Traveled {value} km this year' } },
        local_traveler: { title: 'Local Traveler', description: { monthly: 'Traveled {value} km this month' } },
        epic_traveler: { title: 'Epic Traveler', description: { yearly: 'Traveled {value} km this year' } },
        casual_traveler: { title: 'Casual Traveler', description: { yearly: 'Traveled {value} km this year' } },

        // Places
        ultimate_explorer: { title: 'Ultimate Explorer', description: { monthly: 'Visited {value} unique places' } },
        city_explorer: { title: 'City Explorer', description: { monthly: 'Visited {value} unique places' } },
        local_navigator: { title: 'Local Navigator', description: { monthly: 'Visited {value} unique places' } },
        place_explorer: { title: 'Place Explorer', description: { monthly: 'Visited {value} unique places' } },
        world_explorer: { title: 'World Explorer', description: { yearly: 'Visited {value} unique places' } },
        globe_trotter: { title: 'Globe Trotter', description: { yearly: 'Visited {value} unique places' } },
        city_navigator: { title: 'City Navigator', description: { yearly: 'Visited {value} unique places' } },
        local_explorer: { title: 'Local Explorer', description: { yearly: 'Visited {value} unique places' } },

        // Trips
        road_regular: { title: 'Road Regular', description: { monthly: 'Completed {value} trips', yearly: 'Completed {value} trips' } },
        frequent_flyer: { title: 'Frequent Flyer', description: { monthly: 'Completed {value} trips' } },
        regular_traveler: { title: 'Regular Traveler', description: { monthly: 'Completed {value} trips', yearly: 'Completed {value} trips' } },
        getting_started: { title: 'Getting Started', description: { monthly: 'Completed {value} trips' } },
        always_moving: { title: 'Always Moving', description: { yearly: 'Completed {value} trips' } },
        frequent_traveler: { title: 'Frequent Traveler', description: { yearly: 'Completed {value} trips' } },

        // Active days
        full_month: { title: 'Full Month', description: { monthly: 'Active {value} days' } },
        mostly_active: { title: 'Mostly Active', description: { monthly: 'Active {value} days', yearly: 'Active {value} days (75%)' } },
        half_month: { title: 'Half Month', description: { monthly: 'Active {value} days' } },
        active_week: { title: 'Active Week', description: { monthly: 'Active {value} days' } },
        year_round: { title: 'Year Round', description: { yearly: 'Active {value} days (90%)' } },
        half_year: { title: 'Half Year', description: { yearly: 'Active {value} days (50%)' } },
        quarterly_active: { title: 'Quarterly Active', description: { yearly: 'Active {value} days (25%)' } },

        // Epic journey
        epic_adventure: { title: 'Epic Adventure', description: { monthly: 'Longest trip: {value} km' } },
        road_trip: { title: 'Road Trip', description: { monthly: 'Longest trip: {value} km', yearly: 'Longest trip: {value} km' } },
        long_distance: { title: 'Long Distance', description: { monthly: 'Longest trip: {value} km' } },
        day_trip: { title: 'Day Trip', description: { monthly: 'Longest trip: {value} km' } },
        epic_voyage: { title: 'Epic Voyage', description: { yearly: 'Longest trip: {value} km' } },
        long_journey: { title: 'Long Journey', description: { yearly: 'Longest trip: {value} km' } },
        weekend_trip: { title: 'Weekend Trip', description: { yearly: 'Longest trip: {value} km' } }
    }
}
