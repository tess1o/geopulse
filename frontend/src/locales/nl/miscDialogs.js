export default {
    timelineRegeneration: {
        header: 'Tijdlijnregeneratie',
        completedMessage: 'Tijdlijngeneratie succesvol voltooid!',
        gpsPointsLoaded: '{loaded} / {total} GPS-punten geladen',
        pointsProcessed: '{processed} / {total} punten verwerkt',
        locationsGeocoded: '{resolved} / {total} locaties gecodeerd',
        fromFavorites: '{count} van favorieten',
        fromCache: '{count} uit cache',
        fromExternalApi: '{count} van externe API',
        pending: '{count} in afwachting',
        viewDetailedProgress: 'Gedetailleerde voortgang bekijken',
        fallbackNote: 'Dit proces kan 5-15 seconden of langer duren afhankelijk van de omvang van uw gegevens. Uw tijdlijn is tijdelijk niet beschikbaar tijdens regeneratie.',
        pleaseWait: 'Even geduld...',
        titles: {
            favorite: 'Favoriet toevoegen & Tijdlijn regenereren',
            favoriteDelete: 'Favoriet verwijderen & Tijdlijn regenereren',
            preferences: 'Voorkeuren toepassen & Tijdlijn regenereren',
            classification: 'Reisclassificaties bijwerken',
            reconstruction: 'Ontbrekende tijdlijndata toepassen',
            general: 'Tijdlijn regenereren'
        },
        messages: {
            favorite: "We voegen uw favoriete locatie toe en regenereren uw volledige tijdlijn om deze wijziging te verwerken. Dit zorgt ervoor dat alle tijdlijngegevens accuraat en up-to-date blijven.",
            favoriteDelete: "We verwijderen uw favoriete locatie en regenereren uw volledige tijdlijn om deze wijziging weer te geven. Dit zorgt ervoor dat alle tijdlijngegevens accuraat en up-to-date blijven.",
            preferences: "We passen uw nieuwe voorkeuren toe en regenereren uw volledige tijdlijn op basis van de bijgewerkte instellingen. Dit zorgt voor optimale tijdlijnnauwkeurigheid met uw voorkeuren.",
            classification: "We berekenen de bewegings typen voor uw bestaande reizen opnieuw op basis van uw bijgewerkte snelheidsdrempels. Dit proces werkt bij hoe uw reizen worden geclassificeerd zonder de onderliggende tijdlijnstructuur te wijzigen.",
            reconstruction: "We passen gegenereerde GPS-punten toe van uw verblijfplaatsen en reizen, en werken vervolgens het getroffen tijdlijndeel bij. Bestaande tijdlijngegevens worden niet vervangen.",
            general: "We regenereren uw volledige tijdlijn vanuit uw GPS-gegevens. Dit proces zorgt ervoor dat uw tijdlijn accuraat is en alle beschikbare locatie-informatie weerspiegelt."
        }
    },
    stayDetails: {
        titleWithLocation: 'Verblijfdetails - {location}',
        unknownLocation: 'Onbekende Locatie',
        mapSectionTitle: 'Locatiekaart',
        timingTitle: 'Timing',
        start: 'Start:',
        end: 'Eind:',
        duration: 'Duur:',
        coordinatesTitle: 'Coördinaten',
        latitude: 'Breedtegraad:',
        longitude: 'Lengtegraad:',
        coordinates: 'Coördinaten:',
        notAvailable: 'Nvt',
        close: 'Sluiten',
        toasts: {
            copiedSummary: 'Gekopieerd!',
            copiedDetail: 'Coördinaten naar klembord gekopieerd',
            copyFailedSummary: 'Kopiëren mislukt',
            copyFailedDetail: 'Coördinaten kopiëren is mislukt'
        }
    },
    locationLookup: {
        header: 'Bezoeken bij dit punt',
        withinRadius: 'binnen {radius} m',
        checkingStays: 'Uw opgenomen verblijfplaatsen controleren…',
        tryAgain: 'Opnieuw proberen',
        hasVisits: 'U heeft opgenomen bezoeken op deze locatie.',
        recordedStayFallback: 'Opgenomen verblijf',
        placeDetails: 'Plaatsdetails',
        visitCount: '{count} bezoek | {count} bezoeken',
        firstVisit: 'Eerste: {date}',
        lastVisit: 'Laatste: {date}',
        openVisitDayTooltip: 'Bezoekdag in tijdlijn openen',
        savedPlacesHeading: 'Opgeslagen plaatsen op dit punt',
        unnamedFavorite: 'Onbenoemde favoriet',
        noVisitFound: 'Geen opgenomen bezoek gevonden binnen {radius} m van dit punt.',
        closestStaysHeading: 'Dichtstbijzijnde opgenomen verblijfplaatsen',
        unknownLocation: 'Onbekende locatie',
        noRecordedStays: 'Geen opgenomen verblijfplaatsen gevonden.',
        close: 'Sluiten'
    },
    timelineLabelForm: {
        nameLabel: 'Labelnaam *',
        namePlaceholder: 'bijv. Spanje Vakantie, Werkreis naar NYC',
        dateRangeLabel: 'Datumbereik *',
        dateRangePlaceholder: 'Selecteer start- en einddatums',
        colorLabel: 'Kleur',
        colorPreview: 'Voorbeeld',
        randomButton: 'Willekeurig',
        showAsPresetLabel: 'Weergeven als datumpreset',
        showAsPresetHint: 'Wanneer ingeschakeld, verschijnt dit label in de DatePicker preset-dropdowns.',
        nameRequired: 'Tagnaam is verplicht',
        dateRangeRequired: 'Datumbereik is verplicht',
        cancel: 'Annuleren'
    },
    createTimelineLabel: {
        header: 'Tijdlijnlabel maken',
        createButton: 'Maken',
        overlap: {
            message: 'Dit label overlapt met: {names}. Wilt u het desondanks maken?',
            header: 'Overlappende Labels Gedetecteerd',
            acceptLabel: 'Desondanks Maken',
            rejectLabel: 'Annuleren'
        },
        toasts: {
            checkOverlapsFailedFallback: 'Overlappen controleren is mislukt',
            createdSummary: 'Gemaakt',
            createdDetail: 'Tijdlijnlabel succesvol gemaakt',
            createFailedFallback: 'Tijdlijnlabel maken is mislukt'
        }
    },
    editTimelineLabel: {
        header: 'Tijdlijnlabel bewerken',
        readOnlyHeader: 'Tijdlijnlabel bekijken (alleen-lezen)',
        activeOwnTracksTitle: 'Actieve OwnTracks Tag',
        activeOwnTracksMessage: "Deze tag wordt momenteel beheerd door OwnTracks en kan niet worden bewerkt terwijl hij actief is. U kunt het bewerken nadat het is voltooid (wanneer u tags in OwnTracks wijzigt).",
        updateButton: 'Bijwerken',
        toasts: {
            updatedSummary: 'Bijgewerkt',
            updatedDetail: 'Tijdlijnlabel succesvol bijgewerkt',
            updateFailedFallback: 'Tijdlijnlabel bijwerken is mislukt'
        }
    },
    bulkEdit: {
        header: 'Bulkbewerking {count} {itemType}',
        editingPrefix: 'U bewerkt',
        fieldsHeading: 'Velden Selecteren om Bij te Werken',
        updateCity: 'Stad bijwerken',
        updateCountry: 'Land bijwerken',
        cityPlaceholder: 'Voer stadsnaam in',
        countryPlaceholder: 'Voer landnaam in',
        cityRequired: 'Stadwaarde is verplicht',
        countryRequired: 'Landwaarde is verplicht',
        selectAtLeastOneField: 'Selecteer ten minste één veld om bij te werken',
        typoWarning: {
            header: 'Mogelijke Typo Gedetecteerd',
            notFoundMessage: 'De volgende waarde(n) worden niet gevonden in uw bestaande gegevens:',
            didYouMean: 'Bedoelde u:',
            continuePrompt: 'Wilt u door gaan met deze waarden?',
            continueAnyway: 'Desondanks Doorgaan'
        },
        fields: {
            city: 'Stad',
            country: 'Land'
        },
        updating: 'Bijwerken...',
        updateButton: '{count} items bijwerken',
        toasts: {
            queuedDetail: '{count} bewerkingen in de wachtrij geplaatst. Gebruik Alles Opslaan om ze toe te passen.',
            completeSummary: 'Bulk Bijwerken Voltooid',
            partialDetail: 'Bijgewerkt {success} van {total} items ({failed} mislukt)',
            successDetail: 'Succesvol {count} {itemType} bijgewerkt',
            failedSummary: 'Bijwerken Mislukt',
            failedFallback: '{itemType} bijwerken is mislukt'
        }
    },
    bulkSaveConfirm: {
        updatesItem: '{count} favorieten om te bewerken',
        deletesItem: '{count} favorieten om te verwijderen',
        header: 'Bulkopslag bevestigen',
        summaryTitle: 'Wijs {count} in afwachting zijnde wijziging toe: | Wijs {count} in afwachting zijnde wijzigingen toe:',
        pointsItem: '{count} punt toevoegen | {count} punten toevoegen',
        areasItem: '{count} gebied toevoegen | {count} gebieden toevoegen',
        infoMessage: 'Alle wijzigingen worden samen toegepast, gevolgd door één tijdlijnheropbouw.',
        confirm: 'Bevestigen'
    }
}
