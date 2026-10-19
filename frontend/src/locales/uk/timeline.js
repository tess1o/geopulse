/**
 * Timeline page, its preferences, labels, reports, and the timeline components. (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    locationEdit: {
        editFavoriteHeader: 'Редагувати обране місце'
    },
    notes: {
        openSingle: 'Відкрити нотатку',
        openMultiple: 'Відкрити нотатки ({count})'
    },
    photos: {
        openSingle: 'Відкрити фото',
        openMultiple: 'Відкрити фото ({count})',
        fallbackAlt: 'Фото'
    },
    dataGap: {
        subtitle: 'Прогалина в даних - невідома активність',
        endTimeLabel: 'Час завершення:',
        durationLabel: 'Тривалість:',
        totalDuration: 'Загальна тривалість:',
        onThisDay: 'Цього дня:',
        convertTooltip: 'Перетворити цю прогалину на зупинку',
        convertButton: 'Перетворити на зупинку'
    },
    overnight: {
        continuedFrom: 'Продовження з {time}'
    },
    localTime: {
        resolved: 'Місцевий час у {zone}, за найближчим містом {city} ({country}, {distance} км)',
        beyondMaxDistance: 'Місцевий часовий пояс невідомий: найближче місто {city} ({country}) за {distance} км. Показано часовий пояс профілю.',
        noGeonamesData: 'Місцевий часовий пояс невідомий: дані міст GeoNames не завантажено на сервері. Показано часовий пояс профілю.',
        invalidTimezone: 'Місцевий часовий пояс невідомий: найближче місто {city} ({country}) має невідомий часовий пояс. Показано часовий пояс профілю.',
        notProvided: 'Місцевий часовий пояс недоступний для цього елемента. Показано часовий пояс профілю.'
    },
    card: {
        showGpsPoints: 'Показати GPS-точки',
        viewAllVisits: 'Переглянути всі відвідування цього місця',
        renamePlace: 'Перейменувати місце...',
        resetDataGap: 'Скинути до автоматичної прогалини в даних',
        undoTripSplit: 'Скасувати ручний поділ поїздки',
        manageNoteSingle: 'Керувати нотаткою...',
        manageNotesMultiple: 'Керувати нотатками ({count})...',
        viewNoteSingle: 'Переглянути нотатку...',
        viewNotesMultiple: 'Переглянути нотатки ({count})...',
        addNote: 'Додати нотатку...',
        viewCityDetails: 'Переглянути дані міста {city}',
        viewCountryDetails: 'Переглянути дані країни {country}',
        exportGpx: 'Експортувати як GPX',
        renameStayAria: 'Перейменувати місце зупинки',
        renameDisabledDemo: 'Перейменування вимкнено в демо-режимі',
        resetDisabledDemo: 'Скидання вимкнено в демо-режимі'
    },
    stay: {
        stayedAt: 'Зупинка в',
        manualIndicator: '(Вручну)',
        forDuration: 'Тривалість'
    },
    trip: {
        transitionTo: 'Перехід до',
        transitionToNewPlace: 'Перехід до нового місця',
        durationLabel: 'Тривалість:',
        distanceLabel: 'Відстань:',
        movementLabel: 'Пересування:',
        editMovementType: 'Редагувати тип пересування',
        movementEditDisabledDemo: 'Редагування типу пересування вимкнено в демо-режимі',
        setMovementType: 'Встановити тип пересування',
        unrecognizedHint: 'Алгоритм не розпізнав цю поїздку.',
        changeMovementType: 'Змінити тип пересування...',
        whyThisClassification: 'Чому саме ця класифікація?',
        splitTripWithStay: 'Розділити поїздку зупинкою...',
        mapMatchingDetails: 'Деталі зіставлення з картою...'
    },
    explanation: {
        trigger: {
            stay: 'Чому це перебування? Перегляньте його GPS-точки і як його визначено',
            trip: 'Чому ця поїздка? Перегляньте її GPS-точки і як її визначено',
            dataGap: 'Чому прогалина в даних? Перегляньте GPS-точки навколо неї і як її визначено'
        },
        toggle: {
            stay: 'Чому це перебування?',
            trip: 'Чому ця поїздка?',
            dataGap: 'Чому прогалина в даних?'
        },
        start: {
            FIRST_POINT: 'Починається з першої GPS-точки.',
            AFTER_DATA_GAP: 'Починається з першої GPS-точки після прогалини в даних.',
            ARRIVAL: 'Починається там, де ви зупинилися: {points} точок повільніше за {stopSpeedKmh} км/год протягом {stopSeconds}.',
            SPARSE_STOP: 'Починається там, де ви зупинилися: наступна GPS-точка зʼявилася через {gapSeconds}, лише за {distanceMeters} звідси.',
            LEFT_STAY: 'Починається, коли записано точку за {distanceMeters} від перебування (радіус перебування {radiusMeters}).',
            SHORT_STOP: 'Починається із зупинки тривалістю {stopSeconds}, закороткої для перебування (потрібно {minMinutes} хв), тому вона рахується частиною цієї поїздки.',
            WATER_TRANSITION_TO_WATER: 'Починається там, де ви перейшли із суші на воду.',
            WATER_TRANSITION_TO_LAND: 'Починається там, де ви перейшли з води на сушу.',
            INFERRED_DURING_GAP: 'Тут немає GPS-точок протягом {gapSeconds}, але точки до і після лише за {distanceMeters} одна від одної, тож, імовірно, ви залишалися тут.',
            INFERRED_TRIP_DURING_GAP: 'Тут немає GPS-точок протягом {gapSeconds}; точки до і після за {distanceMeters} одна від одної, тож ви, мабуть, переміщалися.',
            CONTINUITY: 'Між перебуваннями до і після, що за {distanceMeters} одне від одного, поїздку не виявлено, тож ця поїздка їх поєднує.',
            NO_POINTS: 'Немає GPS-точок протягом {gapSeconds}. Перерви, довші за {thresholdSeconds}, показуються як прогалини в даних.',
            NO_POINTS_SINCE_LAST: 'Немає GPS-точок після останньої. Перерва, довша за {thresholdSeconds}, показується як прогалина в даних, доки не надійдуть нові точки.',
            STOPPED_BEFORE_GAP: 'Починається там, де ви зупинилися перед прогалиною в даних.',
            MANUAL_SPLIT: 'Ви додали це перебування, розділивши поїздку.',
            MANUAL_GAP_STAY: 'Ви перетворили прогалину в даних на це перебування.'
        },
        note: {
            CONFIRMED: 'Вважається перебуванням: точки залишалися в межах {radiusMeters} щонайменше {minMinutes} хв.',
            FAVORITE_AREA: 'Точки поза радіусом перебування залишено в цьому перебуванні, бо вони в тій самій обраній області.',
            CONTINUED_OVER_GAP: 'Триває через прогалину в даних тривалістю {gapSeconds}: точки до і після неї в тому самому місці.',
            MERGED: '{stays} перебувань у тому самому місці обʼєднано в це.',
            JOINED_SEGMENTS: '{segments} частин руху обʼєднано в цю поїздку.',
            SHORT_STOP: 'Містить зупинку тривалістю {stopSeconds}, закоротку для перебування (потрібно {minMinutes} хв).',
            MANUAL_SPLIT: 'Змінено вашим ручним розділенням поїздки.',
            MANUAL_GAP_STAY: 'Подовжено вашим ручним перетворенням прогалини в даних.'
        },
        end: {
            MOVED_AWAY: 'Закінчується, коли записано точку за {distanceMeters} (радіус перебування {radiusMeters}).',
            ARRIVAL: 'Закінчується там, де ви зупинилися: {points} точок повільніше за {stopSpeedKmh} км/год протягом {stopSeconds}.',
            SPARSE_STOP: 'Закінчується там, де ви зупинилися: наступна GPS-точка зʼявилася через {gapSeconds}, лише за {distanceMeters} звідси.',
            DATA_GAP: 'Закінчується на прогалині в даних тривалістю {gapSeconds}.',
            LAST_POINT: 'Закінчується останньою GPS-точкою, тож може ще тривати.',
            WATER_TRANSITION_TO_WATER: 'Закінчується там, де ви перейшли із суші на воду.',
            WATER_TRANSITION_TO_LAND: 'Закінчується там, де ви перейшли з води на сушу.',
            STOPPED_BEFORE_GAP: 'Закінчується там, де ви зупинилися перед прогалиною в даних.'
        }
    },
    explanationDialog: {
        header: {
            stay: 'Чому це перебування?',
            trip: 'Чому ця поїздка?',
            dataGap: 'Чому прогалина в даних?'
        },
        loading: 'Завантаження GPS-точок...',
        loadFailed: 'Не вдалося завантажити пояснення.',
        period: '{start} – {end} ({duration})',
        sections: {
            map: 'GPS-точки',
            steps: 'Як це визначено',
            filtered: 'Відкинуті точки',
            ingestion: 'Точки, які не було збережено'
        },
        legend: {
            used: 'Використані',
            filtered: 'Відкинуті (неточні)',
            context: 'До і після',
            deciding: 'Вплинули на рішення',
            radius: 'Радіус перебування',
            anchor: 'Збережене місце'
        },
        mapHint: 'Виберіть крок, щоб побачити його точки на карті. Виберіть точку, щоб побачити подробиці.',
        noPoints: 'За цей час немає збережених GPS-точок.',
        truncated: 'Ця подія має {total} GPS-точок. На карті показано {shown} з них, зокрема всі відкинуті точки й ті, що вплинули на рішення.',
        steps: {
            any: {
                start: {
                    FIRST_POINT: 'Починається о {time} з вашої першої GPS-точки.',
                    AFTER_DATA_GAP: 'Починається о {time} з першої GPS-точки після перерви в даних.',
                    ARRIVAL: 'Починається о {time}, там, де ви зупинилися.',
                    SPARSE_STOP: 'Починається о {time}, там, де ви зупинилися. Поки ви були тут, GPS-точки не записувалися: наступна зʼявилася через {gapSeconds}, лише за {distanceMeters} звідси.',
                    LEFT_STAY: 'Почалася о {time}, коли ви були за {distanceMeters} від місця перебування, далі за його радіус {radiusMeters}.',
                    SHORT_STOP: 'Починається із зупинки з {time} до {endTime} ({stopSeconds}). Для перебування потрібно щонайменше {minMinutes}, тому зупинка рахується частиною цієї поїздки.',
                    WATER_TRANSITION_TO_WATER: 'Починається о {time}, де ви перейшли із суші на воду.',
                    WATER_TRANSITION_TO_LAND: 'Починається о {time}, де ви перейшли з води на сушу.',
                    INFERRED_DURING_GAP: 'Протягом {gapSeconds} немає GPS-даних, але точки до і після перерви лише за {distanceMeters} одна від одної, тож GeoPulse вважає, що ви залишалися тут.',
                    INFERRED_TRIP_DURING_GAP: 'Протягом {gapSeconds} немає GPS-даних, а точки до і після перерви за {distanceMeters} одна від одної, тож між ними ви переміщалися.',
                    CONTINUITY: 'Між перебуваннями до і після цієї поїздки рух не записано, але вони за {distanceMeters} одне від одного, тож GeoPulse додає цю поїздку, щоб їх поєднати. Власних GPS-точок вона не має.',
                    STOPPED_BEFORE_GAP: 'Ви зупинилися тут о {time}, якраз перед тим, як GPS-дані перервалися, і були в тому самому місці, коли вони відновилися.',
                    MANUAL_SPLIT: 'Ви додали це, розділивши поїздку.',
                    MANUAL_GAP_STAY: 'Ви перетворили прогалину в даних на це перебування.',
                    NO_POINTS: 'Немає GPS-точок з {time} до {endTime} ({gapSeconds}). Перерви, довші за {thresholdSeconds}, показуються як прогалини в даних.',
                    NO_POINTS_SINCE_LAST: 'Немає GPS-точок з {time}. Перерва, довша за {thresholdSeconds}, показується як прогалина в даних, доки не надійдуть нові точки.'
                },
                note: {
                    CONFIRMED: 'Вважається перебуванням з {time}: на той час ви були в межах {radiusMeters} від цього місця вже {minMinutes}.',
                    FAVORITE_AREA: '{count} точок були далі від центру, ніж радіус перебування, але вони в тій самій обраній області, тож залишилися частиною цього перебування.',
                    CONTINUED_OVER_GAP: 'З {time} до {endTime} ({gapSeconds}) немає GPS-даних, але до і після перерви ви були в тому самому місці, тож перебування триває через перерву.',
                    MERGED: '{stays} перебувань у цьому місці, близьких за місцем і часом, обʼєднано в це.',
                    JOINED_SEGMENTS: '{segments} поїздок поспіль обʼєднано в цю.',
                    SHORT_STOP: 'Містить зупинку тривалістю {stopSeconds}, коротшу за {minMinutes}, потрібні для перебування.',
                    MANUAL_SPLIT: 'Змінено вашим ручним розділенням поїздки.',
                    MANUAL_GAP_STAY: 'Подовжено вашим ручним перетворенням прогалини в даних.'
                },
                end: {
                    MOVED_AWAY: 'Закінчилося о {time}, коли ви були за {distanceMeters} від центру, далі за радіус перебування {radiusMeters}.',
                    ARRIVAL: 'Закінчилася о {time}, коли ви зупинилися.',
                    SPARSE_STOP: 'Закінчилася о {time}, там, де ви зупинилися: наступна GPS-точка зʼявилася через {gapSeconds}, лише за {distanceMeters} звідси.',
                    DATA_GAP: 'Закінчується о {time}, коли GPS-дані перервалися на {gapSeconds}.',
                    LAST_POINT: 'Ваша остання GPS-точка о {time}, тож це може ще тривати.',
                    WATER_TRANSITION_TO_WATER: 'Закінчується о {time}, де ви перейшли із суші на воду.',
                    WATER_TRANSITION_TO_LAND: 'Закінчується о {time}, де ви перейшли з води на сушу.',
                    STOPPED_BEFORE_GAP: 'Закінчується о {time}, де ви зупинилися якраз перед перервою в GPS-даних.'
                }
            },
            stay: {
                start: {
                    ARRIVAL: 'Ви прибули о {time}, коли зупинилися.',
                    MANUAL_SPLIT: 'Ви додали це перебування, розділивши поїздку.'
                }
            }
        },
        callouts: {
            gapFiltered: 'За цей час записано {count} GPS-точок, але кожна мала точність гіршу за {thresholdMeters} ({setting}), тож їх відкинуто, і цей час показується як прогалина в даних.',
            settingChanged: 'Під час побудови {setting} мало значення {builtWith}, а зараз {current}. Перебудуйте хронологію, щоб застосувати нове значення.'
        },
        filtered: {
            none: 'Використано всі GPS-точки цієї події.',
            some: '{filtered} з {total} GPS-точок відкинуто, бо їхня точність гірша за {thresholdMeters} ({setting}).',
            worst: 'Найменш точна мала ±{accuracyMeters}.',
            context: 'Також відкинуто {count} точок до або після неї.',
            off: 'GPS-точки не фільтруються за точністю, бо {setting} вимкнено.'
        },
        ingestion: {
            intro: 'Ваші джерела GPS відкидають деякі точки ще до збереження, тож їх тут не видно:',
            source: '{source}: {rules}',
            accuracy: 'точки з точністю гіршою за {value}',
            speed: 'точки зі швидкістю понад {value}',
            duplicates: 'повтори того самого місця протягом {value}',
            link: 'Джерела локації'
        },
        point: {
            used: 'Використана для цієї події.',
            before: 'До цієї події.',
            after: 'Після цієї події.',
            filtered: 'Відкинута: точність ±{accuracyMeters}, гірша за поріг точності GPS {thresholdMeters}.',
            outsideRadius: '{distanceMeters} від центру, поза радіусом {radiusMeters}.',
            accuracy: 'Точність ±{value}',
            noAccuracy: 'Точність не записано',
            speed: 'Швидкість {value}',
            reason: {
                FIRST_POINT: 'Ваша перша GPS-точка.',
                AFTER_DATA_GAP: 'Перша точка після перерви в GPS-даних.',
                ARRIVAL: 'Частина зупинки, де ви прибули.',
                SPARSE_STOP: 'Де ви зупинилися: наступна точка зʼявилася значно пізніше, поруч.',
                LEFT_STAY: 'Почала цю поїздку: вона поза радіусом перебування.',
                SHORT_STOP: 'Частина зупинки, закороткої для перебування.',
                WATER_TRANSITION: 'Де ви перейшли між сушею і водою.',
                INFERRED_DURING_GAP: 'Біля перерви, протягом якої припускається це перебування.',
                INFERRED_TRIP_DURING_GAP: 'Біля перерви, протягом якої припускається ця поїздка.',
                STOPPED_BEFORE_GAP: 'Де ви зупинилися перед перервою.',
                NO_POINTS: 'Біля перерви в GPS-даних.',
                NO_POINTS_SINCE_LAST: 'Остання точка перед перервою.',
                CONFIRMED: 'Звідси це вважається перебуванням.',
                FAVORITE_AREA: 'Поза радіусом перебування, але в обраній області.',
                CONTINUED_OVER_GAP: 'Біля перерви, через яку триває перебування.',
                MOVED_AWAY: 'Завершила перебування: вона поза радіусом перебування.',
                DATA_GAP: 'Біля перерви в GPS-даних.',
                LAST_POINT: 'Ваша остання GPS-точка.'
            }
        },
        settingsUsed: 'Налаштування',
        changeSettings: 'Налаштування хронології',
        whyClassification: 'Чому такий тип руху?',
        close: 'Закрити',
        on: 'увімкнено',
        off: 'вимкнено',
        minutes: '{value} хв'
    },
    container: {
        noData: 'Немає хронології для вибраного діапазону дат.',
        showingItems: 'Показано {shown} з {total} елементів.',
        loadMore: 'Завантажити більше',
        viewAllInReports: 'Або перегляньте все у звітах хронології',
        viewPeriodAria: 'Переглянути період {name}',
        exportStartedTitle: 'Експорт розпочато',
        tripExportingDetail: 'Поїздку експортується як GPX',
        stayExportingDetail: 'Зупинку експортується як GPX',
        exportFailedTitle: 'Помилка експорту',
        tripExportFailedDetail: 'Не вдалося експортувати поїздку',
        stayExportFailedDetail: 'Не вдалося експортувати зупинку'
    },
    splitLayout: {
        defaultCollapsedLabel: 'Хронологія',
        defaultExpandedLabel: 'Хронологія пересувань',
        dayNavigationAriaLabel: 'Навігація по днях хронології',
        previousDay: 'Попередній день',
        nextDay: 'Наступний день',
        show: 'Показати {label}',
        collapse: 'Згорнути {label}'
    },
    largeDataset: {
        title: 'Вибрано великий діапазон дат',
        itemsCount: 'Цей діапазон містить {count} елементів',
        breakdown: '({stays} зупинок, {trips} поїздок)',
        breakdownWithGaps: '({stays} зупинок, {trips} поїздок, {dataGaps} прогалин у даних)',
        recommendation: 'Перегляд хронології найкраще підходить для щоденного/щотижневого перегляду. Для аналізу великих періодів скористайтеся звітами хронології:',
        openReports: 'Відкрити звіти хронології',
        orViewRecent: 'Або перегляньте останні дані:',
        last7Days: 'Останні 7 днів',
        last30Days: 'Останні 30 днів',
        loadAnyway: 'Завантажити попри все (може вплинути на швидкодію)',
        continueWithRange: 'Продовжити з поточним діапазоном'
    },
    notesViewer: {
        header: 'Нотатки',
        empty: 'Немає нотаток для цього елемента хронології.',
        editNote: 'Редагувати нотатку',
        deleteNote: 'Видалити нотатку',
        openInMemos: 'Відкрити в Memos',
        truncatedNotice: 'Ця нотатка Memos завелика, тому GeoPulse показує скорочений перегляд.',
        deleteConfirm: 'Видалити цю нотатку GeoPulse?',
        cancel: 'Скасувати',
        delete: 'Видалити',
        close: 'Закрити',
        locationSources: {
            explicit: 'З геоміткою',
            derivedStay: 'Місце зупинки',
            derivedTripGps: 'GPS поїздки',
            derivedTripInterpolated: 'Оцінка поїздки'
        },
        toasts: {
            deletedTitle: 'Нотатку видалено',
            deletedDetail: 'Нотатку GeoPulse видалено',
            deleteFailedTitle: 'Не вдалося видалити',
            deleteFailedDetail: 'Не вдалося видалити нотатку'
        }
    },
    noteEditor: {
        titleLabel: 'Заголовок',
        titlePlaceholder: 'Необовʼязковий заголовок',
        saveToLabel: 'Зберегти в',
        visibilityLabel: 'Видимість у Memos',
        noteLabel: 'Нотатка',
        contentPlaceholder: 'Напишіть нотатку...',
        contentRequired: 'Текст нотатки обовʼязковий',
        cancel: 'Скасувати',
        editHeader: 'Редагувати нотатку',
        addHeader: 'Додати нотатку',
        update: 'Оновити',
        save: 'Зберегти',
        destinations: {
            geopulse: 'GeoPulse',
            memos: 'Memos'
        },
        visibilityOptions: {
            private: 'Приватна',
            protected: 'Захищена',
            public: 'Публічна'
        },
        toasts: {
            updatedTitle: 'Нотатку оновлено',
            savedTitle: 'Нотатку збережено',
            updatedDetail: 'Нотатку GeoPulse оновлено',
            savedInMemos: 'Нотатку створено в Memos',
            savedInGeoPulse: 'Нотатку збережено в GeoPulse',
            saveFailedTitle: 'Не вдалося зберегти',
            saveFailedDetail: 'Не вдалося зберегти нотатку'
        }
    },
    dataGapHelp: {
        toggleAriaLabel: 'Перемкнути довідкову інформацію',
        explanation: 'Прогалини в даних виникають, коли GPS-відстеження було перервано. Ви можете налаштувати обробку прогалин у налаштуваннях хронології.',
        recommendationsHeader: 'На основі цієї прогалини тривалістю {duration}:',
        openSettings: 'Відкрити налаштування хронології',
        toggleShort: 'Потрібна допомога з прогалинами?',
        toggleLong: 'Чому це відбувається?',
        tips: {
            lowerThreshold: 'Знизьте поріг виявлення прогалин, щоб фіксувати коротші прогалини',
            enableStayInference: 'Увімкніть визначення зупинки в прогалині, якщо ви були в тому самому місці',
            enableTripInference: 'Увімкніть визначення поїздки в прогалині, якщо ви подолали велику відстань'
        }
    },
    layout: {
        collapsedLabel: 'Хронологія',
        expandedLabel: 'Хронологія пересувань'
    },
    labels: {
        page: {
            title: 'Мітки хронології',
            subtitleLabelsCount: '{count} мітка | {count} мітки | {count} міток',
            subtitleDaysTagged: '{count} день позначено | {count} дні позначено | {count} днів позначено',
            subtitleFallback: 'Розширені мітки хронології (використовуються чіпами хронології та імпортом)'
        },
        actions: {
            create: 'Створити мітку',
            timeline: 'Переглянути хронологію',
            more: 'Більше'
        },
        help: 'Мітки хронології позначають період вашої хронології (наприклад, поїздку чи подію), щоб він з’являвся як швидкий фільтр і міг бути перетворений на план поїздки.',
        activeBanner: {
            kicker: 'Активна мітка',
            since: 'З {date}'
        },
        sourceNames: {
            manual: 'Вручну',
            ownTracks: 'OwnTracks'
        },
        filters: {
            search: 'Пошук міток...',
            source: {
                all: 'Усі джерела'
            },
            linkState: {
                all: 'Усі',
                linked: "Прив'язані",
                unlinked: "Не прив'язані"
            }
        },
        bulkDelete: 'Видалити ({count})',
        badges: {
            active: 'Активна',
            hidden: 'Приховано',
            visible: 'Видимо'
        },
        now: 'Зараз',
        table: {
            pageReport: 'Показано {first}-{last} з {totalRecords} міток',
            columns: {
                label: 'Мітка',
                period: 'Період',
                tripPlan: 'План поїздки',
                preset: 'Пресет',
                actions: 'Дії'
            },
            notLinked: "Не прив'язано",
            empty: 'Міток хронології не знайдено',
            actionsAria: 'Більше дій для {name}',
            selectAria: 'Вибрати {name}'
        },
        cards: {
            period: 'Період',
            duration: 'Тривалість',
            datePreset: 'Пресет',
            linkedTripPlan: "Прив'язаний план поїздки",
            showingCount: 'Показано {count} міток'
        },
        linkedDelete: {
            header: "Видалити прив'язану мітку",
            linkedToLead: "Ця мітка хронології прив'язана до плану поїздки",
            choose: 'Як ви хочете продовжити?',
            cancel: 'Скасувати',
            onlyLabel: 'Видалити лише мітку',
            labelAndTripPlan: 'Видалити мітку та план поїздки'
        }
    },
    page: {
        workspaceBanner: {
            lead: 'Цей діапазон дат збігається з вашим планом поїздки:',
            openPlanner: 'Відкрити планувальник'
        },
        map: {
            noData: 'Немає даних про місцезнаходження для вибраного діапазону дат.'
        },
        favorite: {
            deleteConfirm: {
                header: 'Видалити обране',
                message: 'Ви впевнені, що хочете видалити це обране місце?'
            },
            deleteSuccess: '«{name}» видалено',
            deleteFailed: 'Не вдалося видалити обране'
        },
        toasts: {
            noLocationData: 'Немає даних про місцезнаходження для вибраного діапазону дат.',
            locationFetchFailed: 'Не вдалося отримати дані про місцезнаходження',
            noTimelineData: 'Немає даних хронології для вибраного діапазону дат.',
            timelineFetchFailed: 'Не вдалося отримати дані хронології',
            timelineLoadFailed: 'Не вдалося завантажити дані хронології',
            viewingTag: 'Перегляд {name}',
            tagPeriod: 'Показано повний період для «{name}»'
        },
        resetDataGapOverride: {
            confirmHeader: 'Скинути прогалину в даних',
            confirmMessage: 'Ви впевнені, що хочете повернути цю прогалину в даних до автоматичного визначення?',
            successSummary: 'Прогалину скинуто',
            successDetail: 'Прогалину в даних повернуто до автоматичного визначення.',
            failedDetail: 'Не вдалося скинути перевизначення прогалини в даних.'
        },
        resetFailedSummary: 'Не вдалося скинути',
        resetTripSplitOverride: {
            confirmHeader: 'Скасувати поділ поїздки',
            confirmMessage: 'Ви впевнені, що хочете скасувати цей ручний поділ поїздки?',
            successSummary: 'Поділ поїздки скасовано',
            successDetail: 'Ручний поділ поїздки скасовано.',
            failedDetail: 'Не вдалося скасувати поділ поїздки.'
        },
        reconstruction: {
            updatedSummary: 'Хронологію оновлено',
            updatedDetail: 'Вашу хронологію оновлено відновленими даними.',
            failedSummary: 'Не вдалося відновити',
            failedDetail: 'Не вдалося відновити дані хронології.',
            trackingFailedSummary: 'Помилка відстеження'
        }
    },
    preferences: {
        gpsPathSimplification: {
            title: 'Налаштування спрощення GPS-шляху',
            description: 'Налаштуйте, як GPS-шляхи спрощуються для зменшення обсягу даних із збереженням точності маршруту',
            enabled: {
                title: 'Увімкнути спрощення шляху',
                description: 'Чи увімкнено спрощення GPS-шляху для поїздок у хронології',
                details: 'Якщо увімкнено, шляхи поїздок спрощуватимуться за алгоритмом Дугласа-Пекера, щоб зменшити кількість GPS-точок із збереженням точності маршруту'
            },
            tolerance: {
                title: 'Допуск спрощення',
                description: 'Базовий допуск у метрах для спрощення GPS-шляху',
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: 'Зберігають більше деталей, менше стиснення',
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: 'Більше стиснення, менше деталей',
                labelLow: '1 м (Висока деталізація)',
                labelMid: '15 м (Збалансовано)',
                labelHigh: '50 м (Високе стиснення)',
                suffix: ' м'
            },
            maxPoints: {
                title: 'Максимум точок',
                description: 'Максимальна кількість GPS-точок для збереження у спрощених шляхах',
                details: 'Якщо спрощений шлях все ще перевищує цю межу, допуск буде автоматично збільшено, доки межу не буде досягнуто. Встановіть 0 для відсутності обмеження',
                labelLow: '0 (Без обмеження)',
                labelMid: '100 (Збалансовано)',
                labelHigh: '500 (Висока межа)',
                suffix: ' точок'
            },
            adaptive: {
                title: 'Адаптивне спрощення',
                description: 'Вмикає адаптивне спрощення, яке коригує допуск залежно від характеристик поїздки',
                details: 'Якщо увімкнено, довші поїздки використовують вищі значення допуску для кращого стиснення, тоді як коротші поїздки зберігають вищу точність із нижчим допуском'
            }
        },
        stayMerging: {
            title: "Налаштування об'єднання точок зупинок",
            description: "Налаштуйте, як близькі точки зупинок об'єднуються в єдині місця",
            enabled: {
                title: "Увімкнути об'єднання точок зупинок",
                description: "Чи об'єднувати близькі точки зупинок, які близькі за часом і відстанню",
                details: "Допомагає об'єднати кілька GPS-кластерів в одному загальному місці в єдині точки зупинок"
            },
            maxDistance: {
                title: "Максимальна відстань об'єднання",
                description: "Максимальна відстань між точками зупинок для розгляду їх об'єднання",
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: "Об'єднувати лише дуже близькі точки",
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: "Об'єднувати точки на більшій відстані",
                labelLow: '20 м (Точно)',
                labelMid: '150 м (Збалансовано)',
                labelHigh: '500 м (Широко)',
                suffix: ' м'
            },
            maxTimeGap: {
                title: 'Максимальний часовий проміжок',
                description: "Максимальний часовий проміжок між точками зупинок для розгляду їх об'єднання",
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: "Об'єднувати лише послідовні зупинки",
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: "Об'єднувати зупинки, розділені довшими проміжками",
                labelLow: '1 хв (Суворо)',
                labelMid: '10 хв (Збалансовано)',
                labelHigh: '60 хв (Широко)',
                suffix: ' хв'
            }
        },
        gpsGaps: {
            title: 'Налаштування виявлення розривів GPS',
            description: 'Налаштуйте, як розриви GPS-даних виявляються та фіксуються у вашій хронології',
            threshold: {
                title: 'Поріг розриву даних',
                description: 'Максимальний часовий проміжок у секундах між GPS-точками, перш ніж вважати це розривом GPS-даних',
                details: 'Коли різниця в часі між двома послідовними GPS-точками перевищує цей поріг, буде створено об\'єкт розриву GPS-даних замість продовження поточної зупинки чи поїздки. Це запобігає штучному продовженню активностей під час періодів відсутності GPS-даних.',
                labelLow: '5 хв (Чутливо)',
                labelMid: '3 год (Збалансовано)',
                labelHigh: '6 год (Поблажливо)',
                suffix: ' секунд'
            },
            minDuration: {
                title: 'Мінімальна тривалість розриву',
                description: 'Мінімальна тривалість у секундах для фіксації розриву як розриву GPS-даних',
                details: 'Розриви, коротші за цей поріг, ігноруватимуться для зменшення шуму. Це запобігає створенню зайвих записів розривів через дуже короткі проблеми зі з\'єднанням.',
                labelLow: '5 хв (Суворо)',
                labelMid: '30 хв (Збалансовано)',
                labelHigh: '2 год (Поблажливо)',
                suffix: ' секунд'
            },
            stayInferenceEnabled: {
                title: 'Визначення зупинки за розривом',
                description: 'Визначати зупинки, коли трапляються розриви GPS-даних, але місця до і після однакові',
                detailsWhenLabel: 'Якщо увімкнено',
                detailsWhenValue: 'Якщо ви були в місці і GPS-дані зупиняються, а потім відновлюються в тому самому місці, система визначає, що ви там залишалися',
                detailsUseCaseLabel: 'Варіант використання',
                detailsUseCaseValue: 'Нічні розриви вдома показуватимуться як зупинка замість розриву даних'
            },
            stayInferenceMaxGap: {
                title: 'Максимальна тривалість розриву для визначення',
                description: 'Максимальна тривалість розриву GPS-даних для визначення зупинки',
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: 'Визначати зупинки лише для коротших розривів (напр., короткочасне вимкнення телефону)',
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: 'Визначати зупинки для довших розривів (напр., нічних, цілодобових)',
                labelLow: '1 год (Суворо)',
                labelMid: '24 год (За замовчуванням)',
                labelHigh: '72 год (Поблажливо)',
                suffix: ' год'
            },
            tripInferenceEnabled: {
                title: 'Визначення поїздки за розривом',
                description: 'Визначати поїздки, коли трапляються розриви GPS-даних із переміщенням на велику відстань',
                detailsWhenLabel: 'Якщо увімкнено',
                detailsWhenValue: 'Якщо GPS-дані зупиняються, а потім відновлюються у віддаленому місці, система визначає, що відбулася поїздка',
                detailsUseCaseLabel: 'Варіант використання',
                detailsUseCaseValue: 'Нічні перельоти або довгі поїздки, коли телефон був вимкнений, показуватимуться як визначені поїздки замість розривів даних',
                detailsClassificationLabel: 'Класифікація поїздки',
                detailsClassificationValue: 'Режим поїздки визначається автоматично за відстанню, тривалістю та швидкістю (напр., літак, автомобіль, потяг)'
            },
            tripInferenceMinDistance: {
                title: 'Мінімальна відстань для визначення поїздки',
                description: 'Мінімальна відстань між GPS-точками для визначення поїздки під час розриву',
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: 'Визначати поїздки для коротших переміщень (напр., міські поїздки на 10 км)',
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: 'Визначати поїздки лише для довших переміщень (напр., 100+ км міжміських подорожей)',
                labelLow: '10 км (Короткі поїздки)',
                labelMid: '100 км (За замовчуванням)',
                labelHigh: '500 км (Велика відстань)',
                suffix: ' м'
            },
            tripInferenceMinGap: {
                title: 'Мінімальна тривалість розриву для визначення поїздки',
                description: 'Мінімальна тривалість розриву для розгляду визначення поїздки',
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: 'Визначати поїздки для коротких розривів (напр., 30 хвилин)',
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: 'Визначати поїздки лише для довших розривів (напр., кілька годин)',
                labelLow: '0 год (Будь-який розрив)',
                labelMid: '1 год (За замовчуванням)',
                labelHigh: '12 год (Довгі розриви)',
                suffix: ' год'
            },
            tripInferenceMaxGap: {
                title: 'Максимальна тривалість розриву для визначення поїздки',
                description: 'Максимальна тривалість розриву для визначення поїздки (довші розриви стають розривами даних)',
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: 'Визначати поїздки лише для коротших розривів (напр., 12 годин)',
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: 'Визначати поїздки для довших розривів (напр., багатоденні поїздки)',
                labelLow: '1 год (Суворо)',
                labelMid: '24 год (За замовчуванням)',
                labelHigh: '168 год (Один тиждень)',
                suffix: ' год'
            }
        },
        stayPointDetection: {
            title: 'Налаштування виявлення точок зупинок',
            description: 'Налаштуйте, як GPS-дані аналізуються для виявлення місць, де ви зупинялися',
            radius: {
                title: 'Радіус виявлення зупинки',
                description: "Поріг відстані для групування GPS-точок у місця зупинок. Також визначає мінімальну відстань між зупинками для створення поїздки",
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: 'Чутливіше виявлення зупинок, розділяє близькі місця',
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: "Менш чутливо, об'єднує близькі місця в єдині зупинки",
                labelLow: '10 м (Чутливо)',
                labelMid: '50 м (Збалансовано)',
                labelHigh: '500 м (Консервативно)',
                suffix: ' м'
            },
            minDuration: {
                title: 'Мінімальна тривалість зупинки',
                description: 'Мінімальна тривалість (у хвилинах) для підтвердження точки зупинки',
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: 'Занадто короткі зупинки можуть бути хибними спрацюваннями',
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: 'Довші зупинки, ймовірніше, є реальними',
                labelLow: '1 хв (Чутливо)',
                labelMid: '7 хв (Збалансовано)',
                labelHigh: '60 хв (Консервативно)',
                suffix: ' хв'
            },
            enhancedFiltering: {
                title: 'Розширена фільтрація',
                description: 'Використовувати дані швидкості та точності для кращого виявлення точок зупинок',
                details: 'Відфільтровує неякісні GPS-точки та покращує точність хронології'
            },
            velocityThreshold: {
                title: 'Поріг швидкості',
                description: 'Максимальна швидкість, за якої точка вважається нерухомою',
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: 'Суворіша фільтрація',
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: 'Дозволяє більше руху в межах зупинок',
                labelLow: '1 км/год (Суворо)',
                labelMid: '9 км/год (Збалансовано)',
                labelHigh: '20 км/год (Поблажливо)',
                suffix: ' км/год'
            },
            accuracyThreshold: {
                title: 'Поріг точності GPS',
                description: 'Мінімальна точність GPS, необхідна для використання точки місцезнаходження',
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: 'Вимагати точніший GPS',
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: 'Приймати менш точні GPS-точки',
                labelLow: '5 м (Висока точність)',
                labelMid: '60 м (Збалансовано)',
                labelHigh: '200 м (Низька точність)',
                suffix: ' м'
            }
        },
        tripClassification: {
            title: 'Налаштування класифікації поїздок',
            description: 'Налаштуйте, як поїздки виявляються та класифікуються за типом пересування',
            priorityBanner: {
                title: 'Порядок пріоритету класифікації',
                steps: {
                    flight: 'ЛІТАК',
                    boat: 'ЧОВЕН',
                    train: 'ПОТЯГ',
                    bicycle: 'ВЕЛОСИПЕД',
                    running: 'БІГ',
                    motorVehicle: 'МОТОРНИЙ ТРАНСПОРТ',
                    walk: 'ХОДЬБА',
                    unknown: 'НЕВІДОМО'
                },
                description: 'Поїздки класифікуються в порядку пріоритету згори донизу. Щойно знайдено збіг, класифікація зупиняється. Цей порядок консервативно обробляє докази щодо води та швидкості - напр., польоти над водою залишаються ЛІТАКОМ, а суміжні наземні швидкості збігаються з ВЕЛОСИПЕДОМ раніше за БІГ і моторний транспорт.'
            },
            algorithm: {
                title: 'Алгоритм виявлення поїздок',
                description: 'Алгоритм, що використовується для виявлення поїздок між точками зупинок',
                detailsSingleLabel: 'Одна',
                detailsSingleValue: 'Завжди одна поїздка між точками зупинок',
                detailsMultipleLabel: 'Декілька',
                detailsMultipleValue: "На основі швидкості одна або декілька поїздок між точками зупинок (напр., АВТО → ХОДЬБА, ХОДЬБА → АВТО тощо)",
                placeholder: 'Оберіть алгоритм',
                optionSingle: 'Одна поїздка',
                optionMultiple: 'Декілька поїздок'
            },
            walk: {
                title: 'Ходьба',
                subtitle: 'Обов\'язковий тип транспорту',
                description: 'Виявляє повільний рух пішки (зазвичай 0-8 км/год)',
                maxAvgSpeed: {
                    label: 'Максимальна середня швидкість',
                    description: 'Поїздки із середньою швидкістю вище цього значення будуть класифіковані як не-ходьба',
                    labelLow: '3.0 км/год (Повільно)',
                    labelMid: '5.5 км/год (Звичайно)',
                    labelHigh: '10.0 км/год (Швидко)'
                },
                maxMaxSpeed: {
                    label: 'Максимальна пікова швидкість',
                    description: 'Короткочасні сплески швидкості вище цього значення перекласифікують поїздку',
                    labelLow: '5.0 км/год (Консервативно)',
                    labelMid: '8.0 км/год (Звичайно)',
                    labelHigh: '15.0 км/год (Щедро)'
                }
            },
            bicycle: {
                title: 'Велосипед',
                subtitle: 'Опціональний тип транспорту',
                description: 'Виявляє поїздки на велосипеді (зазвичай 8-25 км/год). Порядок пріоритету забезпечує правильну класифікацію навіть за перетину швидкостей із бігом та авто.',
                minAvgSpeed: {
                    label: 'Мінімальна середня швидкість',
                    description: 'Поїздки повільніші за це значення будуть класифіковані як біг або ходьба',
                    labelLow: '5.0 км/год (Повільно)',
                    labelMid: '8.0 км/год (За замовчуванням)',
                    labelHigh: '15.0 км/год (Швидко)'
                },
                maxAvgSpeed: {
                    label: 'Максимальна середня швидкість',
                    description: 'Поїздки швидші за це значення будуть класифіковані як моторизований транспорт',
                    labelLow: '15.0 км/год (Повільно)',
                    labelMid: '25.0 км/год (За замовчуванням)',
                    labelHigh: '35.0 км/год (Електровелосипед)'
                },
                maxMaxSpeed: {
                    label: 'Максимальна пікова швидкість',
                    description: 'Враховує спуски, шосейні або електровелосипеди',
                    labelLow: '20.0 км/год (Місто)',
                    labelMid: '35.0 км/год (За замовчуванням)',
                    labelHigh: '80.0 км/год (Шосейний велосипед)'
                }
            },
            running: {
                title: 'Біг',
                subtitle: 'Опціональний тип транспорту',
                description: 'Виявляє поїздки бігом/трюхцем (зазвичай 7-14 км/год). Консервативні пороги відділяють біг від швидкої ходьби та повільної їзди на велосипеді. Якщо вимкнено, швидкості бігу фіксуються як ВЕЛОСИПЕД (якщо увімкнено) або АВТО/НЕВІДОМО.',
                minAvgSpeed: {
                    label: 'Мінімальна середня швидкість',
                    description: 'Поїздки повільніші за це значення будуть класифіковані як ходьба',
                    labelLow: '5.0 км/год (Повільно)',
                    labelMid: '7.0 км/год (За замовчуванням)',
                    labelHigh: '10.0 км/год (Швидко)'
                },
                maxAvgSpeed: {
                    label: 'Максимальна середня швидкість',
                    description: 'Поїздки швидші за це значення будуть класифіковані як велосипед або моторизований транспорт',
                    labelLow: '10.0 км/год (Повільно)',
                    labelMid: '14.0 км/год (За замовчуванням)',
                    labelHigh: '18.0 км/год (Швидко)'
                },
                maxMaxSpeed: {
                    label: 'Максимальна пікова швидкість',
                    description: 'Враховує спринтерські ділянки, залишаючись нижче швидкостей велосипеда',
                    labelLow: '12.0 км/год (Повільно)',
                    labelMid: '18.0 км/год (За замовчуванням)',
                    labelHigh: '25.0 км/год (Спринт)'
                }
            },
            car: {
                title: 'Моторний транспорт',
                subtitle: "Мітка автомобіля, мотоцикла або громадського транспорту",
                description: "Виявляє поїздки, подібні до автомобільних, за спільними порогами швидкості моторного транспорту. Громадський транспорт - це мітка для таких поїздок, а не виявлення автобуса.",
                carLabel: {
                    title: 'Мітка автомобіля',
                    description: 'Дозволити позначати виявлені поїздки моторним транспортом як автомобіль.',
                    ariaEnable: 'Увімкнути мітку автомобіля'
                },
                motorcycleLabel: {
                    title: 'Мітка мотоцикла',
                    description: 'Дозволити позначати виявлені поїздки моторним транспортом як мотоцикл.',
                    ariaEnable: 'Увімкнути мітку мотоцикла'
                },
                publicTransportLabel: {
                    title: 'Мітка громадського транспорту',
                    description: 'Дозволити позначати виявлені автомобільно-подібні поїздки як громадський транспорт.',
                    ariaEnable: 'Увімкнути мітку громадського транспорту'
                },
                preferredLabel: {
                    label: 'Пріоритетна мітка моторного транспорту',
                    description: 'Коли увімкнено кілька міток, виявлені поїздки моторним транспортом за замовчуванням використовують цю мітку.',
                    placeholder: 'Оберіть пріоритетну мітку',
                    optionCar: 'Автомобіль',
                    optionMotorcycle: 'Мотоцикл',
                    optionPublicTransport: 'Громадський транспорт'
                },
                minAvgSpeed: {
                    label: 'Мінімальна середня швидкість',
                    description: 'Поїздки із середньою швидкістю нижче цього значення будуть класифіковані як ходьба, біг або велосипед, якщо ті відповідають першими',
                    labelLow: '5.0 км/год (Чутливо)',
                    labelMid: '10.0 км/год (За замовчуванням)',
                    labelHigh: '25.0 км/год (Консервативно)'
                },
                minMaxSpeed: {
                    label: 'Мінімальна пікова швидкість',
                    description: 'Поїздки, що ніколи не досягають цієї швидкості, не будуть класифіковані як моторний транспорт, якщо не збігається середня швидкість',
                    labelLow: '10.0 км/год (Місто)',
                    labelMid: '15.0 км/год (За замовчуванням)',
                    labelHigh: '50.0 км/год (Шосе)'
                }
            },
            train: {
                title: 'Потяг',
                subtitle: 'Опціональний тип транспорту',
                description: 'Виявляє поїздки потягом за високою швидкістю з низькою дисперсією (30-150 км/год, стабільний рух). Використовує дисперсію швидкості для відрізнення від автомобілів.',
                minAvgSpeed: {
                    label: 'Мінімальна середня швидкість',
                    description: 'Відділяє від автомобілів у щільному трафіку',
                    labelLow: '20.0 км/год (Місто)',
                    labelMid: '30.0 км/год (За замовчуванням)',
                    labelHigh: '50.0 км/год (Швидко)'
                },
                maxAvgSpeed: {
                    label: 'Максимальна середня швидкість',
                    description: 'Охоплює регіональні та міжміські потяги',
                    labelLow: '80.0 км/год (Регіональний)',
                    labelMid: '150.0 км/год (За замовчуванням)',
                    labelHigh: '400.0 км/год (Швидкісний)'
                },
                minMaxSpeed: {
                    label: 'Мінімальна пікова швидкість (фільтр станцій)',
                    description: 'Відфільтровує поїздки лише з часом очікування на станції (критично важливо!)',
                    labelLow: '60.0 км/год (Поблажливо)',
                    labelMid: '80.0 км/год (За замовчуванням)',
                    labelHigh: '120.0 км/год (Суворо)'
                },
                maxMaxSpeed: {
                    label: 'Максимальна пікова швидкість',
                    description: 'Верхня межа швидкості потягів',
                    labelLow: '100.0 км/год (Регіональний)',
                    labelMid: '180.0 км/год (За замовчуванням)',
                    labelHigh: '500.0 км/год (Швидкісний)'
                },
                maxSpeedVariance: {
                    label: 'Максимальна дисперсія швидкості (ключовий розрізнювач)',
                    description: 'Потяги мають низьку дисперсію (< 15), автомобілі - високу (> 25). Це ключ до відрізнення потягів від автомобілів!',
                    labelLow: '5.0 (Суворо)',
                    labelMid: '15.0 (За замовчуванням)',
                    labelHigh: '30.0 (Поблажливо)'
                }
            },
            flight: {
                title: 'Літак',
                subtitle: 'Опціональний тип транспорту',
                description: 'Виявляє авіаперельоти за дуже високими швидкостями (400+ км/год середньої АБО 500+ км/год пікової). Логіка АБО враховує тривалий час руління/на землі. GPS-шум вище 1200 км/год автоматично відхиляється.',
                minAvgSpeed: {
                    label: 'Мінімальна середня швидкість',
                    description: 'Консервативне значення за замовчуванням для типових польотів (включаючи час руління/зльоту/посадки)',
                    labelLow: '100.0 км/год (Регіональний)',
                    labelMid: '400.0 км/год (За замовчуванням)',
                    labelHigh: '600.0 км/год (Далекомагістральний)'
                },
                minMaxSpeed: {
                    label: 'Мінімальна пікова швидкість',
                    description: 'Враховує польоти з довгим часом руління/очікування (логіка АБО із середньою швидкістю)',
                    labelLow: '150.0 км/год (Легкомоторний)',
                    labelMid: '500.0 км/год (За замовчуванням)',
                    labelHigh: '900.0 км/год (Реактивний)'
                },
                minDistance: {
                    label: 'Мінімальна відстань',
                    description: 'Поїздки, коротші за цю відстань, ніколи не класифікуються як польоти. Запобігає визначенню коротких поїздок зі стрибками GPS як польотів (0 = без обмеження)',
                    labelLow: '0 км (За замовчуванням)',
                    labelMid: '250 км',
                    labelHigh: '500 км (Суворо)'
                }
            },
            boat: {
                title: 'Човен',
                subtitle: 'Опціональний тип транспорту',
                description: 'Виявляє поїздки човном за тривалими доказами перебування на воді. Швидкість використовується лише як перевірка на правдоподібність, а не як доказ подорожі човном.',
                setup: {
                    requiredTitle: 'Потрібне налаштування човна',
                    readyTitle: 'Налаштування човна готове',
                    failedTitle: 'Налаштування човна не вдалося',
                    inProgressTitle: 'Налаштування човна триває',
                    readyPhaseText: 'Кешовані докази води доступні.',
                    docsLink: 'Документація з офлайн-налаштування',
                    retry: 'Повторити',
                    phases: {
                        BOAT_SETUP_IS_READY: 'Налаштування човна готове',
                        GPS_WATER_EVIDENCE_NEEDS_ENRICHMENT: 'GPS-докази води потребують збагачення',
                        WATER_DATASET_IS_NOT_IMPORTED: 'Набір даних про воду не імпортовано',
                        STARTING_BOAT_SETUP: 'Запуск налаштування човна',
                        BOAT_SETUP_COMPLETED: 'Налаштування човна завершено',
                        RECLASSIFYING_EXISTING_TRIPS: 'Перекласифікація наявних поїздок',
                        WATER_DATASET_READY: 'Набір даних про воду готовий',
                        PREPARING_WATER_DATASET_ARTIFACT: 'Підготовка артефакту набору даних про воду',
                        IMPORTING_WATER_POLYGONS: 'Імпорт полігонів води',
                        USING_LOCAL_WATER_DATASET_FILE: 'Використання локального файлу набору даних про воду',
                        DOWNLOADING_WATER_DATASET: 'Завантаження набору даних про воду',
                        GPS_WATER_EVIDENCE_READY: 'GPS-докази води готові',
                        CLASSIFYING_GPS_WATER_EVIDENCE: 'Класифікація GPS-доказів води',
                        WAITING_FOR_WATER_DATASET_IMPORT: 'Очікування імпорту набору даних про воду',
                        WAITING_FOR_GPS_WATER_EVIDENCE: 'Очікування GPS-доказів води',
                        BOAT_SETUP_WORKER_DID_NOT_START: 'Обробник налаштування човна не запустився',
                        WATER_DATASET_SETUP_FAILED: 'Не вдалося налаштувати набір даних про воду',
                        BOAT_SETUP_FAILED: 'Налаштування човна не вдалося',
                        fallback: 'Підготовка налаштування човна...'
                    },
                    errorFallback: 'Не вдалося налаштувати набір даних про воду. Перевірте інструкції з офлайн-налаштування.'
                },
                minWaterRatio: {
                    label: 'Мінімальне співвідношення води',
                    description: 'Частка придатної відстані поїздки, яка має бути над водою',
                    labelLow: '10% (Поблажливо)',
                    labelMid: '60% (За замовчуванням)',
                    labelHigh: '100% (Суворо)'
                },
                minWaterDistance: {
                    label: 'Мінімальна відстань над водою',
                    description: 'Загальна відстань над водою, необхідна перед класифікацією поїздки як човен',
                    labelLow: '100 м (Поблажливо)',
                    labelMid: '2000 м (За замовчуванням)',
                    labelHigh: '10000 м (Суворо)'
                },
                minContinuousWaterDistance: {
                    label: 'Мінімальна безперервна відстань над водою',
                    description: 'Відфільтровує короткі перетини мостів, тунелів та берегової лінії',
                    labelLow: '100 м (Поблажливо)',
                    labelMid: '1000 м (За замовчуванням)',
                    labelHigh: '10000 м (Суворо)'
                },
                maxPlausibleSpeed: {
                    label: 'Максимальна правдоподібна швидкість',
                    description: 'Лише межа правдоподібності; повільний рух все ще може бути подорожжю човном',
                    labelLow: '20 км/год (Суворо)',
                    labelMid: '120 км/год (За замовчуванням)',
                    labelHigh: '300 км/год (Поблажливо)'
                }
            },
            shortDistance: {
                title: 'Поріг короткої відстані поїздки',
                description: 'Поріг відстані для застосування послабленого виявлення швидкості ходьби',
                details: 'Поїздки коротші за цю відстань отримують дещо поблажливішу класифікацію швидкості ходьби, щоб врахувати неточності GPS',
                labelLow: '0.1 км (Суворо)',
                labelMid: '1.0 км (Звичайно)',
                labelHigh: '3.0 км (Поблажливо)'
            },
            arrivalDetection: {
                title: 'Тривалість виявлення прибуття',
                description: 'Мінімальний час, протягом якого GPS-точки мають бути згруповані та повільні, щоб виявити прибуття до місця призначення',
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: 'Чутливіше виявлення прибуття, може вловлювати короткі зупинки',
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: 'Консервативніше, виявляє лише стійкі прибуття',
                labelLow: '10с (Чутливо)',
                labelMid: '90с (Звичайно)',
                labelHigh: '300с (Консервативно)'
            },
            sustainedStop: {
                title: 'Тривалість стійкої зупинки',
                description: 'Мінімальний час стабільного повільного руху для виявлення реальної зупинки (фільтрує світлофори)',
                detailsLowerLabel: 'Нижчі значення',
                detailsLowerValue: 'Виявляє коротші зупинки, може включати затримки в трафіку',
                detailsHigherLabel: 'Вищі значення',
                detailsHigherValue: 'Виявляє лише довші зупинки, краще фільтрує трафік',
                labelLow: '10с (Чутливо)',
                labelMid: '60с (Звичайно)',
                labelHigh: '600с (Консервативно)'
            },
            arrivalMinPoints: {
                title: 'Мінімум точок зупинки для виявлення прибуття',
                description: 'Мінімальна кількість GPS-точок, необхідна для виявлення прибуття до місця призначення',
                detailsLowerLabel: 'Нижчі значення (2)',
                detailsLowerValue: 'Швидше виявлення, ідеально для рідкісного GPS (інтервали 10-15 хв)',
                detailsHigherLabel: 'Вищі значення (3-4)',
                detailsHigherValue: 'Надійніше, ідеально для частого GPS (інтервали 30-60 с)',
                labelLow: '2 точки (Швидко)',
                labelMid: '3 точки (Збалансовано)',
                labelHigh: '5 точок (Консервативно)'
            }
        }
    },
    reports: {
        page: {
            title: 'Звіти хронології',
            subtitle: 'Детальні таблиці ваших зупинок, поїздок і прогалин у даних'
        },
        demoNotice: 'Експорт даних вимкнено в демо-режимі.',
        exportAll: 'Експортувати все',
        exportAllTooltip: 'Експортувати зупинки, поїздки та прогалини в даних як CSV',
        demoExportDisabled: 'Експорт вимкнено в демо-режимі',
        demoExportDisabledToast: 'Експорт даних вимкнено в демо-режимі.',
        sections: {
            stays: 'Зупинки',
            trips: 'Поїздки',
            dataGaps: 'Прогалини в даних'
        },
        noDateRange: 'Діапазон дат не вибрано',
        noData: {
            title: 'Дані не знайдено',
            message: 'Для вибраного діапазону дат не знайдено даних хронології.',
            suggestion: 'Спробуйте вибрати інший діапазон дат або перевірте джерела GPS.'
        },
        fetchFailed: 'Не вдалося отримати дані хронології',
        export: {
            successSummary: 'Експорт успішний',
            failedSummary: 'Помилка експорту',
            staysDone: 'Зупинки успішно експортовано',
            staysFailed: 'Не вдалося експортувати зупинки',
            tripsDone: 'Поїздки успішно експортовано',
            tripsFailed: 'Не вдалося експортувати поїздки',
            dataGapsDone: 'Прогалини в даних успішно експортовано',
            dataGapsFailed: 'Не вдалося експортувати прогалини в даних',
            allDone: 'Усі дані успішно експортовано',
            failedGeneric: 'Не вдалося експортувати дані'
        }
    }
}
