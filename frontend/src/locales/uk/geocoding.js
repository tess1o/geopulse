/**
 * Reverse geocoding management (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    page: {
        title: 'Керування зворотним геокодуванням',
        subtitle: 'Керуйте та оновлюйте результати геокодування',
        reconcileAllButton: 'Узгодити все ({count})',
        bulkEditButton: 'Масове редагування ({count})',
        reconcileSelectedButton: 'Узгодити вибрані ({count})',
        providerSwitchInfo: 'Зміна провайдера впливає лише на нові запити. Використовуйте "Узгодити вибрані"/"Узгодити все", щоб оновити наявні кешовані записи.',
        normalization: {
            title: 'Правила нормалізації',
            subtitle: "Індивідуальні правила зіставлення для назв країн і міст. Нові результати геокодування автоматично використовують ці правила; скористайтеся кнопкою «Застосувати правила зараз», щоб оновити наявне збережене геокодування та обрані місця.",
            collapse: 'Згорнути',
            expand: 'Розгорнути',
            addRule: 'Додати правило',
            applyRulesNow: 'Застосувати правила зараз',
            collapsedHint: 'Розділ згорнуто. Розгорніть, щоб керувати правилами.',
            empty: 'Поки що немає правил нормалізації.',
            typeColumn: 'Тип',
            typeCountry: 'Країна',
            typeCity: 'Місто',
            fromColumn: 'Звідки',
            toColumn: 'Куди',
            actionsColumn: 'Дії',
            valueDash: '-'
        },
        filters: {
            providerLabel: 'Провайдер:',
            allProviders: 'Усі провайдери',
            searchLabel: 'Пошук:',
            searchPlaceholder: 'Пошук за місцем, містом або країною',
            clearFilters: 'Очистити фільтри'
        },
        table: {
            title: 'Результати геокодування',
            emptyTitle: 'Результатів геокодування не знайдено',
            emptyDescription: 'Немає даних геокодування для вибраних критеріїв.',
            displayNameColumn: 'Назва',
            cityColumn: 'Місто',
            countryColumn: 'Країна',
            providerColumn: 'Провайдер',
            coordinatesColumn: 'Координати',
            lastUsedColumn: 'Востаннє використано',
            actionsColumn: 'Дії',
            nullValue: '-',
            viewDetailsTooltip: 'Переглянути деталі',
            editTooltip: 'Редагувати',
            reconcileTooltip: 'Узгодити'
        },
        bulkEdit: {
            itemTypeName: 'Результати геокодування'
        },
        ruleDialog: {
            editHeader: 'Редагувати правило нормалізації',
            addHeader: 'Додати правило нормалізації',
            ruleTypeLabel: 'Тип правила',
            fromCountryLabel: 'Із країни',
            toCountryLabel: 'До країни',
            fromCityLabel: 'Із міста',
            toCityLabel: 'До міста',
            countryPlaceholderFrom: 'напр. Болгарія',
            countryPlaceholderTo: 'напр. Bulgaria',
            cityPlaceholderFrom: 'напр. Sofia',
            cityPlaceholderTo: 'напр. Софія',
            cancel: 'Скасувати',
            save: 'Зберегти'
        },
        applyRulesDialog: {
            applySelectedHeader: 'Застосувати вибране правило',
            applyAllHeader: 'Застосувати правила нормалізації',
            applying: 'Застосовується: {source} → {target}',
            applyToGeocoding: 'Застосувати до об’єктів зворотного геокодування',
            applyToFavorites: 'Застосувати до обраних місць',
            cancel: 'Скасувати',
            apply: 'Застосувати'
        },
        ruleTypeOptions: {
            country: 'Країна',
            city: 'Місто'
        },
        deleteRuleConfirm: 'Видалити зіставлення "{source} → {target}"?',
        toasts: {
            loadResultsFailed: 'Не вдалося завантажити результати геокодування',
            loadRulesFailed: 'Не вдалося завантажити правила нормалізації',
            ruleUpdatedSummary: 'Правило оновлено',
            ruleUpdatedDetail: 'Правило нормалізації успішно оновлено',
            ruleAddedSummary: 'Правило додано',
            ruleAddedDetail: 'Правило нормалізації успішно створено',
            saveFailedSummary: 'Не вдалося зберегти',
            saveRuleFailedFallback: 'Не вдалося зберегти правило нормалізації',
            ruleDeletedSummary: 'Правило видалено',
            ruleDeletedDetail: 'Правило нормалізації успішно видалено',
            deleteFailedSummary: 'Не вдалося видалити',
            deleteRuleFailedFallback: 'Не вдалося видалити правило нормалізації',
            normalizationStartedSummary: 'Нормалізацію розпочато',
            normalizationStartedSingleDetail: 'Застосовуємо вибране правило у фоновому режимі...',
            normalizationStartedAllDetail: 'Застосовуємо правила у фоновому режимі...',
            applyFailedSummary: 'Не вдалося застосувати',
            applyFailedFallback: 'Не вдалося запустити завдання застосування нормалізації',
            updateSuccessDetail: 'Результат геокодування успішно оновлено',
            updateFailedSummary: 'Не вдалося оновити',
            updateFailedFallback: 'Не вдалося оновити результат геокодування',
            reconciliationFailedSummary: 'Помилка узгодження',
            reconciliationFailedFallback: 'Не вдалося запустити узгодження',
            reconciliationCompleteSummary: 'Узгодження завершено',
            reconciliationCompleteDetail: 'Успішно узгоджено {success} з {total} результатів',
            normalizationCompleteSummary: 'Застосування нормалізації завершено',
            normalizationCompleteDetail: 'Оновлено {geocodingUpdated} геокодувань і {favoritesUpdated} обраних місць',
            normalizationCompleteFailedSuffix: ' ({count} не вдалося)'
        }
    }
}
