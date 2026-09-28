/**
 * Admin pages: audit logs, user invitations, OIDC providers management. (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    auditLogsPage: {
        title: 'Журнал аудиту',
        subtitle: 'Перегляд дій адміністраторів та системних змін',
        filters: {
            dateRangeLabel: 'Діапазон дат',
            dateRangePlaceholder: 'Виберіть діапазон дат',
            actionTypeLabel: 'Тип дії',
            actionTypePlaceholder: 'Усі дії',
            targetTypeLabel: 'Тип об\'єкта',
            targetTypePlaceholder: 'Усі об\'єкти',
            adminEmailLabel: 'Email адміністратора',
            adminEmailPlaceholder: 'Фільтр за email адміністратора...',
            clearFilters: 'Очистити фільтри'
        },
        actionTypes: {
            settingChanged: 'Налаштування змінено',
            settingReset: 'Налаштування скинуто',
            userEnabled: 'Користувача увімкнено',
            userDisabled: 'Користувача вимкнено',
            userDeleted: 'Користувача видалено',
            userRoleChanged: 'Роль користувача змінено',
            userPasswordReset: 'Пароль користувача скинуто',
            oidcProviderCreated: 'OIDC-провайдера створено',
            oidcProviderUpdated: 'OIDC-провайдера оновлено',
            oidcProviderDeleted: 'OIDC-провайдера видалено',
            oidcProviderReset: 'OIDC-провайдера скинуто',
            invitationCreated: 'Запрошення створено',
            invitationRevoked: 'Запрошення відкликано',
            timelineRegenerationCampaignCreated: 'Кампанію регенерації хронології створено',
            timelineRegenerationCampaignRetried: 'Кампанію регенерації хронології повторено',
            adminLogin: 'Вхід адміністратора'
        },
        targetTypes: {
            setting: 'Налаштування',
            user: 'Користувач',
            oidcProvider: 'OIDC-провайдер',
            invitation: 'Запрошення',
            timelineRegenerationCampaign: 'Кампанія регенерації хронології'
        },
        table: {
            currentPageReport: 'Показано {first}–{last} з {totalRecords} записів журналу',
            columns: {
                timestamp: 'Час',
                admin: 'Адміністратор',
                action: 'Дія',
                targetType: 'Тип об\'єкта',
                targetId: 'ID об\'єкта',
                ipAddress: 'IP-адреса'
            },
            empty: 'Записів журналу не знайдено.'
        },
        expansion: {
            detailsHeader: 'Деталі',
            noDetails: 'Додаткові деталі відсутні',
            adminUserId: 'ID адміністратора:',
            timestampLabel: 'Час:'
        },
        mobile: {
            targetId: 'ID об\'єкта:',
            ipAddress: 'IP-адреса:',
            detailsLabel: 'Деталі'
        },
        toasts: {
            loadFailedDetail: 'Не вдалося завантажити журнал аудиту'
        }
    },
    invitationsPage: {
        title: 'Запрошення користувачів',
        subtitle: 'Керування посиланнями для запрошення користувачів',
        breadcrumb: 'Запрошення',
        createInvitation: 'Створити запрошення',
        statusFilterPlaceholder: 'Фільтр за статусом',
        statuses: {
            all: 'Усі статуси',
            pending: 'Очікує',
            used: 'Використано',
            expired: 'Прострочено',
            revoked: 'Відкликано'
        },
        usedByDeletedUser: 'Видалений користувач',
        table: {
            currentPageReport: 'Показано {first}–{last} з {totalRecords} запрошень',
            columns: {
                token: 'Токен',
                createdBy: 'Створив(ла)',
                created: 'Створено',
                expires: 'Спливає',
                status: 'Статус',
                usedBy: 'Використав(ла)',
                actions: 'Дії'
            },
            empty: 'Запрошень не знайдено.',
            copyLinkTooltip: 'Копіювати посилання',
            revokeTooltip: 'Відкликати запрошення'
        },
        mobile: {
            usedByPrefix: 'Використав(ла): {value}',
            copyLink: 'Копіювати посилання',
            revoke: 'Відкликати'
        },
        createDialog: {
            expirationDateLabel: 'Дата закінчення терміну дії',
            expirationDatePlaceholder: 'Виберіть дату закінчення терміну дії'
        },
        linkDialog: {
            header: 'Посилання на запрошення створено',
            shareText: 'Поділіться цим посиланням із користувачем, якого хочете запросити:',
            copyTooltip: 'Копіювати в буфер обміну',
            expiresLabel: 'Спливає: {date}',
            message: 'Це посилання можна використати лише один раз, і воно спливе у вказану вище дату.',
            close: 'Закрити'
        },
        revokeDialog: {
            header: 'Підтвердити відкликання',
            message: 'Ви впевнені, що хочете відкликати це запрошення?',
            note: 'Посилання на запрошення більше не можна буде використати.',
            confirm: 'Відкликати'
        },
        toasts: {
            loadFailedDetail: 'Не вдалося завантажити запрошення',
            createdDetail: 'Запрошення успішно створено',
            createFailedFallback: 'Не вдалося створити запрошення',
            copiedDetail: 'Посилання скопійовано в буфер обміну',
            copyFailedDetail: 'Не вдалося скопіювати в буфер обміну',
            revokedDetail: 'Запрошення успішно відкликано',
            revokeFailedFallback: 'Не вдалося відкликати запрошення'
        }
    },
    oidcProvidersPage: {
        title: 'OIDC-провайдери',
        subtitle: 'Керування провайдерами автентифікації OAuth/OIDC',
        addProvider: 'Додати провайдера',
        table: {
            headerTitle: 'Налаштовані провайдери',
            columns: {
                name: 'Назва',
                displayName: 'Назва для відображення',
                enabled: 'Увімкнено',
                source: 'Джерело',
                metadata: 'Метадані',
                clientId: 'Client ID',
                actions: 'Дії'
            },
            enabledYes: 'Так',
            enabledNo: 'Ні',
            sourceEnvironment: 'Середовище',
            sourceCustom: 'Власне',
            metadataCached: 'Кешовано',
            metadataNotCached: 'Не кешовано',
            empty: 'OIDC-провайдерів не налаштовано.',
            editTooltip: 'Редагувати провайдера',
            disableTooltip: 'Вимкнути провайдера',
            enableTooltip: 'Увімкнути провайдера',
            testTooltip: 'Перевірити з\'єднання',
            cannotDeleteTooltip: 'Провайдерів, визначених лише через змінні середовища, видалити не можна. Приберіть їх зі змінних середовища, щоб видалити.',
            revertTooltip: 'Видалити власну конфігурацію та повернутися до значень середовища',
            deleteTooltip: 'Остаточно видалити цього власного провайдера'
        },
        mobile: {
            enabledBadge: 'Увімкнено',
            disabledBadge: 'Вимкнено',
            edit: 'Редагувати',
            disable: 'Вимкнути',
            enable: 'Увімкнути',
            test: 'Перевірити',
            delete: 'Видалити'
        },
        deleteDialog: {
            header: 'Підтвердити видалення',
            confirmMessage: 'Ви впевнені, що хочете видалити провайдера {name}?',
            envNoteLabel: 'ℹ️ Примітка:',
            envNoteText: 'Цей провайдер також визначений у змінних середовища.',
            envNoteDetail: 'Видалення прибере власну конфігурацію бази даних і поверне значення середовища за замовчуванням.',
            customWarningLabel: '⚠️ Увага:',
            customWarningText: 'Це власний провайдер. Видалення є остаточним.',
            revertButton: 'Повернутися до середовища',
            deleteButton: 'Видалити'
        },
        testResultDialog: {
            header: 'Результат перевірки провайдера',
            successMessage: 'Успішно підключено до OIDC-провайдера',
            endpointsTitle: 'Виявлені кінцеві точки:',
            authorization: 'Авторизація:',
            token: 'Токен:',
            userinfo: 'UserInfo:',
            jwks: 'JWKS:',
            issuer: 'Емітент:',
            errorDetailsTitle: 'Деталі помилки:',
            errorType: 'Тип:',
            errorDetailsLabel: 'Деталі:',
            close: 'Закрити'
        },
        toasts: {
            loadFailedDetail: 'Не вдалося завантажити OIDC-провайдерів',
            updatedDetail: 'Провайдера успішно оновлено',
            createdDetail: 'Провайдера успішно створено',
            saveFailedFallback: 'Не вдалося зберегти провайдера',
            revertedDetail: 'Власну конфігурацію прибрано. Провайдер повернувся до значень середовища за замовчуванням.',
            deletedDetail: 'Провайдера успішно видалено',
            deleteFailedFallback: 'Не вдалося видалити провайдера',
            connectionSuccessSummary: 'З\'єднання успішне',
            connectionSuccessDetail: 'З\'єднання з провайдером успішно перевірено',
            connectionFailedSummary: 'З\'єднання не вдалося',
            connectionFailedDetail: 'Не вдалося підключитися до провайдера',
            testErrorDetail: 'Не вдалося перевірити з\'єднання з провайдером',
            statusEnabledDetail: 'Провайдера успішно увімкнено',
            statusDisabledDetail: 'Провайдера успішно вимкнено',
            updateStatusFailedFallback: 'Не вдалося оновити статус провайдера'
        }
    }
}
