/**
 * Timeline & Map tab.
 *
 * EN values verbatim from the previous literals. Note `mapMatching.descriptionUnavailable` starts with
 * 'Unavailable', which profileTabsDirtyState.test.js asserts on.
 */
export default {
    title: 'Timeline & Map',
    description: 'Customize timeline and map presentation without regenerating timeline data.',
    behavior: {
        heading: 'Timeline behavior',
        description: 'Choose what appears when you open and interact with the timeline.',
        defaultDateRange: {
            title: 'Default date range',
            description: 'Choose the initial range for Timeline, Dashboard, and Timeline Reports.',
            details: 'Clear the selection to use the app default: Today.',
            placeholder: 'Use app default (Today)'
        },
        telemetry: {
            title: 'Current-location telemetry',
            description: 'Show telemetry values in the current-location map popup.',
            details: 'This affects only popup visibility. Telemetry storage and GPS Data table are unchanged.'
        },
        replayControls: {
            title: 'Auto-show replay controls',
            description: 'Open the replay control bar when a trip is selected.',
            details: 'When disabled, trip replay remains available from a compact Replay button.'
        }
    },
    sources: {
        heading: 'Map display & sources',
        description: 'Choose how maps are rendered and where their visual data comes from.',
        renderMode: {
            title: 'Map render mode',
            description: 'Choose the renderer used throughout the map views.',
            details: 'Switching modes keeps both custom source URLs.'
        },
        buildings3d: {
            title: '3D buildings',
            description: 'Show building shapes on compatible vector maps.',
            details: 'Available only for MapTiler styles with building height data. Buildings appear when you zoom in to street level.'
        },
        rasterTiles: {
            title: 'Custom raster tiles',
            description: 'Optional source used when Raster mode is selected.',
            details: "The URL template must use HTTP or HTTPS and include {'{z}'}, {'{x}'}, and {'{y}'}. Leave empty to use OpenStreetMap.",
            ariaLabel: 'Custom raster tile URL'
        },
        vectorStyle: {
            title: 'Custom vector style',
            description: 'Optional style used when Vector mode is selected.',
            details: 'Enter an HTTP or HTTPS style JSON URL. Leave empty to use OpenFreeMap.',
            ariaLabel: 'Custom vector style URL'
        }
    },
    processing: {
        heading: 'Map processing',
        description: 'Control optional route matching and display performance.',
        mapMatching: {
            title: 'Map matching',
            descriptionAvailable: 'Display cached matched trip geometry when available',
            descriptionUnavailable: 'Unavailable until an administrator configures a Valhalla service.',
            detailsAvailable: 'Requires a configured Valhalla instance. Raw GPS data, exports, and timeline detection are unchanged.',
            detailsUnavailable: 'An administrator must enable Map Matching and configure Valhalla before you can turn this on.'
        },
        rawGps: {
            title: 'Show raw GPS for',
            description: 'Keep the original GPS path for selected movement types.',
            details: 'Matching may still run in the background, but matched geometry, progress, comparison controls, and details stay hidden.',
            placeholder: 'Use matched routes for all supported types',
            ariaLabel: 'Movement types that show raw GPS'
        },
        simplification: {
            title: 'Path simplification',
            description: 'Reduce the number of points drawn for a trip.',
            details: 'Uses the Douglas-Peucker algorithm to simplify paths without affecting your timeline data.'
        },
        tolerance: {
            title: 'Simplification tolerance',
            description: 'Set the distance threshold used to simplify paths.',
            detailLowerLabel: 'Lower values (1-10m)',
            detailLowerValue: 'Preserve more detail, show more points',
            detailHigherLabel: 'Higher values (20-100m)',
            detailHigherValue: 'More compression, show fewer points'
        },
        maxPoints: {
            title: 'Maximum points',
            description: 'Limit how many GPS points are displayed in a path.',
            details: 'If a path exceeds this limit, tolerance is automatically increased. Set to 0 for no limit.'
        },
        adaptive: {
            title: 'Adaptive simplification',
            description: 'Adjust simplification automatically based on trip length.',
            details: 'Longer trips use higher tolerance for better performance; shorter trips retain more detail.'
        }
    },
    toggleAria: {
        mapMatching: 'Enable map matching'
    },
    // Trailing units appended to slider values by SliderControl.
    pointsSuffix: ' points',
    toleranceLabels: {
        low: '1m (High detail)',
        mid: '15m (Balanced)',
        high: '50m (High compression)'
    },
    maxPointsLabels: {
        none: '0 (No limit)',
        balanced: '100 (Balanced)',
        high: '500 (High limit)'
    },
    dateRangeOptions: {
        today: 'Today',
        yesterday: 'Yesterday',
        lastWeek: 'Last 7 days',
        lastMonth: 'Last 30 days'
    },
    renderModeOptions: {
        vector: 'Vector (MapLibre)',
        raster: 'Raster (Leaflet)'
    },
    resetToDefaults: 'Reset to Defaults',
    saveChanges: 'Save Changes',
    validation: {
        tilePlaceholders: "URL must contain {'{z}'}, {'{x}'}, and {'{y}'} placeholders",
        protocol: 'URL must use HTTP or HTTPS protocol',
        invalidProtocol: 'Invalid URL protocol',
        invalidFormat: 'Invalid URL format',
        styleEndpoint: 'URL should point to a style JSON endpoint'
    }
}
