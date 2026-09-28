/**
 * Map controls, layers, and popups. (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    controls: {
        hideFavorites: 'Приховати обране',
        showFavorites: 'Показати обране',
        hideTimeline: 'Приховати хронологію',
        showTimeline: 'Показати хронологію',
        hidePath: 'Приховати маршрут',
        showPath: 'Показати маршрут',
        heatmapTitle: 'Теплова карта',
        stays: 'Зупинки',
        trips: 'Поїздки',
        turnOff: 'Вимкнути',
        hidePhotos: 'Приховати фото',
        showPhotos: 'Показати фото',
        hideNotes: 'Приховати нотатки',
        showNotes: 'Показати нотатки',
        hideWeather: 'Приховати погоду',
        showWeather: 'Показати погоду',
        hide3dBuildings: 'Приховати 3D-будівлі',
        show3dBuildings: 'Показати 3D-будівлі',
        moreControls: 'Додаткові елементи керування картою',
        zoomToData: 'Масштабувати до даних',
        heatmapSelectDateRange: 'Виберіть діапазон дат, щоб увімкнути теплову карту',
        heatmapEnabledTitle: 'Теплову карту увімкнено',
        heatmapShow: 'Показати теплову карту',
        routeDisplayRaw: 'Показано необроблений GPS-маршрут. Натисніть, щоб порівняти зіставлений і необроблений маршрути',
        routeDisplayComparison: 'Порівняння зіставленого маршруту з необробленим GPS. Натисніть, щоб показати зіставлений маршрут',
        routeDisplayMatched: 'Показано зіставлений маршрут. Натисніть, щоб показати необроблений GPS-маршрут',
        rawGpsLoading: 'Завантаження необроблених GPS-точок',
        rawGpsHide: 'Приховати необроблені GPS-точки',
        rawGpsShow: 'Показати необроблені GPS-точки',
        panoramaxUnsupported: 'Покриття Panoramax потребує векторних карт MapLibre',
        panoramaxHide: 'Приховати покриття Panoramax',
        panoramaxShow: 'Показати покриття Panoramax',
        heatmapStaysMenu: 'Теплова карта: зупинки',
        heatmapTripsMenu: 'Теплова карта: поїздки',
        heatmapTurnOffMenu: 'Вимкнути теплову карту'
    },
    tripReplay: {
        pause: 'Призупинити відтворення',
        play: 'Відтворити',
        stop: 'Зупинити відтворення',
        setSpeed: 'Встановити швидкість {speed}x',
        followCamera: 'Слідувати камерою',
        follow: 'Слідувати',
        enable3d: 'Увімкнути 3D-камеру',
        hideControls: 'Приховати керування відтворенням',
        showControls: 'Показати керування відтворенням',
        replay: 'Відтворення'
    },
    messages: {
        engineFatal: 'Не вдалося ініціалізувати картографічний рушій. Перехід у растровий режим.',
        genericWarning: 'Попередження карти'
    },
    popups: {
        common: {
            unknown: 'Невідомо',
            unknownTime: 'Невідомий час',
            unknownLocation: 'Невідоме місце',
            notAvailable: 'н/д',
            value: 'Значення',
            telemetry: 'Телеметрія',
            duration: 'Тривалість',
            distance: 'Відстань',
            status: 'Статус',
            battery: 'Батарея',
            speed: 'Швидкість',
            accuracy: 'Точність'
        },
        favorite: {
            pendingName: 'Обране в очікуванні',
            name: 'Обране',
            pendingArea: 'Область в очікуванні',
            pendingPoint: 'Точка в очікуванні',
            areaFavorite: 'Обрана область',
            favoritePoint: 'Обрана точка',
            category: 'Категорія',
            description: 'Опис',
            address: 'Адреса'
        },
        friend: {
            defaultName: 'Друг',
            lastSeen: 'Востаннє в мережі',
            location: 'Місцезнаходження',
            activity: 'Активність',
            atCurrentPositionFor: 'На поточній позиції {duration}',
            movingFor: 'У русі {duration}',
            avatarAlt: 'Аватар {name}',
            openInGoogleMaps: 'Відкрити в Google Maps'
        },
        location: {
            lastKnown: 'Ваше останнє відоме місцезнаходження GeoPulse',
            yourLocation: 'Ваше місцезнаходження',
            lastRecorded: 'Востаннє зафіксовано {time}',
            updated: 'Оновлено {time}',
            aboutMeters: 'Приблизно {value} м',
            sharedLocation: 'Спільне місцезнаходження',
            visits: 'Відвідування',
            places: 'Місця',
            lastVisit: 'Останнє відвідування',
            openCityDetails: 'Відкрити дані міста',
            openPlaceDetails: 'Відкрити дані місця'
        },
        timeline: {
            trip: 'Поїздка ({movementType})',
            dataGap: 'Прогалина в даних',
            timelineItem: 'Елемент хронології',
            hoverHint: 'Наведіть на виділений маршрут, щоб побачити, коли ви там були і з якою швидкістю рухалися.',
            start: 'Початок',
            end: 'Кінець',
            averageSpeed: 'Середня швидкість',
            movementTrip: 'Поїздка ({movementType})',
            unknownMovement: 'Пересування',
            tripStart: 'Початок поїздки',
            tripEnd: 'Кінець поїздки',
            time: 'Час',
            mode: 'Режим',
            panoramaxCoverage: 'Покриття Panoramax',
            zoomingIn: 'Наближення для деталей…',
            photos: 'Фото',
            panoramicPhotos: 'Панорамні фото (360°)',
            flatPhotos: 'Звичайні фото',
            user: 'Користувач',
            stay: 'Зупинка',
            tripFallback: 'Поїздка',
            hoverTooltip: {
                speedPrefix: 'Швидкість: {speed}',
                exactGpsPoint: 'Точна GPS-точка',
                estimatedBetweenPoints: 'Оцінено між точками',
                fromTripStart: 'Від початку поїздки: {duration}'
            }
        },
        tripPlan: {
            plannedStop: 'Запланована зупинка',
            noDaySet: 'День не встановлено',
            notVisitedManual: 'Не відвідано (вручну)',
            visitedWithConfidence: 'Відвідано · впевненість {confidence}%',
            visited: 'Відвідано',
            notVisitedYet: 'Ще не відвідано',
            priority: 'Пріоритет',
            must: 'Обов\'язково',
            optional: 'Необов\'язково'
        },
        weather: {
            observed: 'Спостережено',
            temperature: 'Температура',
            precipitation: 'Опади',
            wind: 'Вітер'
        },
        notes: {
            loadFailed: 'Не вдалося завантажити нотатки'
        },
        placesMap: {
            defaultTitle: 'Місцезнаходження',
            defaultMarkerName: 'Вибране місце'
        },
        viewerLocation: {
            findingLocation: 'Пошук вашого місцезнаходження',
            centerOnLocation: 'Центрувати на вашому місцезнаходженні',
            showLocation: 'Показати ваше місцезнаходження',
            hideLocation: 'Приховати ваше місцезнаходження'
        },
        sharedLocation: {
            avatarAlt: 'Аватар'
        },
        rawGps: {
            coordinates: 'Координати',
            altitude: 'Висота',
            locationUnavailable: 'Місцезнаходження недоступне',
            findingLocation: 'Пошук місцезнаходження...',
            pointsHere: '{count} GPS-точок тут',
            rawGpsPoint: 'Необроблена GPS-точка',
            favoriteSource: 'Обране',
            geocodingSource: 'Геокодування',
            showingPoints: 'Показано перші {shown} з {total} точок',
            notAvailable: 'Н/Д'
        }
    }
}
