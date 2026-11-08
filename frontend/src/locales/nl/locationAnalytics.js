/**
 * Location analytics: the city-details and country-details pages.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    cityPage: {
        loading: 'Staddetails laden...',
        errorTitle: 'Staddetails Laden is Mislukt',
        loadFailed: 'Staddetails laden is mislukt',
        subtitle: 'Stadinzichten en bezoekgeschiedenis',
        visitsCount: '{count} bezoeken',
        topPlacesTitle: 'Top Plaatsen in {name}',
        placeStats: '{count} bezoeken • {duration}',
        latestPhotosTitle: 'Laatste foto\'s in {name}',
        noPhotosMessage: 'Geen Immich-foto\'s gevonden voor deze stad.',
        loadVisitsFailed: 'Bezoekgeschiedenis laden is mislukt',
        exportSuccessSummary: 'Export Geslaagd',
        exportSuccessDetail: 'Bezoeken naar {name} geëxporteerd',
        exportFailedSummary: 'Export Mislukt',
        exportFailedDetail: 'Bezoeken exporteren is mislukt'
    },
    countryPage: {
        loading: 'Landdetails laden...',
        errorTitle: 'Landdetails Laden is Mislukt',
        loadFailed: 'Landdetails laden is mislukt',
        subtitle: 'Landinzichten en bezoekgeschiedenis',
        citiesVisitedCount: '{count} steden bezocht',
        visitsCount: '{count} bezoeken',
        citiesTitle: 'Steden in {name}',
        cityStats: '{count} bezoeken • {duration} • {places} plaatsen',
        showTop5: 'Top 5 tonen',
        showAllCities: 'Alle {count} steden tonen',
        topPlacesTitle: 'Top Plaatsen in {name}',
        placeStats: '{count} bezoeken • {duration}',
        photoLocationsTitle: 'Fotolocaties in {name}',
        latestPhotosTitle: 'Laatste foto\'s in {name}',
        noPhotosMessage: 'Geen Immich-foto\'s gevonden voor dit land.',
        loadVisitsFailed: 'Bezoekgeschiedenis laden is mislukt',
        exportSuccessSummary: 'Export Geslaagd',
        exportSuccessDetail: 'Bezoeken naar {name} geëxporteerd',
        exportFailedSummary: 'Export Mislukt',
        exportFailedDetail: 'Bezoeken exporteren is mislukt'
    }
}
