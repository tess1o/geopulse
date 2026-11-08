/**
 * Trip Classification dialog (components/dialogs/TripClassificationDialog.vue and its
 * components/dialogs/classification/* presentational sub-components).
 *
 * Movement-type labels for the Select/options and the "current"/"algorithm" classification tags
 * reuse the shared `movementTypes.*` catalog rather than duplicating them here.
 */
export default {
    dialog: {
        header: 'Reisclassificatiedetails',
        loading: 'Classificatiedetails laden...',
        explanationLink: 'Waarom begint en eindigt deze reis hier?',
        close: 'Sluiten'
    },
    overview: {
        title: 'Reisoverzicht',
        startTime: 'Begintijd',
        duration: 'Duur',
        distance: 'Afstand',
        effectiveClassification: 'Effectieve Classificatie',
        automaticClassification: 'Automatische Classificatie',
        classificationSource: 'Classificatiebron',
        manualOverrideActive: 'Voor deze reis is handmatige overschrijving actief.'
    },
    editSection: {
        title: 'Bewegingstype bewerken',
        unrecognizedWarning: 'Algoritme heeft dit bewegingstype niet herkend. Stel dit handmatig hieronder in.',
        overrideInfo: 'Als dit onjuist lijkt, overschrijf dit dan handmatig.',
        readOnlyError: 'Bewegingstypebewerkingen zijn alleen-lezen in demo-modus.',
        selectPlaceholder: 'Bewegingstype selecteren',
        saveButton: 'Overschrijving opslaan',
        resetButton: 'Terug naar Automatisch'
    },
    stats: {
        title: 'GPS-statistieken',
        avgSpeed: 'Gemiddelde GPS-snelheid',
        avgSpeedHint: 'Gewogen naar de duur van elke meting',
        maxSpeed: 'Maximale GPS-snelheid',
        maxSpeedHint: 'Hoogste snelheid die ten minste 30 seconden werd aangehouden',
        calculatedAvgSpeed: 'Berekende Gemiddelde Snelheid',
        calculatedAvgSpeedHint: 'Uit afstand/duur',
        speedVariance: 'Snelheidsvariantie',
        lowAccuracyPoints: 'Lage Nauwkeurigheidspunten',
        gpsReliability: 'GPS-betrouwbaarheid',
        reliable: 'Betrouwbaar',
        unreliable: 'Onbetrouwbaar',
        reliableHint: 'GPS-snelheden liggen binnen het verwachte bereik en worden gebruikt voor classificatie',
        unreliableHint: 'GPS-snelheden onbetrouwbaar - berekende snelheid uit afstand/duur gebruiken in plaats daarvan'
    },
    priority: {
        title: 'Classificatieprioriteitsvolgorde',
        description: 'Reizen worden geclassificeerd in prioriteitsvolgorde van links naar rechts. Zodra een match is gevonden, stopt de classificatie.',
        learnMore: 'Lees meer'
    },
    steps: {
        title: 'Classificatieanalyse',
        description: 'Gedetailleerde drempelcontroles voor automatische classificatie.'
    },
    finalDecisionLabel: 'Uiteindelijke Beslissing:',
    na: 'Nvt',
    toasts: {
        loadFailedFallback: 'Classificatiedetails laden is mislukt',
        loadFailedDetail: 'Reisclassificatiedetails konden niet worden geladen',
        movementUpdatedSummary: 'Bewegingstype bijgewerkt',
        movementUpdatedDetail: 'Reis gemarkeerd als {type}',
        updateFailedSummary: 'Bijwerken mislukt',
        updateFailedFallback: 'Bewegingstype bijwerken is mislukt',
        movementResetSummary: 'Bewegingstype gereset',
        movementResetDetail: 'Reisclassificatie gereset naar {type}',
        resetFailedSummary: 'Reset mislukt',
        resetFailedFallback: 'Bewegingstype resetten is mislukt'
    },
    stepCard: {
        notEnabled: 'Niet ingeschakeld',
        passed: 'Geslaagd',
        failed: 'Mislukt',
        thresholdChecksHeader: 'Drempelcontroles:',
        actual: '(werkelijk: {value})'
    }
}
