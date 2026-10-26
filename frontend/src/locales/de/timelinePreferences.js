/**
 * Timeline preferences page (German).
 */
export default {
    page: {
        title: 'Zeitleisten-Einstellungen',
        description: 'Lege genau fest, wie deine Zeitleiste aus GPS-Daten erstellt wird',
        searchPlaceholder: 'Zeitleisten-Einstellungen durchsuchen …',
        regenerateButton: 'Zeitleiste neu berechnen',
        saveButton: 'Änderungen speichern',
        moreButton: 'Mehr',
        mobileSectionLabel: 'Einstellungsbereich',
        navAriaLabel: 'Bereiche der Zeitleisten-Einstellungen',
        groups: {
            timeline: 'Zeitleiste',
            dataQuality: 'Datenqualität'
        },
        tabs: {
            staypoints: 'Erkennung von Aufenthalten',
            trips: 'Einstufung von Fahrten',
            gpsgaps: 'Erkennung von GPS-Lücken',
            merging: 'Zusammenführen von Aufenthalten'
        },
        infoBanner: {
            title: 'So wird die Zeitleiste berechnet',
            description: 'Deine GPS-Daten werden ausgewertet, um sinnvolle Aufenthalte und Fahrten zu erkennen. Diese Einstellungen steuern, wie empfindlich die Erkennung ist, und gelten nur für dein Konto. Manche Änderungen (etwa Geschwindigkeitsschwellen) aktualisieren die Einstufung von Fahrten schnell, andere erfordern je nach Menge deiner GPS-Daten eine vollständige Neuberechnung.',
            learnMore: 'Mehr dazu in der Dokumentation'
        },
        demoReadOnlyMessage: 'Demo-Modus: Alle Zeitleisten-Einstellungen sind schreibgeschützt. Speichern, Konfiguration importieren, Zurücksetzen und Neuberechnen sind deaktiviert.',
        unsavedWarning: {
            message: 'Du hast ungespeicherte Änderungen',
            discard: 'Verwerfen',
            saveNow: 'Jetzt speichern'
        },
        menu: {
            exportConfig: 'Konfiguration exportieren',
            importConfig: 'Konfiguration importieren',
            resetToDefaults: 'Auf Standard zurücksetzen',
            viewActiveJob: 'Laufenden Job ansehen'
        },
        importDialog: {
            header: 'Zeitleisten-Konfiguration importieren',
            description: 'Prüfe alle erkannten Änderungen, bevor du die Konfiguration übernimmst.',
            detectedChanges: 'Erkannte Änderungen:',
            settingColumn: 'Einstellung',
            currentColumn: 'Aktuell',
            importedColumn: 'Importiert',
            cancel: 'Abbrechen',
            apply: 'Import übernehmen',
            impactClassification: 'Dieser Import ändert nur Parameter der Einstufung und berechnet die Fortbewegungsarten der Fahrten neu.',
            impactStructural: 'Dieser Import enthält strukturelle Einstellungen und löst nach dem Speichern eine vollständige Neuberechnung der Zeitleiste aus.'
        },
        boatSetupDialog: {
            header: 'Boot-Einrichtung',
            introReady: 'Die Booterkennung braucht einmalig eine Vorberechnung der Wasserdaten deiner GPS-Punkte.',
            introDefault: 'Die Booterkennung braucht einen globalen Gewässerdatensatz von etwa 800–900 MB und einmalig eine Vorberechnung deiner GPS-Punkte.',
            downloaded: '{downloaded} heruntergeladen',
            gpsPointsProcessed: 'Verarbeitete GPS-Punkte:',
            defaultErrorCode: 'Boot-Einrichtung fehlgeschlagen',
            useOfflineDataset: 'Offline-Datensatz verwenden',
            close: 'Schließen'
        },
        formatValue: {
            notSet: 'Nicht festgelegt',
            enabled: 'Aktiviert',
            disabled: 'Deaktiviert',
            singleTrip: 'Eine Fahrt',
            multipleTrips: 'Mehrere Fahrten'
        },
        confirm: {
            cancel: 'Abbrechen',
            boatEnablement: {
                header: 'Booterkennung aktivieren',
                readyStructuralMessage: 'Die Boot-Einrichtung ist bereits fertig. Beim Speichern wird deine Zeitleiste mit aktivierter Booterkennung neu berechnet.',
                readyClassificationMessage: 'Die Boot-Einrichtung ist bereits fertig. Beim Speichern werden die vorhandenen Fahrten mit aktivierter Booterkennung neu eingestuft.',
                datasetReadyMessage: 'Die Booterkennung braucht einmalig eine Vorberechnung der Wasserdaten deiner GPS-Punkte. Der globale Gewässerdatensatz ist schon installiert, ein großer Download ist also nicht nötig. Die Erstellung der Zeitleiste wartet, bis die Einrichtung fertig ist.',
                datasetMissingMessage: 'Die Booterkennung braucht einen globalen Gewässerdatensatz von etwa 800–900 MB und einmalig eine Vorberechnung deiner GPS-Punkte. Die Erstellung der Zeitleiste wartet, bis die Einrichtung fertig ist.',
                enableAndRegenerate: 'Aktivieren & neu berechnen',
                enableBoat: 'Boot aktivieren',
                enableAndStartSetup: 'Aktivieren & Einrichtung starten'
            },
            classificationOnly: {
                message: 'Diese Änderungen berechnen die Fortbewegungsarten deiner vorhandenen Fahrten neu. Möchtest du fortfahren?',
                header: 'Einstufung der Fahrten aktualisieren',
                accept: 'Einstufung aktualisieren'
            },
            fullRegeneration: {
                message: 'Wenn du diese Einstellungen änderst, werden alle deine Zeitleistendaten nach den neuen Einstellungen vollständig neu berechnet. Das kann je nach Menge deiner GPS-Daten etwas dauern. Möchtest du fortfahren?',
                header: 'Zeitleisten-Einstellungen speichern',
                accept: 'Speichern & neu berechnen'
            },
            resetDefaults: {
                message: 'Alle Einstellungen werden auf ihre Standardwerte zurückgesetzt. Bist du sicher?',
                header: 'Auf Standard zurücksetzen',
                accept: 'Zurücksetzen'
            },
            regenerateTimeline: {
                message: 'Deine aktuellen Zeitleistendaten werden vollständig gelöscht und von Grund auf neu berechnet.\n\nDas kann je nach Menge deiner GPS-Daten einige Minuten dauern.\n\nMöchtest du fortfahren?',
                header: 'Gesamte Zeitleiste neu berechnen',
                accept: 'Zeitleiste neu berechnen'
            },
            boatSetup: {
                readyMessage: 'Die Boot-Einrichtung ist bereits fertig. Zwischengespeicherte Wasserdaten stehen für die Zeitleiste bereit.',
                datasetReadyMessage: 'Die Boot-Einrichtung berechnet die Wasserdaten deiner GPS-Punkte vor. Die Erstellung der Zeitleiste wartet, bis die Einrichtung fertig ist.',
                datasetMissingMessage: 'Die Boot-Einrichtung lädt oder liest den Gewässerdatensatz und berechnet die Wasserdaten deiner GPS-Punkte vor. Die Erstellung der Zeitleiste wartet, bis die Einrichtung fertig ist.',
                startHeader: 'Boot-Einrichtung starten',
                retryHeader: 'Boot-Einrichtung erneut versuchen',
                startAccept: 'Einrichtung starten',
                retryAccept: 'Erneut versuchen'
            },
            unsavedChanges: {
                message: 'Du hast ungespeicherte Änderungen an den Zeitleisten-Einstellungen. Wenn du die Seite verlässt, gehen sie verloren.',
                header: 'Ungespeicherte Änderungen',
                accept: 'Verlassen ohne Speichern',
                reject: 'Bleiben'
            }
        },
        demoToast: 'Änderungen an den Zeitleisten-Einstellungen sind im Demo-Modus deaktiviert.',
        toasts: {
            nothingToExportSummary: 'Nichts zu exportieren',
            nothingToExportDetail: 'Derzeit sind keine Zeitleisten-Einstellungen vorhanden.',
            exportCompleteSummary: 'Export abgeschlossen',
            exportCompleteDetail: 'Zeitleisten-Konfiguration exportiert.',
            noChangesSummary: 'Keine Änderungen',
            noChangesDetail: 'Es wurden keine Einstellungen geändert',
            importNoChangesDetail: 'Die importierte Konfiguration entspricht deinen aktuellen Einstellungen.',
            importFailedSummary: 'Import fehlgeschlagen',
            importFailedFallback: 'Die importierte Konfigurationsdatei konnte nicht gelesen werden.',
            invalidFileFormat: 'Ungültiges Format der Importdatei.',
            unsupportedSchemaVersion: 'Nicht unterstützte Schemaversion. Erwartet wird {version}.',
            noPreferencesObject: 'Die Importdatei enthält kein gültiges Einstellungsobjekt.',
            noCompatibleFields: 'Die Importdatei enthält keine kompatiblen Zeitleisten-Einstellungen.',
            loadingFailedSummary: 'Laden fehlgeschlagen',
            loadingFailedDetail: 'Zeitleisten-Einstellungen konnten nicht geladen werden',
            boatSetupReadySummary: 'Boot-Einrichtung bereit',
            boatSetupReadyDetail: 'Die Booterkennung ist bereit. Die Zeitleiste kann jetzt zwischengespeicherte Wasserdaten nutzen.',
            boatSetupFailedSummary: 'Boot-Einrichtung fehlgeschlagen',
            boatSetupStatusFailedSummary: 'Status der Boot-Einrichtung nicht abrufbar',
            boatSetupStatusFailedFallback: 'Der Status der Boot-Einrichtung konnte nicht aktualisiert werden.',
            boatSetupStartedSummary: 'Boot-Einrichtung gestartet',
            boatSetupStartedDetail: 'Einrichtung des Gewässerdatensatzes und Vorberechnung der GPS-Punkte laufen.',
            boatSetupStartFailedFallback: 'Die Boot-Einrichtung konnte nicht gestartet werden.',
            boatDetectionEnabledSummary: 'Booterkennung aktiviert',
            boatDetectionEnabledDetail: 'Die Boot-Einrichtung ist bereit und die Einstellung wurde gespeichert.',
            boatSetupEnableFailedFallback: 'Die Boot-Einrichtung konnte nicht aktiviert werden.',
            classificationUpdatedSummary: 'Gespeichert',
            classificationUpdatedDetail: 'Einstufung der Fahrten aktualisiert.',
            saveFailedFallback: 'Einstellungen konnten nicht gespeichert werden.',
            regenerationSuccessDetail: 'Einstellungen gespeichert, Neuberechnung der Zeitleiste gestartet.',
            resetSuccessDetail: 'Einstellungen zurückgesetzt, Neuberechnung der Zeitleiste gestartet.',
            regenerationQueuedDetail: 'Einstellungen gespeichert. Deine Zeitleiste wird im Hintergrund neu berechnet.',
            resetFailedFallback: 'Einstellungen konnten nicht zurückgesetzt werden.',
            changesDiscardedSummary: 'Änderungen verworfen',
            changesDiscardedDetail: 'Alle ungespeicherten Änderungen wurden verworfen',
            regenerationStartedDetail: 'Neuberechnung der Zeitleiste gestartet.',
            regenerationFailedFallback: 'Neuberechnung der Zeitleiste konnte nicht gestartet werden.',
            settingNotVisibleSummary: 'Einstellung nicht sichtbar',
            settingNotVisibleFallback: 'Diese Einstellung ist gerade nicht sichtbar. Aktiviere die zugehörigen Optionen, um sie zu bearbeiten.'
        },
        settings: {
            staypointRadiusMeters: 'Radius für Aufenthalte',
            staypointMinDurationMinutes: 'Mindestdauer eines Aufenthalts',
            useVelocityAccuracy: 'Erweiterte Filterung',
            staypointVelocityThreshold: 'Geschwindigkeitsschwelle',
            staypointMaxAccuracyThreshold: 'GPS-Genauigkeitsschwelle',
            tripDetectionAlgorithm: 'Algorithmus zur Fahrterkennung',
            walkingMaxAvgSpeed: 'Zu Fuß: maximale Durchschnittsgeschwindigkeit',
            walkingMaxMaxSpeed: 'Zu Fuß: maximale Spitzengeschwindigkeit',
            carEnabled: 'Bezeichnung „Auto“ aktiviert',
            motorcycleEnabled: 'Bezeichnung „Motorrad“ aktiviert',
            publicTransportationEnabled: 'Bezeichnung „Öffentliche Verkehrsmittel“ aktiviert',
            preferredMotorizedType: 'Bevorzugte Bezeichnung für Kraftfahrzeuge',
            carMinAvgSpeed: 'Kraftfahrzeug: minimale Durchschnittsgeschwindigkeit',
            carMinMaxSpeed: 'Kraftfahrzeug: minimale Spitzengeschwindigkeit',
            shortDistanceKm: 'Schwelle für kurze Fahrten',
            bicycleEnabled: 'Fahrraderkennung aktiviert',
            bicycleMinAvgSpeed: 'Fahrrad: minimale Durchschnittsgeschwindigkeit',
            bicycleMaxAvgSpeed: 'Fahrrad: maximale Durchschnittsgeschwindigkeit',
            bicycleMaxMaxSpeed: 'Fahrrad: maximale Spitzengeschwindigkeit',
            runningEnabled: 'Lauferkennung aktiviert',
            runningMinAvgSpeed: 'Laufen: minimale Durchschnittsgeschwindigkeit',
            runningMaxAvgSpeed: 'Laufen: maximale Durchschnittsgeschwindigkeit',
            runningMaxMaxSpeed: 'Laufen: maximale Spitzengeschwindigkeit',
            trainEnabled: 'Zugerkennung aktiviert',
            trainMinAvgSpeed: 'Zug: minimale Durchschnittsgeschwindigkeit',
            trainMaxAvgSpeed: 'Zug: maximale Durchschnittsgeschwindigkeit',
            trainMinMaxSpeed: 'Zug: minimale Spitzengeschwindigkeit',
            trainMaxMaxSpeed: 'Zug: maximale Spitzengeschwindigkeit',
            trainMaxSpeedVariance: 'Zug: maximale Geschwindigkeitsschwankung',
            flightEnabled: 'Flugerkennung aktiviert',
            flightMinAvgSpeed: 'Flug: minimale Durchschnittsgeschwindigkeit',
            flightMinMaxSpeed: 'Flug: minimale Spitzengeschwindigkeit',
            flightMinDistanceKm: 'Flug: Mindeststrecke',
            flightAltitudeDetectionEnabled: 'Flugerkennung über die Höhe aktiviert',
            flightAltitudeMinHeightMeters: 'Flug: Mindesthöhe',
            flightAltitudeMinVerticalSpeed: 'Flug: minimale Vertikalgeschwindigkeit',
            boatEnabled: 'Booterkennung aktiviert',
            boatMinWaterRatio: 'Boot: minimaler Wasseranteil',
            boatMinWaterDistanceMeters: 'Boot: minimale Strecke auf dem Wasser',
            boatMinContinuousWaterDistanceMeters: 'Boot: minimale durchgehende Strecke auf dem Wasser',
            boatMaxPlausibleSpeed: 'Boot: maximale plausible Geschwindigkeit',
            tripArrivalDetectionMinDurationSeconds: 'Dauer zur Ankunftserkennung',
            tripSustainedStopMinDurationSeconds: 'Dauer eines echten Halts',
            tripArrivalMinPoints: 'Mindestzahl an Haltepunkten zur Ankunftserkennung',
            isMergeEnabled: 'Zusammenführen von Aufenthalten aktiviert',
            mergeMaxDistanceMeters: 'Maximaler Abstand zum Zusammenführen',
            mergeMaxTimeGapMinutes: 'Maximaler zeitlicher Abstand zum Zusammenführen',
            dataGapThresholdSeconds: 'Schwelle für Datenlücken',
            dataGapMinDurationSeconds: 'Mindestdauer einer Lücke',
            gapStayInferenceEnabled: 'Aufenthalt in Lücken ableiten',
            gapStayInferenceMaxGapHours: 'Aufenthalt in Lücken ableiten: maximale Lückendauer',
            gapTripInferenceEnabled: 'Fahrt in Lücken ableiten',
            gapTripInferenceMinDistanceMeters: 'Fahrt in Lücken ableiten: Mindeststrecke',
            gapTripInferenceMinGapHours: 'Fahrt in Lücken ableiten: minimale Lückendauer',
            gapTripInferenceMaxGapHours: 'Fahrt in Lücken ableiten: maximale Lückendauer'
        }
    }
}
