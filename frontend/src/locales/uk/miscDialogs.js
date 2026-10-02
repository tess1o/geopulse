/**
 * Miscellaneous dialogs (Ukrainian): bulk save/edit confirmation, timeline label CRUD, location
 * lookup, stay details, timeline regeneration progress.
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    timelineRegeneration: {
        header: 'Регенерація хронології',
        completedMessage: 'Генерацію хронології успішно завершено!',
        gpsPointsLoaded: 'Завантажено {loaded} / {total} GPS-точок',
        pointsProcessed: 'Оброблено {processed} / {total} точок',
        locationsGeocoded: 'Геокодовано {resolved} / {total} місць',
        fromFavorites: '{count} з обраних місць',
        fromCache: '{count} з кешу',
        fromExternalApi: '{count} із зовнішнього API',
        pending: '{count} в очікуванні',
        viewDetailedProgress: 'Переглянути детальний прогрес',
        fallbackNote: 'Цей процес може зайняти 5-15 секунд або довше залежно від обсягу ваших даних. Ваша хронологія буде тимчасово недоступна під час регенерації.',
        pleaseWait: 'Будь ласка, зачекайте...',
        titles: {
            favorite: 'Додавання обраного місця та регенерація хронології',
            favoriteDelete: 'Видалення обраного місця та регенерація хронології',
            preferences: 'Застосування налаштувань та регенерація хронології',
            classification: 'Оновлення класифікацій поїздок',
            reconstruction: 'Застосування відсутніх даних хронології',
            general: 'Регенерація хронології'
        },
        messages: {
            favorite: 'Ми додаємо ваше обране місце та регенеруємо повну хронологію, щоб врахувати цю зміну. Це гарантує, що всі дані хронології залишаються точними та актуальними.',
            favoriteDelete: 'Ми видаляємо ваше обране місце та регенеруємо повну хронологію, щоб відобразити цю зміну. Це гарантує, що всі дані хронології залишаються точними та актуальними.',
            preferences: 'Ми застосовуємо ваші нові налаштування та регенеруємо повну хронологію на основі оновлених параметрів. Це забезпечує оптимальну точність хронології відповідно до ваших налаштувань.',
            classification: 'Ми перераховуємо типи руху для ваших наявних поїздок на основі оновлених порогів швидкості. Цей процес оновить спосіб класифікації ваших поїздок без зміни базової структури хронології.',
            reconstruction: 'Ми застосовуємо згенеровані GPS-точки з ваших зупинок і поїздок, а потім оновлюємо відповідну частину хронології. Наявні дані хронології не замінюються.',
            general: 'Ми регенеруємо вашу повну хронологію на основі GPS-даних. Цей процес гарантує точність вашої хронології та відображення всієї доступної інформації про місцезнаходження.'
        }
    },
    stayDetails: {
        titleWithLocation: 'Деталі зупинки - {location}',
        unknownLocation: 'Невідоме місце',
        mapSectionTitle: 'Карта місця',
        timingTitle: 'Час',
        start: 'Початок:',
        end: 'Кінець:',
        duration: 'Тривалість:',
        coordinatesTitle: 'Координати',
        latitude: 'Широта:',
        longitude: 'Довгота:',
        coordinates: 'Координати:',
        notAvailable: 'Н/Д',
        close: 'Закрити',
        toasts: {
            copiedSummary: 'Скопійовано!',
            copiedDetail: 'Координати скопійовано в буфер обміну',
            copyFailedSummary: 'Не вдалося скопіювати',
            copyFailedDetail: 'Не вдалося скопіювати координати'
        }
    },
    locationLookup: {
        header: 'Відвідування поблизу цієї точки',
        withinRadius: 'у межах {radius} м',
        checkingStays: 'Перевірка ваших зафіксованих зупинок…',
        tryAgain: 'Спробувати ще раз',
        hasVisits: 'У вас є зафіксовані відвідування цього місця.',
        recordedStayFallback: 'Зафіксована зупинка',
        placeDetails: 'Деталі місця',
        visitCount: '{count} відвідування | {count} відвідування | {count} відвідувань',
        firstVisit: 'Перше: {date}',
        lastVisit: 'Останнє: {date}',
        openVisitDayTooltip: 'Відкрити день відвідування в хронології',
        savedPlacesHeading: 'Збережені місця в цій точці',
        unnamedFavorite: 'Безіменне обране місце',
        noVisitFound: 'Не знайдено зафіксованих відвідувань у межах {radius} м від цієї точки.',
        closestStaysHeading: 'Найближчі зафіксовані зупинки',
        unknownLocation: 'Невідоме місце',
        noRecordedStays: 'Зафіксованих зупинок не знайдено.',
        close: 'Закрити'
    },
    timelineLabelForm: {
        nameLabel: 'Назва мітки *',
        namePlaceholder: 'напр., Відпустка в Іспанії, Робоча поїздка до Нью-Йорка',
        dateRangeLabel: 'Діапазон дат *',
        dateRangePlaceholder: 'Виберіть початкову та кінцеву дати',
        colorLabel: 'Колір',
        colorPreview: 'Попередній перегляд',
        randomButton: 'Випадковий',
        showAsPresetLabel: 'Показувати як готовий пресет дати',
        showAsPresetHint: 'Якщо увімкнено, ця мітка з’являється у спадних списках пресетів DatePicker.',
        nameRequired: "Назва мітки обов'язкова",
        dateRangeRequired: "Діапазон дат обов'язковий",
        cancel: 'Скасувати'
    },
    createTimelineLabel: {
        header: 'Створити мітку хронології',
        createButton: 'Створити',
        overlap: {
            message: 'Ця мітка перетинається з: {names}. Все одно створити?',
            header: 'Виявлено перетин міток',
            acceptLabel: 'Все одно створити',
            rejectLabel: 'Скасувати'
        },
        toasts: {
            checkOverlapsFailedFallback: 'Не вдалося перевірити перетини',
            createdSummary: 'Створено',
            createdDetail: 'Мітку хронології успішно створено',
            createFailedFallback: 'Не вдалося створити мітку хронології'
        }
    },
    editTimelineLabel: {
        header: 'Редагувати мітку хронології',
        readOnlyHeader: 'Перегляд мітки хронології (лише читання)',
        activeOwnTracksTitle: 'Активна мітка OwnTracks',
        activeOwnTracksMessage: 'Ця мітка наразі керується OwnTracks і не може бути відредагована, доки вона активна. Ви зможете редагувати її після завершення (коли змінюєте мітки в OwnTracks).',
        updateButton: 'Оновити',
        toasts: {
            updatedSummary: 'Оновлено',
            updatedDetail: 'Мітку хронології успішно оновлено',
            updateFailedFallback: 'Не вдалося оновити мітку хронології'
        }
    },
    bulkEdit: {
        header: 'Масове редагування {count} {itemType}',
        editingPrefix: 'Ви редагуєте',
        fieldsHeading: 'Виберіть поля для оновлення',
        updateCity: 'Оновити місто',
        updateCountry: 'Оновити країну',
        cityPlaceholder: 'Введіть назву міста',
        countryPlaceholder: 'Введіть назву країни',
        cityRequired: "Значення міста обов'язкове",
        countryRequired: "Значення країни обов'язкове",
        selectAtLeastOneField: 'Виберіть принаймні одне поле для оновлення',
        typoWarning: {
            header: 'Можлива помилка написання',
            notFoundMessage: 'Наступні значення не знайдено у ваших наявних даних:',
            didYouMean: 'Можливо, ви мали на увазі:',
            continuePrompt: 'Продовжити з цими значеннями?',
            continueAnyway: 'Все одно продовжити'
        },
        fields: {
            city: 'Місто',
            country: 'Країна'
        },
        updating: 'Оновлення...',
        updateButton: 'Оновити {count} елементів',
        toasts: {
            completeSummary: 'Масове оновлення завершено',
            partialDetail: 'Оновлено {success} з {total} елементів ({failed} не вдалося)',
            successDetail: 'Успішно оновлено {count} {itemType}',
            failedSummary: 'Не вдалося оновити',
            failedFallback: 'Не вдалося оновити {itemType}'
        }
    },
    bulkSaveConfirm: {
        header: 'Підтвердження масового збереження',
        summaryTitle: 'Ви збираєтеся зберегти {count} обране місце: | Ви збираєтеся зберегти {count} обраних місця: | Ви збираєтеся зберегти {count} обраних місць:',
        pointsItem: '{count} точкове місце | {count} точкових місця | {count} точкових місць',
        areasItem: '{count} область | {count} області | {count} областей',
        infoMessage: 'Ці обрані місця буде збережено, після чого запуститься повна регенерація хронології.',
        confirm: 'Підтвердити'
    }
}
