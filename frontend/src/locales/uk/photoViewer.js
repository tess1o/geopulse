/**
 * Full-screen photo viewer dialog (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    dialogAriaLabel: 'Перегляд фото',
    fallbackName: 'Фото',
    title: {
        default: 'Перегляд фото',
        multiple: 'Фото ({current}/{total})'
    },
    counter: '{current} / {total}',
    loading: 'Завантаження фото…',
    loadError: 'Не вдалося завантажити мініатюру фото',
    close: 'Закрити',
    closeAriaLabel: 'Закрити перегляд фото',
    download: 'Завантажити оригінал',
    downloadAriaLabel: 'Завантажити оригінал фото',
    downloadOriginal: 'Завантажити оригінал',
    downloading: 'Завантаження…',
    toggleDetailsAriaLabel: 'Перемкнути деталі фото',
    showOnMap: 'Показати на карті',
    showOnMapAriaLabel: 'Показати фото на карті',
    nav: {
        previous: 'Попереднє фото',
        next: 'Наступне фото'
    },
    thumbnails: {
        scrollLeft: 'Прокрутити мініатюри ліворуч',
        scrollRight: 'Прокрутити мініатюри праворуч',
        showPhoto: 'Показати фото {index}',
        altFallback: 'Фото {index}'
    },
    mobileSummary: {
        fallbackInfo: 'Інформація про фото'
    },
    details: {
        heading: 'Деталі фото',
        closeAriaLabel: 'Закрити деталі фото',
        closeTitle: 'Закрити деталі',
        expandedAriaLabel: 'Розгорнуті деталі фото',
        file: 'Файл',
        taken: 'Знято',
        location: 'Місцезнаходження'
    },
    toasts: {
        downloadStarted: {
            summary: 'Завантаження розпочато',
            detail: 'Завантаження фото розпочалося'
        },
        downloadFailed: {
            summary: 'Помилка завантаження',
            detail: 'Не вдалося завантажити фото'
        }
    }
}
