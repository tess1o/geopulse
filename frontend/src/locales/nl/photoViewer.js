/**
 * Full-screen photo viewer dialog (components/dialogs/PhotoViewerDialog.vue).
 */
export default {
    dialogAriaLabel: 'Fotoviewer',
    fallbackName: 'Foto',
    title: {
        default: 'Fotoviewer',
        multiple: 'Foto\'s ({current}/{total})'
    },
    counter: '{current} / {total}',
    loading: 'Foto laden…',
    loadError: 'Fotominiatuur laden is mislukt',
    close: 'Sluiten',
    closeAriaLabel: 'Fotoviewer sluiten',
    download: 'Origineel downloaden',
    downloadAriaLabel: 'Originele foto downloaden',
    downloadOriginal: 'Origineel Downloaden',
    downloading: 'Downloaden…',
    toggleDetailsAriaLabel: 'Fotodetails omschakelen',
    showOnMap: 'Op Kaart Tonen',
    showOnMapAriaLabel: 'Foto op kaart tonen',
    nav: {
        previous: 'Vorige foto',
        next: 'Volgende foto'
    },
    thumbnails: {
        scrollLeft: 'Miniaturen naar links schuiven',
        scrollRight: 'Miniaturen naar rechts schuiven',
        showPhoto: 'Foto {index} tonen',
        altFallback: 'Foto {index}'
    },
    mobileSummary: {
        fallbackInfo: 'Fotoinformatie'
    },
    details: {
        heading: 'Fotodetails',
        closeAriaLabel: 'Fotodetails sluiten',
        closeTitle: 'Details sluiten',
        expandedAriaLabel: 'Uitgebreide fotodetails',
        file: 'Bestand',
        taken: 'Genomen',
        location: 'Locatie'
    },
    toasts: {
        downloadStarted: {
            summary: 'Download Gestart',
            detail: 'Fotodownload is begonnen'
        },
        downloadFailed: {
            summary: 'Download Mislukt',
            detail: 'Foto kon niet worden gedownload'
        }
    }
}
