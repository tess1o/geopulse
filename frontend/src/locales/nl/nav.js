/**
 * Navigation labels.
 *
 * `items.*` is keyed by the stable `key` field already present on every entry in AppNavigation.vue,
 * and route `meta.titleKey` reuses the same paths -- so a nav label and its page title can never
 * drift apart.
 *
 * EN values are copied verbatim from the previous hardcoded literals: the test suite is pinned to
 * English and asserts on exact copy, so this file is a snapshot, not an opportunity to reword.
 */
export default {
    sections: {
        timeline: 'Tijdlijn',
        explore: 'Ontdekken',
        organizeAndShare: 'Organiseren & Delen',
        data: 'Gegevens',
        administration: 'Beheer'
    },
    items: {
        timeline: 'Tijdlijn',
        dashboard: 'Dashboard',
        'timeline-labels': 'Tijdlijnlabels',
        trips: 'Reisplannen',
        'location-analytics': 'Locatie-analyses',
        'journey-insights': 'Reisinzichten',
        rewind: 'Terugspoelen',
        'coverage-explorer': 'Dekkingsverkenner',
        'ai-chat': 'AI-assistent',
        'favorites-management': 'Favorieten',
        geofences: 'Geofences',
        friends: 'Vrienden',
        'share-links': 'Deellinks',
        profile: 'Profiel',
        notifications: 'Meldingen',
        'location-sources': 'Locatiebronnen',
        preferences: 'Tijdlijnvoorkeuren',
        'gps-data': 'GPS-gegevens',
        'geocoding-management': 'Geocodering',
        export: 'Exporteren / Importeren',
        help: 'Help & Ondersteuning',
        'admin-dashboard': 'Overzicht',
        'admin-backups': 'Back-ups & Herstellen',
        'admin-timeline-regeneration': 'Tijdlijn-regeneratiecampagnes',
        'admin-users': 'Gebruikers beheren',
        'admin-invitations': 'Uitnodigingen',
        'admin-oidc-providers': 'OIDC-providers',
        'admin-audit-logs': 'Auditlogboeken',
        'admin-settings': 'Systeeminstellingen'
    },
    theme: {
        title: 'Thema',
        modes: {
            light: 'Licht',
            dark: 'Donker',
            system: 'Systeem'
        }
    },
    // Account menu behind the avatar in the top bar.
    userMenu: {
        open: 'Accountmenu',
        adminBadge: 'Beheerder',
        // The drawer calls this page 'Overview' under its Administration header; the menu has no header.
        adminDashboard: 'Beheerdersdashboard',
        language: 'Taal',
        languageSaveFailed: 'Kon uw taalvoorkeur niet opslaan',
        // One-time pointer for people used to the pre-2.0 side menu (UserMenuHint.vue).
        hint: {
            title: 'Nieuw: uw accountmenu',
            body: 'Profiel, tijdlijnvoorkeuren, thema, taal en afmelden zijn hierheen verhuisd.'
        }
    },
    logout: 'Afmelden',
    version: 'Versie',
    newVersionAvailable: 'Nieuw: {version} beschikbaar',
    // Browser-tab titles (route meta.titleKey) for pages with no sidebar entry of their own.
    pageTitles: {
        home: 'Start',
        cityDetails: 'Stad Details',
        countryDetails: 'Land Details',
        sharedLocation: 'Gedeelde Locatie',
        sharedTimeline: 'Gedeelde Tijdlijn',
        error: 'Fout',
        pageNotFound: 'Pagina Niet Gevonden'
    }
}
