/**
 * Journey Insights page.
 *
 * EN values are verbatim from the previous literals -- the page's test asserts on this exact copy.
 *
 * Not included here: achievement badge titles and descriptions. Those are served by the backend and
 * persisted in `user_badges` with English text baked in, so they are translated separately, by badge
 * id, in `./badges.js` (see the lookup in JourneyInsights.vue) rather than through this file's page
 * chrome.
 */
export default {
    page: {
        title: 'Journey Insights',
        subtitle: 'Your location story, all in one place.'
    },
    loading: 'Building your journey insights…',
    empty: {
        title: 'No Journey Data Available',
        description: 'Start tracking your location to unlock insights about your travel patterns and achievements.'
    },
    hero: {
        eyebrow: 'All-time journey',
        // '{distance} of movement.' -- the distance is a formatted value, not a sentence fragment.
        movementSummary: '{distance} of movement.',
        movementTitle: 'How you moved',
        movementAria: 'Distance split by transport type',
        noMovement: 'No movement has been recorded yet.'
    },
    movement: {
        byCar: 'Car',
        byMotorcycle: 'Motorcycle',
        byPublicTransport: 'Public Transportation',
        byWalk: 'Walk',
        byBicycle: 'Bicycle',
        byRunning: 'Running',
        byTrain: 'Train',
        byFlight: 'Flight',
        byBoat: 'Boat',
        byUnknown: 'Unclassified'
    },
    places: {
        title: "Where you've been",
        countries: 'Countries explored',
        noCountries: 'Start exploring to discover countries!',
        cities: 'Cities visited',
        noCities: 'Start tracking to discover cities!',
        countryFlagAria: '{country} flag',
        // Plural: previously a bare `{{ visits }} visits`, which read "1 visits".
        visits: '{count} visit | {count} visits'
    },
    patterns: {
        title: 'Time patterns',
        mostActiveMonth: 'Most active month',
        mostActiveMonthDetail: 'Your historical peak activity period',
        currentMonth: 'Current month',
        busiestDay: 'Busiest day',
        mostActiveTime: 'Most active time',
        // Resolved from a backend `MessageDescriptor` via `formatMessageDescriptor()` -- see
        // TimePatternService.java for when each key is chosen.
        monthlyComparison: {
            earlyDays: 'Early days - keep exploring!',
            noActivity: 'No activity recorded this month yet',
            noBaseline: 'First month of tracking',
            noRate: 'Building your activity history',
            similar: 'Similar pace to your best month',
            faster: '{percent}% faster pace than your best month!',
            slower: '{percent}% slower pace than your best month'
        },
        dayInsight: {
            noActivity: 'Keep exploring to find your pattern!',
            weekend: 'Perfect for weekend adventures!',
            friday: 'Ready for the weekend!',
            monday: 'Starting the week strong!',
            midweek: 'Making the most of midweek!'
        },
        timeInsight: {
            noActivity: 'Keep exploring to find your pattern!',
            earlyBird: 'Early bird explorer',
            evening: 'Evening adventurer'
        }
    },
    milestones: {
        title: 'Journey milestones',
        earned: 'Earned',
        earnedOn: 'Earned {date}',
        empty: 'Keep exploring to unlock milestones!',
        groups: {
            distance: 'Distance milestones',
            exploration: 'Exploration',
            modes: 'Travel modes',
            consistency: 'Consistency streaks',
            time: 'Time of day',
            weather: 'Weather explorer',
            other: 'Other achievements'
        }
    }
}
