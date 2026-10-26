/**
 * AI chat assistant page (German).
 */
export default {
    page: {
        title: 'KI-Chat-Assistent',
        description: 'Stell Fragen zu deinen Standortdaten und erhalte kluge Einblicke',
        disclaimer: 'Die Antworten der KI beruhen auf einer Datenanalyse, können aber Fehler enthalten. Prüf wichtige Informationen bitte selbst.',
        clearHistoryTooltip: 'Gesprächsverlauf löschen',
        clearHistoryAriaLabel: 'Gesprächsverlauf löschen'
    },
    status: {
        checking: 'Status der KI wird geprüft …',
        unavailable: {
            title: 'KI-Chat nicht verfügbar',
            disabled: 'Der KI-Assistent ist derzeit deaktiviert.',
            apiKeyMissing: 'Es ist kein OpenAI-API-Schlüssel eingerichtet.',
            notConfigured: 'Der KI-Assistent ist nicht richtig eingerichtet.',
            configureButton: 'KI-Einstellungen öffnen',
            demoDisabledTooltip: 'Die KI-Einstellungen sind im Demo-Modus schreibgeschützt',
            demoDisabledText: 'Die KI-Einstellungen sind im Demo-Modus schreibgeschützt, daher ist diese Aktion deaktiviert.'
        }
    },
    examples: {
        startTitle: 'Gespräch beginnen',
        startDescription: 'Frag mich zu deinen Standortdaten, zum Beispiel:',
        expiredTitle: 'Bereit für ein neues Gespräch!',
        expiredDescription: 'Dein letztes Gespräch ist abgelaufen. Frag mich zu deinen Standortdaten:',
        walkOrDrive: 'Gehe ich mehr zu Fuß oder fahre ich mehr Auto?',
        busiestDay: 'An welchem Wochentag bin ich am meisten unterwegs?',
        citiesVisited: 'Wie viele verschiedene Städte habe ich diesen Monat besucht?',
        commonRoute: 'Was ist meine häufigste Route?'
    },
    message: {
        error: 'Fehler'
    },
    loading: {
        title: 'Die KI denkt nach …',
        subtitle: 'Deine Anfrage und deine Standortdaten werden ausgewertet'
    },
    input: {
        placeholder: 'Frag mich zu deinen Standortdaten …',
        placeholderUnavailable: 'Der KI-Assistent ist nicht verfügbar',
        warningDisabled: 'Der KI-Assistent ist deaktiviert.',
        warningApiKeyMissing: 'Kein API-Schlüssel eingerichtet.',
        warningUnavailable: 'KI-Assistent nicht verfügbar.'
    },
    timestamp: {
        justNow: 'Gerade eben',
        minutesAgo: 'vor {count} Min.',
        yesterday: 'Gestern {time}'
    },
    toasts: {
        chatClearedSummary: 'Chat gelöscht',
        chatClearedDetail: 'Dein Gesprächsverlauf wurde gelöscht.',
        chatErrorSummary: 'Fehler im Chat',
        genericError: 'Beim Verarbeiten deiner Anfrage ist leider ein Fehler aufgetreten.',
        aiDisabledError: 'Der KI-Assistent ist deaktiviert. Aktiviere ihn in deinen Profileinstellungen.',
        apiKeyRequiredError: 'Trag den API-Schlüssel deines KI-Anbieters in deinen Profileinstellungen ein.',
        contextTooLargeError: 'Das Gespräch oder die Daten sind zu umfangreich. Stell eine engere Frage oder lösche den Verlauf.',
        rateLimitedError: 'Das Anfragelimit des KI-Anbieters ist erreicht. Bitte versuch es später noch einmal.',
        authFailedError: 'Der KI-Anbieter hat die eingerichteten Zugangsdaten abgelehnt.',
        invalidRequestError: 'Der KI-Anbieter hat diese Anfrage abgelehnt.',
        unavailableSummary: 'KI-Chat nicht verfügbar',
        unavailableDemoDetail: 'Die KI-Einstellungen sind im Demo-Modus schreibgeschützt und lassen sich mit diesem Demo-Konto nicht einrichten.',
        unavailableDisabledDetail: 'Der KI-Assistent ist deaktiviert. Bitte aktiviere ihn in deinen Profileinstellungen.',
        unavailableApiKeyDetail: 'Richte in deinem Profil deinen OpenAI-API-Schlüssel ein, um den Chat-Assistenten zu nutzen.',
        unavailableDefaultDetail: 'Richte in deinem Profil die KI-Einstellungen ein, um den Chat-Assistenten zu nutzen.'
    }
}
