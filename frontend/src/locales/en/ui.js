/**
 * Shared UI components, search, dashboard, and home content.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    mainApp: {
        shareLabel: 'Share',
        shareAria: 'Share timeline',
        reconstructTooltip: 'Generate GPS points from manual stays and trips. Existing GPS points are preserved.',
        reconstructLabel: 'Add Missing Timeline Data',
        reconstructAria: 'Add missing timeline data',
        timelineReportsTab: 'Timeline Reports'
    },
    dashboard: {
        page: {
            title: 'Dashboard',
            subtitle: 'Overview of your location data and analytics',
            selectedPeriodSummary: 'Selected Period Summary',
            sevenDaysOverview: '7 Days Overview',
            thirtyDaysOverview: '30 Days Overview',
            topPlacesSelected: 'Top Places for selected period',
            topPlacesLast7: 'Top Places for last 7 days',
            topPlacesLast30: 'Top Places for last 30 days',
            routeStatsSelected: 'Route Stats for selected period',
            routeStatsLast7: 'Route Stats or last 7 days',
            routeStatsLast30: 'Route Stats for last 30 days',
            noDataTitle: 'No Data Available',
            noDataMessage: 'No location data found for the selected time periods. Check your GPS sources or select a different date range.',
            toasts: {
                selectedRangeFailed: 'Failed to fetch selected range stats',
                weeklyFailed: 'Failed to fetch weekly stats',
                monthlyFailed: 'Failed to fetch monthly stats'
            }
        },
        metrics: {
            totalDistance: 'Total Distance',
            timeMoving: 'Time Moving',
            dailyAverage: 'Daily Average',
            averageSpeed: 'Average Speed',
            mostActiveDay: 'Most Active Day',
            uniqueLocations: 'Unique Locations',
            notAvailable: 'N/A'
        },
        distanceActivityChart: 'Distance Activity',
        distanceAxisTitle: 'Distance ({unit})',
        tooltip: {
            distance: 'Distance: {value}',
            travelTime: 'Travel Time: {value}',
            locations: 'Locations: {value}'
        },
        routeAnalysis: {
            noDataTitle: 'No Route Data',
            noDataMessage: 'There are no routes analyzed for this period.',
            uniqueRoutes: 'Unique Routes',
            mostCommonRoute: 'Most Common Route',
            tripsCount: '{count} trips',
            avgTripDuration: 'Avg Trip Duration',
            longestTripDuration: 'Longest Trip (duration)',
            longestTripDistance: 'Longest Trip (distance)',
            notAvailable: 'N/A'
        },
        topPlaces: {
            noDataTitle: 'No Places Data',
            noDataMessage: 'There are no places visited during this period.',
            visitsCount: '{count} visits',
            totalDuration: '{duration} total',
            defaultLocationName: 'Selected Location'
        }
    },
    home: {
        loadContentFailed: 'Failed to load home content',
        nav: {
            resourcesAriaLabel: 'Project resources',
            documentation: 'Documentation',
            github: 'GitHub',
            whatsNewAriaLabel: "What's new",
            updateAvailable: 'New: {version} available',
            updateAvailableShort: 'New: {version}',
            whatsNewTitle: "What's New in {version}",
            whatsNewEmpty: 'No release notes are available for this version yet.',
            releaseNotesLink: 'Full release notes on GitHub'
        },
        hero: {
            eyebrow: 'Privacy-first Google Timeline alternative',
            titleWelcomeBack: 'Welcome back',
            titleDefault: 'Own Your Location Timeline',
            subtitle: 'GeoPulse turns raw GPS points into a private location timeline with stays, trips, maps, and insights, all under your control.',
            accessDisabled: 'Login and registration are currently disabled by the administrator.',
            registrationDisabled: 'Registration is disabled. Existing users can still sign in.',
            tryDemo: 'Try Demo',
            startJourney: 'Start Your Journey',
            goToTimeline: 'Go to Timeline',
            loadingWorkspace: 'Loading workspace...',
            socialProofText: 'The privacy-first, open-source alternative to Google Timeline.',
            stars: 'Stars',
            forks: 'Forks'
        },
        features: {
            liveTracking: 'Live Tracking',
            smartImport: 'Smart Import',
            autoTimeline: 'Auto-Timeline',
            deepInsights: 'Deep Insights',
            immichIntegration: 'Immich Integration',
            friends: 'Friends',
            geofences: 'Geofences',
            ai: 'AI'
        },
        panel: {
            exploreAriaLabel: 'Explore GeoPulse and tips',
            tabsAriaLabel: 'Mobile section tabs',
            featuresTab: 'Features',
            tipTab: 'Tip',
            tipOfDayTitle: 'Tip of the day',
            tipOfDayAriaLabel: 'Tip of the day'
        },
        footer: {
            copyright: '© GeoPulse. All rights reserved.'
        }
    },
    mobileAuth: {
        title: 'Mobile',
        preparing: 'Preparing mobile authentication...',
        payloadMissing: 'Mobile authentication payload was not returned.',
        opening: 'Opening the app...',
        timedOut: 'Opening the app timed out. Please return to the app and try again.',
        failed: 'Failed to complete mobile authentication handoff.'
    },
    notFound: {
        subtitle: 'Location Not Found',
        title: "Oops! You've wandered off the map",
        description: "The page you're looking for doesn't exist or has been moved to a different location. Let's get you back on track.",
        optionsTitle: 'Where would you like to go?',
        options: {
            home: {
                title: 'Home',
                description: 'Start your journey from the beginning'
            },
            timeline: {
                title: 'Timeline',
                description: 'View your location timeline'
            },
            signIn: {
                title: 'Sign In',
                description: 'Access your GeoPulse account'
            },
            dashboard: {
                title: 'Dashboard',
                description: 'Check your location insights'
            },
            signUp: {
                title: 'Sign Up',
                description: 'Create a new account'
            }
        },
        goBack: 'Go Back',
        helpSummary: "Need help finding what you're looking for?",
        helpIntro: 'Here are some common pages you might be looking for:',
        helpLinks: {
            timeline: 'Timeline',
            timelineDesc: 'View your location history',
            dashboard: 'Dashboard',
            dashboardDesc: 'Location insights and analytics',
            journeyInsights: 'Journey Insights',
            journeyInsightsDesc: 'Discover travel patterns',
            friends: 'Friends',
            friendsDesc: 'Manage your friend connections',
            signIn: 'Sign In',
            signInDesc: 'Access your account'
        },
        helpNote: "If you believe this is a broken link, please contact support and we'll fix it."
    },
    darkModeSwitcher: {
        themeTooltip: 'Theme: {label}',
        themeAriaLabel: 'Theme mode: {label}',
        light: 'Light',
        dark: 'Dark',
        system: 'System',
        currentSuffix: '{label} (Current)'
    },
    errorReferenceToast: {
        copyAriaLabel: 'Copy error reference id'
    },
    providerIcon: {
        defaultAlt: 'Provider icon'
    },
    gpsFiltering: {
        title: 'GPS Data Filtering',
        filterLabel: 'Filter inaccurate data points',
        filterHint: 'Enable to filter out GPS points with accuracy or speed beyond the defined limits.',
        maxAccuracyLabel: 'Max Allowed Accuracy (meters)',
        maxAccuracyPlaceholder: 'e.g., 100',
        maxAccuracyHint: 'Points with accuracy above this value will be rejected.',
        maxSpeedLabel: 'Max Allowed Speed (km/h)',
        maxSpeedPlaceholder: 'e.g., 250',
        maxSpeedHint: 'Points with speed above this value will be rejected.',
        duplicateDetectionTitle: 'Duplicate Detection',
        duplicateDetectionLabel: 'Enable duplicate detection',
        duplicateDetectionHint: 'Skip GPS points that have the same location within a time window. Useful for devices that send repeated locations when stationary.',
        thresholdLabel: 'Time threshold (minutes)',
        thresholdPlaceholder: 'e.g., 2',
        thresholdHint: 'Points with the same coordinates (within ~11m) in this time window will be skipped. Leave empty to use global default.'
    },
    pullToRefresh: {
        pullText: 'Pull to refresh',
        readyText: 'Release to refresh',
        refreshingText: 'Refreshing...'
    },
    tipOfDayCard: {
        nextTip: 'Next tip',
        noTipTitle: 'No tip available yet',
        noTipDescription: 'Tips will appear here when home content is available.'
    },
    dateRangePicker: {
        labelDefault: 'Select Dates',
        placeholderDefault: 'Select date range',
        presetPlaceholderDefault: 'Select Preset',
        presets: {
            today: 'Today',
            yesterday: 'Yesterday',
            last7Days: 'Last 7 days',
            last30Days: 'Last 30 days'
        },
        maxRangeError: 'Maximum range is {days} days',
        periodFallbackName: 'Period',
        labelFallbackName: 'Label',
        timelineLabelsGroup: 'Timeline Labels',
        presetsGroup: 'Presets'
    },
    transportTypeCard: {
        enableAriaLabel: 'Enable {title} detection',
        alwaysActive: 'Always Active',
        expandAriaLabel: 'Expand {title}',
        collapseAriaLabel: 'Collapse {title}',
        disabledMessage: '{title} detection is currently disabled. Enable to configure thresholds.'
    },
    appLayout: {
        viewTimelineJob: 'View timeline job',
        whatsNew: "What's new",
        readFullReleaseNotes: 'Read full release notes',
        gotIt: 'Got it',
        notificationDefaultSummary: 'Notification',
        viewAllNotifications: 'View all notifications',
        openNotification: 'Open Notification'
    },
    exploreFeatures: {
        ariaLabel: 'Explore GeoPulse',
        kicker: 'Explore GeoPulse',
        tabsAriaLabel: 'GeoPulse features',
        readDocs: 'Read docs',
        openInApp: 'Open in app',
        liveTracking: {
            tabLabel: 'Live Tracking',
            title: 'Real-time Source Integrations',
            description: 'Connect OwnTracks, Overland, Traccar, GPSLogger, Dawarich, Home Assistant, and Colota into one timeline.',
            highlights: [
                'Supports HTTP and MQTT ingestion across supported trackers',
                'Per-source GPS filtering improves timeline quality',
                'Combine multiple devices/sources into a single history'
            ]
        },
        imports: {
            tabLabel: 'Imports',
            title: 'Import & Migrate History',
            description: 'Import legacy and backup data with background processing and timeline regeneration.',
            highlights: [
                'Supports GeoPulse, OwnTracks, Google Timeline, GPX, GeoJSON, and CSV',
                'Date-range filtering for partial imports',
                'Replace-or-merge workflow for safe reimports'
            ]
        },
        timeline: {
            tabLabel: 'Timeline',
            title: 'Stays, Trips, and Gaps',
            description: 'GeoPulse turns raw points into timeline events, then lets you refine routes and correct missed stops.',
            highlights: [
                'Automatic stay/trip/gap detection',
                'Configurable travel modes from walking and cycling to motorcycle, train, flight, and boat',
                'Split a missed stop into Trip → Stay → Trip; the correction survives regeneration',
                'Optional Valhalla Map Matching follows roads while raw GPS remains authoritative'
            ]
        },
        insight: {
            tabLabel: 'Insights',
            title: 'Dashboard & Journey Insights && Location Analytics  ',
            description: 'Track movement patterns, places, routes, and achievements across multiple time windows.',
            highlights: [
                'Selected period + 7-day + 30-day overviews',
                'Top places, route analysis, and activity breakdowns',
                'Journey Insights for countries/cities/travel milestones'
            ]
        },
        friends: {
            tabLabel: 'Friends',
            title: 'Friends & Sharing Controls',
            description: 'Connect with friends and control exactly what you share.',
            highlights: [
                'Invite, accept, reject, and cancel friend requests',
                'Separate permissions for live location and timeline history',
                'Live map and shared timeline views, including embeddable shared locations'
            ]
        },
        geofences: {
            tabLabel: 'Geofences',
            title: 'Geofence Rules & Events',
            description: 'Create enter/leave rules with in-app notifications and optional external delivery.',
            highlights: [
                'Track selected subjects with configurable rule conditions',
                'Template-based notifications with macro support',
                'Events tab with unread filtering and seen management'
            ]
        },
        immich: {
            tabLabel: 'Immich',
            title: 'Immich Photo Overlay',
            description: 'Display Immich photos directly on the timeline map for the selected date range.',
            highlights: [
                'Configure URL + API key in Profile',
                'Toggle photo layer on Timeline map',
                'Open photos in-app and download originals'
            ]
        },
        weather: {
            tabLabel: 'Weather',
            title: 'Weather Along Your Timeline',
            description: 'Add local conditions to the places and journeys in your location history.',
            highlights: [
                'See temperature, precipitation, wind, and conditions on stays, trips, and Timeline maps',
                'Compare weather patterns in Journey Insights and earn weather-related badges',
                'Ongoing collection uses Open-Meteo by default; admins can opt into historical backfill'
            ]
        },
        ai: {
            tabLabel: 'AI',
            title: 'AI Assistant & MCP',
            description: 'Ask natural-language questions or connect an AI client to your personal movement history.',
            highlights: [
                'Bring your own OpenAI-compatible API key/model with encrypted per-user settings',
                'Query stays, trips, places, and travel patterns',
                'MCP is enabled by default for API-token-authenticated, read-only AI tools'
            ]
        }
    },
    onboardingTour: {
        welcomeTitle: '🎉 Welcome to GeoPulse!',
        welcomeDescription: "Ready to start tracking your location journey? Let's set up your first location source.",
        startTour: '🚀 Start Tour',
        exploreOnMyOwn: "I'll explore myself",
        closeTour: 'Close tour',
        stepCounter: '{current} of {total}',
        previous: 'Previous',
        next: 'Next',
        getStarted: 'Get Started!',
        steps: {
            welcome: {
                title: 'Welcome to GeoPulse!',
                description: "First, let's set up your location sources so we can start tracking your journey."
            },
            addSource: {
                title: 'Add Your First Location Source',
                description: 'Click the "Add New Source" button to add OwnTracks or Overland as your location source.'
            },
            followInstructions: {
                title: 'Follow Setup Instructions',
                description: "Once you add a source, you'll see detailed setup instructions with your unique endpoint and token."
            },
            allSet: {
                title: "You're All Set!",
                description: 'Once your location source is configured, visit your Timeline to see your location history and explore GeoPulse!'
            }
        }
    },
    appNavbar: {
        demoBadge: 'DEMO',
        locationSharingEnabledTooltip: 'Location sharing enabled',
        locationSharingDisabledTooltip: 'Location sharing disabled',
        shareLocation: 'Share Location',
        inviteFriend: 'Invite Friend',
        inviteFriendDisabledAriaLabel: 'Invite Friend disabled in demo mode',
        inviteFriendDisabledTooltip: 'Invitations are disabled in demo mode'
    },
    globalSearch: {
        bar: {
            placeholder: 'Search locations, pages, settings...',
            groupPages: 'Pages',
            groupSettings: 'Settings',
            groupLocations: 'Locations',
            visitsCount: '{count} visit | {count} visits',
            sinceDate: 'Since {date}'
        },
        settingsTrigger: {
            defaultPlaceholder: 'Search settings...',
            defaultButtonLabel: 'Find Setting'
        },
        registry: {
            tabAuthentication: 'Authentication',
            tabGeocoding: 'Geocoding',
            tabWeather: 'Weather',
            tabPlaceDiscovery: 'Place discovery',
            tabMapMatching: 'Map Matching',
            tabAiAssistant: 'AI Assistant',
            tabImport: 'Import',
            tabExport: 'Export',
            tabBackup: 'Backup and Restore',
            tabNotifications: 'Notifications',
            tabSystem: 'System',
            timelineTabStayPoints: 'Stay Point Detection',
            timelineTabTrips: 'Trip Classification',
            timelineTabGpsGaps: 'GPS Gaps Detection',
            timelineTabMerging: 'Stay Point Merging',
            subtitleTimelinePreferences: 'Timeline Preferences / {tab}',
            subtitleProfile: 'Profile / {subtitle}',
            subtitleSystemSettings: 'System Settings / {tab}',
            subtitlePage: 'Page',
            subtitleAdministration: 'Administration'
        }
    },
    charts: {
        barChart: {
            defaultTitle: 'Data',
            seriesFallback: 'Series {number}',
            valueKm: '{value} km'
        }
    }
}
