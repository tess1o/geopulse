/**
 * Friends: live map, timeline, invitations, and management tables. (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    sentInvites: {
        header: 'Надіслані запрошення',
        loading: 'Завантаження надісланих запрошень...',
        sentAgo: 'Надіслано {time}',
        pending: 'Очікує',
        cancelTooltip: 'Скасувати запрошення',
        cancelAllPending: 'Скасувати всі очікувані',
        emptyTitle: 'Немає запрошень, що очікують',
        emptyMessage: "Коли ви надсилаєте запити на дружбу, вони з'являться тут, поки не будуть прийняті або відхилені.",
        cancelDialog: {
            header: 'Скасувати запрошення',
            message: 'Скасувати запрошення для {name}?',
            note: 'Цю дію неможливо скасувати. Ви можете надіслати нове запрошення пізніше.',
            keep: 'Залишити запрошення',
            confirm: 'Скасувати запрошення'
        }
    },
    receivedInvites: {
        header: 'Отримані запрошення',
        loading: 'Завантаження отриманих запрошень...',
        wantsToConnect: 'Хоче з вами зв\'язатися',
        acceptTooltip: 'Прийняти запрошення',
        declineTooltip: 'Відхилити запрошення',
        acceptAll: 'Прийняти всі',
        declineAll: 'Відхилити всі',
        emptyTitle: 'Немає запрошень, що очікують',
        emptyMessage: 'Запити на дружбу від інших користувачів з\'являтимуться тут. Поділіться своїм іменем користувача, щоб отримувати запрошення!',
        acceptDialog: {
            header: 'Прийняти запит на дружбу',
            message: 'Додати {name} у друзі?',
            note: 'Ви зможете бачити місцезнаходження одне одного на карті.',
            confirm: 'Додати друга'
        },
        rejectDialog: {
            header: 'Відхилити запит на дружбу',
            message: 'Відхилити запит на дружбу від {name}?',
            note: 'Цю особу не буде повідомлено, але вона може надіслати новий запит пізніше.',
            keep: 'Залишити запит',
            confirm: 'Відхилити'
        },
        bulkDialog: {
            acceptHeader: 'Прийняти всі запити',
            declineHeader: 'Відхилити всі запити',
            message: '{action} усі {count} запитів на дружбу, що очікують?',
            acceptAction: 'Прийняти',
            declineAction: 'Відхилити',
            acceptNote: 'Усі ці користувачі стануть вашими друзями і бачитимуть ваше місцезнаходження.',
            declineNote: 'Усі запити, що очікують, буде відхилено. Користувачі можуть надіслати нові запити пізніше.'
        },
        relativeTime: {
            daysAgo: '{count} день тому | {count} дні тому | {count} днів тому',
            hoursAgo: '{count} годину тому | {count} години тому | {count} годин тому',
            minutesAgo: '{count} хвилину тому | {count} хвилини тому | {count} хвилин тому',
            justNow: 'щойно',
            recently: 'нещодавно'
        }
    },
    timelineTab: {
        loading: 'Завантаження хронологій...',
        selectFriends: 'Виберіть друзів, щоб переглянути їхні хронології',
        noSharedTitle: 'Немає спільних хронологій',
        noSharedMessage: 'Жоден з ваших друзів ще не увімкнув спільний доступ до хронології. Попросіть їх увімкнути це в налаштуваннях друзів!',
        loadFailedTitle: 'Не вдалося завантажити хронології',
        loadFailedDetail: 'Не вдалося завантажити хронології друзів'
    },
    mergedList: {
        header: 'Елементи хронології',
        loading: 'Завантаження елементів хронології...',
        empty: 'Немає даних хронології для вибраного діапазону дат',
        noDataFor: 'Немає даних за {duration}',
        loadMore: 'Завантажити більше',
        showingCount: 'Показано {shown} з {total} елементів'
    },
    locationTab: {
        liveLocation: 'Наживо',
        timelineHistory: 'Історія хронології'
    },
    selection: {
        title: 'Вибрати друзів',
        youLabel: '(Ви)'
    },
    invite: {
        header: 'Запросити друга',
        subtitle: 'Надіслати запит на дружбу',
        nameLabel: "Ім'я друга",
        submit: 'Запросити!'
    },
    list: {
        header: 'Друзі',
        inviteFriend: 'Запросити друга',
        columns: {
            friendName: "Ім'я друга",
            lastSeen: 'Востаннє в мережі',
            lastLocation: 'Останнє місцезнаходження',
            remove: 'Видалити'
        },
        removeTooltip: 'Видалити друга',
        empty: 'Друзів поки немає. Запросіть свого першого друга, щоб почати ділитися місцезнаходженням!',
        removeDialog: {
            header: 'Видалити друга',
            message: 'Видалити {name} зі списку друзів?',
            note: 'Ви більше не бачитимете місцезнаходження одне одного. За потреби ви можете надіслати новий запит на дружбу пізніше.',
            confirm: 'Видалити друга'
        }
    },
    datePicker: {
        rangeLabel: 'Діапазон:',
        placeholder: 'Виберіть діапазон дат',
        presetPlaceholder: 'Швидкі пресети',
        today: 'Сьогодні',
        yesterday: 'Вчора'
    },
    filters: {
        all: 'Усі',
        none: 'Жодного',
        online: 'Онлайн',
        onlineNow: 'Зараз онлайн',
        lastSeenRecently: 'Був(ла) нещодавно'
    },
    demo: {
        invitationsTooltip: 'Запрошення вимкнено в демо-режимі'
    },
    listTab: {
        empty: {
            title: 'Ще немає друзів',
            description: 'Почніть створювати свою мережу, запрошуючи друзів для спілкування та обміну місцезнаходженням',
            demoDisabled: 'Запрошення друзів вимкнено в демо-режимі.',
            inviteFirst: 'Запросити першого друга'
        },
        lastSeenLabel: 'Востаннє в мережі: {text}',
        sharesWithYou: 'Що цей друг ділиться з вами:',
        sharesWithFriend: 'Що ви ділитеся з цим другом:',
        liveLocation: 'Місцезнаходження наживо',
        timelineHistory: 'Історія хронології',
        shared: 'Надається',
        notShared: 'Не надається',
        demoPermissionsReadOnly: 'Дозволи для спільного доступу доступні лише для читання в демо-режимі.',
        liveLocationInfo: 'Дозволяє цьому другу бачити ваше поточне місцезнаходження в реальному часі',
        timelineHistoryInfo: 'Дозволяє цьому другу переглядати повну історію вашого місцезнаходження',
        actions: {
            live: 'Наживо',
            liveTooltip: 'Показати друга на карті наживо',
            timeline: 'Хронологія',
            timelineTooltip: 'Переглянути історію хронології друга',
            removeTooltip: 'Видалити друга',
            removeTooltipDemo: 'Видалення друзів вимкнено в демо-режимі'
        },
        status: {
            noLocation: 'Немає місцезнаходження',
            online: 'Онлайн',
            recent: 'Нещодавно',
            offline: 'Офлайн'
        },
        lastSeenText: {
            never: 'Ніколи',
            justNow: 'Щойно',
            minutesAgo: '{count}хв тому',
            hoursAgo: '{count}год тому',
            daysAgo: '{count}д тому'
        },
        permissionDialog: {
            header: 'Підтвердити зміну дозволу',
            timelineAllow: 'Це дозволить {name} переглядати повну історію вашого місцезнаходження (усі минулі зупинки та поїздки). Продовжити?',
            timelineRevoke: 'Це скасує доступ {name} до історії вашого місцезнаходження. Продовжити?',
            liveAllow: 'Це дозволить {name} бачити ваше поточне місцезнаходження в реальному часі. Продовжити?',
            liveRevoke: 'Це скасує доступ {name} до вашого місцезнаходження наживо. Продовжити?'
        },
        permissionToast: {
            updatedSummary: 'Дозвіл оновлено',
            timelineGranted: '{name} тепер може переглядати історію вашої хронології',
            timelineRevoked: '{name} більше не може переглядати історію вашої хронології',
            liveGranted: '{name} тепер може переглядати ваше місцезнаходження наживо',
            liveRevoked: '{name} більше не може переглядати ваше місцезнаходження наживо',
            failedSummary: 'Не вдалося оновити дозвіл',
            timelineFailedDetail: 'Не вдалося оновити дозволи друга',
            liveFailedDetail: 'Не вдалося оновити дозвіл на місцезнаходження наживо'
        }
    },
    mapTab: {
        loading: 'Завантаження карти друзів...',
        noFriends: {
            title: 'Немає друзів для показу',
            description: 'Додайте друзів, щоб бачити їхнє місцезнаходження на карті',
            demoDisabled: 'Запрошення друзів вимкнено в демо-режимі.',
            invite: 'Запросити друзів'
        },
        noLocation: {
            title: 'Немає даних про місцезнаходження',
            description: 'Ваші друзі ще не поділилися своїм місцезнаходженням. Це може бути через те, що вони вимкнули спільний доступ до місцезнаходження або не використовували додатки відстеження місцезнаходження.',
            demoDisabled: 'Запрошення додаткових друзів вимкнено в демо-режимі.',
            refresh: 'Оновити',
            inviteMore: 'Запросити більше друзів'
        },
        noSelection: {
            title: 'Не вибрано жодного друга',
            description: 'Виберіть принаймні одного друга у фільтрі, щоб показати місцезнаходження на карті.',
            showAll: 'Показати всіх друзів'
        }
    },
    invitationsTab: {
        demoDisabled: 'Дії із запрошеннями вимкнено в демо-режимі.',
        received: {
            header: 'Отримані запрошення',
            acceptAll: 'Прийняти всі',
            rejectAll: 'Відхилити всі'
        },
        sent: {
            header: 'Надіслані запрошення',
            cancelAll: 'Скасувати всі',
            pending: 'Очікує'
        },
        actions: {
            accept: 'Прийняти',
            reject: 'Відхилити',
            cancel: 'Скасувати'
        },
        empty: {
            title: 'Немає запрошень, що очікують',
            description: 'Усі ваші запрошення оброблено'
        }
    },
    page: {
        demoReadOnlyMessage: 'Демо-режим: запрошення друзів, зміна дозволів і видалення друзів вимкнено. Наявні спільні демо-місцезнаходження залишаються доступними для перегляду.',
        demoChangesDisabled: 'Зміни друзів вимкнено в демо-режимі.',
        inviteDialog: {
            header: 'Запросити друга',
            emailLabel: "Електронна адреса або ім'я друга",
            emailPlaceholder: 'Введіть електронну адресу або знайдіть користувачів',
            send: 'Надіслати запрошення'
        },
        tabs: {
            live: 'Наживо',
            timeline: 'Хронологія',
            friends: 'Друзі',
            invitations: 'Запрошення'
        },
        validation: {
            emailRequired: "Електронна адреса є обов'язковою",
            emailInvalid: 'Будь ласка, введіть дійсну електронну адресу'
        },
        toasts: {
            invitationSentSummary: 'Запрошення надіслано',
            invitationSentDetail: 'Запит на дружбу надіслано до {email}',
            invitationFailedSummary: 'Помилка запрошення',
            invitationFailedDetail: 'Не вдалося надіслати запрошення',
            removeFriendConfirm: 'Ви впевнені, що хочете видалити {name} зі списку друзів?',
            removeFriendHeader: 'Видалити друга',
            removeFriendConfirmLabel: 'Видалити',
            friendRemovedSummary: 'Друга видалено',
            friendRemovedDetail: 'Друга видалено з вашого списку',
            removeFailedSummary: 'Помилка видалення',
            removeFailedDetail: 'Не вдалося видалити друга',
            acceptedSummary: 'Запрошення прийнято',
            acceptedDetail: 'Тепер ви друзі!',
            acceptFailedSummary: 'Помилка прийняття',
            acceptFailedDetail: 'Не вдалося прийняти запрошення',
            rejectedSummary: 'Запрошення відхилено',
            rejectedDetail: 'Запрошення було відхилено',
            rejectFailedSummary: 'Помилка відхилення',
            rejectFailedDetail: 'Не вдалося відхилити запрошення',
            cancelledSummary: 'Запрошення скасовано',
            cancelledDetail: 'Запрошення було скасовано',
            cancelFailedSummary: 'Помилка скасування',
            cancelFailedDetail: 'Не вдалося скасувати запрошення',
            allAcceptedSummary: 'Усі запрошення прийнято',
            allAcceptedDetail: 'Прийнято запрошень: {count}',
            bulkAcceptFailedSummary: 'Помилка масового прийняття',
            bulkAcceptFailedDetail: 'Не вдалося прийняти всі запрошення',
            allRejectedSummary: 'Усі запрошення відхилено',
            allRejectedDetail: 'Відхилено запрошень: {count}',
            bulkRejectFailedSummary: 'Помилка масового відхилення',
            bulkRejectFailedDetail: 'Не вдалося відхилити всі запрошення',
            allCancelledSummary: 'Усі запрошення скасовано',
            allCancelledDetail: 'Скасовано запрошень: {count}',
            bulkCancelFailedSummary: 'Помилка масового скасування',
            bulkCancelFailedDetail: 'Не вдалося скасувати всі запрошення',
            trailsEnabledSummary: 'Сліди місцезнаходження увімкнено',
            trailsEnabledDetailWithPoints: 'Показано {points} точок у {trailCount} слідах друзів за {range}',
            trailsEnabledDetailEmpty: 'Точок сліду не знайдено за {range}',
            trailLoadFailedSummary: 'Не вдалося завантажити слід',
            trailLoadFailedDetail: 'Не вдалося завантажити сліди місцезнаходження друзів',
            dataRefreshedSummary: 'Дані оновлено',
            dataRefreshedDetail: 'Дані та місцезнаходження друзів оновлено',
            refreshFailedSummary: 'Помилка оновлення',
            refreshFailedDetail: 'Не вдалося оновити дані друзів',
            loadingFailedSummary: 'Помилка завантаження',
            loadingFailedDetail: 'Не вдалося завантажити дані сторінки'
        }
    },
    liveFilter: {
        selectPlaceholder: 'Виберіть друзів для показу',
        trailDurationLabel: 'Тривалість сліду',
        mobileFilterHeader: 'Фільтр друзів',
        doneButton: 'Готово',
        friendFallbackLabel: 'Друг',
        noEmailFallback: 'Немає електронної пошти',
        summary: {
            none: 'Немає доступних друзів',
            noneSelected: 'Нічого не вибрано',
            allSelected: 'Усі {count} вибрано',
            partialSelected: '{selected} з {total} вибрано'
        },
        mobileButton: {
            allSelected: 'Фільтр друзів',
            partial: 'Фільтр ({count})'
        },
        selectionSummary: {
            none: 'Виберіть друзів',
            all: 'Усі друзі',
            oneFriend: '1 друг',
            multiple: '{count} друзів'
        }
    }
}
