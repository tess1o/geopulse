/**
 * Trip Classification dialog (components/dialogs/TripClassificationDialog.vue and its
 * components/dialogs/classification/* presentational sub-components).
 *
 * Movement-type labels for the Select/options and the "current"/"algorithm" classification tags
 * reuse the shared `movementTypes.*` catalog rather than duplicating them here.
 */
export default {
    dialog: {
        header: 'Trip Classification Details',
        loading: 'Loading classification details...',
        close: 'Close'
    },
    overview: {
        title: 'Trip Overview',
        startTime: 'Start Time',
        duration: 'Duration',
        distance: 'Distance',
        effectiveClassification: 'Effective Classification',
        automaticClassification: 'Automatic Classification',
        classificationSource: 'Classification Source',
        manualOverrideActive: 'Manual override is active for this trip.'
    },
    editSection: {
        title: 'Edit Movement Type',
        unrecognizedWarning: 'Algorithm did not recognize this movement type. Set it manually below.',
        overrideInfo: 'If this looks incorrect, override it manually.',
        readOnlyError: 'Movement type edits are read-only in demo mode.',
        selectPlaceholder: 'Select movement type',
        saveButton: 'Save Override',
        resetButton: 'Reset to Automatic'
    },
    stats: {
        title: 'GPS Statistics',
        avgSpeed: 'Average GPS Speed',
        maxSpeed: 'Max GPS Speed',
        calculatedAvgSpeed: 'Calculated Avg Speed',
        calculatedAvgSpeedHint: 'From distance/duration',
        speedVariance: 'Speed Variance',
        lowAccuracyPoints: 'Low Accuracy Points',
        gpsReliability: 'GPS Reliability',
        reliable: 'Reliable',
        unreliable: 'Unreliable',
        reliableHint: 'GPS speeds are within expected range and used for classification',
        unreliableHint: 'GPS speeds unreliable - using calculated speed from distance/duration instead'
    },
    priority: {
        title: 'Classification Priority Order',
        description: 'Trips are classified in priority order from left to right. Once a match is found, classification stops.',
        learnMore: 'Learn more'
    },
    steps: {
        title: 'Classification Analysis',
        description: 'Detailed threshold checks for automatic classification.'
    },
    finalDecisionLabel: 'Final Decision:',
    na: 'N/A',
    toasts: {
        loadFailedFallback: 'Failed to load classification details',
        loadFailedDetail: 'Could not load trip classification details',
        movementUpdatedSummary: 'Movement Type Updated',
        movementUpdatedDetail: 'Trip marked as {type}',
        updateFailedSummary: 'Update Failed',
        updateFailedFallback: 'Could not update movement type',
        movementResetSummary: 'Movement Type Reset',
        movementResetDetail: 'Trip classification reset to {type}',
        resetFailedSummary: 'Reset Failed',
        resetFailedFallback: 'Could not reset movement type'
    },
    stepCard: {
        notEnabled: 'Not Enabled',
        passed: 'Passed',
        failed: 'Failed',
        thresholdChecksHeader: 'Threshold Checks:',
        actual: '(actual: {value})'
    }
}
