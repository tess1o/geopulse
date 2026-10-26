/**
 * GPS Data page (German).
 */
export default {
    page: {
        title: 'GPS-Daten',
        subtitle: 'Technische Informationen zu deinen GPS-Aufzeichnungen',
        actions: {
            selected: '{count} ausgewählt',
            deleteSelected: 'Auswahl löschen',
            deleteAllData: 'Alle Daten löschen',
            exportCsv: 'CSV exportieren',
            exportCsvSelected: 'CSV exportieren ({count} ausgewählt)',
            exportCsvZero: 'CSV exportieren (0 Pkt.)',
            exportCsvFiltered: 'CSV exportieren ({count} Pkt.)'
        },
        stats: {
            totalPoints: 'GPS-Punkte gesamt',
            pointsToday: 'Punkte heute',
            firstPoint: 'Erster GPS-Punkt',
            lastPoint: 'Neuester GPS-Punkt'
        },
        filters: {
            title: 'Filter',
            activeCount: '{count} aktiv',
            clearAll: 'Alle zurücksetzen',
            hideAdvanced: 'Erweitert ausblenden',
            showAdvanced: 'Erweitert anzeigen',
            today: 'Heute',
            yesterday: 'Gestern',
            last7Days: 'Letzte 7 Tage',
            last30Days: 'Letzte 30 Tage',
            from: 'Von',
            to: 'Bis',
            startPlaceholder: 'Startdatum und -uhrzeit',
            endPlaceholder: 'Enddatum und -uhrzeit',
            apply: 'Anwenden',
            clear: 'Zurücksetzen',
            fromBeforeTo: '„Von“ muss vor „Bis“ liegen.',
            sourceTypesLabel: 'Quellen:',
            allSources: 'Alle Quellen',
            accuracyLabel: 'Genauigkeit (Meter):',
            speedLabel: 'Geschwindigkeit (km/h):',
            min: 'Min.',
            max: 'Max.',
            rangeTo: 'bis',
            dateChip: 'Datum: {range}',
            accuracyChip: 'Genauigkeit: {min}–{max} m',
            speedChip: 'Geschwindigkeit: {min}–{max} km/h',
            sourcesChip: 'Quellen: {count} ausgewählt',
            sourceOptions: {
                manualReconstruction: 'Manuelle Rekonstruktion'
            },
            noPointsForPeriod: 'Für den gewählten Zeitraum gibt es keine Punkte',
            showingOfTotal: '{filtered} von {total} Punkten',
            showingCount: '{count} Punkte'
        },
        telemetry: {
            title: 'Telemetrie-Zuordnung (optional)',
            hint: 'Gilt global je Quelle für GPS-Daten und Popups in der Zeitleiste.',
            hideMapping: 'Zuordnung ausblenden',
            showMapping: 'Zuordnung anzeigen',
            sourceTypeLabel: 'Quelle',
            resetToDefaults: 'Auf Standard zurücksetzen',
            addRow: 'Zeile hinzufügen',
            saveMapping: 'Zuordnung speichern',
            loading: 'Telemetrie-Zuordnung wird geladen …',
            empty: 'Keine Telemetrie-Zuordnungen eingerichtet.',
            keyPlaceholder: 'ext-Schlüssel',
            labelPlaceholder: 'Bezeichnung',
            unitPlaceholder: 'Einheit',
            trueValuesPlaceholder: 'Werte für wahr (z. B. 1,true,on)',
            falseValuesPlaceholder: 'Werte für falsch (z. B. 0,false,off)',
            booleanDefaultLabel: 'Boolean {index}',
            columns: {
                key: 'Schlüssel',
                label: 'Bezeichnung',
                type: 'Typ',
                unit: 'Einheit',
                gpsData: 'GPS-Daten',
                currentPopup: 'Aktuelles Popup',
                order: 'Reihenfolge'
            },
            typeOptions: {
                boolean: 'Boolean',
                number: 'Zahl',
                string: 'Text'
            }
        },
        table: {
            title: 'GPS-Punkte',
            showing: '{start}–{end} von {total} Punkten',
            filteredFrom: 'gefiltert aus {total}',
            noPointsFound: 'Keine Punkte gefunden',
            rowsPerPage: 'Zeilen pro Seite:',
            rowsShort: 'Zeilen:',
            pageOf: 'Seite {current} von {total}',
            totalCount: '{count} gesamt',
            emptyTitle: 'Keine GPS-Punkte gefunden',
            emptyDescription: 'Für die gewählten Kriterien gibt es keine GPS-Daten.',
            editTooltip: 'GPS-Punkt bearbeiten',
            deleteTooltip: 'GPS-Punkt löschen',
            columns: {
                date: 'Datum',
                delta: 'Abstand',
                location: 'Ort',
                speed: 'Geschwindigkeit',
                accuracy: 'Genauigkeit',
                altitude: 'Höhe',
                battery: 'Akku',
                telemetry: 'Telemetrie',
                source: 'Quelle',
                actions: 'Aktionen'
            }
        },
        mobile: {
            selectPage: 'Seite auswählen',
            clearPage: 'Auswahl der Seite aufheben',
            selectAllAriaLabel: 'Alle GPS-Punkte auf dieser Seite auswählen',
            sort: 'Sortieren',
            ascending: 'Aufsteigend',
            descending: 'Absteigend',
            sortAscendingAriaLabel: 'Aufsteigend sortieren',
            sortDescendingAriaLabel: 'Absteigend sortieren',
            loading: 'GPS-Punkte werden geladen …',
            actionsTooltip: 'Aktionen für GPS-Punkt',
            selectPointAriaLabel: 'GPS-Punkt vom {date} {time} auswählen',
            actionsAriaLabel: 'Aktionen für GPS-Punkt vom {date} {time}'
        },
        pendingEdits: {
            message: '{count} Änderung an einem GPS-Punkt ist noch nicht angewendet. Wende sie an, um den Punkt zu aktualisieren und die Zeitleiste neu zu berechnen. | {count} Änderungen an GPS-Punkten sind noch nicht angewendet. Wende sie gemeinsam an, um die Punkte zu aktualisieren und die Zeitleiste einmal neu zu berechnen.',
            revertAll: 'Alle verwerfen',
            apply: 'Änderungen anwenden',
            revertTooltip: 'Änderung verwerfen',
            leaveConfirm: {
                header: 'Nicht angewendete Änderungen',
                message: 'Du hast {count} nicht angewendete Änderung an einem GPS-Punkt. Wenn du die Seite verlässt, geht sie verloren. | Du hast {count} nicht angewendete Änderungen an GPS-Punkten. Wenn du die Seite verlässt, gehen sie verloren.',
                accept: 'Verlassen ohne Anwenden',
                reject: 'Bleiben'
            }
        },
        dialogs: {
            delete: {
                header: 'GPS-Punkt löschen',
                message: 'Möchtest du diesen GPS-Punkt wirklich löschen? Das lässt sich nicht rückgängig machen.',
                confirm: 'Löschen'
            },
            bulkDelete: {
                header: 'Mehrere GPS-Punkte löschen',
                message: 'Möchtest du {count} GPS-Punkt wirklich löschen? Das lässt sich nicht rückgängig machen. | Möchtest du {count} GPS-Punkte wirklich löschen? Das lässt sich nicht rückgängig machen.',
                confirm: 'Alle löschen'
            },
            deleteAll: {
                header: 'Alle GPS-Daten löschen',
                warningTitle: 'Dauerhaft gelöscht werden:',
                allPoints: 'Alle {count} GPS-Punkte',
                timelineData: 'Alle Aufenthalte, Fahrten und Datenlücken der Zeitleiste',
                note: 'Deine Einstellungen und Favoriten sind davon {not} betroffen. Das lässt sich nicht rückgängig machen.',
                notWord: 'nicht',
                confirm: 'Alles löschen'
            }
        },
        toasts: {
            telemetryLoadFailedSummary: 'Telemetrie nicht geladen',
            telemetryLoadFailedFallback: 'Telemetrie-Zuordnung konnte nicht geladen werden',
            telemetrySavedSummary: 'Telemetrie gespeichert',
            telemetrySavedDetail: 'Telemetrie-Zuordnung aktualisiert',
            saveFailedSummary: 'Speichern fehlgeschlagen',
            saveTelemetryFailedFallback: 'Telemetrie-Zuordnung konnte nicht gespeichert werden',
            telemetryResetSummary: 'Telemetrie zurückgesetzt',
            telemetryResetDetail: 'Telemetrie-Zuordnung auf Standard zurückgesetzt',
            resetFailedSummary: 'Zurücksetzen fehlgeschlagen',
            resetTelemetryFailedFallback: 'Telemetrie-Zuordnung konnte nicht zurückgesetzt werden',
            summaryLoadFailedDetail: 'Übersicht der GPS-Daten konnte nicht geladen werden',
            pointsLoadFailedDetail: 'GPS-Punkte konnten nicht geladen werden',
            exportStartedSummary: 'Export gestartet',
            exportSelectedDetail: '{count} ausgewählte GPS-Punkte werden exportiert. Der Download startet gleich.',
            exportAllDetail: '{count} GPS-Punkte werden exportiert. Der Download startet gleich.',
            exportFailedSummary: 'Export fehlgeschlagen',
            exportFailedDetail: 'GPS-Daten konnten nicht exportiert werden',
            pointDeletedSummary: 'GPS-Punkt gelöscht',
            pointDeletedDetail: 'Der GPS-Punkt wurde gelöscht',
            timelineRegenStarted: 'Neuberechnung der Zeitleiste gestartet.',
            timelineRegenScheduled: 'Die Zeitleiste wird neu berechnet, sobald der laufende Zeitleisten-Job fertig ist.',
            coverageRebuildScheduled: 'Neuberechnung der Abdeckung geplant.',
            deleteFailedSummary: 'Löschen fehlgeschlagen',
            deleteFailedFallback: 'GPS-Punkt konnte nicht gelöscht werden',
            pointsUpdatedSummary: 'GPS-Punkte aktualisiert',
            pointsUpdatedDetail: '{count} GPS-Punkt aktualisiert. | {count} GPS-Punkte aktualisiert.',
            updateFailedSummary: 'Aktualisieren fehlgeschlagen',
            updateFailedFallback: 'GPS-Punkt konnte nicht aktualisiert werden',
            pointsDeletedSummary: 'GPS-Punkte gelöscht',
            bulkDeleteSuccessDetail: '{count} GPS-Punkt gelöscht | {count} GPS-Punkte gelöscht',
            bulkDeleteFailedSummary: 'Löschen mehrerer Punkte fehlgeschlagen',
            bulkDeleteFailedFallback: 'Ausgewählte GPS-Punkte konnten nicht gelöscht werden',
            allDataDeletedSummary: 'Alle Daten gelöscht',
            allDataDeletedDetail: 'Alle GPS-Punkte und Zeitleistendaten wurden gelöscht',
            deleteAllFailedFallback: 'GPS-Daten konnten nicht gelöscht werden'
        }
    }
}
