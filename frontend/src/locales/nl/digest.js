/**
 * Time Digest milestones.
 *
 * The backend sends each milestone\'s title/description as a `MessageDescriptor`
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
        distance_champion: { title: 'Afstandskampioen', description: { monthly: '{value} km afgelegd deze maand' } },
        road_warrior: { title: 'Wegkrijger', description: { monthly: '{value} km afgelegd deze maand', yearly: '{value} km afgelegd dit jaar' } },
        active_explorer: { title: 'Actieve Verkenner', description: { monthly: '{value} km afgelegd deze maand', yearly: '{value} km afgelegd dit jaar' } },
        local_traveler: { title: 'Lokale Reiziger', description: { monthly: '{value} km afgelegd deze maand' } },
        epic_traveler: { title: 'Epische Reiziger', description: { yearly: '{value} km afgelegd dit jaar' } },
        casual_traveler: { title: 'Casual Reiziger', description: { yearly: '{value} km afgelegd dit jaar' } },

        // Places
        ultimate_explorer: { title: 'Uiteindelijk Verkenner', description: { monthly: '{value} unieke plaatsen bezocht' } },
        city_explorer: { title: 'Stedenverkenner', description: { monthly: '{value} unieke plaatsen bezocht' } },
        local_navigator: { title: 'Lokale Navigator', description: { monthly: '{value} unieke plaatsen bezocht' } },
        place_explorer: { title: 'Plaatsverkenner', description: { monthly: '{value} unieke plaatsen bezocht' } },
        world_explorer: { title: 'Wereldverkenner', description: { yearly: '{value} unieke plaatsen bezocht' } },
        globe_trotter: { title: 'Wereldreiziger', description: { yearly: '{value} unieke plaatsen bezocht' } },
        city_navigator: { title: 'Stedennavigator', description: { yearly: '{value} unieke plaatsen bezocht' } },
        local_explorer: { title: 'Lokale Verkenner', description: { yearly: '{value} unieke plaatsen bezocht' } },

        // Trips
        road_regular: { title: 'Wegreguliere', description: { monthly: '{value} reizen voltooid', yearly: '{value} reizen voltooid' } },
        frequent_flyer: { title: 'Frequente Vlieger', description: { monthly: '{value} reizen voltooid' } },
        regular_traveler: { title: 'Reguliere Reiziger', description: { monthly: '{value} reizen voltooid', yearly: '{value} reizen voltooid' } },
        getting_started: { title: 'Aan de Slag', description: { monthly: '{value} reizen voltooid' } },
        always_moving: { title: 'Altijd in Beweging', description: { yearly: '{value} reizen voltooid' } },
        frequent_traveler: { title: 'Frequente Reiziger', description: { yearly: '{value} reizen voltooid' } },

        // Active days
        full_month: { title: 'Volledige Maand', description: { monthly: '{value} actieve dagen' } },
        mostly_active: { title: 'Voornamelijk Actief', description: { monthly: '{value} actieve dagen', yearly: '{value} actieve dagen (75%)' } },
        half_month: { title: 'Half Maand', description: { monthly: '{value} actieve dagen' } },
        active_week: { title: 'Actieve Week', description: { monthly: '{value} actieve dagen' } },
        year_round: { title: 'Heel Jaar', description: { yearly: '{value} actieve dagen (90%)' } },
        half_year: { title: 'Half Jaar', description: { yearly: '{value} actieve dagen (50%)' } },
        quarterly_active: { title: 'Kwartaal Actief', description: { yearly: '{value} actieve dagen (25%)' } },

        // Epic journey
        epic_adventure: { title: 'Episch Avontuur', description: { monthly: 'Langste reis: {value} km' } },
        road_trip: { title: 'Roadtrip', description: { monthly: 'Langste reis: {value} km', yearly: 'Langste reis: {value} km' } },
        long_distance: { title: 'Lange Afstand', description: { monthly: 'Langste reis: {value} km' } },
        day_trip: { title: 'Dagreis', description: { monthly: 'Langste reis: {value} km' } },
        epic_voyage: { title: 'Epische Reis', description: { yearly: 'Langste reis: {value} km' } },
        long_journey: { title: 'Lange Reis', description: { yearly: 'Langste reis: {value} km' } },
        weekend_trip: { title: 'Weekendreis', description: { yearly: 'Langste reis: {value} km' } }
    }
}

