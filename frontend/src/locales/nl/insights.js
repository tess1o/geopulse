/**
 * Journey Insights page.
 *
 * EN values are verbatim from the previous literals -- the page\'s test asserts on this exact copy.
 *
 * Not included here: achievement badge titles and descriptions. Those are served by the backend and
 * persisted in `user_badges` with English text baked in, so they are translated separately, by badge
 * id, in `./badges.js` (see the lookup in JourneyInsights.vue) rather than through this file\'s page
 * chrome.
 */
export default {
    page: {
        title: 'Reisinzichten',
        subtitle: 'Uw locatieverhaal, allemaal op één plek.'
    },
    loading: 'Uw reisinzichten opbouwen…',
    empty: {
        title: 'Geen Reisgegevens Beschikbaar',
        description: 'Begin met het volgen van uw locatie om inzichten over uw reispatronen en prestaties te ontgrendelen.'
    },
    hero: {
        eyebrow: 'Altijd reis',
        movementSummary: '{distance} aan beweging.',
        movementTitle: 'Hoe u bewoog',
        movementAria: 'Afstand opgedeeld per vervoerder',
        noMovement: 'Er is nog geen beweging geregistreerd.'
    },
    movement: {
        byCar: 'Auto',
        byMotorcycle: 'Motorfiets',
        byPublicTransport: 'Openbaar Vervoer',
        byWalk: 'Lopen',
        byBicycle: 'Fiets',
        byRunning: 'Hardlopen',
        byTrain: 'Trein',
        byFlight: 'Vlieg',
        byBoat: 'Boot',
        byUnknown: 'Ongeclassificeerd'
    },
    places: {
        title: "Waar u bent geweest",
        countries: 'Landen verkend',
        noCountries: 'Begin met verkennen om landen te ontdekken!',
        cities: 'Steden bezocht',
        noCities: 'Begin met volgen om steden te ontdekken!',
        countryFlagAria: '{country} vlag',
        visits: '{count} bezoek | {count} bezoeken'
    },
    patterns: {
        title: 'Tijdpatronen',
        mostActiveMonth: 'Actiefste maand',
        mostActiveMonthDetail: 'Uw historische piekactiviteitsperiode',
        currentMonth: 'Huidige maand',
        busiestDay: 'Drukkeste dag',
        mostActiveTime: 'Actiefste tijd',
        monthlyComparison: {
            earlyDays: 'Vroege dagen - blijf verkennen!',
            noActivity: 'Nog geen activiteit geregistreerd deze maand',
            noBaseline: 'Eerste maand van volgen',
            noRate: 'Uw activiteitsgeschiedenis opbouwen',
            similar: 'Gelijk tempo als uw beste maand',
            faster: '{percent}% sneller tempo dan uw beste maand!',
            slower: '{percent}% langzamer tempo dan uw beste maand'
        },
        dayInsight: {
            noActivity: 'Blijf verkennen om uw patroon te vinden!',
            weekend: 'Perfect voor weekendavonturen!',
            friday: 'Klaar voor het weekend!',
            monday: 'Goed begonnen de week!',
            midweek: 'Het beste uit de week halen!'
        },
        timeInsight: {
            noActivity: 'Blijf verkennen om uw patroon te vinden!',
            earlyBird: 'Vroege vogel verkenner',
            evening: 'Avondavonturier'
        }
    },
    milestones: {
        title: 'Reismijlpalen',
        earned: 'Verdiend',
        earnedOn: 'Verdiend op {date}',
        empty: 'Blijf verkennen om mijlpalen te ontgrendelen!',
        groups: {
            distance: 'Afstand mijlpalen',
            exploration: 'Verkenning',
            modes: 'Reismodi',
            consistency: 'Consistentie reeksen',
            time: 'Tijdstip',
            weather: 'Weerverkenner',
            other: 'Andere prestaties'
        }
    }
}

