// DRAFT: machine-drafted translations, pending native-speaker review
export default {
    addFavorite: {
        locationNamePlaceholder: 'Назва місця',
        save: 'Зберегти'
    },
    editFavorite: {
        nameLabel: 'Назва',
        namePlaceholder: 'Введіть назву місця',
        cityLabel: 'Місто',
        cityPlaceholder: 'Введіть місто (необов\'язково)',
        countryLabel: 'Країна',
        countryPlaceholder: 'Введіть країну (необов\'язково)',
        areaBoundariesTitle: 'Межі області',
        drawing: 'Малювання...',
        redrawArea: 'Перемалювати область',
        drawInstruction: 'Клацніть і перетягніть на карті, щоб намалювати нову прямокутну область',
        currentBoundsLabel: 'Поточні межі:',
        neLabel: 'ПнС:',
        swLabel: 'ПдЗ:',
        save: 'Зберегти'
    },
    reconcile: {
        selectProvider: 'Виберіть провайдера',
        providerPlaceholder: 'Виберіть провайдера геокодування',
        primaryTag: 'Основний',
        fallbackTag: 'Резервний',
        providerHint: 'Провайдер буде використано для отримання нових даних геокодування',
        detailsTitle: 'Деталі узгодження',
        filteredBadge: '(відфільтровано)',
        allBadge: '(усі)',
        activeFiltersLabel: 'Активні фільтри:',
        searchFilter: 'Пошук: "{search}"',
        actionLabel: 'Дія:',
        importantLabel: 'Важливо:',
        originalKeptOnFailure: 'Якщо узгодження не вдасться, початкові дані буде збережено',
        cannotBeUndone: 'Цю дію не можна скасувати',
        progressTitle: 'Прогрес узгодження',
        processedLabel: 'Оброблено:',
        successfulLabel: 'Успішно:',
        errorsLabel: 'Помилки:',
        completedSuccessfully: 'Узгодження успішно завершено!',
        completedWithErrors: 'Завершено з {count} помилкою. | Завершено з {count} помилками. | Завершено з {count} помилками.',
        itemsReconciledSuccessfully: '{count} елемент успішно узгоджено. | {count} елементи успішно узгоджено. | {count} елементів успішно узгоджено.',
        jobFailed: 'Завдання узгодження не вдалося',
        unexpectedError: 'Сталася неочікувана помилка',
        reconcileButton: 'Узгодити',
        favoritesToReconcileLabel: 'Обране для узгодження:',
        typeFilter: 'Тип: {type}',
        favoriteActionValue: 'Оновити місто та країну від провайдера',
        favoriteWarnings: {
            cityCountryOnly: 'Це оновить лише поля міста та країни',
            nameUnchanged: 'Назва та координати місця розташування залишаться незмінними',
            areaCenterPoint: 'Для областей для геокодування буде використано центральну точку'
        },
        favorite: {
            dialogTitleFiltered: 'Узгодити {count} відфільтрованих обраних',
            dialogTitleAll: 'Узгодити всі {count} обраних',
            dialogTitleSingle: 'Узгодити обране місце',
            dialogTitleMultiple: 'Узгодити {count} вибраних обраних'
        },
        geocoding: {
            providerSwitchHint: 'Використовуйте "Узгодити вибрані"/"Узгодити всі", щоб оновити старі записи вибраним провайдером після зміни провайдера.',
            resultsToReconcileLabel: 'Результати для узгодження:',
            providerFilter: 'Провайдер: {provider}',
            actionValue: 'Отримати нові дані від вибраного провайдера',
            warnings: {
                displayNameCityCountry: 'Це оновить поля відображуваної назви, міста та країни',
                syncedAcrossStays: 'Зміни буде синхронізовано в усіх зупинках таймлайну',
                cachedEntriesNotAuto: 'Зміна провайдера в налаштуваннях не перезаписує наявні кешовані записи автоматично'
            },
            dialogTitleFiltered: 'Узгодити {count} відфільтрованих результатів',
            dialogTitleAll: 'Узгодити всі {count} результатів',
            dialogTitleSingle: 'Узгодити результат геокодування',
            dialogTitleMultiple: 'Узгодити {count} вибраних результатів'
        }
    },
    geocodingEdit: {
        header: 'Редагувати результат геокодування',
        mapLabel: 'Карта місця розташування',
        mapHint: 'Карта лише для перегляду, що показує місце геокодування',
        displayNameLabel: 'Відображувана назва',
        displayNamePlaceholder: 'Введіть відображувану назву місця',
        displayNameHint: 'Основна назва, що відображається для цього місця',
        cityLabel: 'Місто',
        cityPlaceholder: 'Введіть назву міста',
        cityHint: 'Необов\'язкова назва міста',
        countryLabel: 'Країна',
        countryPlaceholder: 'Введіть назву країни',
        countryHint: 'Необов\'язкова назва країни',
        providerLabel: 'Провайдер:',
        coordinatesLabel: 'Координати:',
        noteLabel: 'Примітка:',
        syncNoteMessage: 'Зміни буде синхронізовано в усіх зупинках таймлайну, що використовують цей результат геокодування.',
        saveChanges: 'Зберегти зміни'
    }
}
