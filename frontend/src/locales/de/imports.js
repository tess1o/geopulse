/**
 * Import job progress (German), keyed by backend `ImportPhase`. See en/imports.js.
 */
export default {
    phase: {
        validating: 'Dateiformat wird geprüft …',
        importing: 'Daten werden importiert … ({progress} %)',
        clearing_existing_data: 'Vorhandene Daten im Zeitraum werden gelöscht …',
        timeline_generation: 'Zeitleiste wird erstellt …',
        coverage_recalculation: 'Abdeckung wird neu berechnet …',
        completed: 'Import abgeschlossen',
        failed: 'Import fehlgeschlagen'
    }
}
