/**
 * Trip-related dialogs (German): trip details, trip/stay split, map-matching details, movement-type
 * quick edit, data-gap-to-stay conversion, and GPS point edit.
 */
export default {
    tripDetails: {
        dialogTitleDefault: 'Details zur Fahrt',
        dialogTitleWithType: 'Details zur Fahrt – {type}',
        sections: {
            route: 'Strecke der Fahrt',
            timing: 'Zeit',
            tripInfo: 'Details zur Fahrt',
            routeInfo: 'Route',
            coordinates: 'Koordinaten'
        },
        labels: {
            start: 'Beginn:',
            end: 'Ende:',
            duration: 'Dauer:',
            distance: 'Strecke:',
            averageSpeed: 'Ø Geschwindigkeit:',
            routePoints: 'Routenpunkte:',
            routePointsValue: '{count} Punkte',
            origin: 'Start:',
            destination: 'Ziel:'
        },
        unknownOrigin: 'Unbekannter Start',
        unknownDestination: 'Unbekanntes Ziel',
        notAvailable: 'k. A.',
        close: 'Schließen',
        toasts: {
            copiedSummary: 'Kopiert!',
            copiedDetail: 'Koordinaten in die Zwischenablage kopiert',
            copyFailedSummary: 'Kopieren fehlgeschlagen',
            copyFailedDetail: 'Koordinaten konnten nicht kopiert werden',
            gpsUnavailableSummary: 'GPS-Daten nicht verfügbar',
            gpsUnavailableDetail: 'GPS-Punkte für diese Fahrt konnten nicht geladen werden'
        }
    },
    staySplit: {
        header: 'Fahrt mit Aufenthalt teilen',
        loadingPath: 'Strecke wird geladen …',
        clickToSetStay: 'Klicke auf die Route, um den Ort des Aufenthalts festzulegen.',
        stayStartLabel: 'Beginn des Aufenthalts',
        stayEndLabel: 'Ende des Aufenthalts',
        placeNameLabel: 'Ortsname',
        placeNameResolving: 'Ort wird ermittelt …',
        placeNamePlaceholder: 'Wird automatisch ermittelt, wenn leer',
        updatingPreview: 'Ergebnis der Teilung wird aktualisiert …',
        preview: {
            originalTrip: 'Ursprüngliche Fahrt',
            splitResult: 'Ergebnis der Teilung',
            tripFor: 'Fahrt ({duration})',
            to: 'zu {name}',
            stayAt: 'Aufenthalt: {name}',
            forDuration: 'für {duration}',
            from: 'von {name}'
        },
        saveSplit: 'Teilung speichern',
        unknownTimeRange: 'Unbekannter Zeitraum',
        selectedStayFallback: 'Ausgewählter Aufenthalt',
        selectedPlaceFallback: 'ausgewählter Ort',
        validation: {
            tripMissing: 'Die Fahrt fehlt.',
            readOnly: 'Änderungen an der Zeitleiste sind deaktiviert.',
            selectStayLocation: 'Wähle den Ort des Aufenthalts auf der Karte der Fahrt.',
            selectValidTimes: 'Wähle gültige Start- und Endzeiten.',
            endAfterStart: 'Das Ende des Aufenthalts muss nach dem Beginn liegen.',
            minDuration: 'Der Aufenthalt muss mindestens 60 Sekunden dauern.',
            startAfterTripStart: 'Der Aufenthalt muss nach dem Start der Fahrt beginnen.',
            endBeforeTripEnd: 'Der Aufenthalt muss vor dem Ende der Fahrt enden.',
            placeNameTooLong: 'Der Ortsname ist zu lang.',
            selectValidStayLocation: 'Wähle einen gültigen Ort für den Aufenthalt.'
        },
        pathErrors: {
            noPoints: 'Für diese Fahrt gibt es keine Streckenpunkte.',
            loadFailed: 'Strecke konnte nicht geladen werden'
        },
        previewErrorFallback: 'Vorschau der Teilung nicht möglich',
        toasts: {
            splitSummary: 'Fahrt geteilt',
            splitDetail: 'Der Aufenthalt wurde in die Fahrt eingefügt.',
            splitFailedSummary: 'Teilen fehlgeschlagen',
            splitFailedFallback: 'Fahrt konnte nicht geteilt werden'
        },
        distanceFromClickMeters: '{value} m vom Klickpunkt',
        distanceFromClickKm: '{value} km vom Klickpunkt'
    },
    mapMatchingDetails: {
        header: 'Details zum Map-Matching',
        statusLabels: {
            completed: 'ANGEPASST',
            queued: 'WARTET',
            processing: 'WIRD ANGEPASST',
            failed: 'FEHLGESCHLAGEN',
            skipped: 'ÜBERSPRUNGEN'
        },
        neverQueuedNote: 'Diese Fahrt wurde übersprungen, bevor sie in die Warteschlange fürs Map-Matching kam. Es gibt also kein gespeichertes Ergebnis, das sich erneut versuchen ließe.',
        detailCaption: 'Gemeldet von der Routing-Engine',
        lastAttemptedOn: 'Zuletzt versucht am {when}',
        refinedOn: 'Angepasst am {when}',
        openSettings: 'Einstellungen zum Map-Matching öffnen',
        adminHint: 'Die Administration kann das Map-Matching unter Admin → Einstellungen → Map-Matching erneut ausführen.',
        close: 'Schließen'
    },
    movementTypeQuickEdit: {
        header: 'Fortbewegungsart bearbeiten',
        unknownAlgorithmWarning: 'Der Algorithmus hat die Fortbewegungsart nicht erkannt. Leg sie manuell fest.',
        readOnlyWarning: 'Die Fortbewegungsart lässt sich im Demo-Modus nicht ändern.',
        selectPlaceholder: 'Fortbewegungsart wählen',
        save: 'Speichern',
        reset: 'Zurücksetzen',
        close: 'Schließen',
        unknownTime: 'Unbekannte Zeit',
        toasts: {
            updatedSummary: 'Fortbewegung aktualisiert',
            updatedDetail: 'Fahrt auf {type} gesetzt',
            updateFailedSummary: 'Aktualisieren fehlgeschlagen',
            updateFailedFallback: 'Fortbewegungsart konnte nicht aktualisiert werden',
            resetSummary: 'Fortbewegung zurückgesetzt',
            resetDetail: 'Fahrt auf {type} zurückgesetzt',
            resetFailedSummary: 'Zurücksetzen fehlgeschlagen',
            resetFailedFallback: 'Fortbewegungsart konnte nicht zurückgesetzt werden'
        }
    },
    dataGapToStay: {
        header: 'Datenlücke in Aufenthalt umwandeln',
        gapTag: 'Datenlücke',
        unknownTimeRange: 'Unbekannter Zeitraum',
        locationSourceLabel: 'Quelle für den Ort des Aufenthalts',
        resolvingLocation: 'Ort des letzten bekannten Punkts wird ermittelt …',
        defaultLocationLabel: 'Standardort:',
        unknownLocation: 'Unbekannter Ort',
        selectedLocationTypeLabel: 'Art des gewählten Orts',
        searchPlaceLabel: 'Favorit oder Geocoding-Ort suchen',
        searchPlaceholder: 'Mindestens 2 Zeichen eingeben …',
        latitudePlaceholder: 'Breitengrad',
        longitudePlaceholder: 'Längengrad',
        customNamePlaceholder: 'Optionaler eigener Ortsname',
        convert: 'In Aufenthalt umwandeln',
        strategyOptions: {
            latestPoint: 'Letzter bekannter Punkt (Standard)',
            selectedLocation: 'Gewählter Ort'
        },
        selectedSourceOptions: {
            place: 'Favorit oder Geocoding-Ort',
            custom: 'Eigene Koordinaten'
        },
        errors: {
            previewFailed: 'Vorschau des letzten Punkts konnte nicht ermittelt werden',
            searchFailed: 'Ortssuche fehlgeschlagen'
        },
        toasts: {
            convertedSummary: 'Umgewandelt',
            convertedDetail: 'Die Datenlücke wurde in einen Aufenthalt umgewandelt.',
            failedSummary: 'Umwandeln fehlgeschlagen',
            failedFallback: 'Datenlücke konnte nicht umgewandelt werden'
        }
    },
    gpsPointEdit: {
        header: 'GPS-Punkt bearbeiten',
        mapInstructions: 'Klicke auf die Karte, um einen neuen Ort für diesen GPS-Punkt zu wählen.',
        locationLabel: 'Ort',
        latitudeLabel: 'Breitengrad',
        longitudeLabel: 'Längengrad',
        speedLabel: 'Geschwindigkeit (km/h)',
        speedPlaceholder: 'Geschwindigkeit in km/h',
        accuracyLabel: 'Genauigkeit (Meter)',
        accuracyPlaceholder: 'Genauigkeit in Metern',
        stageChange: 'Änderung vormerken',
        revertToOriginal: 'Auf Original zurücksetzen',
        stagingHint: 'Änderungen werden nicht sofort gespeichert: Wende sie gemeinsam mit „Änderungen anwenden“ auf der Seite GPS-Daten an.',
        validation: {
            latitudeRequired: 'Breitengrad ist erforderlich',
            latitudeRange: 'Der Breitengrad muss zwischen -90 und 90 liegen',
            longitudeRequired: 'Längengrad ist erforderlich',
            longitudeRange: 'Der Längengrad muss zwischen -180 und 180 liegen'
        },
        mapPopups: {
            currentLocation: 'Aktueller Ort – zum Verschieben auf die Karte klicken',
            originalLocation: 'Ursprünglicher Ort'
        }
    }
}
