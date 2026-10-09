/**
 * Shared UI components, search, dashboard, and home content. (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    mainApp: {
        shareLabel: 'Поділитися',
        shareAria: 'Поділитися хронологією',
        reconstructTooltip: 'Згенерувати GPS-точки з ручних зупинок і поїздок. Наявні GPS-точки зберігаються.',
        reconstructLabel: 'Додати відсутні дані хронології',
        reconstructAria: 'Додати відсутні дані хронології',
        timelineReportsTab: 'Звіти хронології'
    },
    dashboard: {
        page: {
            title: 'Дашборд',
            subtitle: 'Огляд ваших даних про місцезнаходження та аналітики',
            selectedPeriodSummary: 'Підсумок вибраного періоду',
            sevenDaysOverview: 'Огляд за 7 днів',
            thirtyDaysOverview: 'Огляд за 30 днів',
            topPlacesSelected: 'Топ місць за вибраний період',
            topPlacesLast7: 'Топ місць за останні 7 днів',
            topPlacesLast30: 'Топ місць за останні 30 днів',
            routeStatsSelected: 'Статистика маршрутів за вибраний період',
            routeStatsLast7: 'Статистика маршрутів за останні 7 днів',
            routeStatsLast30: 'Статистика маршрутів за останні 30 днів',
            noDataTitle: 'Дані відсутні',
            noDataMessage: 'Для вибраних періодів не знайдено даних про місцезнаходження. Перевірте джерела GPS або виберіть інший діапазон дат.',
            toasts: {
                selectedRangeFailed: 'Не вдалося отримати статистику за вибраний період',
                weeklyFailed: 'Не вдалося отримати статистику за тиждень',
                monthlyFailed: 'Не вдалося отримати статистику за місяць'
            }
        },
        metrics: {
            totalDistance: 'Загальна відстань',
            timeMoving: 'Час у русі',
            dailyAverage: 'Середнє за день',
            averageSpeed: 'Середня швидкість',
            mostActiveDay: 'Найактивніший день',
            uniqueLocations: 'Унікальні місця',
            notAvailable: 'Н/Д'
        },
        distanceActivityChart: 'Активність за відстанню',
        distanceAxisTitle: 'Відстань ({unit})',
        tooltip: {
            distance: 'Відстань: {value}',
            travelTime: 'Час у дорозі: {value}',
            locations: 'Місця: {value}'
        },
        routeAnalysis: {
            noDataTitle: 'Немає даних про маршрути',
            noDataMessage: 'За цей період не проаналізовано жодного маршруту.',
            uniqueRoutes: 'Унікальні маршрути',
            mostCommonRoute: 'Найпоширеніший маршрут',
            tripsCount: '{count} поїздок',
            avgTripDuration: 'Середня тривалість поїздки',
            longestTripDuration: 'Найдовша поїздка (тривалість)',
            longestTripDistance: 'Найдовша поїздка (відстань)',
            notAvailable: 'Н/Д'
        },
        topPlaces: {
            noDataTitle: 'Немає даних про місця',
            noDataMessage: 'За цей період не було відвідано жодного місця.',
            visitsCount: '{count} відвідувань',
            totalDuration: 'Разом {duration}',
            defaultLocationName: 'Вибране місце'
        }
    },
    home: {
        loadContentFailed: 'Не вдалося завантажити вміст головної сторінки',
        nav: {
            resourcesAriaLabel: 'Ресурси проєкту',
            documentation: 'Документація',
            github: 'GitHub',
            whatsNewAriaLabel: 'Що нового',
            updateAvailable: 'Нове: доступна {version}',
            updateAvailableShort: 'Нове: {version}',
            whatsNewTitle: 'Що нового у {version}',
            whatsNewEmpty: 'Для цієї версії ще немає нотаток про випуск.',
            releaseNotesLink: 'Повні нотатки про випуск на GitHub'
        },
        hero: {
            eyebrow: 'Self-hosted альтернатива Google Timeline',
            titleWelcomeBack: 'З поверненням',
            titleDefault: 'Керуйте власним таймлайном місцеположень',
            subtitle: 'GeoPulse перетворює необроблені GPS-точки на приватний таймлайн місцеположень із зупинками, поїздками, картами й аналітикою — усе під вашим контролем.',
            accessDisabled: 'Вхід і реєстрацію наразі вимкнено адміністратором.',
            registrationDisabled: 'Реєстрацію вимкнено. Наявні користувачі все ще можуть увійти.',
            tryDemo: 'Спробувати демо',
            startJourney: 'Розпочати подорож',
            goToTimeline: 'Перейти до хронології',
            loadingWorkspace: 'Завантаження робочого простору...',
            socialProofText: 'Вихідний код доступний на GitHub · Безкоштовно для особистого використання',
            stars: 'зірок',
            forks: 'форків'
        },
        features: {
            liveTracking: 'Живе відстеження',
            smartImport: 'Розумний імпорт',
            autoTimeline: 'Автотаймлайн',
            deepInsights: 'Глибока аналітика',
            immichIntegration: 'Інтеграція з Immich',
            friends: 'Друзі',
            geofences: 'Геозони',
            ai: 'ШІ'
        },
        panel: {
            exploreAriaLabel: 'Огляд GeoPulse та поради',
            tabsAriaLabel: 'Мобільні вкладки розділів',
            featuresTab: 'Функції',
            tipTab: 'Порада',
            tipOfDayTitle: 'Порада дня',
            tipOfDayAriaLabel: 'Порада дня'
        },
        footer: {
            copyright: '© GeoPulse. Усі права захищено.'
        }
    },
    mobileAuth: {
        title: 'Мобільний',
        preparing: 'Підготовка мобільної автентифікації...',
        payloadMissing: 'Дані мобільної автентифікації не було отримано.',
        opening: 'Відкриття застосунку...',
        timedOut: 'Час очікування відкриття застосунку вичерпано. Будь ласка, поверніться до застосунку та спробуйте ще раз.',
        failed: 'Не вдалося завершити передачу мобільної автентифікації.'
    },
    notFound: {
        subtitle: 'Місцезнаходження не знайдено',
        title: 'От халепа! Ви заблукали поза картою',
        description: "Сторінка, яку ви шукаєте, не існує або була переміщена в інше місце. Давайте повернемо вас на правильний шлях.",
        optionsTitle: 'Куди б ви хотіли перейти?',
        options: {
            home: {
                title: 'Головна',
                description: 'Почніть свою подорож із початку'
            },
            timeline: {
                title: 'Хронологія',
                description: 'Перегляньте свою хронологію місцезнаходжень'
            },
            signIn: {
                title: 'Увійти',
                description: 'Отримайте доступ до свого облікового запису GeoPulse'
            },
            dashboard: {
                title: 'Дашборд',
                description: 'Перевірте свою аналітику місцезнаходжень'
            },
            signUp: {
                title: 'Зареєструватися',
                description: 'Створіть новий обліковий запис'
            }
        },
        goBack: 'Назад',
        helpSummary: 'Потрібна допомога у пошуку того, що ви шукаєте?',
        helpIntro: 'Ось кілька поширених сторінок, які ви можете шукати:',
        helpLinks: {
            timeline: 'Хронологія',
            timelineDesc: 'Перегляньте історію свого місцезнаходження',
            dashboard: 'Дашборд',
            dashboardDesc: 'Аналітика та статистика місцезнаходжень',
            journeyInsights: 'Аналітика подорожей',
            journeyInsightsDesc: 'Відкрийте закономірності подорожей',
            friends: 'Друзі',
            friendsDesc: "Керуйте своїми зв'язками з друзями",
            signIn: 'Увійти',
            signInDesc: 'Отримайте доступ до свого облікового запису'
        },
        helpNote: 'Якщо ви вважаєте, що це непрацююче посилання, зверніться до підтримки, і ми це виправимо.'
    },
    darkModeSwitcher: {
        themeTooltip: 'Тема: {label}',
        themeAriaLabel: 'Режим теми: {label}',
        light: 'Світла',
        dark: 'Темна',
        system: 'Системна',
        currentSuffix: '{label} (Поточна)'
    },
    errorReferenceToast: {
        copyAriaLabel: 'Копіювати ідентифікатор помилки'
    },
    providerIcon: {
        defaultAlt: 'Значок провайдера'
    },
    gpsFiltering: {
        title: 'Фільтрація GPS-даних',
        filterLabel: 'Фільтрувати неточні точки даних',
        filterHint: 'Увімкніть, щоб відфільтрувати GPS-точки з точністю або швидкістю поза заданими межами.',
        maxAccuracyLabel: 'Максимально допустима точність (метри)',
        maxAccuracyPlaceholder: 'напр., 100',
        maxAccuracyHint: 'Точки з точністю вище цього значення будуть відхилені.',
        maxSpeedLabel: 'Максимально допустима швидкість (км/год)',
        maxSpeedPlaceholder: 'напр., 250',
        maxSpeedHint: 'Точки зі швидкістю вище цього значення будуть відхилені.',
        duplicateDetectionTitle: 'Виявлення дублікатів',
        duplicateDetectionLabel: 'Увімкнути виявлення дублікатів',
        duplicateDetectionHint: "Пропускати GPS-точки з однаковим місцезнаходженням у часовому вікні. Корисно для пристроїв, що надсилають повторні місцезнаходження в стані спокою.",
        thresholdLabel: 'Поріг часу (хвилини)',
        thresholdPlaceholder: 'напр., 2',
        thresholdHint: 'Точки з однаковими координатами (в межах ~11м) у цьому часовому вікні будуть пропущені. Залиште порожнім, щоб використовувати глобальне значення за замовчуванням.'
    },
    pullToRefresh: {
        pullText: 'Потягніть для оновлення',
        readyText: 'Відпустіть для оновлення',
        refreshingText: 'Оновлення...'
    },
    tipOfDayCard: {
        nextTip: 'Наступна порада',
        noTipTitle: 'Порад поки немає',
        noTipDescription: "Поради з'являться тут, коли буде доступний вміст головної сторінки."
    },
    dateRangePicker: {
        labelDefault: 'Виберіть дати',
        placeholderDefault: 'Виберіть діапазон дат',
        presetPlaceholderDefault: 'Виберіть пресет',
        presets: {
            today: 'Сьогодні',
            yesterday: 'Вчора',
            last7Days: 'Останні 7 днів',
            last30Days: 'Останні 30 днів'
        },
        maxRangeError: 'Максимальний діапазон становить {days} днів',
        periodFallbackName: 'Період',
        labelFallbackName: 'Мітка',
        timelineLabelsGroup: 'Мітки хронології',
        presetsGroup: 'Пресети'
    },
    transportTypeCard: {
        enableAriaLabel: 'Увімкнути виявлення {title}',
        alwaysActive: 'Завжди активно',
        expandAriaLabel: 'Розгорнути {title}',
        collapseAriaLabel: 'Згорнути {title}',
        disabledMessage: 'Виявлення {title} наразі вимкнено. Увімкніть, щоб налаштувати пороги.'
    },
    appLayout: {
        viewTimelineJob: 'Переглянути завдання хронології',
        whatsNew: 'Що нового',
        readFullReleaseNotes: 'Читати повні нотатки випуску',
        gotIt: 'Зрозуміло',
        notificationDefaultSummary: 'Сповіщення',
        viewAllNotifications: 'Переглянути всі сповіщення',
        openNotification: 'Відкрити сповіщення'
    },
    exploreFeatures: {
        ariaLabel: 'Досліджуйте GeoPulse',
        kicker: 'Досліджуйте GeoPulse',
        tabsAriaLabel: 'Функції GeoPulse',
        readDocs: 'Читати документацію',
        openInApp: 'Відкрити в застосунку',
        liveTracking: {
            tabLabel: 'Відстеження наживо',
            title: "Інтеграції джерел у реальному часі",
            description: "Об'єднайте OwnTracks, Overland, Traccar, GPSLogger, Dawarich, Home Assistant та Colota в одну хронологію.",
            highlights: [
                'Підтримує прийом даних через HTTP та MQTT для підтримуваних трекерів',
                'Фільтрація GPS для кожного джерела покращує якість хронології',
                "Об'єднуйте кілька пристроїв/джерел в єдину історію"
            ]
        },
        imports: {
            tabLabel: 'Імпорт',
            title: 'Імпорт та міграція історії',
            description: 'Імпортуйте застарілі та резервні дані з фоновою обробкою та регенерацією хронології.',
            highlights: [
                'Підтримує GeoPulse, OwnTracks, Google Timeline, GPX, GeoJSON та CSV',
                'Фільтрація за діапазоном дат для часткового імпорту',
                'Робочий процес заміни або об\'єднання для безпечного повторного імпорту'
            ]
        },
        timeline: {
            tabLabel: 'Хронологія',
            title: 'Зупинки, поїздки та прогалини',
            description: 'GeoPulse перетворює необроблені точки на події хронології, а потім дозволяє уточнити маршрути та виправити пропущені зупинки.',
            highlights: [
                'Автоматичне виявлення зупинок/поїздок/прогалин',
                'Налаштовувані режими подорожі від ходьби та велосипеда до мотоцикла, потяга, авіарейсу та човна',
                'Розділіть пропущену зупинку на Поїздка → Зупинка → Поїздка; виправлення зберігається після регенерації',
                'Додаткове зіставлення карти Valhalla слідує дорогам, тоді як необроблені GPS-дані залишаються авторитетними'
            ]
        },
        insight: {
            tabLabel: 'Аналітика',
            title: 'Дашборд, аналітика подорожей та аналітика місцезнаходжень',
            description: 'Відстежуйте закономірності руху, місця, маршрути та досягнення в кількох часових вікнах.',
            highlights: [
                'Огляди за вибраний період + 7 днів + 30 днів',
                'Найпопулярніші місця, аналіз маршрутів та розподіл активності',
                'Аналітика подорожей для країн/міст/віх подорожей'
            ]
        },
        friends: {
            tabLabel: 'Друзі',
            title: "Друзі та керування спільним доступом",
            description: "Спілкуйтеся з друзями та контролюйте, чим саме ви ділитеся.",
            highlights: [
                'Запрошуйте, приймайте, відхиляйте та скасовуйте запити дружби',
                'Окремі дозволи для місцезнаходження наживо та історії хронології',
                'Перегляди карти наживо та спільної хронології, включно з вбудованими спільними місцезнаходженнями'
            ]
        },
        geofences: {
            tabLabel: 'Геозони',
            title: 'Правила та події геозон',
            description: 'Створюйте правила входу/виходу зі сповіщеннями в застосунку та опціональною зовнішньою доставкою.',
            highlights: [
                "Відстежуйте вибраних суб'єктів з налаштовуваними умовами правил",
                'Сповіщення на основі шаблонів з підтримкою макросів',
                'Вкладка подій із фільтрацією непереглянутих та керуванням переглядом'
            ]
        },
        immich: {
            tabLabel: 'Immich',
            title: 'Накладання фото Immich',
            description: 'Відображайте фото Immich безпосередньо на карті хронології для вибраного діапазону дат.',
            highlights: [
                'Налаштуйте URL-адресу + ключ API в профілі',
                'Перемикайте шар фото на карті хронології',
                'Відкривайте фото в застосунку та завантажуйте оригінали'
            ]
        },
        weather: {
            tabLabel: 'Погода',
            title: 'Погода вздовж вашої хронології',
            description: 'Додайте місцеві умови до місць і подорожей у вашій історії місцезнаходжень.',
            highlights: [
                'Переглядайте температуру, опади, вітер та умови на зупинках, поїздках та картах хронології',
                'Порівнюйте погодні закономірності в аналітиці подорожей та отримуйте пов\'язані з погодою значки',
                "Поточний збір даних використовує Open-Meteo за замовчуванням; адміністратори можуть увімкнути історичне заповнення"
            ]
        },
        ai: {
            tabLabel: 'ШІ',
            title: 'ШІ-асистент та MCP',
            description: "Задавайте питання природною мовою або підключіть ШІ-клієнт до своєї особистої історії пересувань.",
            highlights: [
                'Використовуйте власний сумісний з OpenAI ключ API/модель із зашифрованими налаштуваннями для кожного користувача',
                'Запитуйте про зупинки, поїздки, місця та закономірності подорожей',
                'MCP увімкнено за замовчуванням для аутентифікованих за токеном API інструментів ШІ лише для читання'
            ]
        }
    },
    onboardingTour: {
        welcomeTitle: '🎉 Ласкаво просимо до GeoPulse!',
        welcomeDescription: 'Готові почати відстежувати свою подорож місцезнаходження? Налаштуймо ваше перше джерело місцезнаходження.',
        startTour: '🚀 Почати огляд',
        exploreOnMyOwn: 'Я розберуся сам(а)',
        closeTour: 'Закрити огляд',
        stepCounter: '{current} з {total}',
        previous: 'Назад',
        next: 'Далі',
        getStarted: 'Розпочати!',
        steps: {
            welcome: {
                title: 'Ласкаво просимо до GeoPulse!',
                description: 'Спочатку налаштуймо ваші джерела місцезнаходження, щоб ми могли почати відстежувати вашу подорож.'
            },
            addSource: {
                title: 'Додайте своє перше джерело місцезнаходження',
                description: 'Натисніть кнопку "Додати нове джерело", щоб додати OwnTracks або Overland як ваше джерело місцезнаходження.'
            },
            followInstructions: {
                title: 'Дотримуйтесь інструкцій з налаштування',
                description: "Коли ви додасте джерело, ви побачите детальні інструкції з налаштування з вашою унікальною кінцевою точкою та токеном."
            },
            allSet: {
                title: 'Все готово!',
                description: 'Коли ваше джерело місцезнаходження налаштовано, відвідайте свою Хронологію, щоб побачити історію місцезнаходжень та досліджувати GeoPulse!'
            }
        }
    },
    appNavbar: {
        demoBadge: 'ДЕМО',
        locationSharingEnabledTooltip: 'Спільний доступ до місцезнаходження увімкнено',
        locationSharingDisabledTooltip: 'Спільний доступ до місцезнаходження вимкнено',
        shareLocation: 'Поділитися місцезнаходженням',
        inviteFriend: 'Запросити друга',
        inviteFriendDisabledAriaLabel: 'Запрошення друга вимкнено в демо-режимі',
        inviteFriendDisabledTooltip: 'Запрошення вимкнено в демо-режимі'
    },
    globalSearch: {
        bar: {
            placeholder: 'Пошук місць, сторінок, налаштувань...',
            groupPages: 'Сторінки',
            groupSettings: 'Налаштування',
            groupLocations: 'Місця',
            visitsCount: '{count} відвідування | {count} відвідування | {count} відвідувань',
            sinceDate: 'З {date}'
        },
        settingsTrigger: {
            defaultPlaceholder: 'Пошук налаштувань...',
            defaultButtonLabel: 'Знайти налаштування'
        },
        registry: {
            tabAuthentication: 'Автентифікація',
            tabGeocoding: 'Геокодування',
            tabWeather: 'Погода',
            tabPlaceDiscovery: 'Пошук місць',
            tabMapMatching: 'Прив\'язка до карти',
            tabAiAssistant: 'AI-асистент',
            tabImport: 'Імпорт',
            tabExport: 'Експорт',
            tabBackup: 'Резервне копіювання та відновлення',
            tabNotifications: 'Сповіщення',
            tabSystem: 'Система',
            timelineTabStayPoints: 'Визначення точок зупинки',
            timelineTabTrips: 'Класифікація поїздок',
            timelineTabGpsGaps: 'Визначення розривів GPS',
            timelineTabMerging: 'Об\'єднання точок зупинки',
            subtitleTimelinePreferences: 'Налаштування хронології / {tab}',
            subtitleProfile: 'Профіль / {subtitle}',
            subtitleSystemSettings: 'Системні налаштування / {tab}',
            subtitlePage: 'Сторінка',
            subtitleAdministration: 'Адміністрування'
        }
    },
    charts: {
        barChart: {
            defaultTitle: 'Дані',
            seriesFallback: 'Серія {number}',
            valueKm: '{value} км'
        }
    }
}
