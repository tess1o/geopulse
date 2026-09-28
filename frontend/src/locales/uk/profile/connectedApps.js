/**
 * Connected apps tab shell and panels (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * 'AI Assistant' renders as 'AI-помічник' to match the navigation label; 'Immich' and 'Memos' are
 * product names and stay as-is. 'GeoPulse' is likewise a product name.
 *
 * `ai.testConnection`, `immich.testConnection` and `memos.testConnection` are all the same English
 * sentence, kept as separate keys rather than cross-referenced so each panel stays self-contained.
 *
 * `immich.messages.assetsAvailable` and `memos.messages.memoCount` carry THREE plural forms
 * (one / few / many) -- the English catalog holds the original single form, so these two are the
 * only messages whose form count differs between the locales.
 */
export default {
    title: 'Підключені застосунки',
    description: 'Налаштуйте сервіси, які додають AI, фото та нотатки до GeoPulse.',
    listAria: 'Підключені застосунки',
    apps: {
        ai: 'AI-помічник',
        immich: 'Immich',
        memos: 'Memos'
    },
    status: {
        enabled: 'Увімкнено',
        notEnabled: 'Не увімкнено',
        connected: 'Підключено',
        notConfigured: 'Не налаштовано'
    },
    ai: {
        availability: {
            heading: 'Доступність помічника',
            description: 'Керуйте доступністю AI-чату в GeoPulse.',
            enabled: {
                title: 'Увімкнути AI-помічника',
                description: 'Дозволити чат зі штучним інтелектом і допомогу з таймлайном.'
            }
        },
        provider: {
            heading: 'Постачальник',
            description: 'Налаштуйте сервіс OpenAI або сумісний із ним сервіс, який використовує помічник.',
            apiKeyRequired: {
                title: 'Потрібен API-ключ',
                description: 'Вимикайте лише тоді, коли постачальник приймає запити без автентифікації.'
            },
            apiKey: {
                title: 'API-ключ',
                description: 'Вводьте новий ключ лише під час додавання або заміни облікових даних.',
                placeholder: 'Введіть свій API-ключ OpenAI',
                placeholderConfigured: 'API-ключ налаштовано (введіть новий ключ для заміни)',
                ariaLabel: 'API-ключ OpenAI',
                configuredNote: 'API-ключ налаштовано. Залиште поле порожнім, щоб зберегти його.',
                missingNote: 'Введіть API-ключ, щоб увімкнути автентифіковані запити.'
            },
            baseUrl: {
                title: 'Базова URL-адреса API',
                description: 'Використовуйте кінцеву точку OpenAI або інший сумісний сервіс.'
            },
            model: {
                title: 'Модель',
                description: 'Виберіть модель зі списку або введіть її ідентифікатор.',
                placeholder: 'Виберіть або введіть назву моделі',
                ariaLabel: 'Модель AI'
            },
            refreshModels: {
                ariaLabel: 'Оновити моделі постачальника',
                tooltip: 'Отримати моделі із сервера'
            }
        },
        behavior: {
            heading: 'Поведінка помічника',
            description: 'Налаштуйте інструкцію, яка надсилається з кожною розмовою.',
            systemMessage: {
                title: 'Системне повідомлення',
                description: 'Очистіть повідомлення, щоб відновити стандартне значення сервера.',
                details: 'Системне повідомлення визначає тон і поведінку помічника.',
                placeholder: 'Завантаження системного повідомлення...',
                ariaLabel: 'Системне повідомлення AI'
            }
        },
        test: {
            success: 'Зʼєднання успішне!',
            failure: 'Не вдалося зʼєднатися. Перевірте URL-адресу та API-ключ.'
        },
        testConnection: 'Перевірити зʼєднання',
        saveSettings: 'Зберегти налаштування AI'
    },
    immich: {
        availability: {
            heading: 'Інтеграція фото',
            description: 'Керуйте тим, чи зʼявляються фото з Immich на вашому таймлайні.',
            enabled: {
                title: 'Увімкнути Immich',
                description: 'Синхронізувати фото таймлайну з вашого сервера Immich.',
                ariaLabel: 'Увімкнути інтеграцію з Immich'
            }
        },
        connection: {
            heading: 'Зʼєднання',
            description: 'Укажіть адресу сервера та облікові дані для доступу до Immich.',
            serverUrl: {
                title: 'URL-адреса сервера',
                description: 'Введіть повну адресу вашого сервера Immich.',
                ariaLabel: 'URL-адреса сервера Immich'
            },
            apiKey: {
                title: 'API-ключ',
                description: 'Створіть API-ключ у налаштуваннях вашого сервера Immich.',
                placeholder: 'Введіть свій API-ключ Immich',
                placeholderConfigured: 'API-ключ встановлено (введіть новий ключ для заміни)',
                ariaLabel: 'API-ключ Immich',
                configuredNote: 'API-ключ налаштовано. Залиште поле порожнім, щоб зберегти його.'
            }
        },
        messages: {
            connected: 'Успішно зʼєднано із сервером Immich',
            userNotFound: 'Налаштованого користувача Immich не знайдено',
            apiKeyRequired: 'Потрібен API-ключ Immich',
            authenticationFailed: 'Immich відхилив API-ключ',
            serverNotFound: 'Сервер Immich не знайдено',
            connectionTimeout: 'Час очікування зʼєднання з Immich вичерпано',
            connectionFailed: 'Не вдалося зʼєднатися із сервером Immich',
            testFailed: 'Не вдалося перевірити зʼєднання',
            testError: 'Помилка перевірки зʼєднання',
            unexpected: 'Сталася непередбачена помилка',
            assetsAvailable: '{count} медіафайл доступний. | {count} медіафайли доступні. | {count} медіафайлів доступно.'
        },
        errors: {
            serverUrlRequired: 'URL-адреса сервера обовʼязкова, коли інтеграцію увімкнено',
            serverUrlInvalid: 'Будь ласка, введіть коректну URL-адресу (наприклад, https://photos.example.com)',
            apiKeyRequired: 'API-ключ обовʼязковий, коли інтеграцію увімкнено'
        },
        testConnection: 'Перевірити зʼєднання',
        reset: 'Скинути',
        saveSettings: 'Зберегти налаштування'
    },
    memos: {
        availability: {
            heading: 'Інтеграція нотаток',
            description: 'Керуйте тим, чи зʼявляються нотатки Memos з позначками часу на вашому таймлайні.',
            enabled: {
                title: 'Увімкнути Memos',
                description: 'Отримувати нотатки таймлайну з вашого сервера Memos.',
                ariaLabel: 'Увімкнути інтеграцію з Memos'
            }
        },
        connection: {
            heading: 'Зʼєднання',
            description: 'Укажіть адресу сервера та облікові дані для доступу до Memos.',
            serverUrl: {
                title: 'URL-адреса сервера',
                description: 'Введіть повну адресу вашого сервера Memos.',
                ariaLabel: 'URL-адреса сервера Memos'
            },
            apiKey: {
                title: 'API-ключ',
                description: 'Створіть API-токен у налаштуваннях Memos.',
                placeholder: 'Введіть свій API-ключ Memos',
                placeholderConfigured: 'API-ключ встановлено (введіть новий ключ для заміни)',
                ariaLabel: 'API-ключ Memos',
                configuredNote: 'API-ключ налаштовано. Залиште поле порожнім, щоб зберегти його.'
            }
        },
        defaults: {
            heading: 'Стандартні значення таймлайну',
            description: 'Виберіть, як нотатки, створені з GeoPulse, зберігаються в Memos.',
            destination: {
                title: 'Стандартне місце збереження',
                description: 'Виберіть, куди за замовчуванням зберігаються нові нотатки.',
                ariaLabel: 'Стандартне місце збереження',
                options: {
                    geopulse: 'GeoPulse',
                    memos: 'Memos'
                }
            },
            visibility: {
                title: 'Стандартна видимість',
                description: 'Виберіть початкову видимість Memos для нових нотаток.',
                ariaLabel: 'Стандартна видимість Memos',
                options: {
                    private: 'Приватна',
                    protected: 'Захищена',
                    public: 'Публічна'
                }
            }
        },
        filtering: {
            heading: 'Фільтрація та продуктивність',
            description: 'Керуйте кешованими пошуками та тим, які нотатки з тегами зʼявляються на таймлайні.',
            searchCache: {
                title: 'Кеш пошуку',
                description: 'Повторно використовувати недавні пошуки для швидшого завантаження нотаток таймлайну.',
                ariaLabel: 'Увімкнути кеш пошуку Memos'
            },
            includeTags: {
                title: 'Включити теги',
                description: 'Завантажувати лише нотатки, що містять принаймні один із цих тегів.',
                placeholder: 'Додайте тег і натисніть Enter'
            },
            excludeTags: {
                title: 'Виключити теги',
                description: 'Приховати нотатки, що містять будь-який із цих тегів.',
                placeholder: 'Додайте тег і натисніть Enter'
            }
        },
        messages: {
            connected: 'Успішно зʼєднано із сервером Memos',
            userNotFound: 'Користувача не знайдено',
            apiKeyRequired: 'Потрібен API-ключ',
            connectionFailed: 'Не вдалося зʼєднатися',
            testError: 'Помилка перевірки зʼєднання',
            memoCount: 'Сервер повернув {count} нотатку | Сервер повернув {count} нотатки | Сервер повернув {count} нотаток'
        },
        errors: {
            serverUrlRequired: 'URL-адреса сервера обовʼязкова, коли інтеграцію увімкнено',
            serverUrlInvalid: 'Будь ласка, введіть коректну URL-адресу',
            apiKeyRequired: 'API-ключ обовʼязковий, коли інтеграцію увімкнено'
        },
        testConnection: 'Перевірити зʼєднання',
        reset: 'Скинути',
        saveSettings: 'Зберегти налаштування'
    }
}
