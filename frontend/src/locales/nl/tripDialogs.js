// DRAFT (Nederlands): copied from English -- translate the values, keep the keys. Progress: npm run i18n:status -- nl
/**
 * Trip-related dialogs: trip details, trip/stay split, map-matching details, movement-type quick
 * edit, data-gap-to-stay conversion, and GPS point edit.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    tripDetails: {
        dialogTitleDefault: 'Reisdetails',
        dialogTitleWithType: 'Reisdetails - {type}',
        sections: {
            route: 'Reisroute',
            timing: 'Tijdsduur',
            tripInfo: 'Reisdetails',
            routeInfo: 'Route',
            coordinates: 'Coördinaten'
        },
        labels: {
            start: 'Start:',
            end: 'Eind:',
            duration: 'Duur:',
            distance: 'Afstand:',
            averageSpeed: 'Gemiddelde snelheid:',
            routePoints: 'Routepunten:',
            routePointsValue: '{count} punten',
            origin: 'Herkomst:',
            destination: 'Bestemming:'
        },
        unknownOrigin: 'Onbekende herkomst',
        unknownDestination: 'Onbekende bestemming',
        notAvailable: 'N.v.t.',
        close: 'Sluiten',
        toasts: {
            copiedSummary: 'Gekopieerd!',
            copiedDetail: 'Coördinaten gekopieerd naar klembord',
            copyFailedSummary: 'Kopiëren mislukt',
            copyFailedDetail: 'Kon coördinaten niet kopiëren',
            gpsUnavailableSummary: 'GPS-gegevens niet beschikbaar',
            gpsUnavailableDetail: 'Kon GPS-punten voor deze reis niet laden'
        }
    },
    staySplit: {
        header: 'Reis splitsen met verblijf',
        loadingPath: 'Reisroute laden...',
        clickToSetStay: 'Klik op de route om je verblijfslocatie in te stellen.',
        stayStartLabel: 'Verblijf start',
        stayEndLabel: 'Verblijf eind',
        placeLabel: 'Waar heb je gestopt?',
        selectedOnRoute: 'Punt op de route',
        updatingPreview: 'Splitsresultaat bijwerken...',
        preview: {
            originalTrip: 'Originele reis',
            splitResult: 'Splitsresultaat',
            tripFor: 'Reis voor {duration}',
            to: 'naar {name}',
            stayAt: 'Verblijf bij {name}',
            forDuration: 'voor {duration}',
            from: 'van {name}'
        },
        saveSplit: 'Opslaan als splitsing',
        unknownTimeRange: 'Onbekend tijdsbereik',
        selectedStayFallback: 'Geselecteerd verblijf',
        selectedPlaceFallback: 'geselecteerde plaats',
        validation: {
            tripMissing: 'Reis ontbreekt.',
            readOnly: 'Tijdlijnbewerkingen zijn uitgeschakeld.',
            selectStayLocation: 'Selecteer de verblijfslocatie op de reiskaart.',
            selectValidTimes: 'Selecteer geldige start- en eindtijden.',
            endAfterStart: 'Verblijfseind moet na verblijfsstart zijn.',
            minDuration: 'Verblijfsduur moet minimaal 60 seconden zijn.',
            startAfterTripStart: 'Verblijf moet beginnen nadat de reis begint.',
            endBeforeTripEnd: 'Verblijf moet eindigen voordat de reis eindigt.',
            selectPlace: 'Kies een plaats voor de stop, of geef een nieuwe naam op.',
            selectValidStayLocation: 'Selecteer een geldige verblijfslocatie.'
        },
        pathErrors: {
            noPoints: 'Geen routepunten beschikbaar voor deze reis.',
            loadFailed: 'Kon reisroute niet laden'
        },
        previewErrorFallback: 'Kon reis splitsingsvoorbeeld niet tonen',
        toasts: {
            splitFailedSummary: 'Splitsen mislukt',
            splitFailedFallback: 'Kon reis niet splitsen'
        },
        distanceFromClickMeters: '{value}m vanaf klik',
        distanceFromClickKm: '{value}km vanaf klik'
    },
    mapMatchingDetails: {
        header: 'Kaartmatching details',
        statusLabels: {
            completed: 'MATCHED',
            queued: 'QUEUED',
            processing: 'MATCHING',
            failed: 'FAILED',
            skipped: 'SKIPPED'
        },
        neverQueuedNote: 'Deze reis werd overgeslagen voordat hij de kaartmatching-wachtrij bereikte, dus er is geen opgeslagen resultaat om opnieuw te proberen.',
        detailCaption: 'Gemeld door de routeringsengine',
        lastAttemptedOn: 'Laatst geprobeerd op {when}',
        refinedOn: 'Verfijnd op {when}',
        openSettings: 'Kaartmatching-instellingen openen',
        adminHint: 'Een beheerder kan kaartmatching opnieuw uitvoeren via Beheer → Instellingen → Kaartmatching.',
        close: 'Sluiten'
    },
    movementTypeQuickEdit: {
        header: 'Bewegingstype bewerken',
        unknownAlgorithmWarning: 'Algoritme herkent dit bewegingstype niet. Stel het handmatig in.',
        readOnlyWarning: 'Bewegingstypebewerkingen zijn alleen-lezen in demo-modus.',
        selectPlaceholder: 'Selecteer bewegingstype',
        save: 'Opslaan',
        reset: 'Resetten',
        close: 'Sluiten',
        unknownTime: 'Onbekende tijd',
        toasts: {
            updatedSummary: 'Beweging bijgewerkt',
            updatedDetail: 'Reis ingesteld op {type}',
            updateFailedSummary: 'Bijwerken mislukt',
            updateFailedFallback: 'Kon bewegingstype niet bijwerken',
            resetSummary: 'Beweging gereset',
            resetDetail: 'Reis gereset naar {type}',
            resetFailedSummary: 'Resetten mislukt',
            resetFailedFallback: 'Kon bewegingstype niet resetten'
        }
    },
    dataGapToStay: {
        header: 'Datagap omzetten naar verblijf',
        gapTag: 'Datagap',
        unknownTimeRange: 'Onbekend tijdsbereik',
        locationSourceLabel: 'Verblijfslocatiebron',
        resolvingLocation: 'Laatst bekende puntlocatie ophalen...',
        defaultLocationLabel: 'Standaardlocatie:',
        unknownLocation: 'Onbekende locatie',
        selectedLocationTypeLabel: 'Geselecteerd locatietype',
        searchPlaceLabel: 'Zoek favoriete of geocodeerplaats',
        searchPlaceholder: 'Typ minimaal 2 tekens...',
        latitudePlaceholder: 'Breedtegraad',
        longitudePlaceholder: 'Lengtegraad',
        customNamePlaceholder: 'Optionele aangepaste locatienaam',
        convert: 'Omzetten naar verblijf',
        strategyOptions: {
            latestPoint: 'Laatst bekende punt (standaard)',
            selectedLocation: 'Geselecteerde locatie'
        },
        selectedSourceOptions: {
            place: 'Favoriete of geocodeerplaats',
            custom: 'Aangepaste coördinaten'
        },
        errors: {
            previewFailed: 'Kon voorbeeld van laatst bekend punt niet ophalen',
            searchFailed: 'Kon plaatsen niet zoeken'
        },
        toasts: {
            convertedSummary: 'Omgezet',
            convertedDetail: 'Datagap is omgezet naar verblijf.',
            failedSummary: 'Omzetten mislukt',
            failedFallback: 'Kon datagap niet omzetten'
        }
    },
    gpsPointEdit: {
        header: 'GPS-punt bewerken',
        mapInstructions: 'Klik op de kaart om een nieuwe locatie voor dit GPS-punt te selecteren.',
        locationLabel: 'Locatie',
        latitudeLabel: 'Breedtegraad',
        longitudeLabel: 'Lengtegraad',
        speedLabel: 'Snelheid (km/h)',
        speedPlaceholder: 'Snelheid in km/h',
        accuracyLabel: 'Nauwkeurigheid (meters)',
        accuracyPlaceholder: 'Nauwkeurigheid in meters',
        stageChange: 'Wijziging toevoegen',
        revertToOriginal: 'Terug naar origineel',
        stagingHint: 'Wijzigingen worden niet direct opgeslagen: pas ze samen toe met "Wijzigingen toepassen" op de GPS-data pagina.',
        validation: {
            latitudeRequired: 'Breedtegraad is vereist',
            latitudeRange: 'Breedtegraad moet tussen -90 en 90 liggen',
            longitudeRequired: 'Lengtegraad is vereist',
            longitudeRange: 'Lengtegraad moet tussen -180 en 180 liggen'
        },
        mapPopups: {
            currentLocation: 'Huidige locatie - Klik op kaart om te verplaatsen',
            originalLocation: 'Originele locatie'
        }
    }
};
