/**
 * Timeline page, its preferences, labels, reports, and the timeline components.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    locationEdit: {
        editFavoriteHeader: 'Edit Favorite Location'
    },
    notes: {
        openSingle: 'Open note',
        openMultiple: 'Open {count} notes'
    },
    photos: {
        openSingle: 'Open photo',
        openMultiple: 'Open {count} photos',
        fallbackAlt: 'Photo'
    },
    dataGap: {
        subtitle: 'Data Gap - Unknown Activity',
        endTimeLabel: 'End Time:',
        durationLabel: 'Duration:',
        totalDuration: 'Total duration:',
        onThisDay: 'On this day:',
        convertTooltip: 'Convert this data gap to a stay',
        convertButton: 'Convert to stay'
    },
    overnight: {
        continuedFrom: 'Continued from {time}'
    },
    localTime: {
        resolved: 'Local time in {zone}, from the nearest city {city} ({country}, {distance} km away)',
        beyondMaxDistance: 'Local timezone unknown: the nearest city {city} ({country}) is {distance} km away. Showing your profile timezone.',
        noGeonamesData: 'Local timezone unknown: GeoNames city data is not loaded on this server. Showing your profile timezone.',
        invalidTimezone: 'Local timezone unknown: the nearest city {city} ({country}) has an unrecognised timezone. Showing your profile timezone.',
        notProvided: 'Local timezone not available for this item. Showing your profile timezone.'
    },
    card: {
        showGpsPoints: 'Show GPS points',
        viewAllVisits: 'View all visits to this place',
        renamePlace: 'Rename place...',
        resetDataGap: 'Reset to automatic data gap',
        undoTripSplit: 'Undo manual trip split',
        manageNoteSingle: 'Manage note...',
        manageNotesMultiple: 'Manage notes ({count})...',
        viewNoteSingle: 'View note...',
        viewNotesMultiple: 'View notes ({count})...',
        addNote: 'Add note...',
        viewCityDetails: 'View {city} Details',
        viewCountryDetails: 'View {country} Details',
        exportGpx: 'Export as GPX',
        renameStayAria: 'Rename stay place',
        renameDisabledDemo: 'Rename is disabled in demo mode',
        resetDisabledDemo: 'Reset is disabled in demo mode'
    },
    stay: {
        stayedAt: 'Stayed at',
        manualIndicator: '(Manual)',
        forDuration: 'For'
    },
    trip: {
        transitionTo: 'Transition to',
        transitionToNewPlace: 'Transition to new place',
        durationLabel: 'Duration:',
        distanceLabel: 'Distance:',
        movementLabel: 'Movement:',
        editMovementType: 'Edit movement type',
        movementEditDisabledDemo: 'Movement type edits are disabled in demo mode',
        setMovementType: 'Set movement type',
        unrecognizedHint: 'Algorithm did not recognize this trip.',
        changeMovementType: 'Change movement type...',
        whyThisClassification: 'Why this classification?',
        splitTripWithStay: 'Split trip with stay...',
        mapMatchingDetails: 'Map matching details...'
    },
    explanation: {
        trigger: {
            stay: 'Why this stay? See its GPS points and how it was decided',
            trip: 'Why this trip? See its GPS points and how it was decided',
            dataGap: 'Why a data gap? See the GPS points around it and how it was decided'
        },
        toggle: {
            stay: 'Why this stay?',
            trip: 'Why this trip?',
            dataGap: 'Why a data gap?'
        },
        start: {
            FIRST_POINT: 'Starts with the first GPS point there is.',
            AFTER_DATA_GAP: 'Starts with the first GPS point after a data gap.',
            ARRIVAL: 'Starts where you stopped moving: {points} points slower than {stopSpeedKmh} km/h for {stopSeconds}.',
            SPARSE_STOP: 'Starts where you stopped: the next GPS point came {gapSeconds} later, only {distanceMeters} away.',
            LEFT_STAY: 'Starts when a point {distanceMeters} from the stay was recorded (stay radius {radiusMeters}).',
            SHORT_STOP: 'Starts with a stop of {stopSeconds}, too short to be a stay ({minMinutes} min needed), so it counts as part of this trip.',
            WATER_TRANSITION_TO_WATER: 'Starts where you moved from land onto water.',
            WATER_TRANSITION_TO_LAND: 'Starts where you moved from water onto land.',
            INFERRED_DURING_GAP: 'There are no GPS points here for {gapSeconds}, but the points before and after are only {distanceMeters} apart, so you probably stayed here.',
            INFERRED_TRIP_DURING_GAP: 'There are no GPS points here for {gapSeconds}; the points before and after are {distanceMeters} apart, so you must have travelled.',
            CONTINUITY: 'No trip was detected between the stays before and after, which are {distanceMeters} apart, so this trip connects them.',
            NO_POINTS: 'No GPS points for {gapSeconds}. Breaks longer than {thresholdSeconds} are shown as data gaps.',
            NO_POINTS_SINCE_LAST: 'No GPS points since the last one. A break longer than {thresholdSeconds} is shown as a data gap until new points arrive.',
            STOPPED_BEFORE_GAP: 'Starts where you stopped, right before a data gap.',
            MANUAL_SPLIT: 'You added this stay by splitting a trip.',
            MANUAL_GAP_STAY: 'You turned a data gap into this stay.'
        },
        note: {
            CONFIRMED: 'Counts as a stay: the points stayed within {radiusMeters} for at least {minMinutes} min.',
            FAVORITE_AREA: 'Points outside the stay radius were kept in this stay because they are in the same favorite area.',
            CONTINUED_OVER_GAP: 'Continues over a {gapSeconds} data gap: the points before and after it are in the same place.',
            MERGED: '{stays} stays at the same place were merged into this one.',
            JOINED_SEGMENTS: '{segments} parts of movement were joined into this trip.',
            SHORT_STOP: 'Includes a stop of {stopSeconds}, too short to be a stay ({minMinutes} min needed).',
            MANUAL_SPLIT: 'Changed by your manual trip split.',
            MANUAL_GAP_STAY: 'Extended by your manual data gap conversion.'
        },
        end: {
            MOVED_AWAY: 'Ends when a point {distanceMeters} away was recorded (stay radius {radiusMeters}).',
            ARRIVAL: 'Ends where you stopped moving: {points} points slower than {stopSpeedKmh} km/h for {stopSeconds}.',
            SPARSE_STOP: 'Ends where you stopped: the next GPS point came {gapSeconds} later, only {distanceMeters} away.',
            DATA_GAP: 'Ends at a data gap of {gapSeconds}.',
            LAST_POINT: 'Ends with the last GPS point there is, so it may still be going on.',
            WATER_TRANSITION_TO_WATER: 'Ends where you moved from land onto water.',
            WATER_TRANSITION_TO_LAND: 'Ends where you moved from water onto land.',
            STOPPED_BEFORE_GAP: 'Ends where you stopped, right before a data gap.'
        }
    },
    explanationDialog: {
        header: {
            stay: 'Why this stay?',
            trip: 'Why this trip?',
            dataGap: 'Why a data gap?'
        },
        loading: 'Loading GPS points...',
        loadFailed: 'Could not load the explanation.',
        period: '{start} – {end} ({duration})',
        sections: {
            map: 'GPS points',
            steps: 'How it was decided',
            filtered: 'Points left out',
            ingestion: 'Points that were never stored'
        },
        legend: {
            used: 'Used',
            filtered: 'Left out (inaccurate)',
            context: 'Before and after',
            deciding: 'Made a decision',
            radius: 'Stay radius',
            anchor: 'Saved place'
        },
        mapHint: 'Select a step to see its points on the map. Select a point for details.',
        noPoints: 'There are no stored GPS points for this time.',
        truncated: 'This event has {total} GPS points. The map shows {shown} of them, including every point that was left out or made a decision.',
        steps: {
            any: {
                start: {
                    FIRST_POINT: 'Starts at {time} with your first GPS point.',
                    AFTER_DATA_GAP: 'Starts at {time}, with the first GPS point after a break in your data.',
                    ARRIVAL: 'Starts at {time}, where you stopped moving.',
                    SPARSE_STOP: 'Starts at {time}, where you stopped. No GPS points were recorded while you were here: the next one came {gapSeconds} later, only {distanceMeters} away.',
                    LEFT_STAY: 'Started at {time}, when you were {distanceMeters} from the place you were staying, more than its {radiusMeters} radius.',
                    SHORT_STOP: 'Starts with a stop from {time} to {endTime} ({stopSeconds}). A stay needs at least {minMinutes}, so the stop is counted as part of this trip.',
                    WATER_TRANSITION_TO_WATER: 'Starts at {time}, where you moved from land onto water.',
                    WATER_TRANSITION_TO_LAND: 'Starts at {time}, where you moved from water onto land.',
                    INFERRED_DURING_GAP: 'There is no GPS data for {gapSeconds}, but the points before and after the break are only {distanceMeters} apart, so GeoPulse assumes you stayed here.',
                    INFERRED_TRIP_DURING_GAP: 'There is no GPS data for {gapSeconds}, and the points before and after the break are {distanceMeters} apart, so you must have travelled in between.',
                    CONTINUITY: 'No movement was recorded between the stays before and after this trip, but they are {distanceMeters} apart, so GeoPulse adds this trip to connect them. It has no GPS points of its own.',
                    STOPPED_BEFORE_GAP: 'You stopped here at {time}, right before your GPS data stopped, and were in the same place when it came back.',
                    MANUAL_SPLIT: 'You added this by splitting a trip.',
                    MANUAL_GAP_STAY: 'You turned a data gap into this stay.',
                    NO_POINTS: 'No GPS points from {time} to {endTime} ({gapSeconds}). Breaks longer than {thresholdSeconds} are shown as data gaps.',
                    NO_POINTS_SINCE_LAST: 'No GPS points since {time}. A break longer than {thresholdSeconds} is shown as a data gap until new points arrive.'
                },
                note: {
                    CONFIRMED: 'Counted as a stay from {time}: by then you had been within {radiusMeters} of this place for {minMinutes}.',
                    FAVORITE_AREA: '{count} points were farther from the centre than the stay radius, but they are in the same favorite area, so they stayed part of this stay.',
                    CONTINUED_OVER_GAP: 'There is no GPS data from {time} to {endTime} ({gapSeconds}), but you were in the same place before and after, so the stay continues over the break.',
                    MERGED: '{stays} stays at this place, close together in place and time, were merged into this one.',
                    JOINED_SEGMENTS: '{segments} trips in a row were joined into this one.',
                    SHORT_STOP: 'Includes a stop of {stopSeconds}, shorter than the {minMinutes} a stay needs.',
                    MANUAL_SPLIT: 'Changed by your manual trip split.',
                    MANUAL_GAP_STAY: 'Extended by your manual data gap conversion.'
                },
                end: {
                    MOVED_AWAY: 'Ended at {time}, when you were {distanceMeters} from the centre, more than the {radiusMeters} stay radius.',
                    ARRIVAL: 'Ended at {time}, when you stopped moving.',
                    SPARSE_STOP: 'Ended at {time}, where you stopped: the next GPS point came {gapSeconds} later, only {distanceMeters} away.',
                    DATA_GAP: 'Ended at {time}, when your GPS data stopped for {gapSeconds}.',
                    LAST_POINT: 'Your latest GPS point is at {time}, so this may still be going on.',
                    WATER_TRANSITION_TO_WATER: 'Ended at {time}, where you moved from land onto water.',
                    WATER_TRANSITION_TO_LAND: 'Ended at {time}, where you moved from water onto land.',
                    STOPPED_BEFORE_GAP: 'Ended at {time}, where you stopped right before your GPS data stopped.'
                }
            },
            stay: {
                start: {
                    ARRIVAL: 'You arrived at {time}, when you stopped moving.',
                    MANUAL_SPLIT: 'You added this stay by splitting a trip.'
                }
            }
        },
        callouts: {
            gapFiltered: '{count} GPS points were recorded during this time, but each was less accurate than {thresholdMeters} ({setting}), so they were left out and the time shows as a data gap.',
            settingChanged: '{setting} was {builtWith} when this was built and is {current} now. Regenerate the timeline to apply the new value.'
        },
        filtered: {
            none: 'Every GPS point of this event was used.',
            some: '{filtered} of {total} GPS points were left out because they were less accurate than {thresholdMeters} ({setting}).',
            worst: 'The least accurate one was ±{accuracyMeters}.',
            context: '{count} points before or after it were left out as well.',
            off: 'GPS points are not filtered by accuracy, because {setting} is off.'
        },
        ingestion: {
            intro: 'Your GPS sources drop some points before they are stored, so those points cannot be shown here:',
            source: '{source}: {rules}',
            accuracy: 'points less accurate than {value}',
            speed: 'points faster than {value}',
            duplicates: 'repeats of the same place within {value}',
            link: 'Location sources'
        },
        point: {
            used: 'Used for this event.',
            before: 'Before this event.',
            after: 'After this event.',
            filtered: 'Left out: accuracy ±{accuracyMeters}, worse than the {thresholdMeters} GPS Accuracy Threshold.',
            outsideRadius: '{distanceMeters} from the centre, outside the {radiusMeters} radius.',
            accuracy: 'Accuracy ±{value}',
            noAccuracy: 'No accuracy recorded',
            speed: 'Speed {value}',
            reason: {
                FIRST_POINT: 'Your first GPS point.',
                AFTER_DATA_GAP: 'The first point after a break in GPS data.',
                ARRIVAL: 'Part of the stop where you arrived.',
                SPARSE_STOP: 'Where you stopped: the next point came much later, close by.',
                LEFT_STAY: 'Started this trip: it is outside the stay radius.',
                SHORT_STOP: 'Part of a stop too short to be a stay.',
                WATER_TRANSITION: 'Where you moved between land and water.',
                INFERRED_DURING_GAP: 'Next to the break this stay was assumed over.',
                INFERRED_TRIP_DURING_GAP: 'Next to the break this trip was assumed over.',
                STOPPED_BEFORE_GAP: 'Where you stopped before the break.',
                NO_POINTS: 'Next to the break in GPS data.',
                NO_POINTS_SINCE_LAST: 'The last point before the break.',
                CONFIRMED: 'From here on it counted as a stay.',
                FAVORITE_AREA: 'Outside the stay radius but inside the favorite area.',
                CONTINUED_OVER_GAP: 'Next to a break the stay continued over.',
                MOVED_AWAY: 'Ended the stay: it is outside the stay radius.',
                DATA_GAP: 'Next to a break in GPS data.',
                LAST_POINT: 'Your latest GPS point.'
            }
        },
        settingsUsed: 'Settings',
        changeSettings: 'Timeline preferences',
        whyClassification: 'Why this movement type?',
        close: 'Close',
        on: 'on',
        off: 'off',
        minutes: '{value} min'
    },
    container: {
        noData: 'No timeline for the given date range.',
        showingItems: 'Showing {shown} of {total} items.',
        loadMore: 'Load More',
        viewAllInReports: 'Or view all in Timeline Reports',
        viewPeriodAria: 'View {name} period',
        showOnlyDayOnMap: 'Show only this day on the map',
        showAllDaysOnMap: 'Show all days on the map',
        exportStartedTitle: 'Export Started',
        tripExportingDetail: 'Trip is being exported as GPX',
        stayExportingDetail: 'Stay is being exported as GPX',
        exportFailedTitle: 'Export Failed',
        tripExportFailedDetail: 'Failed to export trip',
        stayExportFailedDetail: 'Failed to export stay'
    },
    focusedDay: {
        showing: 'Showing {day}',
        showAll: 'Show all days on the map'
    },
    splitLayout: {
        defaultCollapsedLabel: 'Timeline',
        defaultExpandedLabel: 'Movement Timeline',
        dayNavigationAriaLabel: 'Timeline day navigation',
        previousDay: 'Previous day',
        nextDay: 'Next day',
        show: 'Show {label}',
        collapse: 'Collapse {label}'
    },
    largeDataset: {
        title: 'Large Date Range Selected',
        itemsCount: 'This range contains {count} items',
        breakdown: '({stays} stays, {trips} trips)',
        breakdownWithGaps: '({stays} stays, {trips} trips, {dataGaps} data gaps)',
        recommendation: 'Timeline view works best for daily/weekly browsing. For analyzing large periods, use Timeline Reports:',
        openReports: 'Open Timeline Reports',
        orViewRecent: 'Or view recent data:',
        last7Days: 'Last 7 Days',
        last30Days: 'Last 30 Days',
        loadAnyway: 'Load anyway (may impact performance)',
        continueWithRange: 'Continue with Current Range'
    },
    notesViewer: {
        header: 'Notes',
        empty: 'No notes for this timeline item.',
        editNote: 'Edit note',
        deleteNote: 'Delete note',
        openInMemos: 'Open in Memos',
        truncatedNotice: 'This Memos note is large, so GeoPulse shows a truncated preview.',
        deleteConfirm: 'Delete this GeoPulse note?',
        cancel: 'Cancel',
        delete: 'Delete',
        close: 'Close',
        locationSources: {
            explicit: 'Geotagged',
            derivedStay: 'Stay location',
            derivedTripGps: 'Trip GPS',
            derivedTripInterpolated: 'Trip estimate'
        },
        toasts: {
            deletedTitle: 'Note deleted',
            deletedDetail: 'GeoPulse note was deleted',
            deleteFailedTitle: 'Delete failed',
            deleteFailedDetail: 'Failed to delete note'
        }
    },
    noteEditor: {
        titleLabel: 'Title',
        titlePlaceholder: 'Optional title',
        saveToLabel: 'Save to',
        visibilityLabel: 'Memos visibility',
        noteLabel: 'Note',
        contentPlaceholder: 'Write a note...',
        contentRequired: 'Note content is required',
        cancel: 'Cancel',
        editHeader: 'Edit Note',
        addHeader: 'Add Note',
        update: 'Update',
        save: 'Save',
        destinations: {
            geopulse: 'GeoPulse',
            memos: 'Memos'
        },
        visibilityOptions: {
            private: 'Private',
            protected: 'Protected',
            public: 'Public'
        },
        toasts: {
            updatedTitle: 'Note updated',
            savedTitle: 'Note saved',
            updatedDetail: 'GeoPulse note was updated',
            savedInMemos: 'Note was created in Memos',
            savedInGeoPulse: 'Note was saved in GeoPulse',
            saveFailedTitle: 'Save failed',
            saveFailedDetail: 'Failed to save note'
        }
    },
    dataGapHelp: {
        toggleAriaLabel: 'Toggle help information',
        explanation: 'Data gaps occur when GPS tracking was interrupted. You can adjust how GeoPulse handles gaps in Timeline Settings.',
        recommendationsHeader: 'Based on this {duration} gap:',
        openSettings: 'Open Timeline Settings',
        toggleShort: 'Need help with gaps?',
        toggleLong: 'Why is this happening?',
        tips: {
            lowerThreshold: 'Lower the Gap Detection Threshold to catch shorter gaps',
            enableStayInference: 'Enable Gap Stay Inference if you were at the same location',
            enableTripInference: 'Enable Gap Trip Inference if you traveled a long distance'
        }
    },
    layout: {
        collapsedLabel: 'Timeline',
        expandedLabel: 'Movement Timeline'
    },
    labels: {
        page: {
            title: 'Timeline Labels',
            subtitleLabelsCount: '{count} label | {count} labels',
            subtitleDaysTagged: '{count} day tagged | {count} days tagged',
            subtitleFallback: 'Advanced timeline labels (used by timeline chips and imports)'
        },
        actions: {
            create: 'Create Label',
            timeline: 'View Timeline',
            more: 'More'
        },
        help: 'Timeline labels tag a period of your timeline (like a trip or event) so it shows up as a quick filter and can be turned into a trip plan.',
        activeBanner: {
            kicker: 'Active Label',
            since: 'Since {date}'
        },
        sourceNames: {
            manual: 'Manual',
            ownTracks: 'OwnTracks'
        },
        filters: {
            search: 'Search labels...',
            source: {
                all: 'All Sources'
            },
            linkState: {
                all: 'All',
                linked: 'Linked',
                unlinked: 'Unlinked'
            }
        },
        bulkDelete: 'Delete ({count})',
        badges: {
            active: 'Active',
            hidden: 'Hidden',
            visible: 'Visible'
        },
        now: 'Now',
        table: {
            pageReport: 'Showing {first} to {last} of {totalRecords} labels',
            columns: {
                label: 'Label',
                period: 'Period',
                tripPlan: 'Trip Plan',
                preset: 'Preset',
                actions: 'Actions'
            },
            notLinked: 'Not linked',
            empty: 'No timeline labels found',
            actionsAria: 'More actions for {name}',
            selectAria: 'Select {name}'
        },
        cards: {
            period: 'Period',
            duration: 'Duration',
            datePreset: 'Preset',
            linkedTripPlan: 'Linked Trip Plan',
            showingCount: 'Showing {count} labels'
        },
        linkedDelete: {
            header: 'Delete Linked Label',
            linkedToLead: 'This timeline label is linked to trip plan',
            choose: 'How would you like to proceed?',
            cancel: 'Cancel',
            onlyLabel: 'Delete Label Only',
            labelAndTripPlan: 'Delete Label & Trip Plan'
        }
    },
    page: {
        workspaceBanner: {
            lead: 'This date range matches your trip plan:',
            openPlanner: 'Open Planner'
        },
        map: {
            noData: 'No location data for the selected date range.'
        },
        favorite: {
            deleteConfirm: {
                header: 'Delete Favorite',
                message: 'Are you sure you want to delete this favorite place?'
            },
            deleteSuccess: "'{name}' has been deleted",
            deleteFailed: 'Failed to delete favorite'
        },
        toasts: {
            noLocationData: 'No location data available for the selected date range.',
            locationFetchFailed: 'Failed to fetch location data',
            noTimelineData: 'No timeline data available for the selected date range.',
            timelineFetchFailed: 'Failed to fetch timeline data',
            timelineLoadFailed: 'Failed to load timeline data',
            viewingTag: 'Viewing {name}',
            tagPeriod: "Showing the full period for '{name}'"
        },
        resetDataGapOverride: {
            confirmHeader: 'Reset Data Gap',
            confirmMessage: 'Are you sure you want to reset this data gap back to automatic detection?',
            successSummary: 'Data Gap Reset',
            successDetail: 'The data gap has been reset to automatic detection.',
            failedDetail: 'Failed to reset the data gap override.'
        },
        resetFailedSummary: 'Reset Failed',
        resetTripSplitOverride: {
            confirmHeader: 'Undo Trip Split',
            confirmMessage: 'Are you sure you want to undo this manual trip split?',
            successSummary: 'Trip Split Undone',
            successDetail: 'The manual trip split has been undone.',
            failedDetail: 'Failed to undo the trip split.'
        },
        reconstruction: {
            updatedSummary: 'Timeline Updated',
            updatedDetail: 'Your timeline has been updated with the reconstructed data.',
            failedSummary: 'Reconstruction Failed',
            failedDetail: 'Failed to reconstruct timeline data.',
            trackingFailedSummary: 'Tracking Failed'
        }
    },
    preferences: {
        gpsPathSimplification: {
            title: 'GPS Path Simplification Settings',
            description: 'Configure how GPS paths are simplified to reduce data while preserving route accuracy',
            enabled: {
                title: 'Enable Path Simplification',
                description: 'Whether GPS path simplification is enabled for timeline trips',
                details: 'When enabled, trip paths will be simplified using the Douglas-Peucker algorithm to reduce the number of GPS points while preserving route accuracy'
            },
            tolerance: {
                title: 'Simplification Tolerance',
                description: 'Base tolerance in meters for GPS path simplification',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'Preserve more detail, less compression',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'More compression, less detail',
                labelLow: '1m (High detail)',
                labelMid: '15m (Balanced)',
                labelHigh: '50m (High compression)',
                suffix: ' m'
            },
            maxPoints: {
                title: 'Maximum Points',
                description: 'Maximum number of GPS points to retain in simplified paths',
                details: 'If a simplified path still exceeds this limit, tolerance will be automatically increased until the limit is met. Set to 0 for no limit',
                labelLow: '0 (No limit)',
                labelMid: '100 (Balanced)',
                labelHigh: '500 (High limit)',
                suffix: ' points'
            },
            adaptive: {
                title: 'Adaptive Simplification',
                description: 'Enables adaptive simplification that adjusts tolerance based on trip characteristics',
                details: 'When enabled, longer trips use higher tolerance values for better compression while shorter trips maintain higher accuracy with lower tolerance'
            }
        },
        stayMerging: {
            title: 'Stay Point Merging Settings',
            description: 'Configure how nearby stay points are consolidated into single locations',
            enabled: {
                title: 'Enable Stay Point Merging',
                description: 'Whether to merge nearby stay points that are close in time and distance',
                details: 'Helps consolidate multiple GPS clusters at the same general location into single stay points'
            },
            maxDistance: {
                title: 'Maximum Merge Distance',
                description: 'Maximum distance between stay points to consider them for merging',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'Only merge very close points',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'Merge points further apart',
                labelLow: '20m (Precise)',
                labelMid: '150m (Balanced)',
                labelHigh: '500m (Generous)',
                suffix: ' m'
            },
            maxTimeGap: {
                title: 'Maximum Time Gap',
                description: 'Maximum time gap between stay points to consider them for merging',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'Only merge consecutive stays',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'Merge stays separated by longer gaps',
                labelLow: '1 min (Strict)',
                labelMid: '10 min (Balanced)',
                labelHigh: '60 min (Generous)',
                suffix: ' min'
            }
        },
        gpsGaps: {
            title: 'GPS Gaps Detection Settings',
            description: 'Configure how GPS data gaps are detected and recorded in your timeline',
            threshold: {
                title: 'Data Gap Threshold',
                description: 'Maximum time gap in seconds allowed between GPS points before considering it a GPS data gap',
                details: 'When the time difference between two consecutive GPS points exceeds this threshold, a GPS Data Gap entity will be created instead of extending the current stay or trip. This prevents artificial extension of activities during periods of missing GPS data.',
                labelLow: '5 min (Sensitive)',
                labelMid: '3 hours (Balanced)',
                labelHigh: '6 hours (Lenient)',
                suffix: ' seconds'
            },
            minDuration: {
                title: 'Minimum Gap Duration',
                description: 'Minimum duration in seconds for a gap to be recorded as a GPS Data Gap',
                details: 'Gaps shorter than this threshold will be ignored to reduce noise. This prevents very short connectivity issues from creating unnecessary gap records.',
                labelLow: '5 min (Strict)',
                labelMid: '30 min (Balanced)',
                labelHigh: '2 hours (Lenient)',
                suffix: ' seconds'
            },
            stayInferenceEnabled: {
                title: 'Gap Stay Inference',
                description: 'Infer stays when GPS data gaps occur but locations before and after are the same',
                detailsWhenLabel: 'When enabled',
                detailsWhenValue: 'If you were at a location and GPS data stops, then resumes at the same location, the system infers you stayed there',
                detailsUseCaseLabel: 'Use case',
                detailsUseCaseValue: 'Overnight gaps at home will show as a stay instead of a data gap'
            },
            stayInferenceMaxGap: {
                title: 'Maximum Gap Duration for Inference',
                description: 'Maximum duration of GPS data gap to infer a stay',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'Only infer stays for shorter gaps (e.g., brief phone downtime)',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'Infer stays for longer gaps (e.g., overnight, full day)',
                labelLow: '1 hour (Strict)',
                labelMid: '24 hours (Default)',
                labelHigh: '72 hours (Lenient)',
                suffix: ' hours'
            },
            tripInferenceEnabled: {
                title: 'Gap Trip Inference',
                description: 'Infer trips when GPS data gaps occur with long-distance movement',
                detailsWhenLabel: 'When enabled',
                detailsWhenValue: 'If GPS data stops, then resumes at a distant location, the system infers a trip occurred',
                detailsUseCaseLabel: 'Use case',
                detailsUseCaseValue: 'Overnight flights or long drives where phone was off will show as inferred trips instead of data gaps',
                detailsClassificationLabel: 'Trip classification',
                detailsClassificationValue: 'Trip mode is automatically determined by distance, duration, and speed (e.g., flight, car, train)'
            },
            tripInferenceMinDistance: {
                title: 'Minimum Distance for Trip Inference',
                description: 'Minimum distance between GPS points to infer a trip during a gap',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'Infer trips for shorter movements (e.g., 10km city trips)',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'Only infer trips for longer movements (e.g., 100km+ intercity travel)',
                labelLow: '10 km (Short trips)',
                labelMid: '100 km (Default)',
                labelHigh: '500 km (Long distance)',
                suffix: ' m'
            },
            tripInferenceMinGap: {
                title: 'Minimum Gap Duration for Trip Inference',
                description: 'Minimum gap duration to consider for trip inference',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'Infer trips for brief gaps (e.g., 30 minutes)',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'Only infer trips for longer gaps (e.g., several hours)',
                labelLow: '0 hours (Any gap)',
                labelMid: '1 hour (Default)',
                labelHigh: '12 hours (Long gaps)',
                suffix: ' hours'
            },
            tripInferenceMaxGap: {
                title: 'Maximum Gap Duration for Trip Inference',
                description: 'Maximum gap duration to infer a trip (longer gaps become data gaps)',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'Only infer trips for shorter gaps (e.g., 12 hours)',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'Infer trips for longer gaps (e.g., multi-day trips)',
                labelLow: '1 hour (Strict)',
                labelMid: '24 hours (Default)',
                labelHigh: '168 hours (One week)',
                suffix: ' hours'
            }
        },
        stayPointDetection: {
            title: 'Stay Point Detection Settings',
            description: "Configure how GPS data is analyzed to identify places where you've stayed",
            radius: {
                title: 'Stay Detection Radius',
                description: 'Distance threshold for grouping GPS points into stay locations. Also defines minimum distance between stays to create a trip',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'More sensitive stay detection, separate nearby locations',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'Less sensitive, merge nearby locations into single stays',
                labelLow: '10m (Sensitive)',
                labelMid: '50m (Balanced)',
                labelHigh: '500m (Conservative)',
                suffix: ' m'
            },
            minDuration: {
                title: 'Minimum Stay Duration',
                description: 'The minimum duration (in minutes) for a stay point to be confirmed',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'Too short stays can be false positives',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'Longer stays are more likely to be real',
                labelLow: '1 min (Sensitive)',
                labelMid: '7 min (Balanced)',
                labelHigh: '60 min (Conservative)',
                suffix: ' min'
            },
            enhancedFiltering: {
                title: 'Enhanced Filtering',
                description: 'Use velocity and accuracy data for better stay point detection',
                details: 'Filters out poor quality GPS points and improves timeline accuracy'
            },
            velocityThreshold: {
                title: 'Velocity Threshold',
                description: 'Maximum velocity to consider a point as stationary',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'More strict filtering',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'Allow more movement within stays',
                labelLow: '1 km/h (Strict)',
                labelMid: '9 km/h (Balanced)',
                labelHigh: '20 km/h (Lenient)',
                suffix: ' km/h'
            },
            accuracyThreshold: {
                title: 'GPS Accuracy Threshold',
                description: 'Minimum GPS accuracy required to use a location point',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'Require more accurate GPS',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'Accept less accurate GPS points',
                labelLow: '5m (High accuracy)',
                labelMid: '60m (Balanced)',
                labelHigh: '200m (Low accuracy)',
                suffix: ' m'
            }
        },
        tripClassification: {
            title: 'Trip Classification Settings',
            description: 'Configure how trips are detected and classified by movement type',
            priorityBanner: {
                title: 'Classification Priority Order',
                steps: {
                    flight: 'FLIGHT',
                    boat: 'BOAT',
                    train: 'TRAIN',
                    bicycle: 'BICYCLE',
                    running: 'RUNNING',
                    motorVehicle: 'MOTOR VEHICLE',
                    walk: 'WALK',
                    unknown: 'UNKNOWN'
                },
                description: 'Trips are classified in priority order from top to bottom. Once a match is found, classification stops. This order handles water and speed evidence conservatively - e.g., over-water flights remain FLIGHT, and overlapping land speeds match BICYCLE before RUNNING and motor vehicles.'
            },
            algorithm: {
                title: 'Trip Detection Algorithm',
                description: 'The algorithm used to identify trips between stay points',
                detailsSingleLabel: 'Single',
                detailsSingleValue: 'Always one trip between stay points',
                detailsMultipleLabel: 'Multiple',
                detailsMultipleValue: 'Based on velocity one or more trips between stay points (like CAR → WALK, WALK → CAR, etc)',
                placeholder: 'Select algorithm',
                optionSingle: 'Single trip',
                optionMultiple: 'Multiple trips'
            },
            walk: {
                title: 'Walking',
                subtitle: 'Mandatory transport type',
                description: 'Detects slow-speed movement on foot (0-8 km/h typical)',
                maxAvgSpeed: {
                    label: 'Maximum Average Speed',
                    description: 'Trips with average speeds above this will be classified as non-walking',
                    labelLow: '3.0 km/h (Slow)',
                    labelMid: '5.5 km/h (Normal)',
                    labelHigh: '10.0 km/h (Fast)'
                },
                maxMaxSpeed: {
                    label: 'Maximum Peak Speed',
                    description: 'Brief speed bursts above this will reclassify the trip',
                    labelLow: '5.0 km/h (Conservative)',
                    labelMid: '8.0 km/h (Normal)',
                    labelHigh: '15.0 km/h (Generous)'
                }
            },
            bicycle: {
                title: 'Bicycle',
                subtitle: 'Optional transport type',
                description: 'Detects cycling trips (8-25 km/h typical range). Priority order ensures correct classification even with speed overlap with running and cars.',
                minAvgSpeed: {
                    label: 'Minimum Average Speed',
                    description: 'Trips slower than this will be classified as running or walking',
                    labelLow: '5.0 km/h (Slow)',
                    labelMid: '8.0 km/h (Default)',
                    labelHigh: '15.0 km/h (Fast)'
                },
                maxAvgSpeed: {
                    label: 'Maximum Average Speed',
                    description: 'Trips faster than this will be classified as motorized transport',
                    labelLow: '15.0 km/h (Slow)',
                    labelMid: '25.0 km/h (Default)',
                    labelHigh: '35.0 km/h (E-bike)'
                },
                maxMaxSpeed: {
                    label: 'Maximum Peak Speed',
                    description: 'Allows for downhill segments, road bikes, or e-bikes',
                    labelLow: '20.0 km/h (City)',
                    labelMid: '35.0 km/h (Default)',
                    labelHigh: '80.0 km/h (Road bike)'
                }
            },
            running: {
                title: 'Running',
                subtitle: 'Optional transport type',
                description: 'Detects running/jogging trips (7-14 km/h typical range). Conservative thresholds separate running from fast walking and slow cycling. When disabled, running speeds are captured by BICYCLE (if enabled) or CAR/UNKNOWN.',
                minAvgSpeed: {
                    label: 'Minimum Average Speed',
                    description: 'Trips slower than this will be classified as walking',
                    labelLow: '5.0 km/h (Slow)',
                    labelMid: '7.0 km/h (Default)',
                    labelHigh: '10.0 km/h (Fast)'
                },
                maxAvgSpeed: {
                    label: 'Maximum Average Speed',
                    description: 'Trips faster than this will be classified as cycling or motorized transport',
                    labelLow: '10.0 km/h (Slow)',
                    labelMid: '14.0 km/h (Default)',
                    labelHigh: '18.0 km/h (Fast)'
                },
                maxMaxSpeed: {
                    label: 'Maximum Peak Speed',
                    description: 'Allows for sprint segments while staying below cycling speeds',
                    labelLow: '12.0 km/h (Slow)',
                    labelMid: '18.0 km/h (Default)',
                    labelHigh: '25.0 km/h (Sprint)'
                }
            },
            car: {
                title: 'Motor Vehicle',
                subtitle: 'Car, motorcycle, or public transportation label',
                description: 'Detects car-like trips using shared motor vehicle speed thresholds. Public Transportation is a label for those trips, not bus detection.',
                carLabel: {
                    title: 'Car Label',
                    description: 'Allow detected motor vehicle trips to be labeled as car.',
                    ariaEnable: 'Enable car label'
                },
                motorcycleLabel: {
                    title: 'Motorcycle Label',
                    description: 'Allow detected motor vehicle trips to be labeled as motorcycle.',
                    ariaEnable: 'Enable motorcycle label'
                },
                publicTransportLabel: {
                    title: 'Public Transportation Label',
                    description: 'Allow detected car-like trips to be labeled as public transportation.',
                    ariaEnable: 'Enable public transportation label'
                },
                preferredLabel: {
                    label: 'Preferred Motor Vehicle Label',
                    description: 'When multiple labels are enabled, detected motor vehicle trips use this label by default.',
                    placeholder: 'Select preferred label',
                    optionCar: 'Car',
                    optionMotorcycle: 'Motorcycle',
                    optionPublicTransport: 'Public Transportation'
                },
                minAvgSpeed: {
                    label: 'Minimum Average Speed',
                    description: 'Trips with average speeds below this will be classified as walking, running, or bicycle if those match first',
                    labelLow: '5.0 km/h (Sensitive)',
                    labelMid: '10.0 km/h (Default)',
                    labelHigh: '25.0 km/h (Conservative)'
                },
                minMaxSpeed: {
                    label: 'Minimum Peak Speed',
                    description: 'Trips that never reach this speed will not be classified as a motor vehicle unless average speed matches',
                    labelLow: '10.0 km/h (City)',
                    labelMid: '15.0 km/h (Default)',
                    labelHigh: '50.0 km/h (Highway)'
                }
            },
            train: {
                title: 'Train',
                subtitle: 'Optional transport type',
                description: 'Detects train travel by high speed with low variance (30-150 km/h, steady movement). Uses speed variance to distinguish from cars.',
                minAvgSpeed: {
                    label: 'Minimum Average Speed',
                    description: 'Separates from cars in heavy traffic',
                    labelLow: '20.0 km/h (City)',
                    labelMid: '30.0 km/h (Default)',
                    labelHigh: '50.0 km/h (Fast)'
                },
                maxAvgSpeed: {
                    label: 'Maximum Average Speed',
                    description: 'Covers regional and intercity trains',
                    labelLow: '80.0 km/h (Regional)',
                    labelMid: '150.0 km/h (Default)',
                    labelHigh: '400.0 km/h (High-speed)'
                },
                minMaxSpeed: {
                    label: 'Minimum Peak Speed (Station Filter)',
                    description: 'Filters out trips with only station waiting time (critical!)',
                    labelLow: '60.0 km/h (Lenient)',
                    labelMid: '80.0 km/h (Default)',
                    labelHigh: '120.0 km/h (Strict)'
                },
                maxMaxSpeed: {
                    label: 'Maximum Peak Speed',
                    description: 'Upper limit for train speeds',
                    labelLow: '100.0 km/h (Regional)',
                    labelMid: '180.0 km/h (Default)',
                    labelHigh: '500.0 km/h (High-speed)'
                },
                maxSpeedVariance: {
                    label: 'Maximum Speed Variance (Key Discriminator)',
                    description: 'Trains have low variance (< 15), cars have high variance (> 25). This is the key to distinguishing trains from cars!',
                    labelLow: '5.0 (Strict)',
                    labelMid: '15.0 (Default)',
                    labelHigh: '30.0 (Lenient)'
                }
            },
            flight: {
                title: 'Flight',
                subtitle: 'Optional transport type',
                description: 'Detects air travel by very high speeds (400+ km/h avg OR 500+ km/h peak). OR logic handles extended taxi/ground time. GPS noise above 1200 km/h is automatically rejected.',
                minAvgSpeed: {
                    label: 'Minimum Average Speed',
                    description: 'Conservative default for typical flights (including taxi/takeoff/landing time)',
                    labelLow: '100.0 km/h (Regional)',
                    labelMid: '400.0 km/h (Default)',
                    labelHigh: '600.0 km/h (Long-haul)'
                },
                minMaxSpeed: {
                    label: 'Minimum Peak Speed',
                    description: 'Catches flights with long taxi/wait time (OR logic with avg speed)',
                    labelLow: '150.0 km/h (Light aircraft)',
                    labelMid: '500.0 km/h (Default)',
                    labelHigh: '900.0 km/h (Jet)'
                },
                minDistance: {
                    label: 'Minimum Distance',
                    description: 'Trips shorter than this are never classified as flights. Prevents short trips with GPS jumps from being detected as flights (0 = no minimum)',
                    labelLow: '0 km (Default)',
                    labelMid: '250 km',
                    labelHigh: '500 km (Strict)'
                },
                altitudeDetection: {
                    title: 'Altitude-based Detection',
                    description: 'Also classify a trip as a flight when it stays well above its start and end points and climbs or descends strongly. Meant for gliders, balloons and light aircraft that fly too slowly for the speed rules. Needs GPS altitude data. Ski days or cable-car loops may be misdetected as flights.',
                    ariaEnable: 'Enable altitude-based flight detection'
                },
                altitudeMinHeight: {
                    label: 'Minimum Height Above Start/End',
                    description: 'How high above the higher of its start and end altitude the trip must stay for at least a quarter of its time. Lower values catch shorter flights but may misdetect drives or rides in hilly or mountain terrain.',
                    labelLow: '50 m (Lenient)',
                    labelMid: '300 m (Default)',
                    labelHigh: '5000 m (Strict)'
                },
                altitudeMinVerticalSpeed: {
                    label: 'Minimum Vertical Speed',
                    description: 'How fast the trip must climb or descend, measured over one-minute windows (90th percentile, so brief bursts do not count). Gliders and balloons usually climb or sink at 1-5 m/s; lower values may also match cable cars or ski lifts.',
                    labelLow: '0.3 m/s (Lenient)',
                    labelMid: '1.2 m/s (Default)',
                    labelHigh: '10.0 m/s (Strict)'
                }
            },
            boat: {
                title: 'Boat',
                subtitle: 'Optional transport type',
                description: 'Detects boat trips from sustained water evidence. Speed is only used as a sanity check, not as proof of boat travel.',
                setup: {
                    requiredTitle: 'Boat setup required',
                    readyTitle: 'Boat setup ready',
                    failedTitle: 'Boat setup failed',
                    inProgressTitle: 'Boat setup in progress',
                    readyPhaseText: 'Cached water evidence is available.',
                    docsLink: 'Offline setup documentation',
                    retry: 'Retry',
                    phases: {
                        BOAT_SETUP_IS_READY: 'Boat setup is ready',
                        GPS_WATER_EVIDENCE_NEEDS_ENRICHMENT: 'GPS water evidence needs enrichment',
                        WATER_DATASET_IS_NOT_IMPORTED: 'Water dataset is not imported',
                        STARTING_BOAT_SETUP: 'Starting Boat setup',
                        BOAT_SETUP_COMPLETED: 'Boat setup completed',
                        RECLASSIFYING_EXISTING_TRIPS: 'Reclassifying existing trips',
                        WATER_DATASET_READY: 'Water dataset ready',
                        PREPARING_WATER_DATASET_ARTIFACT: 'Preparing water dataset artifact',
                        IMPORTING_WATER_POLYGONS: 'Importing water polygons',
                        USING_LOCAL_WATER_DATASET_FILE: 'Using local water dataset file',
                        DOWNLOADING_WATER_DATASET: 'Downloading water dataset',
                        GPS_WATER_EVIDENCE_READY: 'GPS water evidence ready',
                        CLASSIFYING_GPS_WATER_EVIDENCE: 'Classifying GPS water evidence',
                        WAITING_FOR_WATER_DATASET_IMPORT: 'Waiting for water dataset import',
                        WAITING_FOR_GPS_WATER_EVIDENCE: 'Waiting for GPS water evidence',
                        BOAT_SETUP_WORKER_DID_NOT_START: 'Boat setup worker did not start',
                        WATER_DATASET_SETUP_FAILED: 'Water dataset setup failed',
                        BOAT_SETUP_FAILED: 'Boat setup failed',
                        fallback: 'Preparing Boat setup...'
                    },
                    errorFallback: 'Water dataset setup failed. Check offline setup instructions.'
                },
                minWaterRatio: {
                    label: 'Minimum Water Ratio',
                    description: 'Share of usable trip distance that must be over water',
                    labelLow: '10% (Lenient)',
                    labelMid: '60% (Default)',
                    labelHigh: '100% (Strict)'
                },
                minWaterDistance: {
                    label: 'Minimum Water Distance',
                    description: 'Total water distance required before a trip can be classified as boat',
                    labelLow: '100 m (Lenient)',
                    labelMid: '2000 m (Default)',
                    labelHigh: '10000 m (Strict)'
                },
                minContinuousWaterDistance: {
                    label: 'Minimum Continuous Water Distance',
                    description: 'Filters out short bridge, tunnel, and shoreline crossings',
                    labelLow: '100 m (Lenient)',
                    labelMid: '1000 m (Default)',
                    labelHigh: '10000 m (Strict)'
                },
                maxPlausibleSpeed: {
                    label: 'Maximum Plausible Speed',
                    description: 'Sanity ceiling only; slow movement can still be boat travel',
                    labelLow: '20 km/h (Strict)',
                    labelMid: '120 km/h (Default)',
                    labelHigh: '300 km/h (Lenient)'
                }
            },
            shortDistance: {
                title: 'Short Trip Distance Threshold',
                description: 'Distance threshold for applying relaxed walking speed detection',
                details: 'Trips shorter than this distance get slightly more lenient walking speed classification to account for GPS inaccuracies',
                labelLow: '0.1 km (Strict)',
                labelMid: '1.0 km (Normal)',
                labelHigh: '3.0 km (Lenient)'
            },
            arrivalDetection: {
                title: 'Arrival Detection Duration',
                description: 'Minimum time GPS points must be clustered and slow to detect arrival at destination',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'More sensitive arrival detection, may catch brief stops',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'More conservative, only detect sustained arrivals',
                labelLow: '10s (Sensitive)',
                labelMid: '90s (Normal)',
                labelHigh: '300s (Conservative)'
            },
            sustainedStop: {
                title: 'Sustained Stop Duration',
                description: 'Minimum time for consistent slow movement to detect a real stop (filters traffic lights)',
                detailsLowerLabel: 'Lower values',
                detailsLowerValue: 'Detect shorter stops, may include traffic delays',
                detailsHigherLabel: 'Higher values',
                detailsHigherValue: 'Only detect longer stops, better traffic filtering',
                labelLow: '10s (Sensitive)',
                labelMid: '60s (Normal)',
                labelHigh: '600s (Conservative)'
            },
            arrivalMinPoints: {
                title: 'Minimum Stop Points for Arrival Detection',
                description: 'Minimum number of GPS points required to detect arrival at destination',
                detailsLowerLabel: 'Lower values (2)',
                detailsLowerValue: 'Faster detection, ideal for infrequent GPS (10-15 min intervals)',
                detailsHigherLabel: 'Higher values (3-4)',
                detailsHigherValue: 'More reliable, ideal for frequent GPS (30-60 sec intervals)',
                labelLow: '2 points (Fast)',
                labelMid: '3 points (Balanced)',
                labelHigh: '5 points (Conservative)'
            }
        }
    },
    reports: {
        page: {
            title: 'Timeline Reports',
            subtitle: 'Detailed tables of your stays, trips, and data gaps'
        },
        demoNotice: 'Data export is disabled in demo mode.',
        exportAll: 'Export All',
        exportAllTooltip: 'Export stays, trips, and data gaps to CSV',
        demoExportDisabled: 'Export is disabled in demo mode',
        demoExportDisabledToast: 'Data export is disabled in demo mode.',
        sections: {
            stays: 'Stays',
            trips: 'Trips',
            dataGaps: 'Data Gaps'
        },
        noDateRange: 'No date range selected',
        noData: {
            title: 'No Data Found',
            message: 'No timeline data found for the selected date range.',
            suggestion: 'Try selecting a different date range or check your GPS sources.'
        },
        fetchFailed: 'Failed to fetch timeline data',
        export: {
            successSummary: 'Export Successful',
            failedSummary: 'Export Failed',
            staysDone: 'Stays exported successfully',
            staysFailed: 'Failed to export stays',
            tripsDone: 'Trips exported successfully',
            tripsFailed: 'Failed to export trips',
            dataGapsDone: 'Data gaps exported successfully',
            dataGapsFailed: 'Failed to export data gaps',
            allDone: 'All data exported successfully',
            failedGeneric: 'Failed to export data'
        }
    }
}
