export default {
    timelineRegeneration: {
        header: 'Timeline Regeneration',
        completedMessage: 'Timeline generation completed successfully!',
        gpsPointsLoaded: '{loaded} / {total} GPS points loaded',
        pointsProcessed: '{processed} / {total} points processed',
        locationsGeocoded: '{resolved} / {total} locations geocoded',
        fromFavorites: '{count} from favorites',
        fromCache: '{count} from cache',
        fromExternalApi: '{count} from external API',
        pending: '{count} pending',
        viewDetailedProgress: 'View Detailed Progress',
        fallbackNote: 'This process may take 5-15 seconds or longer depending on your data size. Your timeline will be temporarily unavailable during regeneration.',
        pleaseWait: 'Please wait...',
        titles: {
            favorite: 'Adding Favorite & Regenerating Timeline',
            favoriteDelete: 'Deleting Favorite & Regenerating Timeline',
            preferences: 'Applying Preferences & Regenerating Timeline',
            classification: 'Updating Trip Classifications',
            reconstruction: 'Applying Missing Timeline Data',
            general: 'Regenerating Timeline'
        },
        messages: {
            favorite: "We're adding your favorite location and regenerating your complete timeline to incorporate this change. This ensures all timeline data remains accurate and up-to-date.",
            favoriteDelete: "We're removing your favorite location and regenerating your complete timeline to reflect this change. This ensures all timeline data remains accurate and up-to-date.",
            preferences: "We're applying your new preferences and regenerating your complete timeline based on the updated settings. This ensures optimal timeline accuracy with your preferences.",
            classification: "We're recalculating movement types for your existing trips based on your updated speed thresholds. This process will update how your trips are classified without changing the underlying timeline structure.",
            reconstruction: "We're applying generated GPS points from your stays and trips, then updating the affected timeline portion. Existing timeline data is not replaced.",
            general: "We're regenerating your complete timeline from your GPS data. This process ensures your timeline is accurate and reflects all available location information."
        }
    },
    stayDetails: {
        titleWithLocation: 'Stay Details - {location}',
        unknownLocation: 'Unknown Location',
        mapSectionTitle: 'Location Map',
        timingTitle: 'Timing',
        start: 'Start:',
        end: 'End:',
        duration: 'Duration:',
        coordinatesTitle: 'Coordinates',
        latitude: 'Latitude:',
        longitude: 'Longitude:',
        coordinates: 'Coordinates:',
        notAvailable: 'N/A',
        close: 'Close',
        toasts: {
            copiedSummary: 'Copied!',
            copiedDetail: 'Coordinates copied to clipboard',
            copyFailedSummary: 'Copy failed',
            copyFailedDetail: 'Unable to copy coordinates'
        }
    },
    locationLookup: {
        header: 'Visits near this point',
        withinRadius: 'within {radius} m',
        checkingStays: 'Checking your recorded stays…',
        tryAgain: 'Try again',
        hasVisits: 'You have recorded visits at this location.',
        recordedStayFallback: 'Recorded stay',
        placeDetails: 'Place Details',
        visitCount: '{count} visit | {count} visits',
        firstVisit: 'First: {date}',
        lastVisit: 'Last: {date}',
        openVisitDayTooltip: 'Open visit day in timeline',
        savedPlacesHeading: 'Saved places at this point',
        unnamedFavorite: 'Unnamed favorite',
        noVisitFound: 'No recorded visit was found within {radius} m of this point.',
        closestStaysHeading: 'Closest recorded stays',
        unknownLocation: 'Unknown location',
        noRecordedStays: 'No recorded stays were found.',
        close: 'Close'
    },
    timelineLabelForm: {
        nameLabel: 'Label Name *',
        namePlaceholder: 'e.g., Spain Vacation, Work Trip to NYC',
        dateRangeLabel: 'Date Range *',
        dateRangePlaceholder: 'Select start and end dates',
        colorLabel: 'Color',
        colorPreview: 'Preview',
        randomButton: 'Random',
        showAsPresetLabel: 'Show as date preset',
        showAsPresetHint: 'When enabled, this label appears in DatePicker preset dropdowns.',
        nameRequired: 'Tag name is required',
        dateRangeRequired: 'Date range is required',
        cancel: 'Cancel'
    },
    createTimelineLabel: {
        header: 'Create Timeline Label',
        createButton: 'Create',
        overlap: {
            message: 'This label overlaps with: {names}. Do you want to create it anyway?',
            header: 'Overlapping Labels Detected',
            acceptLabel: 'Create Anyway',
            rejectLabel: 'Cancel'
        },
        toasts: {
            checkOverlapsFailedFallback: 'Failed to check overlaps',
            createdSummary: 'Created',
            createdDetail: 'Timeline label created successfully',
            createFailedFallback: 'Failed to create timeline label'
        }
    },
    editTimelineLabel: {
        header: 'Edit Timeline Label',
        readOnlyHeader: 'View Timeline Label (Read-Only)',
        activeOwnTracksTitle: 'Active OwnTracks Tag',
        activeOwnTracksMessage: "This tag is currently being managed by OwnTracks and cannot be edited while active. You can edit it after it's completed (when you change tags in OwnTracks).",
        updateButton: 'Update',
        toasts: {
            updatedSummary: 'Updated',
            updatedDetail: 'Timeline label updated successfully',
            updateFailedFallback: 'Failed to update timeline label'
        }
    },
    bulkEdit: {
        header: 'Bulk Edit {count} {itemType}',
        editingPrefix: 'You are editing',
        fieldsHeading: 'Select Fields to Update',
        updateCity: 'Update City',
        updateCountry: 'Update Country',
        cityPlaceholder: 'Enter city name',
        countryPlaceholder: 'Enter country name',
        cityRequired: 'City value is required',
        countryRequired: 'Country value is required',
        selectAtLeastOneField: 'Please select at least one field to update',
        typoWarning: {
            header: 'Possible Typo Detected',
            notFoundMessage: 'The following value(s) are not found in your existing data:',
            didYouMean: 'Did you mean:',
            continuePrompt: 'Do you want to continue with these values?',
            continueAnyway: 'Continue Anyway'
        },
        fields: {
            city: 'City',
            country: 'Country'
        },
        updating: 'Updating...',
        updateButton: 'Update {count} Items',
        toasts: {
            completeSummary: 'Bulk Update Complete',
            partialDetail: 'Updated {success} of {total} items ({failed} failed)',
            successDetail: 'Successfully updated {count} {itemType}',
            failedSummary: 'Update Failed',
            failedFallback: 'Failed to update {itemType}'
        }
    },
    bulkSaveConfirm: {
        header: 'Confirm Bulk Save',
        summaryTitle: 'You are about to save {count} favorite location: | You are about to save {count} favorite locations:',
        pointsItem: '{count} point location | {count} point locations',
        areasItem: '{count} area | {count} areas',
        infoMessage: 'These favorite locations will be saved and after that a full timeline regeneration will be triggered.',
        confirm: 'Confirm'
    }
}
