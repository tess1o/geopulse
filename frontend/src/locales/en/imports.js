/**
 * Import job progress, keyed by backend `ImportPhase` (see `ImportJobResponse.descriptor()`, which
 * sends `imports.phase.<phase>` as the MessageDescriptor key, with the current `progress` percentage
 * as the only interpolation param).
 *
 * This is a coarser, per-phase translation than the specific batch/file detail baked into the
 * backend's English `fallback` text (e.g. "Importing GPS points (batch 3/10)") -- a translated locale
 * shows the generic phase sentence instead of that detail. Untranslated locales, and any phase not
 * listed here, safely fall back to the detailed English fallback via `formatMessageDescriptor()`.
 */
export default {
    phase: {
        validating: 'Validating file format...',
        importing: 'Importing data... ({progress}%)',
        clearing_existing_data: 'Clearing existing data in date range...',
        timeline_generation: 'Generating timeline...',
        coverage_recalculation: 'Recalculating coverage...',
        completed: 'Import completed',
        failed: 'Import failed'
    }
}
