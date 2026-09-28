/**
 * Personal settings page (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * Worth a native check in particular: `groups.experience` ('Experience') has no direct Ukrainian
 * noun in a settings context -- 'Взаємодія' is used here rather than a literal 'Досвід', which would
 * read as "experience" in the sense of accumulated practice.
 */
export default {
    page: {
        title: 'Персональні налаштування',
        description: 'Керуйте своїм обліковим записом, налаштуваннями та підключеними застосунками',
        signedInAs: 'Ви увійшли як {email}'
    },
    searchPlaceholder: 'Пошук налаштувань профілю...',
    sectionsAria: 'Розділи персональних налаштувань',
    mobileSectionLabel: 'Розділ налаштувань',
    demoReadOnly: 'Демо-режим: профіль, безпека, відображення, AI, Immich і Memos доступні лише для читання. Зміни неможливо зберегти в цьому демо.',
    demoReadOnlyToast: 'Зміни профілю вимкнено в демо-режимі.',
    groups: {
        personal: 'Особисте',
        experience: 'Взаємодія',
        connectedApps: 'Підключені застосунки'
    },
    tabs: {
        general: 'Загальні',
        security: 'Безпека',
        timeline: 'Хронологія та карта',
        notifications: 'Сповіщення',
        connectedApps: 'Підключені застосунки'
    },
    jump: {
        notVisibleTitle: 'Налаштування не видно',
        notVisibleDetail: 'Це налаштування зараз не відображається. Увімкніть повʼязані опції, щоб змінити його.'
    },
    unsaved: {
        header: 'Незбережені зміни',
        message: 'У вас є незбережені зміни профілю. Якщо ви залишите цю сторінку, їх буде втрачено.',
        leave: 'Вийти без збереження',
        stay: 'Залишитися'
    },
    save: {
        updateFailed: 'Не вдалося оновити',
        saveFailed: 'Не вдалося зберегти',
        notificationsSaved: 'Налаштування сповіщень збережено',
        profileUpdated: {
            title: 'Профіль оновлено',
            detail: 'Ваш профіль успішно оновлено'
        },
        displayUpdated: {
            title: 'Налаштування відображення оновлено',
            detail: 'Ваші налаштування відображення хронології збережено. Зміни видно одразу.'
        },
        passwordChanged: {
            title: 'Пароль змінено',
            detail: 'Ваш пароль успішно змінено'
        },
        passwordSet: {
            title: 'Пароль встановлено',
            detail: 'Ваш пароль успішно встановлено'
        },
        passwordChangeFailed: 'Не вдалося змінити пароль',
        passwordSetFailed: 'Не вдалося встановити пароль',
        aiSaved: {
            title: 'Успішно',
            detail: 'Налаштування AI збережено'
        },
        aiError: 'Помилка',
        immichUpdated: {
            title: 'Налаштування Immich оновлено',
            detail: 'Ваші налаштування інтеграції з Immich успішно збережено'
        },
        memosUpdated: {
            title: 'Налаштування Memos оновлено',
            detail: 'Ваші налаштування інтеграції з Memos успішно збережено'
        }
    },
    errors: {
        incorrectPassword: 'Поточний пароль неправильний',
        checkInformation: 'Перевірте введені дані та спробуйте ще раз'
    },
    cached: {
        title: 'Використано кешовані дані',
        detail: 'Не вдалося отримати актуальні дані профілю. Показано кешовану інформацію.'
    }
}
