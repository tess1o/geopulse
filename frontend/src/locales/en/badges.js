/**
 * Journey Insights achievement badges.
 *
 * The backend computes and persists these badges (see `insight/service/badge/*BadgeCalculator.java`)
 * with English title/description text baked in at calculation time -- there is no MessageDescriptor
 * plumbing for them. JourneyInsights.vue instead looks up `badges.<badge.id>.title` /
 * `badges.<badge.id>.description` here by the backend's locale-neutral badge id (see `te()`/`t()` in
 * `badgeText()`) and falls back to the backend's English text when a locale hasn't caught up.
 *
 * EN values are verbatim copies of the backend's English text, so this catalog and the backend never
 * visibly disagree for English users even though this file is technically redundant with the
 * fallback. Every id below is fixed and enumerable -- confirmed against every `BadgeCalculator` bean
 * the backend registers (`BadgeCalculatorRegistry`) -- so add an entry here whenever a new badge
 * calculator is added.
 */
export default {
    // Distance
    total_distance_1000: { title: 'Road Warrior', description: 'Travel 1,000+ km total' },
    total_distance_10000: { title: 'Continental Cruiser', description: 'Travel 10,000+ km total' },
    total_distance_50000: { title: 'Transcontinental Traveler', description: 'Travel 50,000+ km total' },
    total_distance_100000: { title: 'Global Nomad', description: 'Travel 100,000+ km total' },
    total_distance_500_000: { title: 'Planet Circler', description: 'Travel 500,000+ km total' },
    total_distance_1_000_000: { title: 'Million Mile Master', description: 'Travel 1,000,000+ km total' },
    target_trip_distance_100: { title: 'Century Rider', description: 'Complete a trip of 100+ km' },
    target_trip_distance_42195: { title: 'Marathon Runner', description: 'Complete a trip of 42+ km' },
    target_trip_distance_500: { title: 'Long Distance', description: 'Travelled 500km in a single trip' },
    daily_driver: { title: 'Daily Driver', description: 'Travel 50+ km in a single day' },
    long_hauler: { title: 'Long Hauler', description: 'Complete a trip lasting 4+ hours' },
    speed_deamon_150: { title: 'Speed Deamon', description: 'Max speed of 150 km/h' },
    speed_deamon_200: { title: 'Lightning Fast', description: 'Max speed of 200 km/h' },

    // Exploration
    cites_visited_3: { title: 'City Starter', description: 'Visit 3+ cities' },
    cites_visited_10: { title: 'Globe Trotter', description: 'Visit 10+ cities' },
    cites_visited_20: { title: 'World Explorer', description: 'Visit 20+ cities' },
    cites_visited_50: { title: 'Urban Nomad', description: 'Visit 50+ cities' },
    cites_visited_100: { title: 'City Collector', description: 'Visit 100+ cities' },
    cites_visited_200: { title: 'Metropolis Conqueror', description: 'Visit 200+ cities' },
    country_visited_2: { title: 'Border Crosser', description: 'Visit 2+ countries' },
    country_visited_5: { title: 'Country Collector', description: 'Visit 5+ countries' },
    country_visited_10: { title: 'International Explorer', description: 'Visit 10+ countries' },
    country_visited_20: { title: 'Passport Veteran', description: 'Visit 20+ countries' },
    country_visited_50: { title: 'World Citizen', description: 'Visit 50+ countries' },
    local_explorer: { title: 'Local Explorer', description: 'Discover 25 places in your area' },
    local_legend: { title: 'Local Legend', description: 'Visit the same place 10 times' },

    // Modes of transport
    flight_trips_1: { title: 'First Flight', description: 'Complete your first flight trip' },
    flight_trips_5: { title: '5 Flight Trips', description: 'Complete 5 flight trips' },
    flight_trips_10: { title: '10 Flight Trips', description: 'Complete 10 flight trips' },
    train_trips_1: { title: 'First Train', description: 'Complete your first train trip' },
    train_trips_5: { title: '5 Train Trips', description: 'Complete 5 train trips' },
    train_trips_10: { title: '10 Train Trips', description: 'Complete 10 train trips' },

    // Consistency
    daily_habit_10: { title: 'Daily Habit Starter', description: 'Travel every day for 10 consecutive days' },
    daily_habit_30: { title: 'Daily Habit Adept', description: 'Travel every day for 30 consecutive days' },
    daily_habit_60: { title: 'Daily Habit Master', description: 'Travel every day for 60 consecutive days' },
    daily_habit_120: { title: 'Daily Habit Champion', description: 'Travel every day for 120 consecutive days' },
    daily_habit_365: { title: 'Daily Habit Legend', description: 'Travel every day for 365 consecutive days' },
    track_data_week_1: { title: 'Week Streak', description: 'Track trips for 7 consecutive days' },
    first_month: { title: 'First Month Complete!', description: 'Successfully tracked for 30 days' },
    first_steps: { title: 'First Steps', description: 'Complete your first trip (≥1 km)' },
    busy_bee: { title: 'Busy Bee', description: 'Take 50 trips within one month' },

    // Time of day
    time_of_day_early_bird: { title: 'Early Bird', description: 'Start a trip before 6:00 AM' },
    time_of_day_night_owl: { title: 'Night Owl', description: 'Start a trip after 10:00 PM' },
    time_of_day_midnight_move: { title: 'Midnight Move', description: 'Start a trip between midnight and 5:00 AM' },

    // Weather
    weather_first_sample: { title: 'Weather Witness', description: 'Collect your first weather sample' },
    weather_frost_walker: { title: 'Frost Walker', description: 'Record a weather sample at 0 C or colder' },
    weather_heatwave: { title: 'Heatwave Explorer', description: 'Record a weather sample at 30 C or warmer' },
    weather_rain_traveler: { title: 'Rain Traveler', description: 'Collect 10 rainy weather samples' },
    weather_four_seasons: { title: 'Four Seasons', description: 'Collect weather samples in all four seasons' }
}
