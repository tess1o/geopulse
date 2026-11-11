export default {
    page: {
        title: 'AI Chat-assistent',
        description: 'Stel vragen over uw locatiegegevens en krijg intelligente inzichten',
        disclaimer: 'AI-antwoorden zijn gebaseerd op data-analyse, maar kunnen fouten bevatten. Verifieer belangrijke informatie onafhankelijk.',
        clearHistoryTooltip: 'Gespreksgeschiedenis wissen',
        clearHistoryAriaLabel: 'Gespreksgeschiedenis wissen'
    },
    status: {
        checking: 'AI-status controleren...',
        unavailable: {
            title: 'AI-chat niet beschikbaar',
            disabled: 'AI-assistent is momenteel uitgeschakeld.',
            apiKeyMissing: 'OpenAI API-sleutel is niet geconfigureerd.',
            notConfigured: 'AI-assistent is niet correct geconfigureerd.',
            configureButton: 'AI-instellingen configureren',
            demoDisabledTooltip: 'AI-instellingen zijn alleen-lezen in demo-modus',
            demoDisabledText: 'AI-instellingen zijn alleen-lezen in demo-modus, dus deze configuratieactie is uitgeschakeld.'
        }
    },
    examples: {
        startTitle: 'Start een gesprek',
        startDescription: 'Vraag me iets over uw locatiegegevens. Bijvoorbeeld:',
        expiredTitle: 'Klaar voor een nieuw gesprek!',
        expiredDescription: 'Uw vorige gesprek is verlopen. Vraag me iets over uw locatiegegevens:',
        walkOrDrive: 'Loop ik meer of rijd ik meer?',
        busiestDay: 'Op welke dag van de week reis ik het meest?',
        citiesVisited: 'Hoeveel verschillende steden heb ik deze maand bezocht?',
        commonRoute: "Wat is mijn meest gebruikte route?"
    },
    message: {
        error: 'Fout'
    },
    loading: {
        title: 'AI denkt...',
        subtitle: 'Uw verzoek en locatiegegevens analyseren'
    },
    input: {
        placeholder: 'Vraag me iets over uw locatiegegevens...',
        placeholderUnavailable: 'AI-assistent is niet beschikbaar',
        warningDisabled: 'AI-assistent is uitgeschakeld.',
        warningApiKeyMissing: 'API-sleutel niet geconfigureerd.',
        warningUnavailable: 'AI-assistent niet beschikbaar.'
    },
    timestamp: {
        justNow: 'Net nu',
        minutesAgo: '{count}m geleden',
        yesterday: 'Gisteren {time}'
    },
    toasts: {
        chatClearedSummary: 'Chat gewist',
        chatClearedDetail: 'Uw gespreksgeschiedenis is gewist.',
        chatErrorSummary: 'Chatfout',
        genericError: 'Sorry, er is een fout opgetreden bij het verwerken van uw verzoek.',
        aiDisabledError: 'AI-assistent is uitgeschakeld. Schakel deze in in uw profielinstellingen.',
        apiKeyRequiredError: 'Voeg uw AI-provider API-sleutel toe in uw profielinstellingen.',
        contextTooLargeError: 'Het gesprek of de gegevens zijn te groot. Probeer een gerichtere vraag of wis de chatgeschiedenis.',
        rateLimitedError: 'De AI-provider-snelheidslimiet is bereikt. Probeer het later opnieuw.',
        authFailedError: 'De AI-provider heeft de geconfigureerde referenties afgewezen.',
        invalidRequestError: 'De AI-provider heeft dit verzoek afgewezen.',
        unavailableSummary: 'AI-chat niet beschikbaar',
        unavailableDemoDetail: 'AI-instellingen zijn alleen-lezen in demo-modus, dus ze kunnen niet worden geconfigureerd vanaf dit demo-account.',
        unavailableDisabledDetail: 'AI-assistent is uitgeschakeld. Schakel deze in in uw profielinstellingen.',
        unavailableApiKeyDetail: 'Configureer uw OpenAI API-sleutel in uw profiel om de chat-assistent te gebruiken.',
        unavailableDefaultDetail: 'Configureer uw AI-instellingen in uw profiel om de chat-assistent te gebruiken.'
    }
}
