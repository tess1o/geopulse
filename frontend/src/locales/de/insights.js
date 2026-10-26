/**
 * Journey Insights page (German). Badge titles and descriptions live in ./badges.js.
 */
export default {
    page: {
        title: 'Reise-Einblicke',
        subtitle: 'Deine Standortgeschichte an einem Ort.'
    },
    loading: 'Deine Reise-Einblicke werden erstellt …',
    empty: {
        title: 'Keine Reisedaten vorhanden',
        description: 'Starte die Standortaufzeichnung, um Einblicke in deine Reisemuster und Erfolge zu bekommen.'
    },
    hero: {
        eyebrow: 'Gesamte Reise',
        movementSummary: '{distance} unterwegs.',
        movementTitle: 'So warst du unterwegs',
        movementAria: 'Strecke nach Fortbewegungsart',
        noMovement: 'Noch keine Bewegung aufgezeichnet.'
    },
    movement: {
        byCar: 'Auto',
        byMotorcycle: 'Motorrad',
        byPublicTransport: 'Öffentliche Verkehrsmittel',
        byWalk: 'Zu Fuß',
        byBicycle: 'Fahrrad',
        byRunning: 'Laufen',
        byTrain: 'Zug',
        byFlight: 'Flug',
        byBoat: 'Boot',
        byUnknown: 'Nicht eingestuft'
    },
    places: {
        title: 'Wo du warst',
        countries: 'Erkundete Länder',
        noCountries: 'Geh auf Entdeckungstour und sammle Länder!',
        cities: 'Besuchte Städte',
        noCities: 'Starte die Aufzeichnung und sammle Städte!',
        countryFlagAria: 'Flagge von {country}',
        visits: '{count} Besuch | {count} Besuche'
    },
    patterns: {
        title: 'Zeitliche Muster',
        mostActiveMonth: 'Aktivster Monat',
        mostActiveMonthDetail: 'Dein bisheriger Spitzenzeitraum',
        currentMonth: 'Aktueller Monat',
        busiestDay: 'Aktivster Tag',
        mostActiveTime: 'Aktivste Tageszeit',
        // Resolved from a backend `MessageDescriptor` via `formatMessageDescriptor()`.
        monthlyComparison: {
            earlyDays: 'Noch am Anfang – entdecke weiter!',
            noActivity: 'Diesen Monat noch keine Aktivität',
            noBaseline: 'Erster Monat der Aufzeichnung',
            noRate: 'Dein Aktivitätsverlauf entsteht gerade',
            similar: 'Ähnliches Tempo wie in deinem besten Monat',
            faster: '{percent} % schneller als in deinem besten Monat!',
            slower: '{percent} % langsamer als in deinem besten Monat'
        },
        dayInsight: {
            noActivity: 'Entdecke weiter, um dein Muster zu finden!',
            weekend: 'Perfekt für Wochenendabenteuer!',
            friday: 'Bereit fürs Wochenende!',
            monday: 'Starker Start in die Woche!',
            midweek: 'Du holst das Beste aus der Wochenmitte!'
        },
        timeInsight: {
            noActivity: 'Entdecke weiter, um dein Muster zu finden!',
            earlyBird: 'Frühaufsteher',
            evening: 'Abendmensch'
        }
    },
    milestones: {
        title: 'Meilensteine',
        earned: 'Erreicht',
        earnedOn: 'Erreicht am {date}',
        empty: 'Entdecke weiter, um Meilensteine freizuschalten!',
        groups: {
            distance: 'Strecke',
            exploration: 'Entdecken',
            modes: 'Fortbewegungsarten',
            consistency: 'Serien',
            time: 'Tageszeit',
            weather: 'Wetter',
            other: 'Weitere Erfolge'
        }
    }
}
