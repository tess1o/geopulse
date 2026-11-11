/**
 * Notifications page (in-app notification inbox).
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    page: {
        title: 'Meldingen',
        description: 'Blijf op de hoogte van wat GeoPulse voor u heeft opgemerkt.',
        refresh: 'Vernieuwen',
        markAllSeen: 'Alles gemarkeerd als gelezen'
    },
    filters: {
        unread: 'Ongelezen',
        all: 'Alle'
    },
    sourceOptions: {
        all: 'Alle bronnen',
        geofence: 'Geofences',
        timeline: 'Tijdlijn',
        import: 'Importeren',
        export: 'Exporteren',
        friends: 'Vriendenuitnodigingen'
    },
    table: {
        source: 'Bron',
        notification: 'Melding',
        time: 'Tijd',
        status: 'Status',
        actions: 'Acties'
    },
    status: {
        seen: 'Gelezen',
        unread: 'Ongelezen'
    },
    newNotification: 'Nieuwe melding',
    markSeen: 'Als gelezen markeren',
    titleFallback: 'Melding',
    empty: {
        unread: "U bent helemaal bij! Geen ongelezen meldingen.",
        none: 'Nog geen meldingen.'
    },
    toast: {
        errorSummary: 'Meldingen Fout',
        loadFailed: 'Meldingen laden is mislukt',
        markSeenFailed: 'Melding als gelezen markeren is mislukt',
        markAllSeenFailed: 'Alle meldingen als gelezen markeren is mislukt'
    },
    store: {
        loadUnreadCountFailed: 'Ongelezen meldingsaantal laden is mislukt',
        loadPreferencesFailed: 'Meldingsvoorkeuren laden is mislukt',
        savePreferencesFailed: 'Meldingsvoorkeuren opslaan is mislukt',
        loadReleaseAnnouncementFailed: 'Releaseaankondiging laden is mislukt',
        unreadToastSummary: 'Ongelezen meldingen',
        unreadToastDetail: 'U hebt {count} ongelezen melding. | U hebt {count} ongelezen meldingen.',
        viewAllNotifications: 'Alle meldingen bekijken',
        browserBlockedSummary: 'Browser meldingen geblokkeerd',
        browserBlockedDetail: 'Meldingsmachtiging inschakelen in uw browserinstellingen om bureaubladmeldingen te gebruiken.',
        browserNotSentSummary: 'Browser melding niet verzonden',
        browserNotSentDetail: 'Browser machtiging is niet verleend. Schakel browserwaarschuwingen opnieuw in vanuit het belmenu.',
        browserFailedSummary: 'Browser melding mislukt',
        browserFailedDetail: 'Uw browser of OS heeft bureaubladmeldingen geblokkeerd. In-app meldingen blijven actief.'
    },
    bell: {
        openAriaLabel: 'Meldingeninbox openen',
        unreadTag: '{count} ongelezen',
        browserAlerts: 'Browserwaarschuwingen',
        browserNotSupported: 'Browser meldingen zijn niet beschikbaar in deze browser.',
        noUnread: 'Geen ongelezen meldingen.',
        noNotifications: 'Geen meldingen gevonden.',
        defaultMessage: 'Nieuwe melding.',
        markSeen: 'Als gelezen markeren',
        markAllSeen: 'Alles gemarkeerd als gelezen',
        errorSummary: 'Melding Fout'
    },
    sourceNotificationSuffix: '{source} melding',
    display: {
        sources: {
            geofence: 'Geofence',
            timeline: 'Tijdlijn',
            import: 'Importeren',
            export: 'Exporteren',
            friendInvite: 'Vrienden',
            weather: 'Weer',
            backupHealth: 'Back-upstatus',
            gpsHealth: 'GPS-status',
            product: 'Product',
            rewind: 'Terugspoelen'
        },
        types: {
            geofenceEnter: 'Geofence binnenkomst',
            geofenceLeave: 'Geofence verlaten',
            timelineRegenerationRequired: 'Tijdlijnverversing gepland',
            importCompleted: 'Import voltooid',
            importFailed: 'Import mislukt',
            exportCompleted: 'Export voltooid',
            exportFailed: 'Export mislukt',
            friendInviteReceived: 'Vriendenuitnodiging',
            friendInviteAccepted: 'Vriend geaccepteerd',
            weatherQuotaReached: 'Weerlimiet bereikt',
            weatherQuotaRestored: 'Weerlimiet hersteld',
            gpsHealthIncidentOpened: 'GPS-tracking is stil',
            gpsHealthIncidentResolved: 'GPS-tracking hervat',
            productReleaseAvailable: 'Wat is er nieuw',
            rewindReady: 'Terugspoelen gereed'
        },
        actions: {
            openGeofenceEvents: 'Geofence-gebeurtenissen openen',
            viewTimelineStatus: 'Tijdlijnstatus bekijken',
            openImports: 'Imports openen',
            openExports: 'Exports openen',
            openInvitations: 'Uitnodigingen openen',
            openFriends: 'Vrienden openen',
            openAdminDashboard: 'Beheerdersdashboard openen',
            openBackupSettings: 'Back-upinstellingen openen',
            openNotifications: 'Meldingen openen',
            openRewind: 'Terugspoelen openen',
            openNotification: 'Melding openen'
        }
    }
}
