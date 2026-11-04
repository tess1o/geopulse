/**
 * Trip Classification dialog (German). Movement-type labels reuse `movementTypes.*`.
 */
export default {
    dialog: {
        header: 'Details zur Einstufung',
        loading: 'Details zur Einstufung werden geladen …',
        explanationLink: 'Warum beginnt und endet diese Strecke hier?',
        close: 'Schließen'
    },
    overview: {
        title: 'Überblick über die Strecke',
        startTime: 'Beginn',
        duration: 'Dauer',
        distance: 'Distanz',
        effectiveClassification: 'Gültige Einstufung',
        automaticClassification: 'Automatische Einstufung',
        classificationSource: 'Quelle der Einstufung',
        manualOverrideActive: 'Für diese Strecke gilt eine manuelle Einstufung.'
    },
    editSection: {
        title: 'Fortbewegungsart bearbeiten',
        unrecognizedWarning: 'Der Algorithmus hat die Fortbewegungsart nicht erkannt. Lege sie unten manuell fest.',
        overrideInfo: 'Wenn das falsch aussieht, lege die Art manuell fest.',
        readOnlyError: 'Die Fortbewegungsart lässt sich im Demo-Modus nicht ändern.',
        selectPlaceholder: 'Fortbewegungsart wählen',
        saveButton: 'Manuell festlegen',
        resetButton: 'Auf automatisch zurücksetzen'
    },
    stats: {
        title: 'GPS-Statistik',
        avgSpeed: 'Ø GPS-Geschwindigkeit',
        avgSpeedHint: 'Gewichtet nach der Dauer jedes Messwerts',
        maxSpeed: 'Max. GPS-Geschwindigkeit',
        maxSpeedHint: 'Höchste Geschwindigkeit, die mindestens 30 Sekunden gehalten wurde',
        calculatedAvgSpeed: 'Berechnete Ø Geschwindigkeit',
        calculatedAvgSpeedHint: 'Aus Distanz/Dauer',
        speedVariance: 'Geschwindigkeitsschwankung',
        lowAccuracyPoints: 'Ungenaue Punkte',
        gpsReliability: 'Zuverlässigkeit des GPS',
        reliable: 'Zuverlässig',
        unreliable: 'Unzuverlässig',
        reliableHint: 'Die GPS-Geschwindigkeiten liegen im erwarteten Bereich und werden für die Einstufung genutzt',
        unreliableHint: 'GPS-Geschwindigkeiten unzuverlässig – stattdessen wird die aus Distanz/Dauer berechnete Geschwindigkeit genutzt'
    },
    priority: {
        title: 'Reihenfolge der Einstufung',
        description: 'Strecken werden in dieser Reihenfolge von links nach rechts geprüft. Sobald eine passt, ist die Einstufung fertig.',
        learnMore: 'Mehr erfahren'
    },
    steps: {
        title: 'Analyse der Einstufung',
        description: 'Detaillierte Schwellenprüfungen der automatischen Einstufung.'
    },
    finalDecisionLabel: 'Ergebnis:',
    na: '–',
    toasts: {
        loadFailedFallback: 'Details zur Einstufung konnten nicht geladen werden',
        loadFailedDetail: 'Details zur Einstufung der Strecke konnten nicht geladen werden',
        movementUpdatedSummary: 'Fortbewegungsart aktualisiert',
        movementUpdatedDetail: 'Strecke als {type} markiert',
        updateFailedSummary: 'Aktualisieren fehlgeschlagen',
        updateFailedFallback: 'Fortbewegungsart konnte nicht aktualisiert werden',
        movementResetSummary: 'Fortbewegungsart zurückgesetzt',
        movementResetDetail: 'Einstufung der Strecke auf {type} zurückgesetzt',
        resetFailedSummary: 'Zurücksetzen fehlgeschlagen',
        resetFailedFallback: 'Fortbewegungsart konnte nicht zurückgesetzt werden'
    },
    stepCard: {
        notEnabled: 'Nicht aktiviert',
        passed: 'Erfüllt',
        failed: 'Nicht erfüllt',
        thresholdChecksHeader: 'Schwellenprüfungen:',
        actual: '(tatsächlich: {value})'
    }
}
