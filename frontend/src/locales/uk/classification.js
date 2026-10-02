/**
 * Trip Classification dialog (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    dialog: {
        header: 'Деталі класифікації поїздки',
        loading: 'Завантаження деталей класифікації...',
        close: 'Закрити'
    },
    overview: {
        title: 'Огляд поїздки',
        startTime: 'Час початку',
        duration: 'Тривалість',
        distance: 'Відстань',
        effectiveClassification: 'Фактична класифікація',
        automaticClassification: 'Автоматична класифікація',
        classificationSource: 'Джерело класифікації',
        manualOverrideActive: 'Для цієї поїздки активне ручне перевизначення.'
    },
    editSection: {
        title: 'Редагувати тип пересування',
        unrecognizedWarning: 'Алгоритм не розпізнав цей тип пересування. Встановіть його вручну нижче.',
        overrideInfo: 'Якщо це виглядає неправильно, перевизначте вручну.',
        readOnlyError: 'У демо-режимі редагування типу пересування недоступне.',
        selectPlaceholder: 'Виберіть тип пересування',
        saveButton: 'Зберегти перевизначення',
        resetButton: 'Скинути до автоматичного'
    },
    stats: {
        title: 'GPS-статистика',
        avgSpeed: 'Середня швидкість GPS',
        maxSpeed: 'Максимальна швидкість GPS',
        calculatedAvgSpeed: 'Розрахована середня швидкість',
        calculatedAvgSpeedHint: 'На основі відстані/тривалості',
        speedVariance: 'Дисперсія швидкості',
        lowAccuracyPoints: 'Точки низької точності',
        gpsReliability: 'Надійність GPS',
        reliable: 'Надійно',
        unreliable: 'Ненадійно',
        reliableHint: 'Швидкості GPS у межах очікуваного діапазону та використані для класифікації',
        unreliableHint: 'Швидкості GPS ненадійні — натомість використано розраховану швидкість на основі відстані/тривалості'
    },
    priority: {
        title: 'Порядок пріоритету класифікації',
        description: 'Поїздки класифікуються за порядком пріоритету зліва направо. Класифікація зупиняється, щойно знайдено збіг.',
        learnMore: 'Дізнатися більше'
    },
    steps: {
        title: 'Аналіз класифікації',
        description: 'Детальні перевірки порогових значень для автоматичної класифікації.'
    },
    finalDecisionLabel: 'Остаточне рішення:',
    na: 'Н/Д',
    toasts: {
        loadFailedFallback: 'Не вдалося завантажити деталі класифікації',
        loadFailedDetail: 'Не вдалося завантажити деталі класифікації поїздки',
        movementUpdatedSummary: 'Тип пересування оновлено',
        movementUpdatedDetail: 'Поїздку позначено як {type}',
        updateFailedSummary: 'Оновлення не вдалося',
        updateFailedFallback: 'Не вдалося оновити тип пересування',
        movementResetSummary: 'Тип пересування скинуто',
        movementResetDetail: 'Класифікацію поїздки скинуто до {type}',
        resetFailedSummary: 'Скидання не вдалося',
        resetFailedFallback: 'Не вдалося скинути тип пересування'
    },
    stepCard: {
        notEnabled: 'Не увімкнено',
        passed: 'Пройдено',
        failed: 'Не пройдено',
        thresholdChecksHeader: 'Перевірки порогових значень:',
        actual: '(фактично: {value})'
    }
}
