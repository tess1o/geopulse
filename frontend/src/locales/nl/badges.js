/**
 * Journey Insights achievement badges.
 *
 * The backend computes and persists these badges (see `insight/service/badge/*BadgeCalculator.java`)
 * with English title/description text baked in at calculation time -- there is no MessageDescriptor
 * plumbing for them. JourneyInsights.vue instead looks up `badges.<badge.id>.title` /
 * `badges.<badge.id>.description` here by the backend\'s locale-neutral badge id (see `te()`/`t()` in
 * `badgeText()`) and falls back to the backend\'s English text when a locale hasn\'t caught up.
 *
 * EN values are verbatim copies of the backend\'s English text, so this catalog and the backend never
 * visibly disagree for English users even though this file is technically redundant with the
 * fallback. Every id below is fixed and enumerable -- confirmed against every `BadgeCalculator` bean
 * the backend registers (`BadgeCalculatorRegistry`) -- so add an entry here whenever a new badge
 * calculator is added.
 */
export default {
    // Distance
    total_distance_1000: { title: 'Wegkrijger', description: '1.000+ km totaal afleggen' },
    total_distance_10000: { title: 'Continentale Cruise', description: '10.000+ km totaal afleggen' },
    total_distance_50000: { title: 'Transcontinentale Reiziger', description: '50.000+ km totaal afleggen' },
    total_distance_100000: { title: 'Wereldzwerver', description: '100.000+ km totaal afleggen' },
    total_distance_500_000: { title: 'Planeetomcirkelaar', description: '500.000+ km totaal afleggen' },
    total_distance_1_000_000: { title: 'Miljoen Mijl Meester', description: '1.000.000+ km totaal afleggen' },
    target_trip_distance_100: { title: 'Honderd Ruiter', description: 'Een reis van 100+ km voltooien' },
    target_trip_distance_42195: { title: 'Marathonloper', description: 'Een reis van 42+ km voltooien' },
    target_trip_distance_500: { title: 'Lange Afstand', description: '500 km in één reis afgelegd' },
    daily_driver: { title: 'Dagelijkse Rijder', description: '50+ km in één dag afleggen' },
    long_hauler: { title: 'Lange Afstand', description: 'Een reis voltooien van 4+ uur' },
    speed_deamon_150: { title: 'Snelheidsdemon', description: 'Maximale snelheid van 150 km/h' },
    speed_deamon_200: { title: 'Bliksemsnel', description: 'Maximale snelheid van 200 km/h' },

    // Exploration
    cites_visited_3: { title: 'Stedelijk Beginner', description: '3+ steden bezoeken' },
    cites_visited_10: { title: 'Wereldreiziger', description: '10+ steden bezoeken' },
    cites_visited_20: { title: 'Wereldverkenner', description: '20+ steden bezoeken' },
    cites_visited_50: { title: 'Stedelijk Nomade', description: '50+ steden bezoeken' },
    cites_visited_100: { title: 'Stedencollector', description: '100+ steden bezoeken' },
    cites_visited_200: { title: 'Grootstedenveroveraar', description: '200+ steden bezoeken' },
    country_visited_2: { title: 'Grensoversteker', description: '2+ landen bezoeken' },
    country_visited_5: { title: 'Landencollector', description: '5+ landen bezoeken' },
    country_visited_10: { title: 'Internationale Verkenner', description: '10+ landen bezoeken' },
    country_visited_20: { title: 'Paspoort Veteraan', description: '20+ landen bezoeken' },
    country_visited_50: { title: 'Wereldburger', description: '50+ landen bezoeken' },
    local_explorer: { title: 'Lokale Verkenner', description: '25 plaatsen in uw omgeving ontdekken' },
    local_legend: { title: 'Lokale Legende', description: 'Datzelfde plaats 10 keer bezoeken' },

    // Modes of transport
    flight_trips_1: { title: 'Eerste Vlucht', description: 'Uw eerste vluchtreis voltooien' },
    flight_trips_5: { title: '5 Vluchtreizen', description: '5 vluchtreizen voltooien' },
    flight_trips_10: { title: '10 Vluchtreizen', description: '10 vluchtreizen voltooien' },
    train_trips_1: { title: 'Eerste Trein', description: 'Uw eerste treinreis voltooien' },
    train_trips_5: { title: '5 Treinreizen', description: '5 treinreizen voltooien' },
    train_trips_10: { title: '10 Treinreizen', description: '10 treinreizen voltooien' },

    // Consistency
    daily_habit_10: { title: 'Dagelijkse Gewoonte Starter', description: 'Elke dag reizen gedurende 10 opeenvolgende dagen' },
    daily_habit_30: { title: 'Dagelijkse Gewoonte Adept', description: 'Elke dag reizen gedurende 30 opeenvolgende dagen' },
    daily_habit_60: { title: 'Dagelijkse Gewoonte Meester', description: 'Elke dag reizen gedurende 60 opeenvolgende dagen' },
    daily_habit_120: { title: 'Dagelijkse Gewoonte Kampioen', description: 'Elke dag reizen gedurende 120 opeenvolgende dagen' },
    daily_habit_365: { title: 'Dagelijkse Gewoonte Legende', description: 'Elke dag reizen gedurende 365 opeenvolgende dagen' },
    track_data_week_1: { title: 'Weekreeks', description: 'Reizen volgen gedurende 7 opeenvolgende dagen' },
    first_month: { title: 'Eerste Maand Voltooid!', description: 'Succesvol 30 dagen gevolgd' },
    first_steps: { title: 'Eerste Stappen', description: 'Uw eerste reis voltooien (≥1 km)' },
    busy_bee: { title: 'Drukke Bij', description: '50 reizen binnen één maand maken' },

    // Time of day
    time_of_day_early_bird: { title: 'Vroege Vogel', description: 'Een reis starten voor 06:00 uur' },
    time_of_day_night_owl: { title: 'Nachtuil', description: 'Een reis starten na 22:00 uur' },
    time_of_day_midnight_move: { title: 'Midnight Move', description: 'Een reis starten tussen middernacht en 05:00 uur' },

    // Weather
    weather_first_sample: { title: 'Weergetuige', description: 'Uw eerste weermonster verzamelen' },
    weather_frost_walker: { title: 'Vriesloper', description: 'Een weermonster opnemen bij 0 C of kouder' },
    weather_heatwave: { title: 'Hittegolf Verkenner', description: 'Een weermonster opnemen bij 30 C of warmer' },
    weather_rain_traveler: { title: 'Regenreiziger', description: '10 regenachtige weermonsters verzamelen' },
    weather_four_seasons: { title: 'Vier Seizoenen', description: 'Weermonsters verzamelen in alle vier de seizoenen' }
}

