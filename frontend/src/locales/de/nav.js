/**
 * Navigation labels (German).
 *
 * Keys mirror en/nav.js exactly -- `items.*` matches the `key` field on each AppNavigation entry and
 * is shared with route `meta.titleKey`.
 */
export default {
    sections: {
        timeline: 'Zeitleiste',
        explore: 'Entdecken',
        organizeAndShare: 'Organisieren & Teilen',
        data: 'Daten',
        administration: 'Administration'
    },
    items: {
        timeline: 'Zeitleiste',
        dashboard: 'Übersicht',
        'timeline-labels': 'Zeitleisten-Labels',
        trips: 'Reisepläne',
        'location-analytics': 'Ortsanalyse',
        'journey-insights': 'Reise-Einblicke',
        rewind: 'Rückblick',
        'coverage-explorer': 'Abdeckungskarte',
        'ai-chat': 'KI-Assistent',
        'favorites-management': 'Favoriten',
        geofences: 'Geofences',
        friends: 'Freunde',
        'share-links': 'Freigabelinks',
        profile: 'Profil',
        notifications: 'Benachrichtigungen',
        'location-sources': 'Standortquellen',
        preferences: 'Zeitleisten-Einstellungen',
        'gps-data': 'GPS-Daten',
        'geocoding-management': 'Geocoding',
        export: 'Export / Import',
        help: 'Hilfe & Support',
        'admin-dashboard': 'Übersicht',
        'admin-backups': 'Backup & Wiederherstellung',
        'admin-timeline-regeneration': 'Zeitleisten-Neuberechnung',
        'admin-users': 'Benutzer verwalten',
        'admin-invitations': 'Einladungen',
        'admin-oidc-providers': 'OIDC-Anbieter',
        'admin-audit-logs': 'Audit-Logs',
        'admin-settings': 'Systemeinstellungen'
    },
    theme: {
        title: 'Design',
        modes: {
            light: 'Hell',
            dark: 'Dunkel',
            system: 'System'
        }
    },
    // Account menu behind the avatar in the top bar.
    userMenu: {
        open: 'Kontomenü',
        adminBadge: 'Admin',
        adminDashboard: 'Admin-Übersicht',
        language: 'Sprache',
        languageSaveFailed: 'Deine Spracheinstellung konnte nicht gespeichert werden',
        hint: {
            title: 'Neu: dein Kontomenü',
            body: 'Profil, Zeitleisten-Einstellungen, Design, Sprache und Abmelden findest du jetzt hier.'
        }
    },
    logout: 'Abmelden',
    version: 'Version',
    newVersionAvailable: 'Neu: {version} verfügbar',
    // Browser-tab titles (route meta.titleKey) for pages with no sidebar entry of their own.
    pageTitles: {
        home: 'Startseite',
        cityDetails: 'Stadtdetails',
        countryDetails: 'Landesdetails',
        sharedLocation: 'Geteilter Standort',
        sharedTimeline: 'Geteilte Zeitleiste',
        error: 'Fehler',
        pageNotFound: 'Seite nicht gefunden'
    }
}
