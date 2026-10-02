/**
 * Time Digest milestones (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * See en/digest.js for how these keys are resolved (`digest.milestone.<id>.title` /
 * `digest.milestone.<id>.description.<monthly|yearly>`, matched against the en catalog's shape).
 */
export default {
    milestone: {
        // Distance
        distance_champion: { title: 'Чемпіон з відстані', description: { monthly: 'Подолано {value} км цього місяця' } },
        road_warrior: { title: 'Дорожній воїн', description: { monthly: 'Подолано {value} км цього місяця', yearly: 'Подолано {value} км цього року' } },
        active_explorer: { title: 'Активний дослідник', description: { monthly: 'Подолано {value} км цього місяця', yearly: 'Подолано {value} км цього року' } },
        local_traveler: { title: 'Місцевий мандрівник', description: { monthly: 'Подолано {value} км цього місяця' } },
        epic_traveler: { title: 'Епічний мандрівник', description: { yearly: 'Подолано {value} км цього року' } },
        casual_traveler: { title: 'Випадковий мандрівник', description: { yearly: 'Подолано {value} км цього року' } },

        // Places
        ultimate_explorer: { title: 'Найкращий дослідник', description: { monthly: 'Відвідано {value} унікальних місць' } },
        city_explorer: { title: 'Дослідник міст', description: { monthly: 'Відвідано {value} унікальних місць' } },
        local_navigator: { title: 'Місцевий навігатор', description: { monthly: 'Відвідано {value} унікальних місць' } },
        place_explorer: { title: 'Дослідник місць', description: { monthly: 'Відвідано {value} унікальних місць' } },
        world_explorer: { title: 'Дослідник світу', description: { yearly: 'Відвідано {value} унікальних місць' } },
        globe_trotter: { title: 'Мандрівник світом', description: { yearly: 'Відвідано {value} унікальних місць' } },
        city_navigator: { title: 'Навігатор міст', description: { yearly: 'Відвідано {value} унікальних місць' } },
        local_explorer: { title: 'Місцевий дослідник', description: { yearly: 'Відвідано {value} унікальних місць' } },

        // Trips
        road_regular: { title: 'Завсідник доріг', description: { monthly: 'Завершено {value} поїздок', yearly: 'Завершено {value} поїздок' } },
        frequent_flyer: { title: 'Частий мандрівник', description: { monthly: 'Завершено {value} поїздок' } },
        regular_traveler: { title: 'Постійний мандрівник', description: { monthly: 'Завершено {value} поїздок', yearly: 'Завершено {value} поїздок' } },
        getting_started: { title: 'Перші кроки', description: { monthly: 'Завершено {value} поїздок' } },
        always_moving: { title: 'Завжди в русі', description: { yearly: 'Завершено {value} поїздок' } },
        frequent_traveler: { title: 'Досвідчений мандрівник', description: { yearly: 'Завершено {value} поїздок' } },

        // Active days
        full_month: { title: 'Повний місяць', description: { monthly: 'Активність {value} днів' } },
        mostly_active: { title: 'Переважно активний', description: { monthly: 'Активність {value} днів', yearly: 'Активність {value} днів (75%)' } },
        half_month: { title: 'Половина місяця', description: { monthly: 'Активність {value} днів' } },
        active_week: { title: 'Активний тиждень', description: { monthly: 'Активність {value} днів' } },
        year_round: { title: 'Цілий рік', description: { yearly: 'Активність {value} днів (90%)' } },
        half_year: { title: 'Половина року', description: { yearly: 'Активність {value} днів (50%)' } },
        quarterly_active: { title: 'Активний квартал', description: { yearly: 'Активність {value} днів (25%)' } },

        // Epic journey
        epic_adventure: { title: 'Епічна пригода', description: { monthly: 'Найдовша поїздка: {value} км' } },
        road_trip: { title: 'Подорож на колесах', description: { monthly: 'Найдовша поїздка: {value} км', yearly: 'Найдовша поїздка: {value} км' } },
        long_distance: { title: 'Далека відстань', description: { monthly: 'Найдовша поїздка: {value} км' } },
        day_trip: { title: 'Одноденна подорож', description: { monthly: 'Найдовша поїздка: {value} км' } },
        epic_voyage: { title: 'Епічна подорож', description: { yearly: 'Найдовша поїздка: {value} км' } },
        long_journey: { title: 'Довга подорож', description: { yearly: 'Найдовша поїздка: {value} км' } },
        weekend_trip: { title: 'Поїздка на вихідні', description: { yearly: 'Найдовша поїздка: {value} км' } }
    }
}
