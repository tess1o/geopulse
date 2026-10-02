/**
 * Notifications tab, plus the shared channel settings block it renders per event group.
 *
 * 'GPS health', 'Monthly Rewind', 'Product updates', 'Destination URLs', 'Configuration key' and
 * 'Configuration tag' are asserted verbatim by NotificationsPreferencesTab.test.js.
 */
export default {
    title: 'Notifications',
    description: 'Choose which events create alerts and how they are delivered.',
    gpsHealth: {
        heading: 'GPS health',
        description: 'Get alerted when every active live GPS source stops sending data.',
        monitor: {
            title: 'Monitor GPS arrivals',
            description: 'Detect extended gaps across all active live sources.'
        },
        silence: {
            title: 'Silence threshold',
            description: 'Wait this many minutes before creating the first alert.',
            details: 'Choose a value from 1 minute to 7 days.',
            ariaLabel: 'GPS silence threshold in minutes'
        },
        channelLabel: 'GPS health delivery'
    },
    rewind: {
        heading: 'Monthly Rewind',
        description: 'Get a reminder when the previous month is ready to explore.',
        notify: {
            title: 'Notify when Rewind is ready',
            description: 'Create an alert on the first day of each month.'
        },
        channelLabel: 'Rewind delivery'
    },
    productUpdates: {
        heading: 'Product updates',
        description: 'Control in-app announcements about new GeoPulse features.',
        whatsNew: {
            title: 'Show release highlights',
            description: 'Show What’s New once after an upgrade.',
            details: 'Release highlights appear only inside GeoPulse and are never sent externally.'
        }
    },
    // Trailing unit for the silence-threshold number input.
    silenceSuffix: ' min',
    saveChanges: 'Save Changes',
    channels: {
        inApp: {
            title: 'Show in inbox',
            description: 'Create an alert in the GeoPulse notification inbox.'
        },
        apprise: {
            title: 'Send through Apprise',
            description: 'Forward this alert to configured external destinations.'
        },
        routing: {
            title: 'Apprise routing',
            description: 'Choose how GeoPulse addresses the destination.',
            urls: 'Destination URL(s)',
            keyTag: 'Apprise config key and tag'
        },
        destinationUrls: {
            title: 'Destination URLs',
            description: 'Enter one or more Apprise destination URLs.',
            ariaLabel: 'Apprise destination URLs'
        },
        configKey: {
            title: 'Configuration key',
            description: 'Name of the stored Apprise configuration.',
            placeholder: 'Apprise config key',
            ariaLabel: 'Apprise configuration key'
        },
        configTag: {
            title: 'Configuration tag',
            description: 'Optional tag used to select configured destinations.',
            placeholder: 'Optional Apprise tag',
            ariaLabel: 'Apprise configuration tag'
        }
    }
}
