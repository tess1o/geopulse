export default {
    // Live progress messages sent by the backend as a MessageDescriptor ({key, parameters, fallback}) via
    // `formatMessageDescriptor()`. Keys here are the `timelineJobs.progressMessages.*` suffixes the
    // backend appends (see TimelineJobProgressService.step()). Not every backend-recognized key has an
    // entry -- an unmapped key safely falls back to the backend's English `fallback` text.
    progressMessages: {
        initializing: 'Initializing timeline generation',
        acquiringLock: 'Acquiring timeline lock',
        cleaningUp: 'Cleaning up old timeline data',
        preparingGpsProcessing: 'Preparing GPS data processing',
        noGpsData: 'No GPS data to process',
        readyToProcess: 'Ready to process {totalGpsPoints} GPS points',
        processingStateMachine: 'Processing GPS points through state machine',
        postProcessingTrips: 'Post-processing trips and validating detections',
        mergingTimeline: 'Merging timeline',
        persistingTimeline: 'Persisting timeline events to database',
        detectingDataGaps: 'Detecting data gaps',
        finalizing: 'Finalizing timeline generation',
        autoMatchingVisits: 'Auto-matching planned visits',
        recalculatingBadges: 'Recalculating achievement badges',
        completed: 'Timeline generation completed',
        preparingBoatSetup: 'Preparing Boat setup',
        boatEvidenceReady: 'Boat water evidence ready',
        regenerationQueued: 'Forced timeline regeneration queued',
        startingLocationResolution: 'Starting location resolution',
        allResolvedFromFavorites: 'All locations resolved from favorites',
        resolvedFromFavorites: 'Resolved {favoritesResolved} locations from favorites',
        resolvedFromCache: 'Resolved {cachedResolved} from cache ({batchResolved} from batch, {individualResolved} from individual lookup)',
        geocodingLocationProgress: 'Geocoding location {current}/{total}',
        geocodingComplete: 'Geocoding complete: {favoritesResolved} favorites, {cachedResolved} cached, {externalCompleted} external API calls',
        applyingCorrections: 'Applying corrections'
    },
    // The steps a job reports (TimelineJobProgressService groups its progress messages into these); a step the
    // catalog does not know falls back to the backend's English title
    steps: {
        queued: 'Waiting to start',
        preparing: 'Preparing',
        processingPoints: 'Processing GPS points',
        correctionWindow: 'Rebuilding the corrected period',
        geocoding: 'Naming stays',
        postProcessingTrips: 'Post-processing trips',
        merging: 'Merging stays',
        corrections: 'Applying corrections',
        persisting: 'Saving the timeline',
        finalizing: 'Finalizing',
        badges: 'Recalculating badges'
    },
    // What started a job (TimelineJobType)
    types: {
        FULL_REBUILD: 'Full regeneration',
        PARTIAL_REBUILD: 'Partial regeneration',
        CORRECTION: 'Correction',
        IMPORT: 'Import',
        CAMPAIGN: 'Regeneration campaign',
        OTHER: 'Timeline job'
    },
    listPage: {
        title: 'Timeline Generation Jobs',
        description: 'View timeline generation work that is running now or finished recently.',
        loading: 'Looking for active jobs...',
        noJob: {
            title: 'No timeline jobs are running now',
            message: 'If you opened this from a notification, the scheduled refresh may already be complete. Recent completed or failed jobs are shown below when available.',
            hint: 'Timeline jobs can be started by timeline preference changes, favorite updates, manual regeneration, or scheduled GeoPulse maintenance.',
            viewTimelineButton: 'View Timeline'
        },
        error: {
            title: 'Failed to check for active jobs'
        },
        history: {
            title: 'Job History',
            description: 'Recent timeline generation jobs',
            loading: 'Loading job history...',
            empty: 'No recent timeline jobs found.',
            duration: 'Duration: {duration}',
            gpsPoints: '{count} GPS points',
            viewDetails: 'View Details'
        },
        notAvailable: 'N/A',
        durationSeconds: '{seconds}s',
        durationMinutesSeconds: '{minutes}m {seconds}s'
    },
    detailsPage: {
        title: 'Timeline Generation Progress',
        description: 'Real-time progress tracking for timeline regeneration job.',
        backButton: 'Back to Timeline',
        loading: 'Loading job details...',
        error: {
            title: 'Failed to load job details'
        },
        jobIdLabel: 'Job ID: {jobId}',
        status: {
            loading: 'Loading...',
            queued: 'Queued for Processing',
            running: 'Processing Timeline',
            completed: 'Completed Successfully',
            failed: 'Failed'
        },
        details: {
            started: 'Started:',
            completed: 'Completed:',
            duration: 'Duration:',
            type: 'Type:',
            gpsPoints: 'GPS points:',
            produced: 'Produced:',
            producedValue: '{stays} stays, {trips} trips, {gaps} data gaps',
            corrections: 'Corrections:',
            correctionsValue: '{applied} applied, {notApplied} not applied',
            memoryLimit: 'Memory limit:',
            noMemoryLimit: 'None (heap up to {heap})',
            cpus: 'CPUs:'
        },
        stepsTitle: 'Processing Steps',
        stepBadges: {
            slowest: 'Slowest',
            memoryPeak: 'Memory peak',
            failed: 'Failed here',
            pending: 'Pending'
        },
        stepMemory: {
            label: 'Highest memory:',
            heap: 'heap {value}',
            process: 'process {value}',
            container: 'container {value}'
        },
        stepShare: '{percent}% of the job',
        stepShareTiny: '<1% of the job',
        durationUnderSeconds: '<{seconds}s',
        detail: {
            gpsPoints: 'GPS Points: {loaded} / {total}',
            processingHeader: 'Processing GPS Points: {processed} / {total}',
            remaining: 'Remaining: {count} points',
            geocodingHeader: 'Reverse Geocoding: {resolved} / {total} locations',
            favoritesInstant: 'Favorites (Instant): {count}',
            cachedInDatabase: 'Cached in Database: {count}',
            externalApiCalls: 'External API Calls: {count}',
            pending: 'Pending: {count}'
        },
        diagnostics: {
            button: 'Copy diagnostics',
            title: 'Job diagnostics',
            description: 'Steps, timings, memory, version and settings of this job, for a support request or an issue. It contains no coordinates, place names, notes or event times.',
            copy: 'Copy',
            copied: 'Diagnostics copied to the clipboard',
            copyFailed: 'Could not copy. Select the text and copy it instead.',
            loadFailed: 'Could not load the diagnostics'
        },
        completion: {
            title: 'Timeline Generation Complete!',
            message: 'Your timeline has been successfully regenerated with all the latest GPS data.'
        },
        durationZero: '0s',
        durationSeconds: '{seconds}s',
        durationMinutesSeconds: '{minutes}m {seconds}s',
        durationHoursMinutesSeconds: '{hours}h {minutes}m {seconds}s'
    },
    // Toasts and dialogs shown while a settings change starts a timeline regeneration (useTimelineRegeneration)
    regeneration: {
        alreadyRunning: {
            header: 'Timeline Job In Progress',
            message: 'A timeline generation job is already running ({progress}% complete). Please wait for it to finish.',
            viewProgress: 'View Progress'
        },
        savedSummary: 'Saved',
        queuedDetail: 'Changes saved. Your timeline will be updated in the background.'
    }
}
