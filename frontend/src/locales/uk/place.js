/**
 * Place details page: the map card, notes section, stats card, and visit history table. (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    map: {
        title: 'Місцезнаходження',
        defaultLocationName: 'Місце',
        defaultAreaName: 'Область місця'
    },
    stats: {
        title: 'Огляд відвідувань',
        totalVisits: 'Всього відвідувань',
        totalTime: 'Загальний час',
        averageVisit: 'Середній візит',
        placesVisited: 'Відвіданих місць',
        activityHeader: 'Активність',
        thisWeek: 'Цього тижня',
        thisMonth: 'Цього місяця',
        thisYear: 'Цього року',
        visitSpanHeader: 'Період відвідувань',
        firstVisit: 'Перше відвідування',
        lastVisit: 'Останнє відвідування',
        durationRangeHeader: 'Діапазон тривалості',
        shortestVisit: 'Найкоротший візит',
        longestVisit: 'Найдовший візит',
        visitPatternsHeader: 'Шаблони відвідувань',
        typicalDay: 'Типовий день',
        arrivalPeriod: 'Період прибуття',
        visitCadence: 'Частота відвідувань',
        notAvailable: 'Н/Д',
        zeroSeconds: '0 секунд',
        lessThanDaily: 'Рідше, ніж щодня',
        everyDay: 'Щодня',
        everyNDays: 'Кожні {count} днів'
    },
    notes: {
        defaultTitle: 'Нотатки',
        defaultEmptyMessage: 'Нотаток для цього місця не знайдено.',
        viewAll: 'Переглянути всі',
        reloadAriaLabel: 'Оновити нотатки',
        reloadTooltip: 'Оновити нотатки',
        openInMemosAriaLabel: 'Відкрити в Memos',
        openInMemosTooltip: 'Відкрити в Memos',
        truncatedNotice: 'Ця нотатка Memos велика, тому GeoPulse показує скорочений попередній перегляд.',
        loadingInline: 'Завантаження нотаток...',
        loadFailed: 'Не вдалося завантажити нотатки',
        sourceMemos: 'Memos',
        sourceGeopulse: 'GeoPulse',
        locationSource: {
            explicit: 'Із геотегом',
            derivedStay: 'Місцезнаходження зупинки',
            derivedTripGps: 'GPS поїздки',
            derivedTripInterpolated: 'Оцінка поїздки'
        }
    },
    detailsPage: {
        loading: 'Завантаження деталей місця...',
        errorTitle: 'Не вдалося завантажити деталі місця',
        loadFailed: 'Не вдалося завантажити деталі місця',
        pageTitleFallback: 'Деталі місця',
        subtitleFallback: 'Детальна інформація та історія відвідувань',
        typeFavorite: 'Обране',
        typeGeocoded: 'Геокодоване місцезнаходження',
        coordinatesCenter: 'Центр: {coordinates}',
        edit: 'Редагувати',
        editDetails: 'Редагувати деталі',
        createFavorite: 'Додати в обране',
        relatedFavorite: {
            titleArea: "Відвідування згруповано з обраною областю",
            titlePoint: 'Відвідування згруповано з обраним',
            messageArea: 'Це місцезнаходження перебуває у вашій обраній області. Ваші відвідування тут відстежуються під:',
            messagePoint: 'Ваші відвідування цього місця відстежуються під вашим найближчим обраним:',
            distanceAway: '(за {distance})',
            visitsTracked: 'Відстежено відвідувань: {count}',
            viewDetails: 'Переглянути деталі {name}'
        },
        latestPhotosTitle: 'Останні фото поблизу {name}',
        noPhotosMessage: 'Поблизу фото Immich для цього місця не знайдено.',
        notesTitle: 'Нотатки поблизу {name}',
        noNotesMessage: 'Поблизу нотаток для цього місця не знайдено.',
        editFavoriteDialogHeader: 'Редагувати обране місцезнаходження',
        createFavoriteDialog: {
            header: 'Додати обране місцезнаходження',
            message: "Створіть обране місцезнаходження в цій геокодованій точці. Ви можете надати йому власну назву.",
            nameLabel: 'Назва обраного',
            namePlaceholder: 'напр., Дім, Робота, Спортзал',
            coordinatesInfo: 'Координати: {lat}, {lon}'
        },
        invalidCoordinatesDetail: 'Недійсні координати для обраного.',
        favoriteCreatedSuccess: 'Обране місцезнаходження успішно створено.',
        favoriteCreateFailed: 'Не вдалося створити обране місцезнаходження.',
        loadVisitsFailed: 'Не вдалося завантажити історію відвідувань',
        exportNoDataSummary: 'Немає даних',
        exportNoDataDetail: 'Дані місця недоступні',
        exportingSummary: 'Експортування',
        exportingDetail: 'Підготовка експорту CSV...',
        exportSuccessSummary: 'Експорт успішний',
        exportSuccessDetail: 'Експортовано відвідувань: {count} у CSV',
        exportFailedSummary: 'Помилка експорту',
        exportFailedDetail: 'Не вдалося експортувати відвідування у CSV',
        geocodingUpdatedSuccessSummary: 'Успіх',
        geocodingUpdatedSuccessDetail: 'Геокодоване місцезнаходження успішно оновлено',
        geocodingUpdateFailedDetail: 'Не вдалося оновити геокодоване місцезнаходження'
    },
    visitsTable: {
        title: 'Історія відвідувань',
        allVisits: 'Усі відвідування',
        countLabel: '{count} відвідувань',
        exportCsv: 'Експорт CSV',
        columns: {
            visitDate: 'Дата відвідування',
            city: 'Місто',
            placeName: 'Назва місця',
            trip: 'Поїздка',
            duration: 'Тривалість',
            endTime: 'Час завершення',
            dayOfWeek: 'День тижня'
        },
        notAvailable: 'Н/Д',
        unknownPlace: 'Невідомо',
        tripTagTitle: "Відвідування пов'язане з плануванням поїздки: {label}",
        tripTagAriaLabel: 'Відкрити планувальник поїздки {label}',
        tripFallbackLabel: 'Поїздка №{id}',
        openInTimelineAriaLabel: 'Відкрити день відвідування в хронології',
        openInTimelineTooltip: 'Відкрити день відвідування в хронології',
        empty: {
            title: 'Відвідувань не знайдено',
            message: 'Для цього місця не зафіксовано жодного відвідування.'
        }
    }
}
