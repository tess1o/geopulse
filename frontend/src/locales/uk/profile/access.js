/**
 * Security tab's access surfaces: OIDC sign-in providers and API tokens (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * 'OIDC', 'MCP' and 'API' stay as proper nouns. 'API-токен' keeps the hyphenated form used for
 * 'AI-помічник' in the navigation catalog.
 *
 * Worth a native check: `oidc.heading` ('Connected accounts') and `oidc.linkedHeading` ('Linked
 * accounts') would both read as 'Підключені акаунти', so the second is rendered as 'Повʼязані
 * акаунти' to keep the two sections distinguishable.
 */
export default {
    oidc: {
        heading: 'Підключені акаунти',
        description: 'Керуйте соціальними або корпоративними способами входу через OIDC.',
        linkedHeading: 'Повʼязані акаунти',
        providerIconAlt: 'Іконка {provider}',
        unlink: 'Відʼєднати',
        tooltips: {
            demoDisabled: 'Вимкнено в демо-режимі',
            unlinkBlocked: 'Неможливо відʼєднати єдиний спосіб автентифікації без встановленого пароля.',
            unlink: 'Відʼєднати цей акаунт'
        },
        linkHeading: 'Повʼязати інший акаунт',
        link: 'Повʼязати',
        noPasswordWarning: 'У вас не встановлено пароль. Додайте інший спосіб входу, перш ніж відʼєднувати єдиний підключений акаунт.',
        confirm: {
            header: 'Підтвердити відʼєднання',
            message: 'Ви впевнені, що хочете відʼєднати акаунт {provider}? Цю дію неможливо скасувати.'
        },
        toasts: {
            error: 'Помилка',
            loadFailed: 'Не вдалося завантажити інформацію про підключені акаунти.',
            linkFailed: {
                title: 'Не вдалося повʼязати',
                detail: 'Не вдалося розпочати повʼязування з {provider}'
            },
            unlinked: {
                title: 'Акаунт відʼєднано',
                detail: 'Акаунт {provider} успішно відʼєднано.'
            },
            unlinkFailed: {
                title: 'Не вдалося відʼєднати',
                detail: 'Не вдалося відʼєднати акаунт'
            }
        }
    },
    apiTokens: {
        heading: 'API-токени',
        description: 'Створюйте іменовані токени для ботів, MCP-клієнтів та автоматизації.',
        create: 'Створити токен',
        columns: {
            name: 'Назва',
            status: 'Статус',
            expires: 'Термін дії',
            lastUsed: 'Останнє використання',
            actions: 'Дії'
        },
        never: 'Ніколи',
        status: {
            active: 'Активний',
            expired: 'Прострочений',
            revoked: 'Відкликаний'
        },
        tooltips: {
            edit: 'Редагувати токен',
            revoke: 'Відкликати токен'
        },
        empty: 'API-токенів ще не створено.',
        cancel: 'Скасувати',
        editDialog: {
            editHeader: 'Редагувати API-токен',
            createHeader: 'Створити API-токен',
            nameLabel: 'Назва',
            namePlaceholder: 'Токен для автоматизації',
            expirationLabel: 'Термін дії',
            expirationPlaceholder: 'Без терміну дії',
            expirationHint: 'Залиште порожнім, щоб токен не мав терміну дії.',
            save: 'Зберегти',
            create: 'Створити'
        },
        createdDialog: {
            header: 'API-токен створено',
            message: 'Цей токен показується лише один раз. Збережіть його в безпечному місці, перш ніж закрити це вікно.',
            confirm: 'Я зберіг цей токен'
        },
        revokeDialog: {
            header: 'Відкликати API-токен',
            confirmPrefix: 'Відкликати ',
            confirmSuffix: '? Автоматизація, що використовує цей токен, негайно зупиниться.',
            confirm: 'Відкликати'
        },
        validation: {
            nameRequired: 'Потрібно вказати назву токена'
        },
        toasts: {
            error: 'Помилка',
            loadFailed: 'Не вдалося завантажити API-токени',
            saveFailed: 'Не вдалося зберегти API-токен',
            revokeFailed: 'Не вдалося відкликати API-токен',
            saved: {
                title: 'Збережено',
                detail: 'API-токен оновлено'
            },
            created: {
                title: 'Створено',
                detail: 'API-токен створено'
            },
            revoked: {
                title: 'Відкликано',
                detail: 'API-токен відкликано'
            },
            copied: {
                title: 'Скопійовано',
                detail: 'Токен скопійовано до буфера обміну'
            },
            copyFailed: {
                title: 'Не вдалося скопіювати',
                detail: 'Виберіть токен і скопіюйте його вручну'
            }
        }
    }
}
