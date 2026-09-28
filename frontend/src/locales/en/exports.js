/**
 * Live progress messages sent by the backend as a MessageDescriptor ({key, params, fallback}) via
 * `formatMessageDescriptor()` for export jobs (CSV/GeoJSON/OwnTracks/GPX/full GeoPulse export). Keys
 * here are the `exports.progressMessages.*` suffixes the backend appends (see
 * `ExportJob.step()`/`ExportJob.STEP_KEY_PREFIX`). Not every backend-recognized key necessarily has an
 * entry -- an unmapped key safely falls back to the backend's English `fallback` text.
 */
export default {
    progressMessages: {
        initializingCsv: 'Initializing CSV export...',
        startingStreamGps: 'Starting to stream GPS data...',
        exportingGpsPointsCount: 'Exporting GPS points: {count} records',
        finalizingCsv: 'Finalizing CSV export...',
        exportCompleted: 'Export completed',
        initializingGeoJson: 'Initializing GeoJSON export...',
        finalizingGeoJson: 'Finalizing GeoJSON export...',
        streamingRecordsProgress: 'Exporting: {written} / {total} records',
        initializingOwnTracks: 'Initializing OwnTracks export...',
        finalizingOwnTracks: 'Finalizing OwnTracks export...',
        initializingGpx: 'Initializing GPX export...',
        startingGpxGeneration: 'Starting GPX generation...',
        finalizingGpx: 'Finalizing GPX export...',
        streamingRawGps: 'Streaming raw GPS data...',
        streamedGpsPointsCount: 'Streamed {count} GPS points...',
        completedStreamingRawGps: 'Completed streaming {count} raw GPS points',
        exportingTimelineTrips: 'Exporting timeline trips...',
        exportingTimelineStays: 'Exporting timeline stays...',
        loadingTripsAndStays: 'Loading trips and stays...',
        exportingGpxFilesCount: 'Exporting GPX files: {processed} / {total}',
        finalizingZip: 'Finalizing ZIP archive...',
        gpxZipExportCompleted: 'GPX ZIP export completed',
        creatingDailyGpxFiles: 'Creating {count} daily GPX files...',
        createdDailyGpxFilesCount: 'Created {created} / {total} daily GPX files',
        streamingRecordsToZipProgress: 'Streaming: {written} records',
        initializingExport: 'Initializing export...',
        addingMetadata: 'Adding metadata...',
        exportingGpsData: 'Exporting GPS data...',
        exportingTimelineData: 'Exporting timeline data...',
        exportingDataGaps: 'Exporting data gaps...',
        exportingFavorites: 'Exporting favorites...',
        exportingReverseGeocodingData: 'Exporting reverse geocoding data...',
        exportingTimelineLabels: 'Exporting timeline labels...',
        exportingTimelineOverrides: 'Exporting timeline overrides...',
        exportingTripWorkspace: 'Exporting trip workspace...',
        exportingNotificationTemplates: 'Exporting notification templates...',
        exportingGeofencingRules: 'Exporting geofencing rules...',
        exportingNotes: 'Exporting notes...',
        exportingWeatherSamples: 'Exporting weather samples...',
        exportingMapMatchingData: 'Exporting map matching data...',
        exportingFriends: 'Exporting friends...',
        exportingFriendPermissions: 'Exporting friend permissions...'
    }
}
