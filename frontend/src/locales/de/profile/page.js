/**
 * Personal settings page (German): page header, tab navigation, save toasts, the unsaved-changes
 * confirmation, and the errors raised by the save handlers.
 */
export default {
    page: {
        title: 'Persönliche Einstellungen',
        description: 'Verwalte dein Konto, deine Einstellungen und verbundene Apps',
        signedInAs: 'Angemeldet als {email}'
    },
    searchPlaceholder: 'Einstellungen durchsuchen …',
    sectionsAria: 'Bereiche der persönlichen Einstellungen',
    mobileSectionLabel: 'Einstellungsbereich',
    demoReadOnly: 'Demo-Modus: Profil, Sicherheit, Darstellung, KI, Immich und Memos sind schreibgeschützt. Änderungen lassen sich in der Demo nicht speichern.',
    demoReadOnlyToast: 'Profiländerungen sind im Demo-Modus deaktiviert.',
    groups: {
        personal: 'Persönlich',
        experience: 'Nutzung',
        connectedApps: 'Verbundene Apps'
    },
    tabs: {
        general: 'Allgemein',
        security: 'Sicherheit',
        timeline: 'Zeitleiste & Karte',
        appearance: 'Darstellung',
        notifications: 'Benachrichtigungen',
        connectedApps: 'Verbundene Apps'
    },
    jump: {
        notVisibleTitle: 'Einstellung nicht sichtbar',
        notVisibleDetail: 'Diese Einstellung ist gerade nicht sichtbar. Aktiviere die zugehörigen Optionen, um sie zu bearbeiten.'
    },
    unsaved: {
        header: 'Ungespeicherte Änderungen',
        message: 'Du hast ungespeicherte Änderungen am Profil. Wenn du die Seite verlässt, gehen sie verloren.',
        leave: 'Verlassen ohne Speichern',
        stay: 'Bleiben'
    },
    save: {
        updateFailed: 'Aktualisieren fehlgeschlagen',
        saveFailed: 'Speichern fehlgeschlagen',
        notificationsSaved: 'Benachrichtigungseinstellungen gespeichert',
        profileUpdated: {
            title: 'Profil aktualisiert',
            detail: 'Dein Profil wurde aktualisiert'
        },
        displayUpdated: {
            title: 'Darstellung aktualisiert',
            detail: 'Deine Einstellungen zur Darstellung der Zeitleiste wurden gespeichert und gelten sofort.'
        },
        passwordChanged: {
            title: 'Passwort geändert',
            detail: 'Dein Passwort wurde geändert'
        },
        passwordSet: {
            title: 'Passwort festgelegt',
            detail: 'Dein Passwort wurde festgelegt'
        },
        passwordChangeFailed: 'Passwort nicht geändert',
        passwordSetFailed: 'Passwort nicht festgelegt',
        aiSaved: {
            title: 'Gespeichert',
            detail: 'KI-Einstellungen gespeichert'
        },
        aiError: 'Fehler',
        immichUpdated: {
            title: 'Immich-Einstellungen aktualisiert',
            detail: 'Deine Einstellungen für die Immich-Anbindung wurden gespeichert'
        },
        memosUpdated: {
            title: 'Memos-Einstellungen aktualisiert',
            detail: 'Deine Einstellungen für die Memos-Anbindung wurden gespeichert'
        }
    },
    errors: {
        incorrectPassword: 'Das aktuelle Passwort ist falsch',
        checkInformation: 'Prüf deine Angaben und versuch es noch einmal'
    },
    cached: {
        title: 'Zwischengespeicherte Daten',
        detail: 'Die neuesten Profildaten konnten nicht abgerufen werden. Es werden zwischengespeicherte Daten angezeigt.'
    }
}
