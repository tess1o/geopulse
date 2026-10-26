/**
 * Friends: live map, timeline, invitations, and management tables (German).
 */
export default {
    sentInvites: {
        header: 'Gesendete Einladungen',
        loading: 'Gesendete Einladungen werden geladen …',
        sentAgo: 'Gesendet {time}',
        pending: 'Offen',
        cancelTooltip: 'Einladung zurückziehen',
        cancelAllPending: 'Alle offenen zurückziehen',
        emptyTitle: 'Keine offenen Einladungen',
        emptyMessage: 'Gesendete Freundschaftsanfragen erscheinen hier, bis sie angenommen oder abgelehnt werden.',
        cancelDialog: {
            header: 'Einladung zurückziehen',
            message: 'Einladung an {name} zurückziehen?',
            note: 'Das lässt sich nicht rückgängig machen. Du kannst später eine neue Einladung senden.',
            keep: 'Einladung behalten',
            confirm: 'Einladung zurückziehen'
        }
    },
    receivedInvites: {
        header: 'Erhaltene Einladungen',
        loading: 'Erhaltene Einladungen werden geladen …',
        wantsToConnect: 'Möchte sich mit dir verbinden',
        acceptTooltip: 'Einladung annehmen',
        declineTooltip: 'Einladung ablehnen',
        acceptAll: 'Alle annehmen',
        declineAll: 'Alle ablehnen',
        emptyTitle: 'Keine offenen Einladungen',
        emptyMessage: 'Freundschaftsanfragen von anderen erscheinen hier. Teile deinen Benutzernamen, um Einladungen zu bekommen!',
        acceptDialog: {
            header: 'Freundschaftsanfrage annehmen',
            message: '{name} als Freund hinzufügen?',
            note: 'Ihr könnt dann gegenseitig eure Standorte auf der Karte sehen.',
            confirm: 'Freund hinzufügen'
        },
        rejectDialog: {
            header: 'Freundschaftsanfrage ablehnen',
            message: 'Freundschaftsanfrage von {name} ablehnen?',
            note: 'Die Person wird nicht benachrichtigt, kann dir aber später eine neue Anfrage senden.',
            keep: 'Anfrage behalten',
            confirm: 'Ablehnen'
        },
        bulkDialog: {
            acceptHeader: 'Alle Anfragen annehmen',
            declineHeader: 'Alle Anfragen ablehnen',
            message: 'Alle {count} offenen Freundschaftsanfragen {action}?',
            acceptAction: 'annehmen',
            declineAction: 'ablehnen',
            acceptNote: 'Alle diese Personen werden deine Freunde und sehen deinen Standort.',
            declineNote: 'Alle offenen Anfragen werden abgelehnt. Die Personen können später neue Anfragen senden.'
        },
        relativeTime: {
            daysAgo: 'vor {count} Tag | vor {count} Tagen',
            hoursAgo: 'vor {count} Stunde | vor {count} Stunden',
            minutesAgo: 'vor {count} Minute | vor {count} Minuten',
            justNow: 'gerade eben',
            recently: 'kürzlich'
        }
    },
    timelineTab: {
        loading: 'Zeitleisten werden geladen …',
        selectFriends: 'Wähle Freunde, um ihre Zeitleisten zu sehen',
        noSharedTitle: 'Keine geteilten Zeitleisten',
        noSharedMessage: 'Noch keiner deiner Freunde teilt seine Zeitleiste. Bitte sie, das in den Freundes-Einstellungen zu aktivieren!',
        loadFailedTitle: 'Zeitleisten nicht geladen',
        loadFailedDetail: 'Die Zeitleisten deiner Freunde konnten nicht geladen werden'
    },
    mergedList: {
        header: 'Einträge der Zeitleiste',
        loading: 'Einträge werden geladen …',
        empty: 'Keine Zeitleistendaten für den gewählten Zeitraum',
        noDataFor: 'Keine Daten für {duration}',
        loadMore: 'Mehr laden',
        showingCount: '{shown} von {total} Einträgen'
    },
    locationTab: {
        liveLocation: 'Live-Standort',
        timelineHistory: 'Zeitleistenverlauf'
    },
    selection: {
        title: 'Freunde wählen',
        youLabel: '(Du)'
    },
    invite: {
        header: 'Freund einladen',
        subtitle: 'Freundschaftsanfrage senden',
        nameLabel: 'Name',
        submit: 'Einladen!'
    },
    list: {
        header: 'Freunde',
        inviteFriend: 'Freund einladen',
        columns: {
            friendName: 'Name',
            lastSeen: 'Zuletzt gesehen',
            lastLocation: 'Letzter Ort',
            remove: 'Entfernen'
        },
        removeTooltip: 'Freund entfernen',
        empty: 'Noch keine Freunde. Lade deinen ersten Freund ein, um Standorte zu teilen!',
        removeDialog: {
            header: 'Freund entfernen',
            message: '{name} aus deinen Freunden entfernen?',
            note: 'Ihr seht dann gegenseitig eure Standorte nicht mehr. Du kannst später bei Bedarf eine neue Anfrage senden.',
            confirm: 'Freund entfernen'
        }
    },
    datePicker: {
        rangeLabel: 'Zeitraum:',
        placeholder: 'Zeitraum wählen',
        presetPlaceholder: 'Schnellauswahl',
        today: 'Heute',
        yesterday: 'Gestern'
    },
    filters: {
        all: 'Alle',
        none: 'Keine',
        online: 'Online',
        onlineNow: 'Jetzt online',
        lastSeenRecently: 'Kürzlich gesehen'
    },
    demo: {
        invitationsTooltip: 'Einladungen sind im Demo-Modus deaktiviert'
    },
    listTab: {
        empty: {
            title: 'Noch keine Freunde',
            description: 'Lade Freunde ein, um euch zu verbinden und Standorte zu teilen',
            demoDisabled: 'Freunde einladen ist im Demo-Modus deaktiviert.',
            inviteFirst: 'Ersten Freund einladen'
        },
        lastSeenLabel: 'Zuletzt gesehen: {text}',
        sharesWithYou: 'Was dieser Freund mit dir teilt:',
        sharesWithFriend: 'Was du mit diesem Freund teilst:',
        liveLocation: 'Live-Standort',
        timelineHistory: 'Zeitleistenverlauf',
        shared: 'Geteilt',
        notShared: 'Nicht geteilt',
        demoPermissionsReadOnly: 'Freigaben sind im Demo-Modus schreibgeschützt.',
        liveLocationInfo: 'Erlaubt diesem Freund, deinen aktuellen Standort in Echtzeit zu sehen',
        timelineHistoryInfo: 'Erlaubt diesem Freund, deinen gesamten Standortverlauf zu sehen',
        actions: {
            live: 'Live',
            liveTooltip: 'Freund auf der Live-Karte zeigen',
            timeline: 'Zeitleiste',
            timelineTooltip: 'Zeitleistenverlauf des Freundes ansehen',
            removeTooltip: 'Freund entfernen',
            removeTooltipDemo: 'Freunde entfernen ist im Demo-Modus deaktiviert'
        },
        status: {
            noLocation: 'Kein Standort',
            online: 'Online',
            recent: 'Kürzlich',
            offline: 'Offline'
        },
        lastSeenText: {
            never: 'Nie',
            justNow: 'Gerade eben',
            minutesAgo: 'vor {count} Min.',
            hoursAgo: 'vor {count} Std.',
            daysAgo: 'vor {count} T'
        },
        permissionDialog: {
            header: 'Änderung der Freigabe bestätigen',
            timelineAllow: '{name} kann dann deinen gesamten Standortverlauf sehen (alle bisherigen Aufenthalte und Fahrten). Fortfahren?',
            timelineRevoke: '{name} verliert dann den Zugriff auf deinen Standortverlauf. Fortfahren?',
            liveAllow: '{name} kann dann deinen aktuellen Standort in Echtzeit sehen. Fortfahren?',
            liveRevoke: '{name} verliert dann den Zugriff auf deinen Live-Standort. Fortfahren?'
        },
        permissionToast: {
            updatedSummary: 'Freigabe aktualisiert',
            timelineGranted: '{name} kann jetzt deinen Zeitleistenverlauf sehen',
            timelineRevoked: '{name} kann deinen Zeitleistenverlauf nicht mehr sehen',
            liveGranted: '{name} kann jetzt deinen Live-Standort sehen',
            liveRevoked: '{name} kann deinen Live-Standort nicht mehr sehen',
            failedSummary: 'Freigabe nicht aktualisiert',
            timelineFailedDetail: 'Die Freigaben für den Freund konnten nicht aktualisiert werden',
            liveFailedDetail: 'Die Freigabe des Live-Standorts konnte nicht aktualisiert werden'
        }
    },
    mapTab: {
        loading: 'Freundeskarte wird geladen …',
        noFriends: {
            title: 'Keine Freunde zum Anzeigen',
            description: 'Füge Freunde hinzu, um ihre Standorte auf der Karte zu sehen',
            demoDisabled: 'Freunde einladen ist im Demo-Modus deaktiviert.',
            invite: 'Freunde einladen'
        },
        noLocation: {
            title: 'Keine Standortdaten vorhanden',
            description: 'Deine Freunde haben noch keinen Standort geteilt. Vielleicht haben sie die Standortfreigabe ausgeschaltet oder noch keine Tracking-App genutzt.',
            demoDisabled: 'Weitere Freunde einladen ist im Demo-Modus deaktiviert.',
            refresh: 'Aktualisieren',
            inviteMore: 'Weitere Freunde einladen'
        },
        noSelection: {
            title: 'Keine Freunde ausgewählt',
            description: 'Wähle im Filter mindestens einen Freund, um Standorte auf der Karte zu sehen.',
            showAll: 'Alle Freunde zeigen'
        }
    },
    invitationsTab: {
        demoDisabled: 'Aktionen für Einladungen sind im Demo-Modus deaktiviert.',
        received: {
            header: 'Erhaltene Einladungen',
            acceptAll: 'Alle annehmen',
            rejectAll: 'Alle ablehnen'
        },
        sent: {
            header: 'Gesendete Einladungen',
            cancelAll: 'Alle zurückziehen',
            pending: 'Offen'
        },
        actions: {
            accept: 'Annehmen',
            reject: 'Ablehnen',
            cancel: 'Zurückziehen'
        },
        empty: {
            title: 'Keine offenen Einladungen',
            description: 'Alle deine Einladungen sind bearbeitet'
        }
    },
    page: {
        demoReadOnlyMessage: 'Demo-Modus: Einladungen, Änderungen an Freigaben und das Entfernen von Freunden sind deaktiviert. Bestehende geteilte Demo-Standorte kannst du weiterhin ansehen.',
        demoChangesDisabled: 'Änderungen an Freunden sind im Demo-Modus deaktiviert.',
        inviteDialog: {
            header: 'Freund einladen',
            emailLabel: 'E-Mail-Adresse oder Name',
            emailPlaceholder: 'E-Mail-Adresse eingeben oder Personen suchen',
            send: 'Einladung senden'
        },
        tabs: {
            live: 'Live',
            timeline: 'Zeitleiste',
            friends: 'Freunde',
            invitations: 'Einladungen'
        },
        validation: {
            emailRequired: 'E-Mail-Adresse ist erforderlich',
            emailInvalid: 'Bitte gib eine gültige E-Mail-Adresse ein'
        },
        toasts: {
            invitationSentSummary: 'Einladung gesendet',
            invitationSentDetail: 'Freundschaftsanfrage an {email} gesendet',
            invitationFailedSummary: 'Einladung fehlgeschlagen',
            invitationFailedDetail: 'Einladung konnte nicht gesendet werden',
            removeFriendConfirm: 'Möchtest du {name} wirklich aus deinen Freunden entfernen?',
            removeFriendHeader: 'Freund entfernen',
            removeFriendConfirmLabel: 'Entfernen',
            friendRemovedSummary: 'Freund entfernt',
            friendRemovedDetail: 'Der Freund wurde aus deiner Liste entfernt',
            removeFailedSummary: 'Entfernen fehlgeschlagen',
            removeFailedDetail: 'Freund konnte nicht entfernt werden',
            acceptedSummary: 'Einladung angenommen',
            acceptedDetail: 'Ihr seid jetzt befreundet!',
            acceptFailedSummary: 'Annehmen fehlgeschlagen',
            acceptFailedDetail: 'Einladung konnte nicht angenommen werden',
            rejectedSummary: 'Einladung abgelehnt',
            rejectedDetail: 'Die Einladung wurde abgelehnt',
            rejectFailedSummary: 'Ablehnen fehlgeschlagen',
            rejectFailedDetail: 'Einladung konnte nicht abgelehnt werden',
            cancelledSummary: 'Einladung zurückgezogen',
            cancelledDetail: 'Die Einladung wurde zurückgezogen',
            cancelFailedSummary: 'Zurückziehen fehlgeschlagen',
            cancelFailedDetail: 'Einladung konnte nicht zurückgezogen werden',
            allAcceptedSummary: 'Alle Einladungen angenommen',
            allAcceptedDetail: 'Einladungen angenommen: {count}',
            bulkAcceptFailedSummary: 'Annehmen fehlgeschlagen',
            bulkAcceptFailedDetail: 'Nicht alle Einladungen konnten angenommen werden',
            allRejectedSummary: 'Alle Einladungen abgelehnt',
            allRejectedDetail: 'Einladungen abgelehnt: {count}',
            bulkRejectFailedSummary: 'Ablehnen fehlgeschlagen',
            bulkRejectFailedDetail: 'Nicht alle Einladungen konnten abgelehnt werden',
            allCancelledSummary: 'Alle Einladungen zurückgezogen',
            allCancelledDetail: 'Einladungen zurückgezogen: {count}',
            bulkCancelFailedSummary: 'Zurückziehen fehlgeschlagen',
            bulkCancelFailedDetail: 'Nicht alle Einladungen konnten zurückgezogen werden',
            trailsEnabledSummary: 'Standortspuren aktiviert',
            trailsEnabledDetailWithPoints: '{points} Punkte in {trailCount} Spur(en) von Freunden für {range}',
            trailsEnabledDetailEmpty: 'Keine Spurpunkte für {range} gefunden',
            trailLoadFailedSummary: 'Spuren nicht geladen',
            trailLoadFailedDetail: 'Die Standortspuren deiner Freunde konnten nicht geladen werden',
            dataRefreshedSummary: 'Daten aktualisiert',
            dataRefreshedDetail: 'Daten und Standorte deiner Freunde wurden aktualisiert',
            refreshFailedSummary: 'Aktualisieren fehlgeschlagen',
            refreshFailedDetail: 'Die Daten deiner Freunde konnten nicht aktualisiert werden',
            loadingFailedSummary: 'Laden fehlgeschlagen',
            loadingFailedDetail: 'Die Seitendaten konnten nicht geladen werden'
        }
    },
    liveFilter: {
        selectPlaceholder: 'Freunde zum Anzeigen wählen',
        trailDurationLabel: 'Länge der Spur',
        mobileFilterHeader: 'Freunde filtern',
        doneButton: 'Fertig',
        friendFallbackLabel: 'Freund',
        noEmailFallback: 'Keine E-Mail',
        summary: {
            none: 'Keine Freunde vorhanden',
            noneSelected: 'Keine ausgewählt',
            allSelected: 'Alle {count} ausgewählt',
            partialSelected: '{selected} von {total} ausgewählt'
        },
        mobileButton: {
            allSelected: 'Freunde filtern',
            partial: 'Filter ({count})'
        },
        selectionSummary: {
            none: 'Freunde wählen',
            all: 'Alle Freunde',
            oneFriend: '1 Freund',
            multiple: '{count} Freunde'
        }
    }
}
