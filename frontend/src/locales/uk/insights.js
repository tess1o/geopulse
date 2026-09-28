/**
 * Journey Insights page (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * `visits` carries THREE plural forms (one / few / many) -- Ukrainian selects on the count's last
 * digits, unlike English's two. vue-i18n picks the form via `t(key, named, count)`.
 */
export default {
    page: {
        title: 'Статистика подорожей',
        subtitle: 'Ваша історія переміщень в одному місці.'
    },
    loading: 'Формуємо статистику ваших подорожей…',
    empty: {
        title: 'Немає даних про подорожі',
        description: 'Почніть відстежувати своє місцеположення, щоб відкрити статистику про ваші переміщення та досягнення.'
    },
    hero: {
        eyebrow: 'За весь час',
        movementSummary: '{distance} переміщень.',
        movementTitle: 'Як ви переміщалися',
        movementAria: 'Розподіл відстані за видом транспорту',
        noMovement: 'Переміщень ще не зафіксовано.'
    },
    movement: {
        byCar: 'Автомобіль',
        byMotorcycle: 'Мотоцикл',
        byPublicTransport: 'Громадський транспорт',
        byWalk: 'Пішки',
        byBicycle: 'Велосипед',
        byRunning: 'Біг',
        byTrain: 'Потяг',
        byFlight: 'Літак',
        byBoat: 'Човен',
        byUnknown: 'Нерозпізнано'
    },
    places: {
        title: 'Де ви були',
        countries: 'Досліджено країн',
        noCountries: 'Почніть подорожувати, щоб відкрити країни!',
        cities: 'Відвідано міст',
        noCities: 'Почніть відстеження, щоб відкрити міста!',
        countryFlagAria: 'Прапор {country}',
        visits: '{count} візит | {count} візити | {count} візитів'
    },
    patterns: {
        title: 'Часові закономірності',
        mostActiveMonth: 'Найактивніший місяць',
        mostActiveMonthDetail: 'Ваш історичний пік активності',
        currentMonth: 'Поточний місяць',
        busiestDay: 'Найзавантаженіший день',
        mostActiveTime: 'Найактивніший час'
    },
    milestones: {
        title: 'Віхи подорожей',
        earned: 'Отримано',
        earnedOn: 'Отримано {date}',
        empty: 'Продовжуйте досліджувати, щоб відкрити віхи!',
        groups: {
            distance: 'Віхи відстані',
            exploration: 'Дослідження',
            modes: 'Види переміщення',
            consistency: 'Серії активності',
            time: 'Час доби',
            weather: 'Дослідник погоди',
            other: 'Інші досягнення'
        }
    }
}
