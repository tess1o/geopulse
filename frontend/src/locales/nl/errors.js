/**
 * Error copy.
 *
 * Keyed by the shape `utils/errorHandler.js` `formatError()` already had: a `{title, message}` pair
 * per HTTP status, plus network/timeout/session branches. Keeping the key structure aligned with
 * that switch means extraction is a straight substitution with no mapping layer.
 *
 * EN values are verbatim from the previous literals -- tests assert on this exact copy.
 */
export default {
    generic: {
        title: 'Er is iets misgegaan',
        message: 'Er is een onverwachte fout opgetreden. Probeer het opnieuw.'
    },
    unexpected: 'Er is een onverwachte fout opgetreden',
    network: {
        title: 'Verbindingsprobleem',
        message: 'Kan geen verbinding maken met de GeoPulse-servers. Controleer uw internetverbinding en probeer het opnieuw.',
        // Slightly shorter than `message`; the connection-error toast uses this one.
        shortMessage: 'Kan geen verbinding maken met de GeoPulse-servers. Controleer uw internetverbinding.'
    },
    timeout: {
        title: 'Verzoektimeout',
        message: 'Het verzoek duurt langer dan verwacht. Probeer het opnieuw.'
    },
    // Shared by the HTTP 401 branch and the auth-expiry branch, which showed identical copy.
    session: {
        title: 'Sessie verlopen',
        message: 'Uw sessie is verlopen. Meld u opnieuw aan.'
    },
    http: {
        400: {
            title: 'Ongeldig verzoek',
            message: 'Het verzoek kan niet worden verwerkt. Controleer uw invoer en probeer het opnieuw.'
        },
        401: {
            title: 'Authenticatie vereist',
            message: '@:errors.session.message'
        },
        403: {
            title: 'Toegang geweigerd',
            message: "U hebt geen toestemming om deze actie uit te voeren."
        },
        404: {
            title: 'Niet gevonden',
            message: 'De opgevraagde resource kan niet worden gevonden.'
        },
        409: {
            title: 'Conflict',
            message: 'Deze actie is in conflict met de huidige status. Vernieuw de pagina en probeer het opnieuw.'
        },
        429: {
            title: 'Teveel verzoeken',
            message: "U maakt verzoeken te snel. Wacht een moment en probeer het opnieuw."
        },
        500: {
            title: 'Serverfout',
            message: 'Interne serverfout'
        },
        502: {
            title: 'Service niet beschikbaar',
            message: 'GeoPulse is tijdelijk niet beschikbaar. Probeer het over enkele minuten opnieuw.'
        },
        503: {
            title: 'Service niet beschikbaar',
            message: '@:errors.http.502.message'
        },
        504: {
            title: 'Service niet beschikbaar',
            message: '@:errors.http.502.message'
        },
        // Fallback for any status without its own entry: `Error {status}` / `An error occurred ({status}).`
        unknown: {
            title: 'Fout {status}',
            message: 'Er is een fout opgetreden ({status}). Probeer het opnieuw.'
        }
    },
    backendUnavailable: {
        title: 'Backend niet beschikbaar',
        message: 'De GeoPulse-servers zijn momenteel niet beschikbaar. Probeer het later opnieuw.'
    },
    reference: {
        hint: 'Controleer de backend-logboeken voor ID: {id}'
    },
    retry: {
        available: 'Opnieuw proberen beschikbaar',
        hint: 'Klik hier om het opnieuw te proberen'
    },
    // Copy for the full-page error screen, keyed by its `type` discriminator.
    page: {
        connection: {
            title: 'Verbindingsprobleem',
            message: 'Kan geen verbinding maken met de GeoPulse-servers. Dit kan te wijten zijn aan een netwerkprobleem of serveronderhoud.'
        },
        server: {
            title: 'Serverfout',
            message: 'De GeoPulse-servers ondervinden problemen. Ons team is op de hoogte en werkt aan een oplossing.'
        },
        generic: {
            title: 'Er is iets misgegaan',
            message: 'Er is een onverwachte fout opgetreden. Probeer het opnieuw of neem contact op met de ondersteuning als het probleem aanhoudt.'
        },
        actions: {
            checkConnection: 'Verbinding controleren',
            goHome: 'Naar startpagina'
        },
        details: {
            summary: 'Technische details',
            requestHeading: 'Verzoekinformatie',
            method: 'Methode:',
            url: 'URL:',
            status: 'Status:',
            timestamp: 'Tijdstip:',
            errorHeading: 'Foutinformatie',
            requestId: 'Verzoek-ID:',
            errorId: 'Fout-ID:'
        },
        tips: {
            heading: 'Wat kunt u doen?',
            wait: 'Wacht een paar minuten en klik op "Verbinding controleren" - servers worden mogelijk opnieuw opgestart',
            internet: 'Controleer uw internetverbinding',
            refresh: 'Probeer de pagina te vernieuwen of de browsercache te wissen',
            maintenance: 'Als het probleem aanhoudt, zijn de GeoPulse-servers mogelijk onderhoud ondergaan',
            checkBack: 'Bekijk over 10-15 minuten opnieuw'
        },
        status: {
            internetConnection: 'Internetverbinding:',
            backend: 'GeoPulse-backend:',
            lastChecked: 'Laatst gecontroleerd:',
            online: 'Backend online',
            offline: 'Backend offline',
            // The connectivity row vs the short form inside it.
            connected: 'Verbonden',
            offlineShort: 'Offline'
        },
        restored: {
            title: 'Verbinding hersteld!',
            detail: 'De GeoPulse-servers zijn weer online. U wordt doorgestuurd...'
        },
        stillUnavailable: {
            title: 'Nog steeds niet beschikbaar',
            detail: 'De GeoPulse-servers ondervinden nog steeds problemen. Probeer het over enkele minuten opnieuw.'
        }
    }
}
