/**
 * General tab (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * Timezone city names stay in Latin script (see the EN file): they are IANA identifiers, not prose.
 */
export default {
    title: 'Загальні',
    description: 'Керуйте своїми даними та повсякденними налаштуваннями відображення.',
    profile: {
        heading: 'Профіль',
        description: 'Виберіть імʼя та зображення, які показуються в GeoPulse.',
        fullName: {
            title: 'Повне імʼя',
            description: 'Імʼя, яке показується у вашому обліковому записі.',
            placeholder: 'Введіть повне імʼя'
        },
        image: {
            heading: 'Зображення профілю',
            optional: 'Необовʼязково',
            description: 'Налаштуйте зображення, яке використовується для маркерів на карті.',
            upload: 'Завантажити власний аватар',
            formats: 'PNG, JPEG або WEBP; автоматично зменшується перед завантаженням.',
            selected: 'Вибрано: {name}',
            chooseAria: 'Вибрати зображення профілю {index}'
        }
    },
    regional: {
        heading: 'Регіональні налаштування',
        description: 'Керуйте часовим поясом, форматами та одиницями вимірювання.',
        timezone: {
            title: 'Часовий пояс',
            description: 'Використовується для дат, часу та статистики.',
            placeholder: 'Виберіть свій часовий пояс'
        },
        dateFormat: {
            title: 'Формат дати',
            description: 'Виберіть, як показувати числові дати.',
            details: 'Параметри дат в URL і далі використовують стабільний формат ISO.',
            placeholder: 'Виберіть бажаний формат дати'
        },
        timeFormat: {
            title: 'Формат часу',
            description: 'Виберіть 12- або 24-годинний формат часу.',
            placeholder: 'Виберіть бажаний формат часу'
        },
        distanceUnit: {
            title: 'Одиниця відстані',
            description: 'Керує відображенням відстаней і швидкостей.',
            placeholder: 'Виберіть одиницю відстані'
        },
        temperatureUnit: {
            title: 'Одиниця температури',
            description: 'Керує температурами, що показуються в погодних поданнях.',
            placeholder: 'Виберіть одиницю температури'
        }
    },
    navigation: {
        heading: 'Навігація',
        description: 'Виберіть, де GeoPulse відкривається після входу.',
        homePage: {
            title: 'Головна сторінка за замовчуванням',
            description: 'Виберіть першу сторінку, яка показується після входу.',
            details: 'Очистіть вибір, щоб використати типову поведінку GeoPulse. Власні адреси мають бути внутрішніми шляхами, що починаються з /.',
            placeholder: 'Виберіть свою типову сторінку',
            customOption: 'Власна адреса...',
            customLabel: 'Власний внутрішній шлях',
            customPlaceholder: '/app/your-custom-page'
        }
    },
    dateFormatOptions: {
        dmy: 'DD/MM/YYYY (європейський)',
        mdy: 'MM/DD/YYYY (США)',
        ymd: 'YYYY-MM-DD (ISO)'
    },
    timeFormatOptions: {
        h24: '24-годинний (13:45)',
        h12: '12-годинний (1:45 PM)'
    },
    distanceUnitOptions: {
        kilometers: 'Кілометри (км, м)',
        miles: 'Милі (миль, фут)'
    },
    temperatureUnitOptions: {
        celsius: 'Цельсій (°C)',
        fahrenheit: 'Фаренгейт (°F)'
    },
    actions: {
        reset: 'Скинути',
        save: 'Зберегти зміни'
    },
    errors: {
        fullNameRequired: 'Повне імʼя обовʼязкове',
        fullNameTooShort: 'Повне імʼя має містити щонайменше 2 символи',
        customUrlRequired: 'Власна адреса обовʼязкова',
        customUrlInternal: 'Адреса має бути внутрішнім шляхом, що починається з /',
        customUrlInvalid: 'Недопустимий формат адреси',
        customUrlTooLong: 'Адреса занадто довга (максимум 1000 символів)',
        imageLoad: 'Не вдалося завантажити зображення',
        imageTooLarge: 'Зображення занадто велике після стиснення. Спробуйте інше зображення.',
        imageFormat: 'Непідтримуваний формат зображення. Використовуйте PNG, JPEG або WEBP.',
        imageUnsupported: 'Обробка зображень не підтримується в цьому браузері',
        imageProcess: 'Не вдалося обробити вибране зображення',
        avatarTooLarge: 'Аватар занадто великий після стиснення'
    }
}
