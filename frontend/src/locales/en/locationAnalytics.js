/**
 * Location analytics: the city-details and country-details pages.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    cityPage: {
        loading: 'Loading city details...',
        errorTitle: 'Failed to Load City Details',
        loadFailed: 'Failed to load city details',
        subtitle: 'City insights and visit history',
        visitsCount: '{count} visits',
        topPlacesTitle: 'Top Places in {name}',
        placeStats: '{count} visits • {duration}',
        latestPhotosTitle: 'Latest photos in {name}',
        noPhotosMessage: 'No Immich photos found for this city.',
        loadVisitsFailed: 'Failed to load visit history',
        exportSuccessSummary: 'Export Successful',
        exportSuccessDetail: 'Exported visits to {name}',
        exportFailedSummary: 'Export Failed',
        exportFailedDetail: 'Failed to export visits'
    },
    countryPage: {
        loading: 'Loading country details...',
        errorTitle: 'Failed to Load Country Details',
        loadFailed: 'Failed to load country details',
        subtitle: 'Country insights and visit history',
        citiesVisitedCount: '{count} cities visited',
        visitsCount: '{count} visits',
        citiesTitle: 'Cities in {name}',
        cityStats: '{count} visits • {duration} • {places} places',
        showTop5: 'Show top 5',
        showAllCities: 'Show all {count} cities',
        topPlacesTitle: 'Top Places in {name}',
        placeStats: '{count} visits • {duration}',
        photoLocationsTitle: 'Photo locations in {name}',
        latestPhotosTitle: 'Latest photos in {name}',
        noPhotosMessage: 'No Immich photos found for this country.',
        loadVisitsFailed: 'Failed to load visit history',
        exportSuccessSummary: 'Export Successful',
        exportSuccessDetail: 'Exported visits to {name}',
        exportFailedSummary: 'Export Failed',
        exportFailedDetail: 'Failed to export visits'
    }
}
