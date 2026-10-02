/**
 * Place details page: the map card, notes section, stats card, and visit history table.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    map: {
        title: 'Location',
        defaultLocationName: 'Place',
        defaultAreaName: 'Place Area'
    },
    stats: {
        title: 'Visit overview',
        totalVisits: 'Total visits',
        totalTime: 'Total time',
        averageVisit: 'Average visit',
        placesVisited: 'Places visited',
        activityHeader: 'Activity',
        thisWeek: 'This week',
        thisMonth: 'This month',
        thisYear: 'This year',
        visitSpanHeader: 'Visit span',
        firstVisit: 'First visit',
        lastVisit: 'Last visit',
        durationRangeHeader: 'Duration range',
        shortestVisit: 'Shortest visit',
        longestVisit: 'Longest visit',
        visitPatternsHeader: 'Visit patterns',
        typicalDay: 'Typical day',
        arrivalPeriod: 'Arrival period',
        visitCadence: 'Visit cadence',
        notAvailable: 'N/A',
        zeroSeconds: '0 seconds',
        lessThanDaily: 'Less than daily',
        everyDay: 'Every day',
        everyNDays: 'Every {count} days'
    },
    notes: {
        defaultTitle: 'Notes',
        defaultEmptyMessage: 'No notes found for this place.',
        viewAll: 'View all',
        reloadAriaLabel: 'Reload notes',
        reloadTooltip: 'Reload notes',
        openInMemosAriaLabel: 'Open in Memos',
        openInMemosTooltip: 'Open in Memos',
        truncatedNotice: 'This Memos note is large, so GeoPulse shows a truncated preview.',
        loadingInline: 'Loading notes...',
        loadFailed: 'Failed to load notes',
        sourceMemos: 'Memos',
        sourceGeopulse: 'GeoPulse',
        locationSource: {
            explicit: 'Geotagged',
            derivedStay: 'Stay location',
            derivedTripGps: 'Trip GPS',
            derivedTripInterpolated: 'Trip estimate'
        }
    },
    detailsPage: {
        loading: 'Loading place details...',
        errorTitle: 'Failed to Load Place Details',
        loadFailed: 'Failed to load place details',
        pageTitleFallback: 'Place Details',
        subtitleFallback: 'Detailed information and visit history',
        typeFavorite: 'Favorite',
        typeGeocoded: 'Geocoded Location',
        coordinatesCenter: 'Center: {coordinates}',
        edit: 'Edit',
        editDetails: 'Edit Details',
        createFavorite: 'Create Favorite',
        relatedFavorite: {
            titleArea: 'Visits Grouped with Area Favorite',
            titlePoint: 'Visits Grouped with Favorite',
            messageArea: 'This location is within your favorite area. Your visits here are being tracked under:',
            messagePoint: 'Your visits to this location are being tracked under your nearby favorite:',
            distanceAway: '({distance} away)',
            visitsTracked: '{count} visits tracked',
            viewDetails: 'View {name} Details'
        },
        latestPhotosTitle: 'Latest photos near {name}',
        noPhotosMessage: 'No nearby Immich photos found for this place.',
        notesTitle: 'Notes near {name}',
        noNotesMessage: 'No nearby notes found for this place.',
        editFavoriteDialogHeader: 'Edit Favorite Location',
        createFavoriteDialog: {
            header: 'Create Favorite Location',
            message: 'Create a favorite location at this geocoding point. You can give it a custom name.',
            nameLabel: 'Favorite Name',
            namePlaceholder: 'e.g., Home, Work, Gym',
            coordinatesInfo: 'Coordinates: {lat}, {lon}'
        },
        invalidCoordinatesDetail: 'Invalid coordinates for favorite.',
        favoriteCreatedSuccess: 'Favorite location created successfully.',
        favoriteCreateFailed: 'Failed to create favorite location.',
        loadVisitsFailed: 'Failed to load visit history',
        exportNoDataSummary: 'No Data',
        exportNoDataDetail: 'No place data available',
        exportingSummary: 'Exporting',
        exportingDetail: 'Preparing CSV export...',
        exportSuccessSummary: 'Export Successful',
        exportSuccessDetail: 'Exported {count} visits to CSV',
        exportFailedSummary: 'Export Failed',
        exportFailedDetail: 'Failed to export visits to CSV',
        geocodingUpdatedSuccessSummary: 'Success',
        geocodingUpdatedSuccessDetail: 'Geocoding location updated successfully',
        geocodingUpdateFailedDetail: 'Failed to update geocoding location'
    },
    visitsTable: {
        title: 'Visit History',
        allVisits: 'All Visits',
        countLabel: '{count} visits',
        exportCsv: 'Export CSV',
        columns: {
            visitDate: 'Visit Date',
            city: 'City',
            placeName: 'Place Name',
            trip: 'Trip',
            duration: 'Duration',
            endTime: 'End Time',
            dayOfWeek: 'Day of Week'
        },
        notAvailable: 'N/A',
        unknownPlace: 'Unknown',
        tripTagTitle: 'Visit is linked to trip planner: {label}',
        tripTagAriaLabel: 'Open trip planner {label}',
        tripFallbackLabel: 'Trip #{id}',
        openInTimelineAriaLabel: 'Open visit day in timeline',
        openInTimelineTooltip: 'Open visit day in timeline',
        empty: {
            title: 'No Visits Found',
            message: 'No visits recorded for this location.'
        }
    }
}
