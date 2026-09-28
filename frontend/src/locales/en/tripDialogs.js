/**
 * Trip-related dialogs: trip details, trip/stay split, map-matching details, movement-type quick
 * edit, data-gap-to-stay conversion, and GPS point edit.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    tripDetails: {
        dialogTitleDefault: 'Trip Details',
        dialogTitleWithType: 'Trip Details - {type}',
        sections: {
            route: 'Trip Route',
            timing: 'Timing',
            tripInfo: 'Trip Details',
            routeInfo: 'Route',
            coordinates: 'Coordinates'
        },
        labels: {
            start: 'Start:',
            end: 'End:',
            duration: 'Duration:',
            distance: 'Distance:',
            averageSpeed: 'Average Speed:',
            routePoints: 'Route Points:',
            routePointsValue: '{count} points',
            origin: 'Origin:',
            destination: 'Destination:'
        },
        unknownOrigin: 'Unknown Origin',
        unknownDestination: 'Unknown Destination',
        notAvailable: 'N/A',
        close: 'Close',
        toasts: {
            copiedSummary: 'Copied!',
            copiedDetail: 'Coordinates copied to clipboard',
            copyFailedSummary: 'Copy Failed',
            copyFailedDetail: 'Could not copy coordinates',
            gpsUnavailableSummary: 'GPS Data Unavailable',
            gpsUnavailableDetail: 'Could not load GPS points for this trip'
        }
    },
    staySplit: {
        header: 'Split Trip with Stay',
        loadingPath: 'Loading trip path...',
        clickToSetStay: 'Click on the route to set your stay location.',
        stayStartLabel: 'Stay start',
        stayEndLabel: 'Stay end',
        placeNameLabel: 'Place name',
        placeNameResolving: 'Resolving place...',
        placeNamePlaceholder: 'Resolved automatically when empty',
        updatingPreview: 'Updating split result...',
        preview: {
            originalTrip: 'Original trip',
            splitResult: 'Split result',
            tripFor: 'Trip for {duration}',
            to: 'to {name}',
            stayAt: 'Stay at {name}',
            forDuration: 'for {duration}',
            from: 'from {name}'
        },
        saveSplit: 'Save Split',
        unknownTimeRange: 'Unknown time range',
        selectedStayFallback: 'Selected stay',
        selectedPlaceFallback: 'selected place',
        validation: {
            tripMissing: 'Trip is missing.',
            readOnly: 'Timeline edits are disabled.',
            selectStayLocation: 'Select the stay location on the trip map.',
            selectValidTimes: 'Select valid start and end times.',
            endAfterStart: 'Stay end must be after stay start.',
            minDuration: 'Stay duration must be at least 60 seconds.',
            startAfterTripStart: 'Stay must start after the trip starts.',
            endBeforeTripEnd: 'Stay must end before the trip ends.',
            placeNameTooLong: 'Place name is too long.',
            selectValidStayLocation: 'Select a valid stay location.'
        },
        pathErrors: {
            noPoints: 'No path points available for this trip.',
            loadFailed: 'Failed to load trip path'
        },
        previewErrorFallback: 'Could not preview trip split',
        toasts: {
            splitSummary: 'Trip Split',
            splitDetail: 'Stay was inserted into the trip.',
            splitFailedSummary: 'Split Failed',
            splitFailedFallback: 'Could not split trip'
        },
        distanceFromClickMeters: '{value}m from click',
        distanceFromClickKm: '{value}km from click'
    },
    mapMatchingDetails: {
        header: 'Map Matching Details',
        statusLabels: {
            completed: 'MATCHED',
            queued: 'QUEUED',
            processing: 'MATCHING',
            failed: 'FAILED',
            skipped: 'SKIPPED'
        },
        neverQueuedNote: 'This trip was skipped before it reached the map-matching queue, so there is no stored result to retry.',
        detailCaption: 'Reported by the routing engine',
        lastAttemptedOn: 'Last attempted on {when}',
        refinedOn: 'Refined on {when}',
        openSettings: 'Open map matching settings',
        adminHint: 'An administrator can re-run map matching from Admin → Settings → Map Matching.',
        close: 'Close'
    },
    movementTypeQuickEdit: {
        header: 'Edit Movement Type',
        unknownAlgorithmWarning: 'Algorithm did not recognize this movement type. Set it manually.',
        readOnlyWarning: 'Movement type edits are read-only in demo mode.',
        selectPlaceholder: 'Select movement type',
        save: 'Save',
        reset: 'Reset',
        close: 'Close',
        unknownTime: 'Unknown time',
        toasts: {
            updatedSummary: 'Movement Updated',
            updatedDetail: 'Trip set to {type}',
            updateFailedSummary: 'Update Failed',
            updateFailedFallback: 'Could not update movement type',
            resetSummary: 'Movement Reset',
            resetDetail: 'Trip reset to {type}',
            resetFailedSummary: 'Reset Failed',
            resetFailedFallback: 'Could not reset movement type'
        }
    },
    dataGapToStay: {
        header: 'Convert Data Gap to Stay',
        gapTag: 'Data Gap',
        unknownTimeRange: 'Unknown time range',
        locationSourceLabel: 'Stay location source',
        resolvingLocation: 'Resolving latest known point location...',
        defaultLocationLabel: 'Default location:',
        unknownLocation: 'Unknown location',
        selectedLocationTypeLabel: 'Selected location type',
        searchPlaceLabel: 'Search Favorite or Geocoding place',
        searchPlaceholder: 'Type at least 2 characters...',
        latitudePlaceholder: 'Latitude',
        longitudePlaceholder: 'Longitude',
        customNamePlaceholder: 'Optional custom location name',
        convert: 'Convert to Stay',
        strategyOptions: {
            latestPoint: 'Latest known point (default)',
            selectedLocation: 'Selected location'
        },
        selectedSourceOptions: {
            place: 'Favorite or geocoding place',
            custom: 'Custom coordinates'
        },
        errors: {
            previewFailed: 'Failed to resolve latest point preview',
            searchFailed: 'Failed to search places'
        },
        toasts: {
            convertedSummary: 'Converted',
            convertedDetail: 'Data gap was converted to stay.',
            failedSummary: 'Conversion Failed',
            failedFallback: 'Could not convert data gap'
        }
    },
    gpsPointEdit: {
        header: 'Edit GPS Point',
        mapInstructions: 'Click on the map to select a new location for this GPS point.',
        locationLabel: 'Location',
        latitudeLabel: 'Latitude',
        longitudeLabel: 'Longitude',
        speedLabel: 'Speed (km/h)',
        speedPlaceholder: 'Speed in km/h',
        accuracyLabel: 'Accuracy (meters)',
        accuracyPlaceholder: 'Accuracy in meters',
        save: 'Save',
        validation: {
            latitudeRequired: 'Latitude is required',
            latitudeRange: 'Latitude must be between -90 and 90',
            longitudeRequired: 'Longitude is required',
            longitudeRange: 'Longitude must be between -180 and 180'
        },
        mapPopups: {
            currentLocation: 'Current Location - Click on map to move',
            originalLocation: 'Original Location'
        }
    }
}
