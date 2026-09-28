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
        timeline: 'Timeline',
        explore: 'Explore',
        organizeAndShare: 'Organize & Share',
        settingsAndData: 'Settings & Data',
        administration: 'Administration',
        overview: 'Overview',
        operations: 'Operations',
        peopleAndAccess: 'People & Access',
        configuration: 'Configuration',
        appearance: 'Appearance'
    },
    items: {
        timeline: 'Timeline',
        dashboard: 'Dashboard',
        'timeline-labels': 'Timeline Labels',
        trips: 'Trip Plans',
        'location-analytics': 'Location Analytics',
        'journey-insights': 'Journey Insights',
        rewind: 'Rewind',
        'coverage-explorer': 'Coverage Explorer',
        'ai-chat': 'AI Assistant',
        'favorites-management': 'Favorites',
        geofences: 'Geofences',
        friends: 'Friends',
        'share-links': 'Share Links',
        profile: 'Profile',
        notifications: 'Notifications',
        'location-sources': 'Location Sources',
        preferences: 'Timeline Preferences',
        'gps-data': 'GPS Data',
        'geocoding-management': 'Geocoding',
        export: 'Export / Import',
        help: 'Help & Support',
        'admin-dashboard': 'Overview',
        'admin-backups': 'Backups & Restore',
        'admin-timeline-regeneration': 'Timeline Regeneration Campaigns',
        'admin-users': 'Manage Users',
        'admin-invitations': 'Invitations',
        'admin-oidc-providers': 'OIDC Providers',
        'admin-audit-logs': 'Audit Logs',
        'admin-settings': 'System Settings'
    },
    theme: {
        label: 'Theme: {mode}',
        modes: {
            light: 'Light',
            dark: 'Dark',
            system: 'System'
        }
    },
    loggedInAs: 'Logged in as:',
    logout: 'Logout',
    version: 'Version',
    newVersionAvailable: 'New: {version} available',
    // Browser-tab titles (route meta.titleKey) for pages with no sidebar entry of their own.
    pageTitles: {
        home: 'Home',
        cityDetails: 'City Details',
        countryDetails: 'Country Details',
        sharedLocation: 'Shared Location',
        sharedTimeline: 'Shared Timeline',
        error: 'Error',
        pageNotFound: 'Page Not Found'
    }
}
