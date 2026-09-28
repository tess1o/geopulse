/**
 * Notifications page (in-app notification inbox). (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    page: {
        title: 'Сповіщення',
        description: 'Слідкуйте за тим, що GeoPulse помітив для вас.',
        refresh: 'Оновити',
        markAllSeen: 'Позначити всі як переглянуті'
    },
    filters: {
        unread: 'Непереглянуті',
        all: 'Усі'
    },
    sourceOptions: {
        all: 'Усі джерела',
        geofence: 'Геозони',
        timeline: 'Хронологія',
        import: 'Імпорт',
        export: 'Експорт',
        friends: 'Запрошення друзів'
    },
    table: {
        source: 'Джерело',
        notification: 'Сповіщення',
        time: 'Час',
        status: 'Статус',
        actions: 'Дії'
    },
    status: {
        seen: 'Переглянуто',
        unread: 'Непереглянуто'
    },
    newNotification: 'Нове сповіщення',
    markSeen: 'Позначити переглянутим',
    titleFallback: 'Сповіщення',
    empty: {
        unread: 'Усе переглянуто! Непереглянутих сповіщень немає.',
        none: 'Сповіщень поки немає.'
    },
    toast: {
        errorSummary: 'Помилка сповіщень',
        loadFailed: 'Не вдалося завантажити сповіщення',
        markSeenFailed: 'Не вдалося позначити сповіщення як переглянуте',
        markAllSeenFailed: 'Не вдалося позначити всі сповіщення як переглянуті'
    },
    store: {
        loadUnreadCountFailed: 'Не вдалося завантажити кількість непереглянутих сповіщень',
        loadPreferencesFailed: 'Не вдалося завантажити налаштування сповіщень',
        savePreferencesFailed: 'Не вдалося зберегти налаштування сповіщень',
        loadReleaseAnnouncementFailed: 'Не вдалося завантажити оголошення про реліз',
        unreadToastSummary: 'Непереглянуті сповіщення',
        unreadToastDetail: 'У вас {count} непереглянуте сповіщення. | У вас {count} непереглянутих сповіщення. | У вас {count} непереглянутих сповіщень.',
        viewAllNotifications: 'Переглянути всі сповіщення',
        browserBlockedSummary: 'Сповіщення браузера заблоковано',
        browserBlockedDetail: 'Увімкніть дозвіл на сповіщення в налаштуваннях браузера, щоб використовувати сповіщення на робочому столі.',
        browserNotSentSummary: 'Сповіщення браузера не надіслано',
        browserNotSentDetail: 'Дозвіл браузера не надано. Увімкніть сповіщення браузера в меню дзвіночка ще раз.',
        browserFailedSummary: 'Помилка сповіщення браузера',
        browserFailedDetail: 'Ваш браузер або ОС заблокували сповіщення на робочому столі. Сповіщення в застосунку залишаються активними.'
    },
    bell: {
        openAriaLabel: 'Відкрити скриньку сповіщень',
        unreadTag: '{count} непереглянуто',
        browserAlerts: 'Сповіщення браузера',
        browserNotSupported: 'Сповіщення браузера недоступні в цьому браузері.',
        noUnread: 'Немає непереглянутих сповіщень.',
        noNotifications: 'Сповіщень не знайдено.',
        defaultMessage: 'Нове сповіщення.',
        markSeen: 'Позначити переглянутим',
        markAllSeen: 'Позначити всі переглянутими',
        errorSummary: 'Помилка сповіщення'
    },
    sourceNotificationSuffix: '{source} сповіщення',
    display: {
        sources: {
            geofence: 'Геозона',
            timeline: 'Хронологія',
            import: 'Імпорт',
            export: 'Експорт',
            friendInvite: 'Друзі',
            weather: 'Погода',
            backupHealth: 'Стан резервного копіювання',
            gpsHealth: 'Стан GPS',
            product: 'Продукт',
            rewind: 'Підсумки'
        },
        types: {
            geofenceEnter: 'Вхід у геозону',
            geofenceLeave: 'Вихід із геозони',
            timelineRegenerationRequired: 'Заплановано оновлення хронології',
            importCompleted: 'Імпорт завершено',
            importFailed: 'Помилка імпорту',
            exportCompleted: 'Експорт завершено',
            exportFailed: 'Помилка експорту',
            friendInviteReceived: 'Запрошення в друзі',
            friendInviteAccepted: 'Запрошення прийнято',
            weatherQuotaReached: 'Досягнуто квоти погоди',
            weatherQuotaRestored: 'Квоту погоди відновлено',
            gpsHealthIncidentOpened: 'Відстеження GPS призупинено',
            gpsHealthIncidentResolved: 'Відстеження GPS відновлено',
            productReleaseAvailable: 'Що нового',
            rewindReady: 'Підсумки готові'
        },
        actions: {
            openGeofenceEvents: 'Відкрити події геозон',
            viewTimelineStatus: 'Переглянути статус хронології',
            openImports: 'Відкрити імпорти',
            openExports: 'Відкрити експорти',
            openInvitations: 'Відкрити запрошення',
            openFriends: 'Відкрити друзів',
            openAdminDashboard: 'Відкрити панель адміністратора',
            openBackupSettings: 'Відкрити налаштування резервного копіювання',
            openNotifications: 'Відкрити сповіщення',
            openRewind: 'Відкрити підсумки',
            openNotification: 'Відкрити сповіщення'
        }
    }
}
