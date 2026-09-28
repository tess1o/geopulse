/**
 * Trip-related dialogs: trip details, trip/stay split, map-matching details, movement-type quick
 * edit, data-gap-to-stay conversion, and GPS point edit (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    tripDetails: {
        dialogTitleDefault: 'Деталі поїздки',
        dialogTitleWithType: 'Деталі поїздки - {type}',
        sections: {
            route: 'Маршрут поїздки',
            timing: 'Час',
            tripInfo: 'Деталі поїздки',
            routeInfo: 'Маршрут',
            coordinates: 'Координати'
        },
        labels: {
            start: 'Початок:',
            end: 'Кінець:',
            duration: 'Тривалість:',
            distance: 'Відстань:',
            averageSpeed: 'Середня швидкість:',
            routePoints: 'Точки маршруту:',
            routePointsValue: '{count} точок',
            origin: 'Звідки:',
            destination: 'Куди:'
        },
        unknownOrigin: 'Невідоме місце відправлення',
        unknownDestination: 'Невідоме місце призначення',
        notAvailable: 'Н/Д',
        close: 'Закрити',
        toasts: {
            copiedSummary: 'Скопійовано!',
            copiedDetail: 'Координати скопійовано в буфер обміну',
            copyFailedSummary: 'Помилка копіювання',
            copyFailedDetail: 'Не вдалося скопіювати координати',
            gpsUnavailableSummary: 'GPS-дані недоступні',
            gpsUnavailableDetail: 'Не вдалося завантажити GPS-точки для цієї поїздки'
        }
    },
    staySplit: {
        header: 'Розділити поїздку зупинкою',
        loadingPath: 'Завантаження маршруту поїздки...',
        clickToSetStay: 'Натисніть на маршрут, щоб вказати місце зупинки.',
        stayStartLabel: 'Початок зупинки',
        stayEndLabel: 'Кінець зупинки',
        placeNameLabel: "Назва місця",
        placeNameResolving: 'Визначення назви місця...',
        placeNamePlaceholder: 'Визначається автоматично, якщо залишити порожнім',
        updatingPreview: 'Оновлення результату розділення...',
        preview: {
            originalTrip: 'Початкова поїздка',
            splitResult: 'Результат розділення',
            tripFor: 'Поїздка тривалістю {duration}',
            to: 'до {name}',
            stayAt: 'Зупинка в {name}',
            forDuration: 'тривалістю {duration}',
            from: 'від {name}'
        },
        saveSplit: 'Зберегти розділення',
        unknownTimeRange: 'Невідомий часовий діапазон',
        selectedStayFallback: 'Вибрана зупинка',
        selectedPlaceFallback: 'вибране місце',
        validation: {
            tripMissing: 'Поїздку не знайдено.',
            readOnly: 'Редагування таймлайну вимкнено.',
            selectStayLocation: 'Виберіть місце зупинки на карті поїздки.',
            selectValidTimes: 'Виберіть коректні час початку та завершення.',
            endAfterStart: 'Час завершення зупинки має бути пізніше за час початку.',
            minDuration: 'Тривалість зупинки має бути не менше 60 секунд.',
            startAfterTripStart: 'Зупинка має починатися після початку поїздки.',
            endBeforeTripEnd: 'Зупинка має завершитися до завершення поїздки.',
            placeNameTooLong: 'Назва місця занадто довга.',
            selectValidStayLocation: 'Виберіть коректне місце зупинки.'
        },
        pathErrors: {
            noPoints: 'Немає точок маршруту для цієї поїздки.',
            loadFailed: 'Не вдалося завантажити маршрут поїздки'
        },
        previewErrorFallback: 'Не вдалося попередньо переглянути розділення поїздки',
        toasts: {
            splitSummary: 'Поїздку розділено',
            splitDetail: 'Зупинку додано до поїздки.',
            splitFailedSummary: 'Помилка розділення',
            splitFailedFallback: 'Не вдалося розділити поїздку'
        },
        distanceFromClickMeters: '{value} м від точки натискання',
        distanceFromClickKm: '{value} км від точки натискання'
    },
    mapMatchingDetails: {
        header: 'Деталі зіставлення з картою',
        statusLabels: {
            completed: 'ЗІСТАВЛЕНО',
            queued: 'У ЧЕРЗІ',
            processing: 'ЗІСТАВЛЕННЯ',
            failed: 'ПОМИЛКА',
            skipped: 'ПРОПУЩЕНО'
        },
        neverQueuedNote: 'Цю поїздку було пропущено ще до черги зіставлення з картою, тому збереженого результату для повтору немає.',
        detailCaption: 'Повідомлено маршрутизатором',
        lastAttemptedOn: 'Остання спроба: {when}',
        refinedOn: 'Уточнено: {when}',
        openSettings: 'Відкрити налаштування зіставлення з картою',
        adminHint: 'Адміністратор може повторно запустити зіставлення з картою через Адміністрування → Налаштування → Зіставлення з картою.',
        close: 'Закрити'
    },
    movementTypeQuickEdit: {
        header: 'Редагувати тип пересування',
        unknownAlgorithmWarning: 'Алгоритм не розпізнав цей тип пересування. Встановіть його вручну.',
        readOnlyWarning: 'Редагування типу пересування недоступне в демо-режимі.',
        selectPlaceholder: 'Виберіть тип пересування',
        save: 'Зберегти',
        reset: 'Скинути',
        close: 'Закрити',
        unknownTime: 'Невідомий час',
        toasts: {
            updatedSummary: 'Тип пересування оновлено',
            updatedDetail: 'Для поїздки встановлено тип {type}',
            updateFailedSummary: 'Помилка оновлення',
            updateFailedFallback: 'Не вдалося оновити тип пересування',
            resetSummary: 'Тип пересування скинуто',
            resetDetail: 'Для поїздки скинуто тип на {type}',
            resetFailedSummary: 'Помилка скидання',
            resetFailedFallback: 'Не вдалося скинути тип пересування'
        }
    },
    dataGapToStay: {
        header: 'Перетворити розрив даних на зупинку',
        gapTag: 'Розрив даних',
        unknownTimeRange: 'Невідомий часовий діапазон',
        locationSourceLabel: 'Джерело місця зупинки',
        resolvingLocation: 'Визначення останньої відомої точки...',
        defaultLocationLabel: 'Місце за замовчуванням:',
        unknownLocation: 'Невідоме місце',
        selectedLocationTypeLabel: 'Тип обраного місця',
        searchPlaceLabel: 'Пошук обраного або геокодованого місця',
        searchPlaceholder: 'Введіть щонайменше 2 символи...',
        latitudePlaceholder: 'Широта',
        longitudePlaceholder: 'Довгота',
        customNamePlaceholder: 'Необов’язкова власна назва місця',
        convert: 'Перетворити на зупинку',
        strategyOptions: {
            latestPoint: 'Остання відома точка (за замовчуванням)',
            selectedLocation: 'Обране місце'
        },
        selectedSourceOptions: {
            place: 'Обране або геокодоване місце',
            custom: 'Власні координати'
        },
        errors: {
            previewFailed: 'Не вдалося визначити останню відому точку',
            searchFailed: 'Не вдалося виконати пошук місць'
        },
        toasts: {
            convertedSummary: 'Перетворено',
            convertedDetail: 'Розрив даних перетворено на зупинку.',
            failedSummary: 'Помилка перетворення',
            failedFallback: 'Не вдалося перетворити розрив даних'
        }
    },
    gpsPointEdit: {
        header: 'Редагувати GPS-точку',
        mapInstructions: 'Натисніть на карту, щоб вибрати нове місце для цієї GPS-точки.',
        locationLabel: 'Місцезнаходження',
        latitudeLabel: 'Широта',
        longitudeLabel: 'Довгота',
        speedLabel: 'Швидкість (км/год)',
        speedPlaceholder: 'Швидкість у км/год',
        accuracyLabel: 'Точність (метри)',
        accuracyPlaceholder: 'Точність у метрах',
        save: 'Зберегти',
        validation: {
            latitudeRequired: "Широта є обов'язковою",
            latitudeRange: 'Широта має бути в діапазоні від -90 до 90',
            longitudeRequired: "Довгота є обов'язковою",
            longitudeRange: 'Довгота має бути в діапазоні від -180 до 180'
        },
        mapPopups: {
            currentLocation: 'Поточне місцезнаходження - натисніть на карту, щоб перемістити',
            originalLocation: 'Початкове місцезнаходження'
        }
    }
}
