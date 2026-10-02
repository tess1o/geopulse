/**
 * GPS Data (Technical Data) page (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    page: {
        title: 'GPS-дані',
        subtitle: 'Технічна інформація про ваші дані GPS-відстеження',
        actions: {
            selected: 'Вибрано: {count}',
            deleteSelected: 'Видалити вибрані',
            deleteAllData: 'Видалити всі дані',
            exportCsv: 'Експорт CSV',
            exportCsvSelected: 'Експорт CSV (вибрано: {count})',
            exportCsvZero: 'Експорт CSV (0 точок)',
            exportCsvFiltered: 'Експорт CSV ({count} точок)'
        },
        stats: {
            totalPoints: 'Усього GPS-точок',
            pointsToday: 'Точок сьогодні',
            firstPoint: 'Перша GPS-точка',
            lastPoint: 'Остання GPS-точка'
        },
        filters: {
            title: 'Фільтри',
            activeCount: 'Активних: {count}',
            clearAll: 'Очистити все',
            hideAdvanced: 'Сховати додаткові',
            showAdvanced: 'Показати додаткові',
            today: 'Сьогодні',
            yesterday: 'Вчора',
            last7Days: 'Останні 7 днів',
            last30Days: 'Останні 30 днів',
            from: 'Від',
            to: 'До',
            startPlaceholder: 'Дата і час початку',
            endPlaceholder: 'Дата і час завершення',
            apply: 'Застосувати',
            clear: 'Очистити',
            fromBeforeTo: 'Дата "Від" має бути раніше за дату "До".',
            sourceTypesLabel: 'Типи джерел:',
            allSources: 'Усі джерела',
            accuracyLabel: 'Точність (метри):',
            speedLabel: 'Швидкість (км/год):',
            min: 'Мін',
            max: 'Макс',
            rangeTo: 'до',
            dateChip: 'Дата: {range}',
            accuracyChip: 'Точність: {min} - {max} м',
            speedChip: 'Швидкість: {min} - {max} км/год',
            sourcesChip: 'Джерела: вибрано {count}',
            sourceOptions: {
                manualReconstruction: 'Ручна реконструкція'
            },
            noPointsForPeriod: 'Немає точок за обраний період',
            showingOfTotal: 'Показано {filtered} з {total} точок',
            showingCount: 'Показано {count} точок'
        },
        telemetry: {
            title: 'Зіставлення телеметрії (необов\'язково)',
            hint: 'Спільне для типу джерела для GPS-даних і спливаючих вікон таймлайну.',
            hideMapping: 'Сховати зіставлення',
            showMapping: 'Показати зіставлення',
            sourceTypeLabel: 'Тип джерела',
            resetToDefaults: 'Скинути до типових',
            addRow: 'Додати рядок',
            saveMapping: 'Зберегти зіставлення',
            loading: 'Завантаження зіставлення телеметрії...',
            empty: 'Зіставлення телеметрії не налаштовано.',
            keyPlaceholder: 'ключ ext',
            labelPlaceholder: 'Мітка',
            unitPlaceholder: 'Одиниця',
            trueValuesPlaceholder: 'Значення "істина" (напр. 1,true,on)',
            falseValuesPlaceholder: 'Значення "хиба" (напр. 0,false,off)',
            booleanDefaultLabel: 'Булеве {index}',
            columns: {
                key: 'Ключ',
                label: 'Мітка',
                type: 'Тип',
                unit: 'Одиниця',
                gpsData: 'GPS-дані',
                currentPopup: 'Поточне спливаюче вікно',
                order: 'Порядок'
            },
            typeOptions: {
                boolean: 'Булеве',
                number: 'Число',
                string: 'Рядок'
            }
        },
        table: {
            title: 'GPS-точки',
            showing: 'Показано {start}-{end} з {total} точок',
            filteredFrom: 'відфільтровано з {total}',
            noPointsFound: 'Точок не знайдено',
            rowsPerPage: 'Рядків на сторінку:',
            rowsShort: 'Рядків:',
            pageOf: 'Сторінка {current} з {total}',
            totalCount: 'Усього: {count}',
            emptyTitle: 'GPS-точок не знайдено',
            emptyDescription: 'Немає даних GPS-відстеження для обраних критеріїв.',
            editTooltip: 'Редагувати GPS-точку',
            deleteTooltip: 'Видалити GPS-точку',
            columns: {
                date: 'Дата',
                delta: 'Різниця',
                location: 'Розташування',
                speed: 'Швидкість',
                accuracy: 'Точність',
                altitude: 'Висота',
                battery: 'Батарея',
                telemetry: 'Телеметрія',
                source: 'Джерело',
                actions: 'Дії'
            }
        },
        mobile: {
            selectPage: 'Вибрати сторінку',
            clearPage: 'Очистити сторінку',
            selectAllAriaLabel: 'Вибрати всі GPS-точки на цій сторінці',
            sort: 'Сортування',
            ascending: 'За зростанням',
            descending: 'За спаданням',
            sortAscendingAriaLabel: 'Сортувати за зростанням',
            sortDescendingAriaLabel: 'Сортувати за спаданням',
            loading: 'Завантаження GPS-точок...',
            actionsTooltip: 'Дії з GPS-точкою',
            selectPointAriaLabel: 'Вибрати GPS-точку від {date} {time}',
            actionsAriaLabel: 'Дії для GPS-точки від {date} {time}'
        },
        dialogs: {
            delete: {
                header: 'Видалити GPS-точку',
                message: 'Ви впевнені, що хочете видалити цю GPS-точку? Цю дію неможливо скасувати.',
                confirm: 'Видалити'
            },
            bulkDelete: {
                header: 'Видалити кілька GPS-точок',
                message: 'Ви впевнені, що хочете видалити {count} GPS-точку? Цю дію неможливо скасувати. | Ви впевнені, що хочете видалити {count} GPS-точки? Цю дію неможливо скасувати. | Ви впевнені, що хочете видалити {count} GPS-точок? Цю дію неможливо скасувати.',
                confirm: 'Видалити все'
            },
            deleteAll: {
                header: 'Видалити всі GPS-дані',
                warningTitle: 'Це остаточно видалить:',
                allPoints: 'Усі {count} GPS-точок',
                timelineData: 'Усі зупинки, поїздки та розриви даних таймлайну',
                note: 'Ваші налаштування та обрані місця {not} постраждають. Цю дію неможливо скасувати.',
                notWord: 'не',
                confirm: 'Видалити все повністю'
            }
        },
        toasts: {
            telemetryLoadFailedSummary: 'Помилка завантаження телеметрії',
            telemetryLoadFailedFallback: 'Не вдалося завантажити зіставлення телеметрії',
            telemetrySavedSummary: 'Телеметрію збережено',
            telemetrySavedDetail: 'Зіставлення телеметрії успішно оновлено',
            saveFailedSummary: 'Помилка збереження',
            saveTelemetryFailedFallback: 'Не вдалося зберегти зіставлення телеметрії',
            telemetryResetSummary: 'Телеметрію скинуто',
            telemetryResetDetail: 'Зіставлення телеметрії скинуто до типових значень',
            resetFailedSummary: 'Помилка скидання',
            resetTelemetryFailedFallback: 'Не вдалося скинути зіставлення телеметрії',
            summaryLoadFailedDetail: 'Не вдалося завантажити зведення GPS-даних',
            pointsLoadFailedDetail: 'Не вдалося завантажити GPS-точки',
            exportStartedSummary: 'Експорт розпочато',
            exportSelectedDetail: 'Експортуємо {count} вибраних GPS-точок. Завантаження почнеться незабаром.',
            exportAllDetail: 'Експортуємо {count} GPS-точок. Завантаження почнеться незабаром.',
            exportFailedSummary: 'Помилка експорту',
            exportFailedDetail: 'Не вдалося експортувати GPS-дані',
            pointDeletedSummary: 'GPS-точку видалено',
            pointDeletedDetail: 'GPS-точку успішно видалено',
            timelineRegenStarted: 'Розпочато регенерацію таймлайну.',
            timelineRegenScheduled: 'Регенерація таймлайну запуститься після завершення поточного завдання таймлайну.',
            coverageRebuildScheduled: 'Заплановано перебудову покриття.',
            deleteFailedSummary: 'Помилка видалення',
            deleteFailedFallback: 'Не вдалося видалити GPS-точку',
            pointUpdatedSummary: 'GPS-точку оновлено',
            pointUpdatedDetail: 'GPS-точку успішно оновлено',
            updateFailedSummary: 'Помилка оновлення',
            updateFailedFallback: 'Не вдалося оновити GPS-точку',
            pointsDeletedSummary: 'GPS-точки видалено',
            bulkDeleteSuccessDetail: 'Успішно видалено {count} GPS-точку | Успішно видалено {count} GPS-точки | Успішно видалено {count} GPS-точок',
            bulkDeleteFailedSummary: 'Помилка масового видалення',
            bulkDeleteFailedFallback: 'Не вдалося видалити вибрані GPS-точки',
            allDataDeletedSummary: 'Усі дані видалено',
            allDataDeletedDetail: 'Усі GPS-точки та дані таймлайну успішно видалено',
            deleteAllFailedFallback: 'Не вдалося видалити всі GPS-дані'
        }
    }
}
