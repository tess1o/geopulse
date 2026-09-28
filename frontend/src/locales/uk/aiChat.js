/**
 * AI Chat (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    page: {
        title: 'AI-асистент чату',
        description: 'Ставте запитання про свої дані про місцезнаходження та отримуйте розумну аналітику',
        disclaimer: 'Відповіді ШІ базуються на аналізі даних, але можуть містити помилки. Будь ласка, перевіряйте важливу інформацію самостійно.',
        clearHistoryTooltip: 'Очистити історію розмови',
        clearHistoryAriaLabel: 'Очистити історію розмови'
    },
    status: {
        checking: 'Перевірка статусу ШІ...',
        unavailable: {
            title: 'Чат з ШІ недоступний',
            disabled: 'ШІ-асистент наразі вимкнено.',
            apiKeyMissing: 'Ключ API OpenAI не налаштовано.',
            notConfigured: 'ШІ-асистент налаштовано неправильно.',
            configureButton: 'Налаштувати ШІ',
            demoDisabledTooltip: 'Налаштування ШІ доступні лише для читання в демо-режимі',
            demoDisabledText: 'Налаштування ШІ доступні лише для читання в демо-режимі, тому ця дія налаштування вимкнена.'
        }
    },
    examples: {
        startTitle: 'Почніть розмову',
        startDescription: 'Запитайте мене про свої дані про місцезнаходження. Наприклад:',
        expiredTitle: 'Готово до нової розмови!',
        expiredDescription: 'Термін дії вашої попередньої розмови закінчився. Запитайте мене про свої дані про місцезнаходження:',
        walkOrDrive: 'Я більше ходжу пішки чи їжджу?',
        busiestDay: 'У який день тижня я найбільше подорожую?',
        citiesVisited: 'Скільки різних міст я відвідав цього місяця?',
        commonRoute: 'Який мій найпоширеніший маршрут?'
    },
    message: {
        error: 'Помилка'
    },
    loading: {
        title: 'ШІ думає...',
        subtitle: 'Аналізуємо ваш запит і дані про місцезнаходження'
    },
    input: {
        placeholder: 'Запитайте мене про свої дані про місцезнаходження...',
        placeholderUnavailable: 'ШІ-асистент недоступний',
        warningDisabled: 'ШІ-асистент вимкнено.',
        warningApiKeyMissing: 'Ключ API не налаштовано.',
        warningUnavailable: 'ШІ-асистент недоступний.'
    },
    timestamp: {
        justNow: 'Щойно',
        minutesAgo: '{count} хв тому',
        yesterday: 'Вчора {time}'
    },
    toasts: {
        chatClearedSummary: 'Чат очищено',
        chatClearedDetail: 'Історію вашої розмови очищено.',
        chatErrorSummary: 'Помилка чату',
        genericError: 'На жаль, під час обробки вашого запиту сталася помилка.',
        aiDisabledError: 'ШІ-асистент вимкнено. Увімкніть його в налаштуваннях профілю.',
        apiKeyRequiredError: 'Додайте ключ API вашого провайдера ШІ в налаштуваннях профілю.',
        contextTooLargeError: 'Розмова або дані завеликі. Спробуйте вужче запитання або очистіть історію чату.',
        rateLimitedError: 'Досягнуто ліміту запитів провайдера ШІ. Спробуйте пізніше.',
        authFailedError: 'Провайдер ШІ відхилив налаштовані облікові дані.',
        invalidRequestError: 'Провайдер ШІ відхилив цей запит.',
        unavailableSummary: 'Чат з ШІ недоступний',
        unavailableDemoDetail: 'Налаштування ШІ доступні лише для читання в демо-режимі, тому їх не можна налаштувати з цього демо-акаунту.',
        unavailableDisabledDetail: 'ШІ-асистент вимкнено. Увімкніть його в налаштуваннях профілю.',
        unavailableApiKeyDetail: 'Будь ласка, налаштуйте свій ключ API OpenAI в профілі, щоб використовувати чат-асистент.',
        unavailableDefaultDetail: 'Будь ласка, налаштуйте свої налаштування ШІ в профілі, щоб використовувати чат-асистент.'
    }
}
