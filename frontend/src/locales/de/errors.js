/**
 * Error copy (German). Keys mirror en/errors.js, which follows `utils/errorHandler.js` `formatError()`.
 */
export default {
    generic: {
        title: 'Etwas ist schiefgelaufen',
        message: 'Ein unerwarteter Fehler ist aufgetreten. Bitte versuch es noch einmal.'
    },
    unexpected: 'Ein unerwarteter Fehler ist aufgetreten',
    network: {
        title: 'Verbindungsproblem',
        message: 'Keine Verbindung zu den GeoPulse-Servern. Prüf deine Internetverbindung und versuch es noch einmal.',
        // Slightly shorter than `message`; the connection-error toast uses this one.
        shortMessage: 'Keine Verbindung zu den GeoPulse-Servern. Prüf deine Internetverbindung.'
    },
    timeout: {
        title: 'Zeitüberschreitung',
        message: 'Die Anfrage dauert länger als erwartet. Bitte versuch es noch einmal.'
    },
    // Shared by the HTTP 401 branch and the auth-expiry branch.
    session: {
        title: 'Sitzung abgelaufen',
        message: 'Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.'
    },
    http: {
        400: {
            title: 'Ungültige Anfrage',
            message: 'Die Anfrage konnte nicht verarbeitet werden. Prüf deine Eingaben und versuch es noch einmal.'
        },
        401: {
            title: 'Anmeldung erforderlich',
            message: '@:errors.session.message'
        },
        403: {
            title: 'Zugriff verweigert',
            message: 'Du hast keine Berechtigung für diese Aktion.'
        },
        404: {
            title: 'Nicht gefunden',
            message: 'Die angeforderte Ressource wurde nicht gefunden.'
        },
        409: {
            title: 'Konflikt',
            message: 'Diese Aktion widerspricht dem aktuellen Stand. Lade die Seite neu und versuch es noch einmal.'
        },
        429: {
            title: 'Zu viele Anfragen',
            message: 'Du sendest Anfragen zu schnell. Warte einen Moment und versuch es noch einmal.'
        },
        500: {
            title: 'Serverfehler',
            message: 'Interner Serverfehler'
        },
        502: {
            title: 'Dienst nicht verfügbar',
            message: 'GeoPulse ist vorübergehend nicht erreichbar. Versuch es in ein paar Minuten noch einmal.'
        },
        503: {
            title: 'Dienst nicht verfügbar',
            message: '@:errors.http.502.message'
        },
        504: {
            title: 'Dienst nicht verfügbar',
            message: '@:errors.http.502.message'
        },
        // Fallback for any status without its own entry.
        unknown: {
            title: 'Fehler {status}',
            message: 'Ein Fehler ist aufgetreten ({status}). Bitte versuch es noch einmal.'
        }
    },
    backendUnavailable: {
        title: 'Backend nicht erreichbar',
        message: 'Die GeoPulse-Server sind gerade nicht erreichbar. Bitte versuch es später noch einmal.'
    },
    reference: {
        hint: 'Suche in den Backend-Logs nach der ID: {id}'
    },
    retry: {
        available: 'Erneuter Versuch möglich',
        hint: 'Hier klicken, um es noch einmal zu versuchen'
    },
    // Copy for the full-page error screen, keyed by its `type` discriminator.
    page: {
        connection: {
            title: 'Verbindungsproblem',
            message: 'Keine Verbindung zu den GeoPulse-Servern. Das kann an einem Netzwerkproblem oder an Wartungsarbeiten liegen.'
        },
        server: {
            title: 'Serverfehler',
            message: 'Bei den GeoPulse-Servern gibt es Probleme. Das Team ist informiert und arbeitet an einer Lösung.'
        },
        generic: {
            title: 'Etwas ist schiefgelaufen',
            message: 'Ein unerwarteter Fehler ist aufgetreten. Versuch es noch einmal oder wende dich an den Support, wenn das Problem bleibt.'
        },
        actions: {
            checkConnection: 'Verbindung prüfen',
            goHome: 'Zur Startseite'
        },
        details: {
            summary: 'Technische Details',
            requestHeading: 'Anfrage',
            method: 'Methode:',
            url: 'URL:',
            status: 'Status:',
            timestamp: 'Zeitpunkt:',
            errorHeading: 'Fehler',
            requestId: 'Anfrage-ID:',
            errorId: 'Fehler-ID:'
        },
        tips: {
            heading: 'Was kannst du tun?',
            wait: 'Warte ein paar Minuten und klick auf „Verbindung prüfen“ – die Server starten vielleicht gerade neu',
            internet: 'Prüf deine Internetverbindung',
            refresh: 'Lade die Seite neu oder leere den Browser-Cache',
            maintenance: 'Wenn das Problem bleibt, werden die GeoPulse-Server vielleicht gerade gewartet',
            checkBack: 'Schau in 10–15 Minuten wieder vorbei'
        },
        status: {
            internetConnection: 'Internetverbindung:',
            backend: 'GeoPulse-Backend:',
            lastChecked: 'Zuletzt geprüft:',
            online: 'Backend online',
            offline: 'Backend offline',
            // The connectivity row vs the short form inside it.
            connected: 'Verbunden',
            offlineShort: 'Offline'
        },
        restored: {
            title: 'Verbindung wiederhergestellt!',
            detail: 'Die GeoPulse-Server sind wieder online. Du wirst weitergeleitet …'
        },
        stillUnavailable: {
            title: 'Weiterhin nicht erreichbar',
            detail: 'Bei den GeoPulse-Servern gibt es weiterhin Probleme. Versuch es in ein paar Minuten noch einmal.'
        }
    }
}
