/**
 * Journey Insights achievement badges (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * See en/badges.js for how these keys are resolved (`badges.<badge.id>.title` /
 * `badges.<badge.id>.description`, looked up by `badgeText()` in JourneyInsights.vue). Key set must
 * stay identical to en/badges.js; `locales.test.js` enforces that.
 */
export default {
    // Distance
    total_distance_1000: { title: 'Дорожній воїн', description: 'Подолайте 1000+ км сумарно' },
    total_distance_10000: { title: 'Континентальний мандрівник', description: 'Подолайте 10 000+ км сумарно' },
    total_distance_50000: { title: 'Трансконтинентальний мандрівник', description: 'Подолайте 50 000+ км сумарно' },
    total_distance_100000: { title: 'Глобальний кочівник', description: 'Подолайте 100 000+ км сумарно' },
    total_distance_500_000: { title: 'Володар планети', description: 'Подолайте 500 000+ км сумарно' },
    total_distance_1_000_000: { title: 'Майстер мільйона миль', description: 'Подолайте 1 000 000+ км сумарно' },
    target_trip_distance_100: { title: 'Вершник на сотню', description: 'Завершіть поїздку довжиною 100+ км' },
    target_trip_distance_42195: { title: 'Марафонець', description: 'Завершіть поїздку довжиною 42+ км' },
    target_trip_distance_500: { title: 'Далека відстань', description: 'Подолайте 500 км за одну поїздку' },
    daily_driver: { title: 'Щоденний водій', description: 'Подолайте 50+ км за один день' },
    long_hauler: { title: 'Далекобійник', description: 'Завершіть поїздку тривалістю 4+ години' },
    speed_deamon_150: { title: 'Демон швидкості', description: 'Максимальна швидкість 150 км/год' },
    speed_deamon_200: { title: 'Блискавична швидкість', description: 'Максимальна швидкість 200 км/год' },

    // Exploration
    cites_visited_3: { title: 'Початківець міст', description: 'Відвідайте 3+ міста' },
    cites_visited_10: { title: 'Мандрівник світом', description: 'Відвідайте 10+ міст' },
    cites_visited_20: { title: 'Дослідник світу', description: 'Відвідайте 20+ міст' },
    cites_visited_50: { title: 'Міський кочівник', description: 'Відвідайте 50+ міст' },
    cites_visited_100: { title: 'Колекціонер міст', description: 'Відвідайте 100+ міст' },
    cites_visited_200: { title: 'Підкорювач мегаполісів', description: 'Відвідайте 200+ міст' },
    country_visited_2: { title: 'Перетинач кордонів', description: 'Відвідайте 2+ країни' },
    country_visited_5: { title: 'Колекціонер країн', description: 'Відвідайте 5+ країн' },
    country_visited_10: { title: 'Міжнародний дослідник', description: 'Відвідайте 10+ країн' },
    country_visited_20: { title: 'Ветеран паспорта', description: 'Відвідайте 20+ країн' },
    country_visited_50: { title: 'Громадянин світу', description: 'Відвідайте 50+ країн' },
    local_explorer: { title: 'Місцевий дослідник', description: 'Відкрийте 25 місць у своїй місцевості' },
    local_legend: { title: 'Місцева легенда', description: 'Відвідайте одне й те саме місце 10 разів' },

    // Modes of transport
    flight_trips_1: { title: 'Перший політ', description: 'Завершіть свою першу поїздку літаком' },
    flight_trips_5: { title: '5 польотів', description: 'Завершіть 5 поїздок літаком' },
    flight_trips_10: { title: '10 польотів', description: 'Завершіть 10 поїздок літаком' },
    train_trips_1: { title: 'Перша поїздка потягом', description: 'Завершіть свою першу поїздку потягом' },
    train_trips_5: { title: '5 поїздок потягом', description: 'Завершіть 5 поїздок потягом' },
    train_trips_10: { title: '10 поїздок потягом', description: 'Завершіть 10 поїздок потягом' },

    // Consistency
    daily_habit_10: { title: 'Початок звички', description: 'Подорожуйте щодня протягом 10 днів поспіль' },
    daily_habit_30: { title: 'Стійка звичка', description: 'Подорожуйте щодня протягом 30 днів поспіль' },
    daily_habit_60: { title: 'Майстер звички', description: 'Подорожуйте щодня протягом 60 днів поспіль' },
    daily_habit_120: { title: 'Чемпіон звички', description: 'Подорожуйте щодня протягом 120 днів поспіль' },
    daily_habit_365: { title: 'Легенда звички', description: 'Подорожуйте щодня протягом 365 днів поспіль' },
    track_data_week_1: { title: 'Тижнева серія', description: 'Відстежуйте поїздки 7 днів поспіль' },
    first_month: { title: 'Перший місяць завершено!', description: 'Успішно відстежували протягом 30 днів' },
    first_steps: { title: 'Перші кроки', description: 'Завершіть свою першу поїздку (≥1 км)' },
    busy_bee: { title: 'Роботяща бджілка', description: 'Здійсніть 50 поїздок протягом одного місяця' },

    // Time of day
    time_of_day_early_bird: { title: 'Рання пташка', description: 'Розпочніть поїздку до 6:00' },
    time_of_day_night_owl: { title: 'Нічна сова', description: 'Розпочніть поїздку після 22:00' },
    time_of_day_midnight_move: { title: 'Опівнічний рух', description: 'Розпочніть поїздку між північчю та 5:00' },

    // Weather
    weather_first_sample: { title: 'Свідок погоди', description: 'Зберіть свій перший зразок погоди' },
    weather_frost_walker: { title: 'Мандрівник морозу', description: 'Зафіксуйте зразок погоди при 0°C або нижче' },
    weather_heatwave: { title: 'Дослідник спеки', description: 'Зафіксуйте зразок погоди при 30°C або вище' },
    weather_rain_traveler: { title: 'Мандрівник дощу', description: 'Зберіть 10 зразків дощової погоди' },
    weather_four_seasons: { title: 'Чотири пори року', description: 'Зберіть зразки погоди в усі чотири пори року' }
}
