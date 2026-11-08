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
    areaDrawing: {
        "done": "Klaar",
        "adjustInstruction": "Sleep een hoekpunt om het te verplaatsen, of sleep een zijde om een hoekpunt toe te voegen en het vorm te geven. Gereed past de wijzigingen toe; Annuleren verwijdert ze.",
        "adjust": "Hoekpunten aanpassen",
        "corner": "Hoekpunt {number}. Sleep of gebruik pijltoetsen om te verplaatsen. Druk op + om de volgende zijde te splitsen. Shift verplaatst sneller.",
    "title": "Favoriete gebied tekenen",
    "rectangle": "Rechthoek",
    "polygon": "Polygoon",
    "undo": "Laatste punt ongedaan maken",
    "finish": "Vorm afronden",
    "rectangleInstruction": "Sleep om een rechthoek te tekenen.",
    "polygonInstruction": "Klik of tik om hoeken toe te voegen. Sleep hoeken voor precisie. Selecteer de eerste of laatste hoek, of Vorm afronden, om te sluiten. Escape annuleert.",
    "errors": {
        "vertices": "Gebruik tussen de 3 en 500 hoeken.",
        "coordinates": "Gebruik geldige lengte- en breedtegraadcoördinaten.",
        "closed": "De vorm moet gesloten zijn.",
        "crossing": "Zijden mogen niet kruisen of overlappen; de vorm moet een positief oppervlak hebben.",
        "antimeridian": "Vormen die de datumgrens oversteken worden niet ondersteund."
    }
},
    addFavorite: {
        locationNamePlaceholder: 'Locatienaam',
        save: 'Opslaan'
    },
    editFavorite: {
        nameLabel: 'Naam',
        namePlaceholder: 'Voer locatienaam in',
        cityLabel: 'Stad',
        cityPlaceholder: 'Voer stad in (optioneel)',
        countryLabel: 'Land',
        countryPlaceholder: 'Voer land in (optioneel)',
        areaBoundariesTitle: 'Gebiedsgrenzen',
        drawing: 'Tekenen...',
        redrawArea: 'Gebied opnieuw tekenen',
        drawInstruction: 'Klik en sleep op de kaart om een nieuw rechthoekig gebied te tekenen',
        currentBoundsLabel: 'Huidige grenzen:',
        neLabel: 'NO:',
        swLabel: 'ZW:',
        save: 'Opslaan'
    },
    reconcile: {
        selectProvider: 'Provider selecteren',
        providerPlaceholder: 'Kies een geocoderingsprovider',
        primaryTag: 'Primaire',
        fallbackTag: 'Fallback',
        providerHint: 'De provider wordt gebruikt om nieuwe geocoderingsgegevens op te halen',
        detailsTitle: 'Herleidingsdetails',
        filteredBadge: '(gefilterd)',
        allBadge: '(alle)',
        activeFiltersLabel: 'Actieve filters:',
        searchFilter: 'Zoeken: "{search}"',
        actionLabel: 'Actie:',
        importantLabel: 'Belangrijk:',
        originalKeptOnFailure: 'Als herleiding mislukt, blijven de oorspronkelijke gegevens behouden',
        cannotBeUndone: 'Deze bewerking kan niet ongedaan worden gemaakt',
        progressTitle: 'Herleidingsvoortgang',
        processedLabel: 'Verwerkt:',
        successfulLabel: 'Geslaagd:',
        errorsLabel: 'Fouten:',
        completedSuccessfully: 'Herleiding succesvol voltooid!',
        completedWithErrors: 'Voltooid met {count} fout. | Voltooid met {count} fouten.',
        itemsReconciledSuccessfully: '{count} item succesvol herleid. | {count} items succesvol herleid.',
        jobFailed: 'Herleidingsbaan mislukt',
        unexpectedError: 'Er is een onverwachte fout opgetreden',
        reconcileButton: 'Herleiden',
        favoritesToReconcileLabel: 'Favorieten om te herleiden:',
        typeFilter: 'Type: {type}',
        favoriteActionValue: 'Stad en land bijwerken vanuit provider',
        favoriteWarnings: {
            cityCountryOnly: 'Dit werkt alleen de stad en land-velden bij',
            nameUnchanged: 'Naam en locatiecoördinaten blijven ongewijzigd',
            areaCenterPoint: 'Voor gebieden wordt het middelpunt gebruikt voor geocodering'
        },
        favorite: {
            dialogTitleFiltered: '{count} Gefilterde Favorieten Herleiden',
            dialogTitleAll: 'Alle {count} Favorieten Herleiden',
            dialogTitleSingle: 'Favoriete Locatie Herleiden',
            dialogTitleMultiple: '{count} Geselecteerde Favorieten Herleiden'
        },
        geocoding: {
            providerSwitchHint: 'Gebruik Geselecteerde Herleiden / Alle Herleiden om oude records te vernieuwen met de geselecteerde provider na het wisselen van providers.',
            resultsToReconcileLabel: 'Resultaten om te herleiden:',
            providerFilter: 'Provider: {provider}',
            actionValue: 'Vers data ophalen bij geselecteerde provider',
            warnings: {
                displayNameCityCountry: 'Dit werkt de weergavenaam, stad en land-velden bij',
                syncedAcrossStays: 'Wijzigingen worden gesynchroniseerd over alle tijdlijnverblijfplaatsen',
                cachedEntriesNotAuto: 'Providerwijzigingen in instellingen herschrijven bestaande gecachete records niet automatisch'
            },
            dialogTitleFiltered: '{count} Gefilterde Resultaten Herleiden',
            dialogTitleAll: 'Alle {count} Resultaten Herleiden',
            dialogTitleSingle: 'Geocodering Resultaat Herleiden',
            dialogTitleMultiple: '{count} Geselecteerde Resultaten Herleiden'
        }
    },
    geocodingEdit: {
        header: 'Geocodering Resultaat Bewerken',
        mapLabel: 'Locatiekaart',
        mapHint: 'Alleen-lezen kaart met de geocoderingslocatie',
        displayNameLabel: 'Weergavenaam',
        displayNamePlaceholder: 'Voer locatie weergavenaam in',
        displayNameHint: 'De belangrijkste weergavenaam die voor deze locatie wordt getoond',
        cityLabel: 'Stad',
        cityPlaceholder: 'Voer stadnaam in',
        cityHint: 'Optionele stadnaam',
        countryLabel: 'Land',
        countryPlaceholder: 'Voer landnaam in',
        countryHint: 'Optionele landnaam',
        providerLabel: 'Provider:',
        coordinatesLabel: 'Coördinaten:',
        noteLabel: 'Notitie:',
        syncNoteMessage: 'Wijzigingen worden gesynchroniseerd over alle tijdlijnverblijfplaatsen die dit geocoderingsresultaat gebruiken.',
        saveChanges: 'Wijzigingen opslaan'
    }
}
