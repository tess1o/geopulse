/**
 * Notifications page (in-app notification inbox) (German).
 */
export default {
    page: {
        title: 'Benachrichtigungen',
        description: 'Behalte im Blick, was GeoPulse für dich bemerkt hat.',
        refresh: 'Aktualisieren',
        markAllSeen: 'Alle als gelesen markieren'
    },
    filters: {
        unread: 'Ungelesen',
        all: 'Alle'
    },
    sourceOptions: {
        all: 'Alle Quellen',
        geofence: 'Geofences',
        timeline: 'Zeitleiste',
        import: 'Import',
        export: 'Export',
        friends: 'Freundschaftsanfragen'
    },
    table: {
        source: 'Quelle',
        notification: 'Benachrichtigung',
        time: 'Zeit',
        status: 'Status',
        actions: 'Aktionen'
    },
    status: {
        seen: 'Gelesen',
        unread: 'Ungelesen'
    },
    newNotification: 'Neue Benachrichtigung',
    markSeen: 'Als gelesen markieren',
    titleFallback: 'Benachrichtigung',
    empty: {
        unread: 'Alles erledigt! Keine ungelesenen Benachrichtigungen.',
        none: 'Noch keine Benachrichtigungen.'
    },
    toast: {
        errorSummary: 'Fehler bei Benachrichtigungen',
        loadFailed: 'Benachrichtigungen konnten nicht geladen werden',
        markSeenFailed: 'Benachrichtigung konnte nicht als gelesen markiert werden',
        markAllSeenFailed: 'Benachrichtigungen konnten nicht als gelesen markiert werden'
    },
    store: {
        loadUnreadCountFailed: 'Anzahl ungelesener Benachrichtigungen konnte nicht geladen werden',
        loadPreferencesFailed: 'Benachrichtigungseinstellungen konnten nicht geladen werden',
        savePreferencesFailed: 'Benachrichtigungseinstellungen konnten nicht gespeichert werden',
        loadReleaseAnnouncementFailed: 'Versionshinweis konnte nicht geladen werden',
        unreadToastSummary: 'Ungelesene Benachrichtigungen',
        unreadToastDetail: 'Du hast {count} ungelesene Benachrichtigung. | Du hast {count} ungelesene Benachrichtigungen.',
        viewAllNotifications: 'Alle Benachrichtigungen ansehen',
        browserBlockedSummary: 'Browser-Benachrichtigungen blockiert',
        browserBlockedDetail: 'Erlaube Benachrichtigungen in den Browser-Einstellungen, um Desktop-Hinweise zu nutzen.',
        browserNotSentSummary: 'Browser-Benachrichtigung nicht gesendet',
        browserNotSentDetail: 'Der Browser hat keine Berechtigung. Aktiviere Browser-Hinweise im Glocken-Menü erneut.',
        browserFailedSummary: 'Browser-Benachrichtigung fehlgeschlagen',
        browserFailedDetail: 'Dein Browser oder Betriebssystem blockiert Desktop-Hinweise. Benachrichtigungen in der App funktionieren weiterhin.'
    },
    bell: {
        openAriaLabel: 'Benachrichtigungen öffnen',
        unreadTag: '{count} ungelesen',
        browserAlerts: 'Browser-Hinweise',
        browserNotSupported: 'Dieser Browser unterstützt keine Benachrichtigungen.',
        noUnread: 'Keine ungelesenen Benachrichtigungen.',
        noNotifications: 'Keine Benachrichtigungen gefunden.',
        defaultMessage: 'Neue Benachrichtigung.',
        markSeen: 'Als gelesen markieren',
        markAllSeen: 'Alle als gelesen markieren',
        errorSummary: 'Fehler bei Benachrichtigung'
    },
    sourceNotificationSuffix: 'Benachrichtigung: {source}',
    display: {
        sources: {
            geofence: 'Geofence',
            timeline: 'Zeitleiste',
            import: 'Import',
            export: 'Export',
            friendInvite: 'Freunde',
            weather: 'Wetter',
            backupHealth: 'Backup-Status',
            gpsHealth: 'GPS-Status',
            product: 'Produkt',
            rewind: 'Rückblick'
        },
        types: {
            geofenceEnter: 'Geofence betreten',
            geofenceLeave: 'Geofence verlassen',
            timelineRegenerationRequired: 'Aktualisierung der Zeitleiste geplant',
            importCompleted: 'Import abgeschlossen',
            importFailed: 'Import fehlgeschlagen',
            exportCompleted: 'Export abgeschlossen',
            exportFailed: 'Export fehlgeschlagen',
            friendInviteReceived: 'Freundschaftsanfrage',
            friendInviteAccepted: 'Freundschaft angenommen',
            weatherQuotaReached: 'Wetter-Kontingent erreicht',
            weatherQuotaRestored: 'Wetter-Kontingent wieder verfügbar',
            gpsHealthIncidentOpened: 'GPS-Aufzeichnung ist still',
            gpsHealthIncidentResolved: 'GPS-Aufzeichnung läuft wieder',
            productReleaseAvailable: 'Was ist neu',
            rewindReady: 'Rückblick bereit'
        },
        actions: {
            openGeofenceEvents: 'Geofence-Ereignisse öffnen',
            viewTimelineStatus: 'Status der Zeitleiste ansehen',
            openImports: 'Importe öffnen',
            openExports: 'Exporte öffnen',
            openInvitations: 'Einladungen öffnen',
            openFriends: 'Freunde öffnen',
            openAdminDashboard: 'Admin-Übersicht öffnen',
            openBackupSettings: 'Backup-Einstellungen öffnen',
            openNotifications: 'Benachrichtigungen öffnen',
            openRewind: 'Rückblick öffnen',
            openNotification: 'Benachrichtigung öffnen'
        }
    }
}
