/**
 * Connected apps tab shell: the tab strip, per-app status chips, and the four panels.
 *
 * 'Immich' and 'Memos' are product names and stay untranslated. 'Enabled', 'Connected' and
 * 'Not configured' are asserted verbatim by ConnectedAppsTab.test.js.
 *
 * Every value under `ai`, `immich` and `memos` is the literal that used to sit in the matching panel
 * (AIAssistantTab, ImmichTab, MemosTab) or in its status/validation tables, copied verbatim --
 * profileTabsDirtyState.test.js matches some of them ('Test Connection', 'Reset', 'Timeline
 * defaults', 'Filtering & performance', 'Refresh provider models'), so none may be reworded.
 *
 * `messages.assetsAvailable` and `messages.memoCount` are the only values that are not a straight
 * literal: the panels built them with a JS template string, so the count is a named parameter here.
 * Both carry proper plural forms -- the originals read "1 assets available." and "1 memo(s)", which
 * Ukrainian\'s three required forms made impossible to preserve anyway.
 */
export default {
    title: 'Verbonden apps',
    description: 'Configureer diensten die AI, foto\'s en notities aan GeoPulse toevoegen.',
    listAria: 'Verbonden apps',
    apps: {
        ai: 'AI-assistent',
        immich: 'Immich',
        memos: 'Memos',
        mcp: 'MCP'
    },
    status: {
        enabled: 'Ingeschakeld',
        notEnabled: 'Niet ingeschakeld',
        connected: 'Verbonden',
        notConfigured: 'Niet geconfigureerd',
        available: 'Beschikbaar'
    },
    ai: {
        availability: {
            heading: 'Assistentbeschikbaarheid',
            description: 'Controleer of AI-chat beschikbaar is in GeoPulse.',
            enabled: {
                title: 'AI-assistent inschakelen',
                description: 'AI-gestuurde chat en tijdlijnassistentie toestaan.'
            }
        },
        provider: {
            heading: 'Provider',
            description: 'Configureer de OpenAI- of OpenAI-compatibele service die door de assistent wordt gebruikt.',
            apiKeyRequired: {
                title: 'API-sleutel vereist',
                description: 'Schakel uit alleen wanneer de provider onauthentiseerde verzoeken accepteert.'
            },
            apiKey: {
                title: 'API-sleutel',
                description: 'Voer een nieuwe sleutel in alleen bij het toevoegen of vervangen van inloggegevens.',
                placeholder: 'Voer uw OpenAI API-sleutel in',
                placeholderConfigured: 'API-sleutel is ingesteld (voer nieuwe sleutel in om te vervangen)',
                ariaLabel: 'OpenAI API-sleutel',
                configuredNote: 'API-sleutel is geconfigureerd. Laat leeg om deze te behouden.',
                missingNote: 'Voer een API-sleutel in om geverifieerde verzoeken in te schakelen.'
            },
            // The base URL field\'s aria-label is the same sentence as its title, so it reuses
            // `baseUrl.title` rather than duplicating the value -- matching the GPS-health channel.
            baseUrl: {
                title: 'API basis-URL',
                description: 'Gebruik OpenAI’s endpoint of een andere compatibele service.'
            },
            model: {
                title: 'Model',
                description: 'Kies een vermelde model of voer de identifier in.',
                placeholder: 'Selecteer of voer modelnaam in',
                ariaLabel: 'AI-model'
            },
            refreshModels: {
                ariaLabel: 'Providermodellen vernieuwen',
                tooltip: 'Modellen van server ophalen'
            }
        },
        behavior: {
            heading: 'Assistentgedrag',
            description: 'Pas de instructie aan die met elk gesprek wordt meegegeven.',
            systemMessage: {
                title: 'Systeembericht',
                description: 'Wis het bericht om de serverstandaard te herstellen.',
                details: 'Het systeembericht leidt de toon en het gedrag van de assistent.',
                placeholder: 'Systeembericht laden...',
                ariaLabel: 'AI-systeembericht'
            }
        },
        test: {
            success: 'Verbinding geslaagd!',
            failure: 'Verbinding mislukt. Controleer URL en API-sleutel.'
        },
        testConnection: 'Verbinding testen',
        saveSettings: 'AI-instellingen opslaan'
    },
    immich: {
        availability: {
            heading: 'Foto-integratie',
            description: 'Controleer of Immich-foto\'s op uw tijdlijn verschijnen.',
            enabled: {
                title: 'Immich inschakelen',
                description: 'Tijdlijnfoto\'s synchroniseren vanaf uw Immich-server.',
                ariaLabel: 'Immich-integratie inschakelen'
            }
        },
        connection: {
            heading: 'Verbinding',
            description: 'Geef de serveradres en inloggegevens op die worden gebruikt om toegang te krijgen tot Immich.',
            serverUrl: {
                title: 'Server-URL',
                description: 'Voer het volledige adres van uw Immich-server in.',
                ariaLabel: 'Immich server-URL'
            },
            apiKey: {
                title: 'API-sleutel',
                description: 'Maak een API-sleutel aan in uw Immich-serverinstellingen.',
                placeholder: 'Voer uw Immich API-sleutel in',
                placeholderConfigured: 'API-sleutel is ingesteld (voer nieuwe sleutel in om te vervangen)',
                ariaLabel: 'Immich API-sleutel',
                configuredNote: 'API-sleutel is geconfigureerd. Laat leeg om deze te behouden.'
            }
        },
        // Backend status code -> sentence. The codes are the keys the store returns and are never
        // translated; see the matching table in ImmichTab.vue.
        messages: {
            connected: 'Succesvol verbonden met Immich-server. ',
            userNotFound: 'De geconfigureerde Immich-gebruiker kon niet worden gevonden',
            apiKeyRequired: 'Een Immich API-sleutel is vereist',
            authenticationFailed: 'Immich heeft de API-sleutel geweigerd',
            serverNotFound: 'De Immich-server kon niet worden gevonden',
            connectionTimeout: 'De Immich-verbinding is verlopen',
            connectionFailed: 'Verbinding maken met de Immich-server is mislukt',
            testFailed: 'Verbinding testen is mislukt',
            testError: 'Verbindingstest is mislukt',
            unexpected: 'Er is een onverwachte fout opgetreden',
            assetsAvailable: '{count} asset beschikbaar. | {count} assets beschikbaar.'
        },
        errors: {
            serverUrlRequired: 'Server-URL is vereist wanneer integratie is ingeschakeld',
            serverUrlInvalid: 'Voer een geldige URL in (bijv. https://photos.example.com)',
            apiKeyRequired: 'API-sleutel is vereist wanneer integratie is ingeschakeld'
        },
        testConnection: 'Verbinding testen',
        reset: 'Resetten',
        saveSettings: 'Instellingen opslaan'
    },
    memos: {
        availability: {
            heading: 'Notitie-integratie',
            description: 'Controleer of getimestampte Memos-notities op uw tijdlijn verschijnen.',
            enabled: {
                title: 'Memos inschakelen',
                description: 'Tijdlijnnotities ophalen vanaf uw Memos-server.',
                ariaLabel: 'Memos-integratie inschakelen'
            }
        },
        connection: {
            heading: 'Verbinding',
            description: 'Geef de serveradres en inloggegevens op die worden gebruikt om toegang te krijgen tot Memos.',
            serverUrl: {
                title: 'Server-URL',
                description: 'Voer het volledige adres van uw Memos-server in.',
                ariaLabel: 'Memos server-URL'
            },
            apiKey: {
                title: 'API-sleutel',
                description: 'Maak een API-token aan in uw Memos-instellingen.',
                placeholder: 'Voer uw Memos API-sleutel in',
                placeholderConfigured: 'API-sleutel is ingesteld (voer nieuwe sleutel in om te vervangen)',
                ariaLabel: 'Memos API-sleutel',
                configuredNote: 'API-sleutel is geconfigureerd. Laat leeg om deze te behouden.'
            }
        },
        defaults: {
            heading: 'Tijdlijnstandaardinstellingen',
            description: 'Kies hoe vanuit GeoPulse gemaakte notities in Memos worden opgeslagen.',
            destination: {
                title: 'Standaard opslagbestemming',
                description: 'Kies waar nieuwe notities standaard worden opgeslagen.',
                ariaLabel: 'Standaard opslagbestemming',
                // Labels only: the Select\'s optionValue keeps the backend enum strings.
                options: {
                    geopulse: 'GeoPulse',
                    memos: 'Memos'
                }
            },
            visibility: {
                title: 'Standaard zichtbaarheid',
                description: 'Kies de initiële Memos-zichtbaarheid voor nieuwe notities.',
                ariaLabel: 'Standaard Memos-zichtbaarheid',
                options: {
                    private: 'Privé',
                    protected: 'Beschermd',
                    public: 'Openbaar'
                }
            }
        },
        filtering: {
            heading: 'Filteren & prestaties',
            description: 'Controleer gecachete zoekopdrachten en welke getagde notities op de tijdlijn verschijnen.',
            searchCache: {
                title: 'Zoekcache',
                description: 'Gebruik recente zoekopdrachten opnieuw voor sneller laden van tijdlijnnotities.',
                ariaLabel: 'Memos-zoekcache inschakelen'
            },
            includeTags: {
                title: 'Tags opnemen',
                description: 'Laad alleen notities die ten minste één van deze tags bevatten.',
                placeholder: 'Voeg een tag toe en druk op Enter'
            },
            excludeTags: {
                title: 'Tags uitsluiten',
                description: 'Verberg notities die een van deze tags bevatten.',
                placeholder: 'Voeg een tag toe en druk op Enter'
            }
        },
        // Backend status code -> sentence. The codes are the keys the store returns and are never
        // translated; see the matching table in MemosTab.vue.
        messages: {
            connected: 'Succesvol verbonden met Memos-server',
            userNotFound: 'Gebruiker niet gevonden',
            apiKeyRequired: 'API-sleutel is vereist',
            connectionFailed: 'Verbinding mislukt',
            testError: 'Verbindingstest is mislukt',
            memoCount: 'Server heeft {count} memo geretourneerd | Server heeft {count} memos geretourneerd'
        },
        errors: {
            serverUrlRequired: 'Server-URL is vereist wanneer integratie is ingeschakeld',
            serverUrlInvalid: 'Voer een geldige URL in',
            apiKeyRequired: 'API-sleutel is vereist wanneer integratie is ingeschakeld'
        },
        testConnection: 'Verbinding testen',
        reset: 'Resetten',
        saveSettings: 'Instellingen opslaan'
    },
    // MCP server setup. 'MCP', 'Claude Desktop', 'Claude Code' en 'ChatGPT' zijn productnamen en blijven
    // onvertaald; de configsnippets zelf worden gebouwd in mcpClientSnippets.js, niet hier.
    mcp: {
        connection: {
            heading: 'Verbinding',
            description: 'GeoPulse draait een MCP-server, zodat AI-clients vragen over uw locatiegeschiedenis kunnen beantwoorden. Het is altijd aan, en elke tool leest alleen gegevens.',
            serverUrl: {
                title: 'Server-URL',
                description: 'Het Streamable HTTP-eindpunt waarop uw MCP-client verbinding maakt.'
            },
            token: {
                title: 'API-token',
                description: 'Clients loggen in met een alleen-lezen API-token. Maak een apart token per client zodat u ze een voor een kunt intrekken.',
                create: 'Alleen-lezen token maken',
                manage: 'Tokens beheren',
                name: 'MCP ({client})',
                shownOnce: 'Kopieer deze token nu. GeoPulse toont deze niet opnieuw.'
            }
        },
        client: {
            heading: 'Clientinstellingen',
            description: 'Kies uw client en kopieer de configuratie.',
            options: {
                claudeDesktop: 'Claude Desktop',
                claudeCode: 'Claude Code',
                other: 'Andere clients'
            },
            hints: {
                claudeDesktop: 'Voeg dit toe aan {file}, start vervolgens Claude Desktop opnieuw. Het heeft Node.js nodig voor npx.',
                claudeCode: 'Voer dit uit in een terminal, start vervolgens Claude Code.',
                other: 'Gebruik deze waarden in elke client die Streamable HTTP en aangepaste headers ondersteunt. ChatGPT verbindt via een tunnel.',
                chatGptLink: 'ChatGPT instellen'
            },
            placeholderHint: 'Vervang {placeholder} door uw API-token, of maak hierboven een token aan om het in te vullen.'
        },
        tools: {
            heading: 'Wat uw AI-client kan lezen',
            description: 'De tools kunnen uw tijdlijn, locaties of deelingsinstellingen niet wijzigen.',
            areas: {
                timeline: { title: 'Tijdlijn', example: '"Wat heb ik afgelopen zaterdag gedaan?"' },
                now: { title: 'Nu', example: '"Waar ben ik?"' },
                places: { title: 'Plaatsen', example: '"Welke landen heb ik bezocht?"' },
                summaries: { title: 'Samenvattingen', example: '"Vat mijn oktober samen."' },
                trips: { title: 'Reizen en perioden', example: '"Wanneer was mijn Japan-reis?"' },
                notesPhotosWeather: { title: 'Notities, foto\'s en weer', example: '"Hoe was het weer op de tocht?"' },
                geofences: { title: 'Geofences', example: '"Wanneer heb ik het werk laatst verlaten?"' },
                friends: { title: 'Vrienden', example: '"Waar is Alex nu?"' }
            },
            requirements: 'Fotozoeken vereist de Immich-integratie, weer vereist weer ingeschakeld op de server, en dekking vereist dekking ingeschakeld. Vriendengegevens volgen uw bestaande delingsrechten.',
            docsLink: 'MCP-documentatie'
        },
        copy: 'Kopieer',
        toasts: {
            copied: 'Naar klembord gekopieerd',
            copyFailed: 'Naar klembord kopiëren is mislukt',
            createFailed: 'Token maken is mislukt'
        }
    }
}

