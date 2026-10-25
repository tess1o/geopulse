/**
 * The favorite-location and geocoding-result dialogs (German): add/edit a favorite, reconcile favorites
 * or geocoding results against a provider, and edit a single geocoding result.
 */
export default {
    addFavorite: {
        locationNamePlaceholder: 'Name des Orts',
        save: 'Speichern'
    },
    editFavorite: {
        nameLabel: 'Name',
        namePlaceholder: 'Name des Orts eingeben',
        cityLabel: 'Stadt',
        cityPlaceholder: 'Stadt eingeben (optional)',
        countryLabel: 'Land',
        countryPlaceholder: 'Land eingeben (optional)',
        areaBoundariesTitle: 'Grenzen des Bereichs',
        drawing: 'Wird gezeichnet …',
        redrawArea: 'Bereich neu zeichnen',
        drawInstruction: 'Klicke und zieh auf der Karte, um einen neuen rechteckigen Bereich zu zeichnen',
        currentBoundsLabel: 'Aktuelle Grenzen:',
        neLabel: 'NO:',
        swLabel: 'SW:',
        save: 'Speichern'
    },
    reconcile: {
        selectProvider: 'Anbieter wählen',
        providerPlaceholder: 'Geocoding-Anbieter wählen',
        primaryTag: 'Primär',
        fallbackTag: 'Ersatz',
        providerHint: 'Über diesen Anbieter werden neue Geocoding-Daten abgerufen',
        detailsTitle: 'Details zum Abgleich',
        filteredBadge: '(gefiltert)',
        allBadge: '(alle)',
        activeFiltersLabel: 'Aktive Filter:',
        searchFilter: 'Suche: „{search}“',
        actionLabel: 'Aktion:',
        importantLabel: 'Wichtig:',
        originalKeptOnFailure: 'Schlägt der Abgleich fehl, bleiben die ursprünglichen Daten erhalten',
        cannotBeUndone: 'Das lässt sich nicht rückgängig machen',
        progressTitle: 'Fortschritt des Abgleichs',
        processedLabel: 'Verarbeitet:',
        successfulLabel: 'Erfolgreich:',
        errorsLabel: 'Fehler:',
        completedSuccessfully: 'Abgleich erfolgreich abgeschlossen!',
        completedWithErrors: 'Mit {count} Fehler abgeschlossen. | Mit {count} Fehlern abgeschlossen.',
        itemsReconciledSuccessfully: '{count} Eintrag abgeglichen. | {count} Einträge abgeglichen.',
        jobFailed: 'Abgleich fehlgeschlagen',
        unexpectedError: 'Ein unerwarteter Fehler ist aufgetreten',
        reconcileButton: 'Abgleichen',
        favoritesToReconcileLabel: 'Abzugleichende Favoriten:',
        typeFilter: 'Typ: {type}',
        favoriteActionValue: 'Stadt und Land vom Anbieter übernehmen',
        favoriteWarnings: {
            cityCountryOnly: 'Nur die Felder Stadt und Land werden aktualisiert',
            nameUnchanged: 'Name und Koordinaten bleiben unverändert',
            areaCenterPoint: 'Bei Bereichen wird der Mittelpunkt für das Geocoding verwendet'
        },
        favorite: {
            dialogTitleFiltered: '{count} gefilterte Favoriten abgleichen',
            dialogTitleAll: 'Alle {count} Favoriten abgleichen',
            dialogTitleSingle: 'Favorit abgleichen',
            dialogTitleMultiple: '{count} ausgewählte Favoriten abgleichen'
        },
        geocoding: {
            providerSwitchHint: 'Nach einem Wechsel des Anbieters aktualisierst du alte Einträge mit „Auswahl abgleichen“ bzw. „Alle abgleichen“.',
            resultsToReconcileLabel: 'Abzugleichende Ergebnisse:',
            providerFilter: 'Anbieter: {provider}',
            actionValue: 'Aktuelle Daten vom gewählten Anbieter abrufen',
            warnings: {
                displayNameCityCountry: 'Anzeigename, Stadt und Land werden aktualisiert',
                syncedAcrossStays: 'Die Änderungen werden in allen Aufenthalten der Zeitleiste übernommen',
                cachedEntriesNotAuto: 'Ein Anbieterwechsel in den Einstellungen ändert vorhandene Einträge im Cache nicht automatisch'
            },
            dialogTitleFiltered: '{count} gefilterte Ergebnisse abgleichen',
            dialogTitleAll: 'Alle {count} Ergebnisse abgleichen',
            dialogTitleSingle: 'Geocoding-Ergebnis abgleichen',
            dialogTitleMultiple: '{count} ausgewählte Ergebnisse abgleichen'
        }
    },
    geocodingEdit: {
        header: 'Geocoding-Ergebnis bearbeiten',
        mapLabel: 'Karte',
        mapHint: 'Schreibgeschützte Karte mit dem Ort des Geocoding-Ergebnisses',
        displayNameLabel: 'Anzeigename',
        displayNamePlaceholder: 'Anzeigenamen des Orts eingeben',
        displayNameHint: 'Der Hauptname, unter dem dieser Ort angezeigt wird',
        cityLabel: 'Stadt',
        cityPlaceholder: 'Stadt eingeben',
        cityHint: 'Optionaler Name der Stadt',
        countryLabel: 'Land',
        countryPlaceholder: 'Land eingeben',
        countryHint: 'Optionaler Name des Lands',
        providerLabel: 'Anbieter:',
        coordinatesLabel: 'Koordinaten:',
        noteLabel: 'Hinweis:',
        syncNoteMessage: 'Die Änderungen werden in allen Aufenthalten der Zeitleiste übernommen, die dieses Geocoding-Ergebnis nutzen.',
        saveChanges: 'Änderungen speichern'
    }
}
