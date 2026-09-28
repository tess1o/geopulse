/**
 * Notifications page (in-app notification inbox).
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    page: {
        title: 'Notifications',
        description: 'Stay on top of what GeoPulse has noticed for you.',
        refresh: 'Refresh',
        markAllSeen: 'Mark All Seen'
    },
    filters: {
        unread: 'Unread',
        all: 'All'
    },
    sourceOptions: {
        all: 'All sources',
        geofence: 'Geofences',
        timeline: 'Timeline',
        import: 'Import',
        export: 'Export',
        friends: 'Friend Invites'
    },
    table: {
        source: 'Source',
        notification: 'Notification',
        time: 'Time',
        status: 'Status',
        actions: 'Actions'
    },
    status: {
        seen: 'Seen',
        unread: 'Unread'
    },
    newNotification: 'New notification',
    markSeen: 'Mark Seen',
    titleFallback: 'Notification',
    empty: {
        unread: "You're all caught up! No unread notifications.",
        none: 'No notifications yet.'
    },
    toast: {
        errorSummary: 'Notifications Error',
        loadFailed: 'Failed to load notifications',
        markSeenFailed: 'Failed to mark notification as seen',
        markAllSeenFailed: 'Failed to mark all notifications as seen'
    },
    store: {
        loadUnreadCountFailed: 'Failed to load unread notification count',
        loadPreferencesFailed: 'Failed to load notification preferences',
        savePreferencesFailed: 'Failed to save notification preferences',
        loadReleaseAnnouncementFailed: 'Failed to load release announcement',
        unreadToastSummary: 'Unread notifications',
        unreadToastDetail: 'You have {count} unread notification. | You have {count} unread notifications.',
        viewAllNotifications: 'View all notifications',
        browserBlockedSummary: 'Browser notifications blocked',
        browserBlockedDetail: 'Enable notification permission in your browser settings to use desktop alerts.',
        browserNotSentSummary: 'Browser notification not sent',
        browserNotSentDetail: 'Browser permission is not granted. Enable Browser alerts from the bell menu again.',
        browserFailedSummary: 'Browser notification failed',
        browserFailedDetail: 'Your browser or OS blocked desktop alerts. In-app notifications are still active.'
    },
    bell: {
        openAriaLabel: 'Open notifications inbox',
        unreadTag: '{count} unread',
        browserAlerts: 'Browser alerts',
        browserNotSupported: 'Browser notifications are not available in this browser.',
        noUnread: 'No unread notifications.',
        noNotifications: 'No notifications found.',
        defaultMessage: 'New notification.',
        markSeen: 'Mark seen',
        markAllSeen: 'Mark all seen',
        errorSummary: 'Notification Error'
    },
    sourceNotificationSuffix: '{source} notification',
    display: {
        sources: {
            geofence: 'Geofence',
            timeline: 'Timeline',
            import: 'Import',
            export: 'Export',
            friendInvite: 'Friends',
            weather: 'Weather',
            backupHealth: 'Backup health',
            gpsHealth: 'GPS health',
            product: 'Product',
            rewind: 'Rewind'
        },
        types: {
            geofenceEnter: 'Geofence enter',
            geofenceLeave: 'Geofence leave',
            timelineRegenerationRequired: 'Timeline refresh scheduled',
            importCompleted: 'Import completed',
            importFailed: 'Import failed',
            exportCompleted: 'Export completed',
            exportFailed: 'Export failed',
            friendInviteReceived: 'Friend invitation',
            friendInviteAccepted: 'Friend accepted',
            weatherQuotaReached: 'Weather quota reached',
            weatherQuotaRestored: 'Weather quota restored',
            gpsHealthIncidentOpened: 'GPS tracking is quiet',
            gpsHealthIncidentResolved: 'GPS tracking resumed',
            productReleaseAvailable: 'What’s new',
            rewindReady: 'Rewind ready'
        },
        actions: {
            openGeofenceEvents: 'Open Geofence Events',
            viewTimelineStatus: 'View Timeline Status',
            openImports: 'Open Imports',
            openExports: 'Open Exports',
            openInvitations: 'Open Invitations',
            openFriends: 'Open Friends',
            openAdminDashboard: 'Open Admin Dashboard',
            openBackupSettings: 'Open backup settings',
            openNotifications: 'Open Notifications',
            openRewind: 'Open Rewind',
            openNotification: 'Open Notification'
        }
    }
}
