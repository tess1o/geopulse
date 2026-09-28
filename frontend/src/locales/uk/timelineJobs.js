/**
 * Timeline jobs (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    progressMessages: {
        initializing: 'Ініціалізація генерації хронології',
        acquiringLock: 'Отримання блокування хронології',
        cleaningUp: 'Очищення старих даних хронології',
        preparingGpsProcessing: 'Підготовка обробки GPS-даних',
        noGpsData: 'Немає GPS-даних для обробки',
        readyToProcess: 'Готово до обробки {totalGpsPoints} GPS-точок',
        processingStateMachine: 'Обробка GPS-точок через скінченний автомат',
        postProcessingTrips: 'Постобробка поїздок і перевірка виявлень',
        mergingTimeline: "Об'єднання хронології",
        persistingTimeline: 'Збереження подій хронології в базу даних',
        detectingDataGaps: 'Виявлення прогалин у даних',
        finalizing: 'Завершення генерації хронології',
        autoMatchingVisits: 'Автоматичне зіставлення запланованих відвідувань',
        recalculatingBadges: 'Перерахунок досягнень',
        completed: 'Генерацію хронології завершено',
        preparingBoatSetup: 'Підготовка налаштування човна',
        boatEvidenceReady: "Дані про водні докази готові",
        regenerationQueued: 'Примусову регенерацію хронології поставлено в чергу',
        startingLocationResolution: 'Початок визначення місцезнаходжень',
        allResolvedFromFavorites: 'Усі місцезнаходження визначено з обраного',
        resolvedFromFavorites: 'Визначено {favoritesResolved} місцезнаходжень з обраного',
        geocodingComplete: 'Геокодування завершено: {favoritesResolved} з обраного, {cachedResolved} з кешу, {externalCompleted} зовнішніх запитів'
    },
    listPage: {
        title: 'Завдання генерації хронології',
        description: 'Перегляньте генерацію хронології, яка виконується зараз або завершилася нещодавно.',
        loading: 'Пошук активних завдань...',
        noJob: {
            title: 'Наразі немає активних завдань хронології',
            message: 'Якщо ви відкрили це зі сповіщення, заплановане оновлення вже могло завершитися. Нещодавні завершені або невдалі завдання показані нижче, якщо вони є.',
            hint: 'Завдання хронології можуть запускатися змінами налаштувань хронології, оновленнями обраних місць, ручною регенерацією або плановим обслуговуванням GeoPulse.',
            viewTimelineButton: 'Переглянути хронологію'
        },
        error: {
            title: 'Не вдалося перевірити активні завдання'
        },
        history: {
            title: 'Історія завдань',
            description: 'Останні завдання генерації хронології',
            loading: 'Завантаження історії завдань...',
            empty: 'Останніх завдань хронології не знайдено.',
            duration: 'Тривалість: {duration}',
            gpsPoints: '{count} GPS-точок',
            viewDetails: 'Переглянути деталі'
        },
        notAvailable: 'Н/Д',
        durationSeconds: '{seconds} с',
        durationMinutesSeconds: '{minutes} хв {seconds} с'
    },
    detailsPage: {
        title: 'Прогрес генерації хронології',
        description: 'Відстеження прогресу генерації хронології в реальному часі.',
        backButton: 'Назад до хронології',
        loading: 'Завантаження деталей завдання...',
        error: {
            title: 'Не вдалося завантажити деталі завдання'
        },
        jobIdLabel: 'ID завдання: {jobId}',
        status: {
            loading: 'Завантаження...',
            queued: 'У черзі на обробку',
            running: 'Обробка хронології',
            completed: 'Успішно завершено',
            failed: 'Помилка'
        },
        details: {
            started: 'Розпочато:',
            completed: 'Завершено:',
            duration: 'Тривалість:',
            currentStep: 'Поточний крок:',
            stepOf: '{current} з {total}'
        },
        stepsTitle: 'Кроки обробки',
        steps: {
            acquiringLock: {
                title: 'Отримання блокування',
                description: 'Забезпечення ексклюзивного доступу до даних хронології'
            },
            cleaningUp: {
                title: 'Очищення',
                description: 'Видалення старих подій хронології'
            },
            preparingGpsProcessing: {
                title: 'Підготовка обробки GPS',
                description: 'Підрахунок GPS-точок і підготовка потокового ітератора'
            },
            processingGeocoding: {
                title: 'Обробка та геокодування',
                description: 'Потокова обробка GPS-точок через скінченний автомат із подальшим визначенням назв місць'
            },
            postProcessingTrips: {
                title: 'Постобробка поїздок',
                description: 'Перевірка та уточнення визначення поїздок'
            },
            mergingSimplifying: {
                title: "Об'єднання та спрощення",
                description: 'Застосування оптимізацій хронології'
            },
            persistingTimeline: {
                title: 'Збереження хронології',
                description: 'Збереження подій хронології в базі даних'
            },
            dataGapDetection: {
                title: 'Виявлення розривів даних',
                description: 'Визначення розривів у покритті GPS'
            },
            finalizing: {
                title: 'Завершення',
                description: 'Обчислення етапів і завершення генерації'
            }
        },
        detail: {
            gpsPoints: 'GPS-точки: {loaded} / {total}',
            processingHeader: 'Обробка GPS-точок: {processed} / {total}',
            remaining: 'Залишилося: {count} точок',
            geocodingHeader: 'Зворотне геокодування: {resolved} / {total} місць',
            favoritesInstant: 'Обрані (миттєво): {count}',
            cachedInDatabase: 'У кеші бази даних: {count}',
            externalApiCalls: 'Зовнішні виклики API: {count}',
            pending: 'Очікує: {count}'
        },
        completion: {
            title: 'Генерацію хронології завершено!',
            message: 'Вашу хронологію успішно перегенеровано з урахуванням усіх останніх GPS-даних.'
        },
        durationZero: '0 с',
        durationSeconds: '{seconds} с',
        durationMinutesSeconds: '{minutes} хв {seconds} с',
        durationHoursMinutesSeconds: '{hours} год {minutes} хв {seconds} с'
    }
}
