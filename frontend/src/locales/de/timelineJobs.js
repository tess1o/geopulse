/**
 * Timeline generation jobs (German): live progress messages, the jobs list, and the job details page.
 */
export default {
    // Live progress messages sent by the backend as a MessageDescriptor; unmapped keys fall back to the
    // backend's English `fallback` text.
    progressMessages: {
        initializing: 'Erstellung der Zeitleiste wird vorbereitet',
        acquiringLock: 'Zeitleiste wird gesperrt',
        cleaningUp: 'Alte Zeitleistendaten werden entfernt',
        preparingGpsProcessing: 'Verarbeitung der GPS-Daten wird vorbereitet',
        noGpsData: 'Keine GPS-Daten zu verarbeiten',
        readyToProcess: 'Bereit, {totalGpsPoints} GPS-Punkte zu verarbeiten',
        processingStateMachine: 'GPS-Punkte werden verarbeitet',
        postProcessingTrips: 'Fahrten werden nachbearbeitet und Erkennungen geprüft',
        mergingTimeline: 'Zeitleiste wird zusammengeführt',
        persistingTimeline: 'Ereignisse der Zeitleiste werden gespeichert',
        detectingDataGaps: 'Datenlücken werden erkannt',
        finalizing: 'Erstellung der Zeitleiste wird abgeschlossen',
        autoMatchingVisits: 'Geplante Besuche werden automatisch zugeordnet',
        recalculatingBadges: 'Abzeichen werden neu berechnet',
        completed: 'Zeitleiste erstellt',
        preparingBoatSetup: 'Boot-Einrichtung wird vorbereitet',
        boatEvidenceReady: 'Wasserdaten für die Booterkennung bereit',
        regenerationQueued: 'Erzwungene Neuberechnung der Zeitleiste eingeplant',
        startingLocationResolution: 'Ortsermittlung wird gestartet',
        allResolvedFromFavorites: 'Alle Orte aus Favoriten ermittelt',
        resolvedFromFavorites: '{favoritesResolved} Orte aus Favoriten ermittelt',
        resolvedFromCache: '{cachedResolved} aus dem Cache ermittelt ({batchResolved} per Sammelabfrage, {individualResolved} einzeln)',
        geocodingLocationProgress: 'Geocoding von Ort {current}/{total}',
        geocodingComplete: 'Geocoding abgeschlossen: {favoritesResolved} Favoriten, {cachedResolved} aus dem Cache, {externalCompleted} externe API-Aufrufe'
    },
    listPage: {
        title: 'Zeitleisten-Jobs',
        description: 'Sieh dir laufende und kürzlich beendete Erstellungen der Zeitleiste an.',
        loading: 'Laufende Jobs werden gesucht …',
        noJob: {
            title: 'Gerade läuft kein Zeitleisten-Job',
            message: 'Wenn du über eine Benachrichtigung hier bist, ist die geplante Aktualisierung vielleicht schon fertig. Kürzlich beendete oder fehlgeschlagene Jobs stehen unten, sofern vorhanden.',
            hint: 'Zeitleisten-Jobs starten durch Änderungen an den Zeitleisten-Einstellungen, geänderte Favoriten, eine manuelle Neuberechnung oder geplante Wartung von GeoPulse.',
            viewTimelineButton: 'Zeitleiste ansehen'
        },
        error: {
            title: 'Laufende Jobs konnten nicht geprüft werden'
        },
        history: {
            title: 'Verlauf',
            description: 'Letzte Zeitleisten-Jobs',
            loading: 'Verlauf wird geladen …',
            empty: 'Keine aktuellen Zeitleisten-Jobs gefunden.',
            duration: 'Dauer: {duration}',
            gpsPoints: '{count} GPS-Punkte',
            viewDetails: 'Details ansehen'
        },
        notAvailable: 'k. A.',
        durationSeconds: '{seconds} s',
        durationMinutesSeconds: '{minutes} Min. {seconds} s'
    },
    detailsPage: {
        title: 'Fortschritt der Zeitleiste',
        description: 'Fortschritt der Neuberechnung in Echtzeit.',
        backButton: 'Zurück zur Zeitleiste',
        loading: 'Job-Details werden geladen …',
        error: {
            title: 'Job-Details konnten nicht geladen werden'
        },
        jobIdLabel: 'Job-ID: {jobId}',
        status: {
            loading: 'Wird geladen …',
            queued: 'In der Warteschlange',
            running: 'Zeitleiste wird berechnet',
            completed: 'Erfolgreich abgeschlossen',
            failed: 'Fehlgeschlagen'
        },
        details: {
            started: 'Gestartet:',
            completed: 'Abgeschlossen:',
            duration: 'Dauer:',
            currentStep: 'Aktueller Schritt:',
            stepOf: '{current} von {total}'
        },
        stepsTitle: 'Verarbeitungsschritte',
        steps: {
            acquiringLock: {
                title: 'Sperren',
                description: 'Exklusiven Zugriff auf die Zeitleistendaten sicherstellen'
            },
            cleaningUp: {
                title: 'Aufräumen',
                description: 'Alte Ereignisse der Zeitleiste entfernen'
            },
            preparingGpsProcessing: {
                title: 'GPS-Verarbeitung vorbereiten',
                description: 'GPS-Punkte zählen und Verarbeitung vorbereiten'
            },
            processingGeocoding: {
                title: 'Verarbeitung & Geocoding',
                description: 'GPS-Punkte verarbeiten und anschließend Ortsnamen ermitteln'
            },
            postProcessingTrips: {
                title: 'Fahrten nachbearbeiten',
                description: 'Erkennung von Fahrten prüfen und verfeinern'
            },
            mergingSimplifying: {
                title: 'Zusammenführen & Vereinfachen',
                description: 'Optimierungen der Zeitleiste anwenden'
            },
            persistingTimeline: {
                title: 'Zeitleiste speichern',
                description: 'Ereignisse der Zeitleiste in der Datenbank speichern'
            },
            dataGapDetection: {
                title: 'Datenlücken erkennen',
                description: 'Lücken in der GPS-Abdeckung finden'
            },
            finalizing: {
                title: 'Abschließen',
                description: 'Meilensteine berechnen und Erstellung abschließen'
            }
        },
        detail: {
            gpsPoints: 'GPS-Punkte: {loaded} / {total}',
            processingHeader: 'GPS-Punkte verarbeitet: {processed} / {total}',
            remaining: 'Verbleibend: {count} Punkte',
            geocodingHeader: 'Reverse Geocoding: {resolved} / {total} Orte',
            favoritesInstant: 'Favoriten (sofort): {count}',
            cachedInDatabase: 'Aus der Datenbank: {count}',
            externalApiCalls: 'Externe API-Aufrufe: {count}',
            pending: 'Ausstehend: {count}'
        },
        completion: {
            title: 'Zeitleiste erstellt!',
            message: 'Deine Zeitleiste wurde mit allen aktuellen GPS-Daten neu berechnet.'
        },
        durationZero: '0 s',
        durationSeconds: '{seconds} s',
        durationMinutesSeconds: '{minutes} Min. {seconds} s',
        durationHoursMinutesSeconds: '{hours} Std. {minutes} Min. {seconds} s'
    },
    // Toasts and dialogs shown while a settings change starts a timeline regeneration (useTimelineRegeneration)
    regeneration: {
        alreadyRunning: {
            header: 'Zeitleisten-Job läuft',
            message: 'Es läuft bereits ein Zeitleisten-Job ({progress} % fertig). Bitte warte, bis er abgeschlossen ist.',
            viewProgress: 'Fortschritt ansehen'
        },
        savedSummary: 'Gespeichert',
        queuedDetail: 'Änderungen gespeichert. Deine Zeitleiste wird im Hintergrund aktualisiert.'
    }
}
