/**
 * Full-screen photo viewer dialog (German).
 */
export default {
    dialogAriaLabel: 'Fotoansicht',
    fallbackName: 'Foto',
    title: {
        default: 'Fotoansicht',
        multiple: 'Fotos ({current}/{total})'
    },
    counter: '{current} / {total}',
    loading: 'Foto wird geladen …',
    loadError: 'Vorschaubild konnte nicht geladen werden',
    close: 'Schließen',
    closeAriaLabel: 'Fotoansicht schließen',
    download: 'Original herunterladen',
    downloadAriaLabel: 'Originalfoto herunterladen',
    downloadOriginal: 'Original herunterladen',
    downloading: 'Wird heruntergeladen …',
    toggleDetailsAriaLabel: 'Fotodetails ein- oder ausblenden',
    showOnMap: 'Auf der Karte zeigen',
    showOnMapAriaLabel: 'Foto auf der Karte zeigen',
    nav: {
        previous: 'Vorheriges Foto',
        next: 'Nächstes Foto'
    },
    thumbnails: {
        scrollLeft: 'Vorschaubilder nach links scrollen',
        scrollRight: 'Vorschaubilder nach rechts scrollen',
        showPhoto: 'Foto {index} zeigen',
        altFallback: 'Foto {index}'
    },
    mobileSummary: {
        fallbackInfo: 'Fotoinformationen'
    },
    details: {
        heading: 'Fotodetails',
        closeAriaLabel: 'Fotodetails schließen',
        closeTitle: 'Details schließen',
        expandedAriaLabel: 'Ausgeklappte Fotodetails',
        file: 'Datei',
        taken: 'Aufgenommen',
        location: 'Ort'
    },
    toasts: {
        downloadStarted: {
            summary: 'Download gestartet',
            detail: 'Das Foto wird heruntergeladen'
        },
        downloadFailed: {
            summary: 'Download fehlgeschlagen',
            detail: 'Foto konnte nicht heruntergeladen werden'
        }
    }
}
