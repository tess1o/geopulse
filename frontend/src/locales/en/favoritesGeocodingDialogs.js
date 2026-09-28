/**
 * The favorite-location and geocoding-result dialogs: add/edit a favorite, reconcile favorites or
 * geocoding results against a provider, and edit a single geocoding result.
 *
 * The two reconcile dialogs (`FavoriteReconcileDialog.vue` / `GeocodingReconcileDialog.vue`) share
 * a near-identical layout and copy (provider select, progress section, footer buttons), so their
 * shared strings live directly under `reconcile.*` and only the wording that actually differs
 * (dialog titles, warning list, filter labels) is split into `reconcile.favorite.*` /
 * `reconcile.geocoding.*`.
 */
export default {
    addFavorite: {
        locationNamePlaceholder: 'Location name',
        save: 'Save'
    },
    editFavorite: {
        nameLabel: 'Name',
        namePlaceholder: 'Enter location name',
        cityLabel: 'City',
        cityPlaceholder: 'Enter city (optional)',
        countryLabel: 'Country',
        countryPlaceholder: 'Enter country (optional)',
        areaBoundariesTitle: 'Area Boundaries',
        drawing: 'Drawing...',
        redrawArea: 'Redraw Area',
        drawInstruction: 'Click and drag on the map to draw a new rectangular area',
        currentBoundsLabel: 'Current Bounds:',
        neLabel: 'NE:',
        swLabel: 'SW:',
        save: 'Save'
    },
    reconcile: {
        selectProvider: 'Select Provider',
        providerPlaceholder: 'Choose a geocoding provider',
        primaryTag: 'Primary',
        fallbackTag: 'Fallback',
        providerHint: 'The provider will be used to fetch new geocoding data',
        detailsTitle: 'Reconciliation Details',
        filteredBadge: '(filtered)',
        allBadge: '(all)',
        activeFiltersLabel: 'Active filters:',
        searchFilter: 'Search: "{search}"',
        actionLabel: 'Action:',
        importantLabel: 'Important:',
        originalKeptOnFailure: 'If reconciliation fails, original data will be kept',
        cannotBeUndone: 'This operation cannot be undone',
        progressTitle: 'Reconciliation Progress',
        processedLabel: 'Processed:',
        successfulLabel: 'Successful:',
        errorsLabel: 'Errors:',
        completedSuccessfully: 'Reconciliation completed successfully!',
        completedWithErrors: 'Completed with {count} error. | Completed with {count} errors.',
        itemsReconciledSuccessfully: '{count} item reconciled successfully. | {count} items reconciled successfully.',
        jobFailed: 'Reconciliation job failed',
        unexpectedError: 'An unexpected error occurred',
        reconcileButton: 'Reconcile',
        favoritesToReconcileLabel: 'Favorites to reconcile:',
        typeFilter: 'Type: {type}',
        favoriteActionValue: 'Update city and country from provider',
        favoriteWarnings: {
            cityCountryOnly: 'This will update the city and country fields only',
            nameUnchanged: 'Name and location coordinates will remain unchanged',
            areaCenterPoint: 'For areas, the center point will be used for geocoding'
        },
        favorite: {
            dialogTitleFiltered: 'Reconcile {count} Filtered Favorites',
            dialogTitleAll: 'Reconcile All {count} Favorites',
            dialogTitleSingle: 'Reconcile Favorite Location',
            dialogTitleMultiple: 'Reconcile {count} Selected Favorites'
        },
        geocoding: {
            providerSwitchHint: 'Use Reconcile Selected/Reconcile All to refresh old records with the selected provider after switching providers.',
            resultsToReconcileLabel: 'Results to reconcile:',
            providerFilter: 'Provider: {provider}',
            actionValue: 'Fetch fresh data from selected provider',
            warnings: {
                displayNameCityCountry: 'This will update the display name, city, and country fields',
                syncedAcrossStays: 'Changes will be synchronized across all timeline stays',
                cachedEntriesNotAuto: 'Provider changes in settings do not rewrite existing cached entries automatically'
            },
            dialogTitleFiltered: 'Reconcile {count} Filtered Results',
            dialogTitleAll: 'Reconcile All {count} Results',
            dialogTitleSingle: 'Reconcile Geocoding Result',
            dialogTitleMultiple: 'Reconcile {count} Selected Results'
        }
    },
    geocodingEdit: {
        header: 'Edit Geocoding Result',
        mapLabel: 'Location Map',
        mapHint: 'Read-only map showing the geocoding location',
        displayNameLabel: 'Display Name',
        displayNamePlaceholder: 'Enter location display name',
        displayNameHint: 'The main display name shown for this location',
        cityLabel: 'City',
        cityPlaceholder: 'Enter city name',
        cityHint: 'Optional city name',
        countryLabel: 'Country',
        countryPlaceholder: 'Enter country name',
        countryHint: 'Optional country name',
        providerLabel: 'Provider:',
        coordinatesLabel: 'Coordinates:',
        noteLabel: 'Note:',
        syncNoteMessage: 'Changes will be synchronized across all timeline stays using this geocoding result.',
        saveChanges: 'Save Changes'
    }
}
