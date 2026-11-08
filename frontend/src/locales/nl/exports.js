/**
 * Live progress messages sent by the backend as a MessageDescriptor ({key, parameters, fallback}) via
 * `formatMessageDescriptor()` for export jobs (CSV/GeoJSON/OwnTracks/GPX/full GeoPulse export). Keys
 * here are the `exports.progressMessages.*` suffixes the backend appends (see
 * `ExportJob.step()`/`ExportJob.STEP_KEY_PREFIX`). Not every backend-recognized key necessarily has an
 * entry -- an unmapped key safely falls back to the backend\'s English `fallback` text.
 */
export default {
    progressMessages: {
        initializingCsv: 'CSV-export initialiseren...',
        collectingTimeline: 'Tijdlijndata verzamelen...',
        exportingGpsPoints: 'GPS-punten exporteren...',
        startingStreamGps: 'GPS-data streamen starten...',
        exportingGpsPointsCount: 'GPS-punten exporteren: {count} records',
        finalizingCsv: 'CSV-export afronden...',
        exportCompleted: 'Export voltooid',
        initializingGeoJson: 'GeoJSON-export initialiseren...',
        finalizingGeoJson: 'GeoJSON-export afronden...',
        streamingRecordsProgress: 'Exporteren: {written} / {total} records',
        initializingOwnTracks: 'OwnTracks-export initialiseren...',
        finalizingOwnTracks: 'OwnTracks-export afronden...',
        initializingGpx: 'GPX-export initialiseren...',
        startingGpxGeneration: 'GPX-generatie starten...',
        finalizingGpx: 'GPX-export afronden...',
        streamingRawGps: 'Ruwe GPS-data streamen...',
        streamedGpsPointsCount: '{count} GPS-punten gestreamd...',
        completedStreamingRawGps: 'Streamen van {count} ruwe GPS-punten voltooid',
        exportingTimelineTrips: 'Tijdlijnreizen exporteren...',
        exportingTimelineStays: 'Tijdlijnverblijfplaatsen exporteren...',
        loadingTripsAndStays: 'Reizen en verblijfplaatsen laden...',
        exportingGpxFilesCount: 'GPX-bestanden exporteren: {processed} / {total}',
        finalizingZip: 'ZIP-archief afronden...',
        gpxZipExportCompleted: 'GPX ZIP-export voltooid',
        creatingDailyGpxFiles: '{count} dagelijkse GPX-bestanden aanmaken...',
        createdDailyGpxFilesCount: '{created} / {total} dagelijkse GPX-bestanden aangemaakt',
        streamingRecordsToZipProgress: 'Streamen: {written} records',
        initializingExport: 'Export initialiseren...',
        addingMetadata: 'Metadata toevoegen...',
        exportingGpsData: 'GPS-data exporteren...',
        exportingTimelineData: 'Tijdlijndata exporteren...',
        exportingDataGaps: 'Datagaps exporteren...',
        exportingFavorites: 'Favorieten exporteren...',
        exportingReverseGeocodingData: 'Omgekeerde geocoderingsdata exporteren...',
        exportingTimelineLabels: 'Tijdlijnlabels exporteren...',
        exportingTimelineOverrides: 'Tijdlijnoverschrijvingen exporteren...',
        exportingTripWorkspace: 'Reiswerkruimte exporteren...',
        exportingNotificationTemplates: 'Meldingsjablonen exporteren...',
        exportingGeofencingRules: 'Geofencingregels exporteren...',
        exportingNotes: 'Notities exporteren...',
        exportingWeatherSamples: 'Weergedempte gegevens exporteren...',
        exportingMapMatchingData: 'Map matchingdata exporteren...',
        exportingFriends: 'Vrienden exporteren...',
        exportingFriendPermissions: 'Vriendenmachtigingen exporteren...'
    }
}

