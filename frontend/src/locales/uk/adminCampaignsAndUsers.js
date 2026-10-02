/**
 * Admin pages: timeline regeneration campaigns, user details. (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    campaignsPage: {
        breadcrumb: {
            title: 'Регенерація хронології'
        },
        header: {
            title: 'Регенерація хронології',
            subtitle: 'Створюйте та відстежуйте примусові кампанії регенерації хронології'
        },
        createCampaign: 'Створити кампанію',
        labels: {
            campaign: 'Кампанія',
            source: 'Джерело',
            status: 'Статус',
            affectedFrom: 'Регенерувати з',
            progress: 'Прогрес',
            reason: 'Причина',
            affectedUsers: 'Зачеплені користувачі',
            total: 'Усього',
            pending: 'Очікує',
            running: 'Виконується',
            completed: 'Завершено',
            failed: 'Помилки',
            skipped: 'Пропущено',
            created: 'Створено',
            updated: 'Оновлено',
            actions: 'Дії',
            done: 'Готово'
        },
        table: {
            currentPageReport: 'Показано з {first} по {last} з {totalRecords} кампаній',
            empty: 'Кампаній регенерації хронології не знайдено.',
            retryTooltip: 'Повторити для помилкових користувачів',
            progressText: '{processed} / {total} оброблено',
            pendingCount: '{count} очікує',
            runningCount: '{count} виконується',
            doneCount: '{count} готово'
        },
        mobile: {
            detailsButton: 'Деталі',
            retryButton: 'Повторити'
        },
        createDialog: {
            header: 'Створити кампанію регенерації хронології',
            campaignKeyLabel: 'Ключ кампанії',
            campaignKeyPlaceholder: 'july-12-timeline-repair',
            regenerateFromLabel: 'Регенерувати з',
            regenerateFromPlaceholder: 'Виберіть дату та час відліку',
            reasonLabel: 'Причина',
            reasonPlaceholder: 'Поясніть, чому потрібно регенерувати хронології. Користувачі побачать це повідомлення.',
            previewRequired: 'Потрібен попередній перегляд',
            previewHelp: 'Попередній перегляд підраховує користувачів із GPS-даними на або після вибраної позначки часу.',
            runPreview: 'Виконати перегляд',
            reviewCreate: 'Перевірити та створити'
        },
        confirmDialog: {
            header: 'Підтвердити регенерацію хронології'
        },
        detailsDialog: {
            header: 'Деталі регенерації хронології',
            failedUsersTitle: 'Користувачі з помилками',
            retryButton: 'Повторити',
            table: {
                email: 'Ел. пошта',
                attempts: 'Спроби',
                lastError: 'Остання помилка',
                empty: 'Користувачів з помилками немає.'
            },
            close: 'Закрити'
        },
        toasts: {
            loadFailed: 'Не вдалося завантажити кампанії регенерації хронології',
            previewFailedSummary: 'Помилка перегляду',
            previewFailedDetail: 'Не вдалося отримати попередній перегляд зачеплених користувачів',
            createdSummary: 'Кампанію створено',
            createdDetail: 'Кампанію регенерації хронології створено.',
            createFailedSummary: 'Помилка створення',
            createFailedDetail: 'Не вдалося створити кампанію регенерації хронології',
            detailsLoadFailed: 'Не вдалося завантажити деталі кампанії',
            retryQueuedSummary: 'Додано в чергу повтору',
            retryQueuedDetail: 'Користувачів з помилками додано в чергу для повторної обробки.',
            retryFailedSummary: 'Помилка повтору',
            retryFailedDetail: 'Не вдалося повторити обробку користувачів кампанії'
        }
    },
    userDetailsPage: {
        title: 'Деталі користувача',
        breadcrumb: {
            users: 'Користувачі',
            loading: 'Завантаження...',
            notFound: 'Користувача не знайдено'
        },
        noName: "Без імені",
        never: 'Ніколи',
        infoCard: {
            title: 'Інформація про користувача',
            authentication: 'Автентифікація',
            password: 'Пароль',
            oidcOnly: 'Лише OIDC',
            timezone: 'Часовий пояс',
            linkedOidcProviders: "Пов'язані OIDC-провайдери"
        },
        statsCard: {
            title: 'Активність і статистика',
            gpsPoints: 'GPS-точки',
            lastGpsPoint: 'Остання GPS-точка',
            accountCreated: 'Обліковий запис створено',
            lastUpdated: 'Останнє оновлення'
        },
        actionsCard: {
            title: 'Адміністративні дії',
            demoteToUser: 'Понизити до користувача',
            promoteToAdmin: 'Підвищити до адміністратора',
            resetPassword: 'Скинути пароль',
            warningMessage: 'Ви не можете вимкнути або видалити власний обліковий запис.'
        },
        apiTokensCard: {
            title: 'API-токени',
            columnName: 'Назва',
            columnStatus: 'Статус',
            columnExpires: 'Термін дії',
            columnLastUsed: 'Останнє використання',
            columnActions: 'Дії',
            revokeTooltip: 'Відкликати токен',
            empty: 'API-токенів не знайдено.',
            status: {
                active: 'Активний',
                expired: 'Термін дії минув',
                revoked: 'Відкликано'
            }
        },
        notFound: {
            message: 'Користувача не знайдено',
            backButton: 'Назад до користувачів'
        },
        deleteDialog: {
            message: 'Ви впевнені, що хочете видалити користувача {email}?',
            detail: 'Це остаточно видалить усі його дані.'
        },
        passwordDialog: {
            header: 'Скидання пароля',
            message: 'Тимчасовий пароль для {email}:',
            copyHint: 'Поділіться цим паролем з користувачем безпечним способом.',
            close: 'Закрити'
        },
        revokeDialog: {
            header: 'Відкликати API-токен',
            message: 'Відкликати {tokenName} для {email}? Автоматизація, що використовує цей токен, негайно припиниться.',
            revokeButton: 'Відкликати'
        },
        toasts: {
            loadUserFailed: 'Не вдалося завантажити деталі користувача',
            loadTokensFailed: 'Не вдалося завантажити API-токени',
            roleChanged: 'Роль користувача змінено на {role}',
            roleChangeFailed: 'Не вдалося змінити роль користувача',
            passwordResetSuccess: 'Пароль успішно скинуто',
            passwordResetFailed: 'Не вдалося скинути пароль',
            passwordCopied: 'Пароль скопійовано в буфер обміну',
            passwordCopyFailed: 'Не вдалося скопіювати пароль у буфер обміну',
            tokenRevokedSummary: 'Відкликано',
            tokenRevokedDetail: 'API-токен відкликано',
            tokenRevokeFailed: 'Не вдалося відкликати API-токен'
        }
    }
}
