/**
 * Place details page: the map card, notes section, stats card, and visit history table.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    map: {
        title: 'Locatie',
        defaultLocationName: 'Plaats',
        defaultAreaName: 'Plaatsgebied'
    },
    stats: {
        title: 'Bezoekoverzicht',
        totalVisits: 'Totaal aantal bezoeken',
        totalTime: 'Totale tijd',
        averageVisit: 'Gemiddeld bezoek',
        placesVisited: 'Plaatsen bezocht',
        activityHeader: 'Activiteit',
        thisWeek: 'Deze week',
        thisMonth: 'Deze maand',
        thisYear: 'Dit jaar',
        visitSpanHeader: 'Bezoekperiode',
        firstVisit: 'Eerste bezoek',
        lastVisit: 'Laatste bezoek',
        durationRangeHeader: 'Duurperiode',
        shortestVisit: 'Kortste bezoek',
        longestVisit: 'Langste bezoek',
        visitPatternsHeader: 'Bezoekpatronen',
        typicalDay: 'Typische dag',
        arrivalPeriod: 'Aankomstperiode',
        visitCadence: 'Bezoekfrequentie',
        notAvailable: 'Nvt',
        zeroSeconds: '0 seconden',
        lessThanDaily: 'Minder dan dagelijks',
        everyDay: 'Dagelijks',
        everyNDays: 'Elke {count} dagen'
    },
    notes: {
        defaultTitle: 'Notities',
        defaultEmptyMessage: 'Geen notities gevonden voor deze plaats.',
        viewAll: 'Alle bekijken',
        reloadAriaLabel: 'Notities herladen',
        reloadTooltip: 'Notities herladen',
        openInMemosAriaLabel: 'Open in Memos',
        openInMemosTooltip: 'Open in Memos',
        truncatedNotice: 'Deze Memos-notitie is groot, dus GeoPulse toont een ingekort voorbeeld.',
        loadingInline: 'Notities laden...',
        loadFailed: 'Notities laden is mislukt',
        sourceMemos: 'Memos',
        sourceGeopulse: 'GeoPulse',
        locationSource: {
            explicit: 'Geotagged',
            derivedStay: 'Verblijfplaatslocatie',
            derivedTripGps: 'Reis GPS',
            derivedTripInterpolated: 'Reisschatting'
        }
    },
    detailsPage: {
        loading: 'Plaatsdetails laden...',
        errorTitle: 'Plaatsdetails Laden is Mislukt',
        loadFailed: 'Plaatsdetails laden is mislukt',
        pageTitleFallback: 'Plaatsdetails',
        subtitleFallback: 'Gedetailleerde informatie en bezoekgeschiedenis',
        typeFavorite: 'Favoriet',
        typeGeocoded: 'Geocodeerde Locatie',
        coordinatesCenter: 'Centrum: {coordinates}',
        edit: 'Bewerken',
        editDetails: 'Details Bewerken',
        createFavorite: 'Favoriet Maken',
        relatedFavorite: {
            titleArea: 'Bezoeken gegroepeerd met Gebied Favoriet',
            titlePoint: 'Bezoeken gegroepeerd met Favoriet',
            messageArea: 'Deze locatie ligt binnen uw favoriete gebied. Uw bezoeken hier worden bijgehouden onder:',
            messagePoint: 'Uw bezoeken aan deze locatie worden bijgehouden onder uw naburige favoriet:',
            distanceAway: '({distance} verderop)',
            visitsTracked: '{count} bezoeken bijgehouden',
            viewDetails: '{name} Details Bekijken'
        },
        latestPhotosTitle: 'Laatste foto\'s in de buurt van {name}',
        noPhotosMessage: 'Geen naburige Immich-foto\'s gevonden voor deze plaats.',
        notesTitle: 'Notities in de buurt van {name}',
        noNotesMessage: 'Geen naburige notities gevonden voor deze plaats.',
        editFavoriteDialogHeader: 'Favoriete Locatie Bewerken',
        createFavoriteDialog: {
            header: 'Favoriete Locatie Maken',
            message: 'Maak een favoriete locatie op dit geocoderingspunt. U kunt er een aangepaste naam aan geven.',
            nameLabel: 'Favoriet Naam',
            namePlaceholder: 'bijv., Thuis, Werk, Sportschool',
            coordinatesInfo: 'Coördinaten: {lat}, {lon}'
        },
        invalidCoordinatesDetail: 'Ongeldige coördinaten voor favoriet.',
        favoriteCreatedSuccess: 'Favoriete locatie succesvol gemaakt.',
        favoriteCreateFailed: 'Favoriete locatie maken is mislukt.',
        loadVisitsFailed: 'Bezoekgeschiedenis laden is mislukt',
        exportNoDataSummary: 'Geen Data',
        exportNoDataDetail: 'Geen plaatsgegevens beschikbaar',
        exportingSummary: 'Exporteren',
        exportingDetail: 'CSV-export voorbereiden...',
        exportSuccessSummary: 'Export Geslaagd',
        exportSuccessDetail: '{count} bezoeken geëxporteerd naar CSV',
        exportFailedSummary: 'Export Mislukt',
        exportFailedDetail: 'Bezoeken exporteren naar CSV is mislukt',
        geocodingUpdatedSuccessSummary: 'Succes',
        geocodingUpdatedSuccessDetail: 'Geocodeerde locatie succesvol bijgewerkt',
        geocodingUpdateFailedDetail: 'Geocodeerde locatie bijwerken is mislukt'
    },
    visitsTable: {
        title: 'Bezoekgeschiedenis',
        allVisits: 'Alle Bezoeken',
        countLabel: '{count} bezoeken',
        exportCsv: 'CSV Exporteren',
        columns: {
            visitDate: 'Bezoekdatum',
            city: 'Stad',
            placeName: 'Plaatsnaam',
            trip: 'Reis',
            duration: 'Duur',
            endTime: 'Eindtijd',
            dayOfWeek: 'Dag van de Week'
        },
        notAvailable: 'Nvt',
        unknownPlace: 'Onbekend',
        tripTagTitle: 'Bezoek is gelinkt aan reisplanner: {label}',
        tripTagAriaLabel: 'Reisplanner openen {label}',
        tripFallbackLabel: 'Reis #{id}',
        openInTimelineAriaLabel: 'Bezoekdag in tijdlijn openen',
        openInTimelineTooltip: 'Bezoekdag in tijdlijn openen',
        empty: {
            title: 'Geen Bezoeken Gevonden',
            message: 'Geen bezoeken geregistreerd voor deze locatie.'
        }
    }
}
