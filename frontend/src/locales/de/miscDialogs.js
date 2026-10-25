/**
 * Miscellaneous dialogs (German): timeline regeneration progress, stay details, location lookup,
 * timeline label forms, bulk edit, and bulk save confirmation.
 */
export default {
    timelineRegeneration: {
        header: 'Neuberechnung der Zeitleiste',
        completedMessage: 'Zeitleiste erfolgreich erstellt!',
        gpsPointsLoaded: '{loaded} / {total} GPS-Punkte geladen',
        pointsProcessed: '{processed} / {total} Punkte verarbeitet',
        locationsGeocoded: '{resolved} / {total} Orte geocodiert',
        fromFavorites: '{count} aus Favoriten',
        fromCache: '{count} aus dem Cache',
        fromExternalApi: '{count} über externe API',
        pending: '{count} ausstehend',
        viewDetailedProgress: 'Detaillierten Fortschritt ansehen',
        fallbackNote: 'Das kann je nach Datenmenge 5–15 Sekunden oder länger dauern. Während der Neuberechnung ist deine Zeitleiste vorübergehend nicht verfügbar.',
        pleaseWait: 'Bitte warten …',
        titles: {
            favorite: 'Favorit wird hinzugefügt & Zeitleiste neu berechnet',
            favoriteDelete: 'Favorit wird gelöscht & Zeitleiste neu berechnet',
            preferences: 'Einstellungen werden angewendet & Zeitleiste neu berechnet',
            classification: 'Einstufung der Fahrten wird aktualisiert',
            reconstruction: 'Fehlende Zeitleistendaten werden angewendet',
            general: 'Zeitleiste wird neu berechnet'
        },
        messages: {
            favorite: 'Wir fügen deinen Favoriten hinzu und berechnen deine gesamte Zeitleiste neu, damit die Änderung überall berücksichtigt wird.',
            favoriteDelete: 'Wir entfernen deinen Favoriten und berechnen deine gesamte Zeitleiste neu, damit die Änderung überall berücksichtigt wird.',
            preferences: 'Wir wenden deine neuen Einstellungen an und berechnen deine gesamte Zeitleiste danach neu.',
            classification: 'Wir berechnen die Fortbewegungsarten deiner vorhandenen Fahrten anhand der neuen Geschwindigkeitsschwellen neu. Nur die Einstufung ändert sich, die Struktur der Zeitleiste bleibt gleich.',
            reconstruction: 'Wir übernehmen die erzeugten GPS-Punkte aus deinen Aufenthalten und Fahrten und aktualisieren den betroffenen Teil der Zeitleiste. Vorhandene Zeitleistendaten werden nicht ersetzt.',
            general: 'Wir berechnen deine gesamte Zeitleiste aus deinen GPS-Daten neu, damit sie alle vorhandenen Standortinformationen genau widerspiegelt.'
        }
    },
    stayDetails: {
        titleWithLocation: 'Details zum Aufenthalt – {location}',
        unknownLocation: 'Unbekannter Ort',
        mapSectionTitle: 'Karte',
        timingTitle: 'Zeit',
        start: 'Beginn:',
        end: 'Ende:',
        duration: 'Dauer:',
        coordinatesTitle: 'Koordinaten',
        latitude: 'Breitengrad:',
        longitude: 'Längengrad:',
        coordinates: 'Koordinaten:',
        notAvailable: 'k. A.',
        close: 'Schließen',
        toasts: {
            copiedSummary: 'Kopiert!',
            copiedDetail: 'Koordinaten in die Zwischenablage kopiert',
            copyFailedSummary: 'Kopieren fehlgeschlagen',
            copyFailedDetail: 'Koordinaten konnten nicht kopiert werden'
        }
    },
    locationLookup: {
        header: 'Besuche in der Nähe dieses Punkts',
        withinRadius: 'im Umkreis von {radius} m',
        checkingStays: 'Deine Aufenthalte werden geprüft …',
        tryAgain: 'Erneut versuchen',
        hasVisits: 'Du hast an diesem Ort erfasste Besuche.',
        recordedStayFallback: 'Erfasster Aufenthalt',
        placeDetails: 'Ortsdetails',
        visitCount: '{count} Besuch | {count} Besuche',
        firstVisit: 'Erster: {date}',
        lastVisit: 'Letzter: {date}',
        openVisitDayTooltip: 'Tag des Besuchs in der Zeitleiste öffnen',
        savedPlacesHeading: 'Gespeicherte Orte an diesem Punkt',
        unnamedFavorite: 'Favorit ohne Namen',
        noVisitFound: 'Im Umkreis von {radius} m um diesen Punkt wurde kein Besuch gefunden.',
        closestStaysHeading: 'Nächste erfasste Aufenthalte',
        unknownLocation: 'Unbekannter Ort',
        noRecordedStays: 'Keine erfassten Aufenthalte gefunden.',
        close: 'Schließen'
    },
    timelineLabelForm: {
        nameLabel: 'Name des Labels *',
        namePlaceholder: 'z. B. Spanienurlaub, Dienstreise nach Berlin',
        dateRangeLabel: 'Zeitraum *',
        dateRangePlaceholder: 'Start- und Enddatum wählen',
        colorLabel: 'Farbe',
        colorPreview: 'Vorschau',
        randomButton: 'Zufällig',
        showAsPresetLabel: 'Als Datumsvorlage anzeigen',
        showAsPresetHint: 'Wenn aktiviert, erscheint dieses Label in den Vorlagen der Datumsauswahl.',
        nameRequired: 'Name ist erforderlich',
        dateRangeRequired: 'Zeitraum ist erforderlich',
        cancel: 'Abbrechen'
    },
    createTimelineLabel: {
        header: 'Zeitleisten-Label erstellen',
        createButton: 'Erstellen',
        overlap: {
            message: 'Dieses Label überschneidet sich mit: {names}. Trotzdem erstellen?',
            header: 'Überschneidende Labels erkannt',
            acceptLabel: 'Trotzdem erstellen',
            rejectLabel: 'Abbrechen'
        },
        toasts: {
            checkOverlapsFailedFallback: 'Überschneidungen konnten nicht geprüft werden',
            createdSummary: 'Erstellt',
            createdDetail: 'Zeitleisten-Label erstellt',
            createFailedFallback: 'Zeitleisten-Label konnte nicht erstellt werden'
        }
    },
    editTimelineLabel: {
        header: 'Zeitleisten-Label bearbeiten',
        readOnlyHeader: 'Zeitleisten-Label ansehen (schreibgeschützt)',
        activeOwnTracksTitle: 'Aktiver OwnTracks-Tag',
        activeOwnTracksMessage: 'Dieser Tag wird gerade von OwnTracks verwaltet und lässt sich nicht bearbeiten, solange er aktiv ist. Du kannst ihn bearbeiten, sobald er abgeschlossen ist (wenn du in OwnTracks den Tag wechselst).',
        updateButton: 'Aktualisieren',
        toasts: {
            updatedSummary: 'Aktualisiert',
            updatedDetail: 'Zeitleisten-Label aktualisiert',
            updateFailedFallback: 'Zeitleisten-Label konnte nicht aktualisiert werden'
        }
    },
    bulkEdit: {
        header: '{count} {itemType} gemeinsam bearbeiten',
        editingPrefix: 'Du bearbeitest',
        fieldsHeading: 'Zu ändernde Felder wählen',
        updateCity: 'Stadt ändern',
        updateCountry: 'Land ändern',
        cityPlaceholder: 'Stadt eingeben',
        countryPlaceholder: 'Land eingeben',
        cityRequired: 'Stadt ist erforderlich',
        countryRequired: 'Land ist erforderlich',
        selectAtLeastOneField: 'Wähle mindestens ein Feld zum Ändern',
        typoWarning: {
            header: 'Möglicher Tippfehler',
            notFoundMessage: 'Diese Werte kommen in deinen Daten bisher nicht vor:',
            didYouMean: 'Meintest du:',
            continuePrompt: 'Mit diesen Werten fortfahren?',
            continueAnyway: 'Trotzdem fortfahren'
        },
        fields: {
            city: 'Stadt',
            country: 'Land'
        },
        updating: 'Wird aktualisiert …',
        updateButton: '{count} Einträge aktualisieren',
        toasts: {
            completeSummary: 'Gemeinsame Änderung abgeschlossen',
            partialDetail: '{success} von {total} Einträgen aktualisiert ({failed} fehlgeschlagen)',
            successDetail: '{count} {itemType} aktualisiert',
            failedSummary: 'Aktualisieren fehlgeschlagen',
            failedFallback: '{itemType} konnten nicht aktualisiert werden'
        }
    },
    bulkSaveConfirm: {
        header: 'Gemeinsames Speichern bestätigen',
        summaryTitle: 'Du speicherst gleich {count} Favoriten: | Du speicherst gleich {count} Favoriten:',
        pointsItem: '{count} Punkt | {count} Punkte',
        areasItem: '{count} Bereich | {count} Bereiche',
        infoMessage: 'Diese Favoriten werden gespeichert, danach wird die Zeitleiste vollständig neu berechnet.',
        confirm: 'Bestätigen'
    }
}
