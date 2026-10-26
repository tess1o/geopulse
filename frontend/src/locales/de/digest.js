/**
 * Time Digest milestones (German), resolved from backend MessageDescriptors. See en/digest.js.
 */
export default {
    milestone: {
        // Distance
        distance_champion: { title: 'Streckenchampion', description: { monthly: 'Diesen Monat {value} km unterwegs' } },
        road_warrior: { title: 'Straßenkämpfer', description: { monthly: 'Diesen Monat {value} km unterwegs', yearly: 'Dieses Jahr {value} km unterwegs' } },
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
        road_regular: { title: 'Stammgast auf der Straße', description: { monthly: '{value} Fahrten absolviert', yearly: '{value} Fahrten absolviert' } },
        frequent_flyer: { title: 'Vielflieger', description: { monthly: '{value} Fahrten absolviert' } },
        regular_traveler: { title: 'Regelmäßig unterwegs', description: { monthly: '{value} Fahrten absolviert', yearly: '{value} Fahrten absolviert' } },
        getting_started: { title: 'Der Anfang ist gemacht', description: { monthly: '{value} Fahrten absolviert' } },
        always_moving: { title: 'Immer in Bewegung', description: { yearly: '{value} Fahrten absolviert' } },
        frequent_traveler: { title: 'Vielreisender', description: { yearly: '{value} Fahrten absolviert' } },

        // Active days
        full_month: { title: 'Ganzer Monat', description: { monthly: '{value} Tage aktiv' } },
        mostly_active: { title: 'Meistens aktiv', description: { monthly: '{value} Tage aktiv', yearly: '{value} Tage aktiv (75 %)' } },
        half_month: { title: 'Halber Monat', description: { monthly: '{value} Tage aktiv' } },
        active_week: { title: 'Aktive Woche', description: { monthly: '{value} Tage aktiv' } },
        year_round: { title: 'Das ganze Jahr', description: { yearly: '{value} Tage aktiv (90 %)' } },
        half_year: { title: 'Halbes Jahr', description: { yearly: '{value} Tage aktiv (50 %)' } },
        quarterly_active: { title: 'Ein Vierteljahr aktiv', description: { yearly: '{value} Tage aktiv (25 %)' } },

        // Epic journey
        epic_adventure: { title: 'Episches Abenteuer', description: { monthly: 'Längste Fahrt: {value} km' } },
        road_trip: { title: 'Roadtrip', description: { monthly: 'Längste Fahrt: {value} km', yearly: 'Längste Fahrt: {value} km' } },
        long_distance: { title: 'Langstrecke', description: { monthly: 'Längste Fahrt: {value} km' } },
        day_trip: { title: 'Tagesausflug', description: { monthly: 'Längste Fahrt: {value} km' } },
        epic_voyage: { title: 'Epische Reise', description: { yearly: 'Längste Fahrt: {value} km' } },
        long_journey: { title: 'Lange Reise', description: { yearly: 'Längste Fahrt: {value} km' } },
        weekend_trip: { title: 'Wochenendtrip', description: { yearly: 'Längste Fahrt: {value} km' } }
    }
}
