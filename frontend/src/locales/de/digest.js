/**
 * Time Digest milestones (German), resolved from backend MessageDescriptors. See en/digest.js.
 */
export default {
    milestone: {
        // Distance
        distance_champion: { title: 'Kilometerchampion', description: { monthly: 'Diesen Monat {value} km unterwegs' } },
        road_warrior: { title: 'Kilometerfresser', description: { monthly: 'Diesen Monat {value} km unterwegs', yearly: 'Dieses Jahr {value} km unterwegs' } },
        active_explorer: { title: 'Aktiver Entdecker', description: { monthly: 'Diesen Monat {value} km unterwegs', yearly: 'Dieses Jahr {value} km unterwegs' } },
        local_traveler: { title: 'Lokal unterwegs', description: { monthly: 'Diesen Monat {value} km unterwegs' } },
        epic_traveler: { title: 'Epischer Reisender', description: { yearly: 'Dieses Jahr {value} km unterwegs' } },
        casual_traveler: { title: 'Gelegenheitsreisender', description: { yearly: 'Dieses Jahr {value} km unterwegs' } },

        // Places
        ultimate_explorer: { title: 'Ultimativer Entdecker', description: { monthly: '{value} verschiedene Orte besucht' } },
        city_explorer: { title: 'Stadterkunder', description: { monthly: '{value} verschiedene Orte besucht' } },
        local_navigator: { title: 'Ortskundig', description: { monthly: '{value} verschiedene Orte besucht' } },
        place_explorer: { title: 'Ortsentdecker', description: { monthly: '{value} verschiedene Orte besucht' } },
        world_explorer: { title: 'Welterkunder', description: { yearly: '{value} verschiedene Orte besucht' } },
        globe_trotter: { title: 'Weltenbummler', description: { yearly: '{value} verschiedene Orte besucht' } },
        city_navigator: { title: 'Stadtnavigator', description: { yearly: '{value} verschiedene Orte besucht' } },
        local_explorer: { title: 'Lokaler Entdecker', description: { yearly: '{value} verschiedene Orte besucht' } },

        // Trips
        road_regular: { title: 'Stammgast auf der Straße', description: { monthly: '{value} Strecken zurückgelegt', yearly: '{value} Strecken zurückgelegt' } },
        frequent_flyer: { title: 'Vielflieger', description: { monthly: '{value} Strecken zurückgelegt' } },
        regular_traveler: { title: 'Regelmäßig unterwegs', description: { monthly: '{value} Strecken zurückgelegt', yearly: '{value} Strecken zurückgelegt' } },
        getting_started: { title: 'Der Anfang ist gemacht', description: { monthly: '{value} Strecken zurückgelegt' } },
        always_moving: { title: 'Immer in Bewegung', description: { yearly: '{value} Strecken zurückgelegt' } },
        frequent_traveler: { title: 'Vielreisender', description: { yearly: '{value} Strecken zurückgelegt' } },

        // Active days
        full_month: { title: 'Ganzer Monat', description: { monthly: '{value} Tage aktiv' } },
        mostly_active: { title: 'Meistens aktiv', description: { monthly: '{value} Tage aktiv', yearly: '{value} Tage aktiv (75 %)' } },
        half_month: { title: 'Halber Monat', description: { monthly: '{value} Tage aktiv' } },
        active_week: { title: 'Aktive Woche', description: { monthly: '{value} Tage aktiv' } },
        year_round: { title: 'Das ganze Jahr', description: { yearly: '{value} Tage aktiv (90 %)' } },
        half_year: { title: 'Halbes Jahr', description: { yearly: '{value} Tage aktiv (50 %)' } },
        quarterly_active: { title: 'Ein Vierteljahr aktiv', description: { yearly: '{value} Tage aktiv (25 %)' } },

        // Epic journey
        epic_adventure: { title: 'Episches Abenteuer', description: { monthly: 'Längste Strecke: {value} km' } },
        road_trip: { title: 'Roadtrip', description: { monthly: 'Längste Strecke: {value} km', yearly: 'Längste Strecke: {value} km' } },
        long_distance: { title: 'Langstrecke', description: { monthly: 'Längste Strecke: {value} km' } },
        day_trip: { title: 'Tagesausflug', description: { monthly: 'Längste Strecke: {value} km' } },
        epic_voyage: { title: 'Epische Reise', description: { yearly: 'Längste Strecke: {value} km' } },
        long_journey: { title: 'Lange Reise', description: { yearly: 'Längste Strecke: {value} km' } },
        weekend_trip: { title: 'Wochenendtrip', description: { yearly: 'Längste Strecke: {value} km' } }
    }
}
