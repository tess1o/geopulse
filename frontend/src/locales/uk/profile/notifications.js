/**
 * Notifications tab (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * 'Apprise' and 'GeoPulse' stay as proper nouns. 'Rewind' is translated as 'Підсумки' (a monthly
 * recap/summary, not a literal rewind), matching the nav sidebar's translation for the same feature
 * (nav.items.rewind).
 */
export default {
    title: 'Сповіщення',
    description: 'Виберіть, які події створюють сповіщення та як вони доставляються.',
    gpsHealth: {
        heading: 'Стан GPS',
        description: 'Отримуйте сповіщення, коли всі активні джерела GPS перестають надсилати дані.',
        monitor: {
            title: 'Стежити за надходженням GPS-даних',
            description: 'Виявляти тривалі перерви в усіх активних джерелах.'
        },
        silence: {
            title: 'Поріг тиші',
            description: 'Зачекати стільки хвилин, перш ніж створити перше сповіщення.',
            details: 'Виберіть значення від 1 хвилини до 7 днів.',
            ariaLabel: 'Поріг тиші GPS у хвилинах'
        },
        channelLabel: 'Доставка сповіщень про стан GPS'
    },
    rewind: {
        heading: 'Щомісячні підсумки',
        description: 'Отримайте нагадування, коли попередній місяць буде готовий до перегляду.',
        notify: {
            title: 'Повідомляти, коли підсумки готові',
            description: 'Створювати сповіщення першого дня кожного місяця.'
        },
        channelLabel: 'Доставка підсумків'
    },
    productUpdates: {
        heading: 'Оновлення продукту',
        description: 'Керуйте оголошеннями в застосунку про нові можливості GeoPulse.',
        whatsNew: {
            title: 'Показувати огляд випуску',
            description: 'Показувати «Що нового» один раз після оновлення.',
            details: 'Огляд випуску показується лише всередині GeoPulse і ніколи не надсилається назовні.'
        }
    },
    silenceSuffix: ' хв',
    saveChanges: 'Зберегти зміни',
    channels: {
        inApp: {
            title: 'Показувати у вхідних',
            description: 'Створювати сповіщення у вхідних GeoPulse.'
        },
        apprise: {
            title: 'Надсилати через Apprise',
            description: 'Пересилати це сповіщення на налаштовані зовнішні адреси.'
        },
        routing: {
            title: 'Маршрутизація Apprise',
            description: 'Виберіть, як GeoPulse адресує призначення.',
            urls: 'Адреса(и) призначення',
            keyTag: 'Ключ і тег конфігурації Apprise'
        },
        destinationUrls: {
            title: 'Адреси призначення',
            description: 'Введіть одну або кілька адрес призначення Apprise.',
            ariaLabel: 'Адреси призначення Apprise'
        },
        configKey: {
            title: 'Ключ конфігурації',
            description: 'Назва збереженої конфігурації Apprise.',
            placeholder: 'Ключ конфігурації Apprise',
            ariaLabel: 'Ключ конфігурації Apprise'
        },
        configTag: {
            title: 'Тег конфігурації',
            description: 'Необовʼязковий тег для вибору налаштованих адрес.',
            placeholder: 'Необовʼязковий тег Apprise',
            ariaLabel: 'Тег конфігурації Apprise'
        }
    }
}
