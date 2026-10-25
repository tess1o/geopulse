/**
 * Place details page (German): the map card, notes section, stats card, and visit history table.
 */
export default {
    map: {
        title: 'Lage',
        defaultLocationName: 'Ort',
        defaultAreaName: 'Bereich des Orts'
    },
    stats: {
        title: 'Besuchsübersicht',
        totalVisits: 'Besuche gesamt',
        totalTime: 'Gesamtzeit',
        averageVisit: 'Ø Besuch',
        placesVisited: 'Besuchte Orte',
        activityHeader: 'Aktivität',
        thisWeek: 'Diese Woche',
        thisMonth: 'Diesen Monat',
        thisYear: 'Dieses Jahr',
        visitSpanHeader: 'Zeitraum der Besuche',
        firstVisit: 'Erster Besuch',
        lastVisit: 'Letzter Besuch',
        durationRangeHeader: 'Spanne der Dauer',
        shortestVisit: 'Kürzester Besuch',
        longestVisit: 'Längster Besuch',
        visitPatternsHeader: 'Besuchsmuster',
        typicalDay: 'Typischer Tag',
        arrivalPeriod: 'Ankunftszeit',
        visitCadence: 'Häufigkeit',
        notAvailable: 'k. A.',
        zeroSeconds: '0 Sekunden',
        lessThanDaily: 'Seltener als täglich',
        everyDay: 'Jeden Tag',
        everyNDays: 'Alle {count} Tage'
    },
    notes: {
        defaultTitle: 'Notizen',
        defaultEmptyMessage: 'Keine Notizen zu diesem Ort gefunden.',
        viewAll: 'Alle ansehen',
        reloadAriaLabel: 'Notizen neu laden',
        reloadTooltip: 'Notizen neu laden',
        openInMemosAriaLabel: 'In Memos öffnen',
        openInMemosTooltip: 'In Memos öffnen',
        truncatedNotice: 'Diese Memos-Notiz ist groß, deshalb zeigt GeoPulse nur eine gekürzte Vorschau.',
        loadingInline: 'Notizen werden geladen …',
        loadFailed: 'Notizen konnten nicht geladen werden',
        sourceMemos: 'Memos',
        sourceGeopulse: 'GeoPulse',
        locationSource: {
            explicit: 'Mit Geotag',
            derivedStay: 'Ort des Aufenthalts',
            derivedTripGps: 'GPS der Fahrt',
            derivedTripInterpolated: 'Geschätzt aus der Fahrt'
        }
    },
    detailsPage: {
        loading: 'Ortsdetails werden geladen …',
        errorTitle: 'Ortsdetails nicht geladen',
        loadFailed: 'Ortsdetails konnten nicht geladen werden',
        pageTitleFallback: 'Ortsdetails',
        subtitleFallback: 'Ausführliche Informationen und Besuchsverlauf',
        typeFavorite: 'Favorit',
        typeGeocoded: 'Geocodierter Ort',
        coordinatesCenter: 'Mittelpunkt: {coordinates}',
        edit: 'Bearbeiten',
        editDetails: 'Details bearbeiten',
        createFavorite: 'Favorit erstellen',
        relatedFavorite: {
            titleArea: 'Besuche einem Favoritenbereich zugeordnet',
            titlePoint: 'Besuche einem Favoriten zugeordnet',
            messageArea: 'Dieser Ort liegt in deinem Favoritenbereich. Deine Besuche hier werden erfasst unter:',
            messagePoint: 'Deine Besuche an diesem Ort werden unter deinem nahen Favoriten erfasst:',
            distanceAway: '({distance} entfernt)',
            visitsTracked: '{count} Besuche erfasst',
            viewDetails: 'Details zu {name}'
        },
        latestPhotosTitle: 'Neueste Fotos in der Nähe von {name}',
        noPhotosMessage: 'Keine Immich-Fotos in der Nähe dieses Orts gefunden.',
        notesTitle: 'Notizen in der Nähe von {name}',
        noNotesMessage: 'Keine Notizen in der Nähe dieses Orts gefunden.',
        editFavoriteDialogHeader: 'Favorit bearbeiten',
        createFavoriteDialog: {
            header: 'Favorit erstellen',
            message: 'Erstelle einen Favoriten an diesem Geocoding-Punkt. Du kannst ihm einen eigenen Namen geben.',
            nameLabel: 'Name des Favoriten',
            namePlaceholder: 'z. B. Zuhause, Arbeit, Fitnessstudio',
            coordinatesInfo: 'Koordinaten: {lat}, {lon}'
        },
        invalidCoordinatesDetail: 'Ungültige Koordinaten für den Favoriten.',
        favoriteCreatedSuccess: 'Favorit erstellt.',
        favoriteCreateFailed: 'Favorit konnte nicht erstellt werden.',
        loadVisitsFailed: 'Besuchsverlauf konnte nicht geladen werden',
        exportNoDataSummary: 'Keine Daten',
        exportNoDataDetail: 'Keine Ortsdaten vorhanden',
        exportingSummary: 'Export läuft',
        exportingDetail: 'CSV-Export wird vorbereitet …',
        exportSuccessSummary: 'Export erfolgreich',
        exportSuccessDetail: '{count} Besuche als CSV exportiert',
        exportFailedSummary: 'Export fehlgeschlagen',
        exportFailedDetail: 'Besuche konnten nicht als CSV exportiert werden',
        geocodingUpdatedSuccessSummary: 'Gespeichert',
        geocodingUpdatedSuccessDetail: 'Geocoding-Ort aktualisiert',
        geocodingUpdateFailedDetail: 'Geocoding-Ort konnte nicht aktualisiert werden'
    },
    visitsTable: {
        title: 'Besuchsverlauf',
        allVisits: 'Alle Besuche',
        countLabel: '{count} Besuche',
        exportCsv: 'CSV exportieren',
        columns: {
            visitDate: 'Datum',
            city: 'Stadt',
            placeName: 'Ortsname',
            trip: 'Reise',
            duration: 'Dauer',
            endTime: 'Ende',
            dayOfWeek: 'Wochentag'
        },
        notAvailable: 'k. A.',
        unknownPlace: 'Unbekannt',
        tripTagTitle: 'Besuch ist mit dem Reiseplaner verknüpft: {label}',
        tripTagAriaLabel: 'Reiseplaner {label} öffnen',
        tripFallbackLabel: 'Reise #{id}',
        openInTimelineAriaLabel: 'Tag des Besuchs in der Zeitleiste öffnen',
        openInTimelineTooltip: 'Tag des Besuchs in der Zeitleiste öffnen',
        empty: {
            title: 'Keine Besuche gefunden',
            message: 'Für diesen Ort wurden keine Besuche erfasst.'
        }
    }
}
