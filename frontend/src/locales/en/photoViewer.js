/**
 * Full-screen photo viewer dialog (components/dialogs/PhotoViewerDialog.vue).
 */
export default {
    dialogAriaLabel: 'Photo viewer',
    fallbackName: 'Photo',
    title: {
        default: 'Photo Viewer',
        multiple: 'Photos ({current}/{total})'
    },
    counter: '{current} / {total}',
    loading: 'Loading photo…',
    loadError: 'Failed to load photo thumbnail',
    close: 'Close',
    closeAriaLabel: 'Close photo viewer',
    download: 'Download original',
    downloadAriaLabel: 'Download original photo',
    downloadOriginal: 'Download Original',
    downloading: 'Downloading…',
    toggleDetailsAriaLabel: 'Toggle photo details',
    showOnMap: 'Show on Map',
    showOnMapAriaLabel: 'Show photo on map',
    nav: {
        previous: 'Previous photo',
        next: 'Next photo'
    },
    thumbnails: {
        scrollLeft: 'Scroll thumbnails left',
        scrollRight: 'Scroll thumbnails right',
        showPhoto: 'Show photo {index}',
        altFallback: 'Photo {index}'
    },
    mobileSummary: {
        fallbackInfo: 'Photo information'
    },
    details: {
        heading: 'Photo details',
        closeAriaLabel: 'Close photo details',
        closeTitle: 'Close details',
        expandedAriaLabel: 'Expanded photo details',
        file: 'File',
        taken: 'Taken',
        location: 'Location'
    },
    toasts: {
        downloadStarted: {
            summary: 'Download Started',
            detail: 'Photo download has begun'
        },
        downloadFailed: {
            summary: 'Download Failed',
            detail: 'Could not download photo'
        }
    }
}
