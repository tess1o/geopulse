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
        geocodingComplete: 'Geocoding complete: {favoritesResolved} favorites, {cachedResolved} cached, {externalCompleted} external API calls'
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
            currentStep: 'Current Step:',
            stepOf: '{current} of {total}'
        },
        stepsTitle: 'Processing Steps',
        steps: {
            acquiringLock: {
                title: 'Acquiring Lock',
                description: 'Ensuring exclusive access to timeline data'
            },
            cleaningUp: {
                title: 'Cleaning Up',
                description: 'Removing old timeline events'
            },
            preparingGpsProcessing: {
                title: 'Preparing GPS Processing',
                description: 'Counting GPS points and preparing streaming iterator'
            },
            processingGeocoding: {
                title: 'Processing & Geocoding',
                description: 'Streaming and processing GPS points through state machine, then resolving location names'
            },
            postProcessingTrips: {
                title: 'Post-Processing Trips',
                description: 'Validating and refining trip detection'
            },
            mergingSimplifying: {
                title: 'Merging & Simplifying',
                description: 'Applying timeline optimizations'
            },
            persistingTimeline: {
                title: 'Persisting Timeline',
                description: 'Saving timeline events to database'
            },
            dataGapDetection: {
                title: 'Data Gap Detection',
                description: 'Identifying gaps in GPS coverage'
            },
            finalizing: {
                title: 'Finalizing',
                description: 'Calculating milestones and completing generation'
            }
        },
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
        completion: {
            title: 'Timeline Generation Complete!',
            message: 'Your timeline has been successfully regenerated with all the latest GPS data.'
        },
        durationZero: '0s',
        durationSeconds: '{seconds}s',
        durationMinutesSeconds: '{minutes}m {seconds}s',
        durationHoursMinutesSeconds: '{hours}h {minutes}m {seconds}s'
    }
}
