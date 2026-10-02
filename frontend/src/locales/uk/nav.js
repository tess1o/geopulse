/**
 * Navigation labels (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * Keys mirror en/nav.js exactly -- `items.*` matches the `key` field on each AppNavigation entry and
 * is shared with route `meta.titleKey`. Run `npm run test:run -- locales` to verify key parity.
 */
export default {
    sections: {
        timeline: 'Хронологія',
        explore: 'Дослідження',
        organizeAndShare: 'Організація та обмін',
        settingsAndData: 'Налаштування та дані',
        administration: 'Адміністрування',
        overview: 'Огляд',
        operations: 'Операції',
        peopleAndAccess: 'Користувачі та доступ',
        configuration: 'Конфігурація',
        appearance: 'Зовнішній вигляд'
    },
    items: {
        timeline: 'Хронологія',
        dashboard: 'Дашборд',
        'timeline-labels': 'Мітки хронології',
        trips: 'Плани поїздок',
        'location-analytics': 'Аналітика локацій',
        'journey-insights': 'Статистика подорожей',
        rewind: 'Підсумки',
        'coverage-explorer': 'Дослідження покриття',
        'ai-chat': 'AI-помічник',
        'favorites-management': 'Обране',
        geofences: 'Геозони',
        friends: 'Друзі',
        'share-links': 'Посилання для обміну',
        profile: 'Профіль',
        notifications: 'Сповіщення',
        'location-sources': 'Джерела локацій',
        preferences: 'Налаштування хронології',
        'gps-data': 'GPS-дані',
        'geocoding-management': 'Геокодування',
        export: 'Експорт / Імпорт',
        help: 'Довідка та підтримка',
        'admin-dashboard': 'Огляд',
        'admin-backups': 'Резервні копії та відновлення',
        'admin-timeline-regeneration': 'Кампанії перегенерації хронології',
        'admin-users': 'Керування користувачами',
        'admin-invitations': 'Запрошення',
        'admin-oidc-providers': 'OIDC-провайдери',
        'admin-audit-logs': 'Журнали аудиту',
        'admin-settings': 'Системні налаштування'
    },
    theme: {
        label: 'Тема: {mode}',
        modes: {
            light: 'Світла',
            dark: 'Темна',
            system: 'Системна'
        }
    },
    loggedInAs: 'Ви увійшли як:',
    logout: 'Вийти',
    version: 'Версія',
    newVersionAvailable: 'Доступна версія {version}',
    pageTitles: {
        home: 'Головна',
        cityDetails: 'Деталі міста',
        countryDetails: 'Деталі країни',
        sharedLocation: 'Спільне місцезнаходження',
        sharedTimeline: 'Спільна хронологія',
        error: 'Помилка',
        pageNotFound: 'Сторінку не знайдено'
    }
}
