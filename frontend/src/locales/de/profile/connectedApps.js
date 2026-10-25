/**
 * Connected apps tab shell (German): the tab strip, per-app status chips, and the four panels.
 *
 * 'Immich', 'Memos' and 'MCP' are product names and stay untranslated.
 */
export default {
    title: 'Verbundene Apps',
    description: 'Richte Dienste ein, die GeoPulse um KI, Fotos und Notizen erweitern.',
    listAria: 'Verbundene Apps',
    apps: {
        ai: 'KI-Assistent',
        immich: 'Immich',
        memos: 'Memos',
        mcp: 'MCP'
    },
    status: {
        enabled: 'Aktiviert',
        notEnabled: 'Nicht aktiviert',
        connected: 'Verbunden',
        notConfigured: 'Nicht eingerichtet',
        available: 'Verfügbar'
    },
    ai: {
        availability: {
            heading: 'Verfügbarkeit des Assistenten',
            description: 'Lege fest, ob der KI-Chat in GeoPulse verfügbar ist.',
            enabled: {
                title: 'KI-Assistent aktivieren',
                description: 'KI-gestützten Chat und Hilfe zur Zeitleiste erlauben.'
            }
        },
        provider: {
            heading: 'Anbieter',
            description: 'Richte den OpenAI- oder OpenAI-kompatiblen Dienst für den Assistenten ein.',
            apiKeyRequired: {
                title: 'API-Schlüssel erforderlich',
                description: 'Nur ausschalten, wenn der Anbieter Anfragen ohne Authentifizierung annimmt.'
            },
            apiKey: {
                title: 'API-Schlüssel',
                description: 'Gib nur dann einen neuen Schlüssel ein, wenn du Zugangsdaten hinzufügst oder ersetzt.',
                placeholder: 'Deinen OpenAI-API-Schlüssel eingeben',
                placeholderConfigured: 'API-Schlüssel ist eingerichtet (neuen Schlüssel eingeben, um ihn zu ersetzen)',
                ariaLabel: 'OpenAI-API-Schlüssel',
                configuredNote: 'API-Schlüssel ist eingerichtet. Leer lassen, um ihn zu behalten.',
                missingNote: 'Gib einen API-Schlüssel ein, um authentifizierte Anfragen zu ermöglichen.'
            },
            baseUrl: {
                title: 'API-Basis-URL',
                description: 'Nutze den Endpunkt von OpenAI oder einen anderen kompatiblen Dienst.'
            },
            model: {
                title: 'Modell',
                description: 'Wähle ein Modell aus der Liste oder gib seine Kennung ein.',
                placeholder: 'Modellnamen wählen oder eingeben',
                ariaLabel: 'KI-Modell'
            },
            refreshModels: {
                ariaLabel: 'Modelle des Anbieters aktualisieren',
                tooltip: 'Modelle vom Server abrufen'
            }
        },
        behavior: {
            heading: 'Verhalten des Assistenten',
            description: 'Passe die Anweisung an, die mit jedem Gespräch gesendet wird.',
            systemMessage: {
                title: 'Systemnachricht',
                description: 'Leere die Nachricht, um den Server-Standard wiederherzustellen.',
                details: 'Die Systemnachricht bestimmt Ton und Verhalten des Assistenten.',
                placeholder: 'Systemnachricht wird geladen …',
                ariaLabel: 'KI-Systemnachricht'
            }
        },
        test: {
            success: 'Verbindung erfolgreich!',
            failure: 'Verbindung fehlgeschlagen. Prüf URL und API-Schlüssel.'
        },
        testConnection: 'Verbindung testen',
        saveSettings: 'KI-Einstellungen speichern'
    },
    immich: {
        availability: {
            heading: 'Foto-Anbindung',
            description: 'Lege fest, ob Immich-Fotos in deiner Zeitleiste erscheinen.',
            enabled: {
                title: 'Immich aktivieren',
                description: 'Fotos für die Zeitleiste von deinem Immich-Server abrufen.',
                ariaLabel: 'Immich-Anbindung aktivieren'
            }
        },
        connection: {
            heading: 'Verbindung',
            description: 'Gib Serveradresse und Zugangsdaten für Immich an.',
            serverUrl: {
                title: 'Server-URL',
                description: 'Die vollständige Adresse deines Immich-Servers.',
                ariaLabel: 'Immich-Server-URL'
            },
            apiKey: {
                title: 'API-Schlüssel',
                description: 'Erstelle einen API-Schlüssel in den Einstellungen deines Immich-Servers.',
                placeholder: 'Deinen Immich-API-Schlüssel eingeben',
                placeholderConfigured: 'API-Schlüssel ist gesetzt (neuen Schlüssel eingeben, um ihn zu ersetzen)',
                ariaLabel: 'Immich-API-Schlüssel',
                configuredNote: 'API-Schlüssel ist eingerichtet. Leer lassen, um ihn zu behalten.'
            }
        },
        // Backend status code -> sentence; see the matching table in ImmichTab.vue.
        messages: {
            connected: 'Mit dem Immich-Server verbunden. ',
            userNotFound: 'Der eingerichtete Immich-Benutzer wurde nicht gefunden',
            apiKeyRequired: 'Ein Immich-API-Schlüssel ist erforderlich',
            authenticationFailed: 'Immich hat den API-Schlüssel abgelehnt',
            serverNotFound: 'Der Immich-Server wurde nicht gefunden',
            connectionTimeout: 'Zeitüberschreitung bei der Verbindung zu Immich',
            connectionFailed: 'Verbindung zum Immich-Server fehlgeschlagen',
            testFailed: 'Verbindung konnte nicht getestet werden',
            testError: 'Verbindungstest fehlgeschlagen',
            unexpected: 'Ein unerwarteter Fehler ist aufgetreten',
            assetsAvailable: '{count} Medium verfügbar. | {count} Medien verfügbar.'
        },
        errors: {
            serverUrlRequired: 'Bei aktivierter Anbindung ist eine Server-URL erforderlich',
            serverUrlInvalid: 'Bitte gib eine gültige URL ein (z. B. https://photos.example.com)',
            apiKeyRequired: 'Bei aktivierter Anbindung ist ein API-Schlüssel erforderlich'
        },
        testConnection: 'Verbindung testen',
        reset: 'Zurücksetzen',
        saveSettings: 'Einstellungen speichern'
    },
    memos: {
        availability: {
            heading: 'Notiz-Anbindung',
            description: 'Lege fest, ob Memos-Notizen mit Zeitstempel in deiner Zeitleiste erscheinen.',
            enabled: {
                title: 'Memos aktivieren',
                description: 'Notizen für die Zeitleiste von deinem Memos-Server abrufen.',
                ariaLabel: 'Memos-Anbindung aktivieren'
            }
        },
        connection: {
            heading: 'Verbindung',
            description: 'Gib Serveradresse und Zugangsdaten für Memos an.',
            serverUrl: {
                title: 'Server-URL',
                description: 'Die vollständige Adresse deines Memos-Servers.',
                ariaLabel: 'Memos-Server-URL'
            },
            apiKey: {
                title: 'API-Schlüssel',
                description: 'Erstelle ein API-Token in deinen Memos-Einstellungen.',
                placeholder: 'Deinen Memos-API-Schlüssel eingeben',
                placeholderConfigured: 'API-Schlüssel ist gesetzt (neuen Schlüssel eingeben, um ihn zu ersetzen)',
                ariaLabel: 'Memos-API-Schlüssel',
                configuredNote: 'API-Schlüssel ist eingerichtet. Leer lassen, um ihn zu behalten.'
            }
        },
        defaults: {
            heading: 'Standards für die Zeitleiste',
            description: 'Lege fest, wie in GeoPulse erstellte Notizen in Memos gespeichert werden.',
            destination: {
                title: 'Standard-Speicherort',
                description: 'Wo neue Notizen standardmäßig gespeichert werden.',
                ariaLabel: 'Standard-Speicherort',
                // Labels only: the Select's optionValue keeps the backend enum strings.
                options: {
                    geopulse: 'GeoPulse',
                    memos: 'Memos'
                }
            },
            visibility: {
                title: 'Standard-Sichtbarkeit',
                description: 'Die anfängliche Sichtbarkeit neuer Notizen in Memos.',
                ariaLabel: 'Standard-Sichtbarkeit in Memos',
                options: {
                    private: 'Privat',
                    protected: 'Geschützt',
                    public: 'Öffentlich'
                }
            }
        },
        filtering: {
            heading: 'Filter & Leistung',
            description: 'Steuere zwischengespeicherte Suchen und welche getaggten Notizen in der Zeitleiste erscheinen.',
            searchCache: {
                title: 'Such-Cache',
                description: 'Letzte Suchen wiederverwenden, damit Notizen in der Zeitleiste schneller laden.',
                ariaLabel: 'Such-Cache für Memos aktivieren'
            },
            includeTags: {
                title: 'Tags einschließen',
                description: 'Nur Notizen laden, die mindestens eines dieser Tags enthalten.',
                placeholder: 'Tag eingeben und Enter drücken'
            },
            excludeTags: {
                title: 'Tags ausschließen',
                description: 'Notizen ausblenden, die eines dieser Tags enthalten.',
                placeholder: 'Tag eingeben und Enter drücken'
            }
        },
        // Backend status code -> sentence; see the matching table in MemosTab.vue.
        messages: {
            connected: 'Mit dem Memos-Server verbunden',
            userNotFound: 'Benutzer nicht gefunden',
            apiKeyRequired: 'API-Schlüssel ist erforderlich',
            connectionFailed: 'Verbindung fehlgeschlagen',
            testError: 'Verbindungstest fehlgeschlagen',
            memoCount: 'Der Server hat {count} Notiz geliefert | Der Server hat {count} Notizen geliefert'
        },
        errors: {
            serverUrlRequired: 'Bei aktivierter Anbindung ist eine Server-URL erforderlich',
            serverUrlInvalid: 'Bitte gib eine gültige URL ein',
            apiKeyRequired: 'Bei aktivierter Anbindung ist ein API-Schlüssel erforderlich'
        },
        testConnection: 'Verbindung testen',
        reset: 'Zurücksetzen',
        saveSettings: 'Einstellungen speichern'
    },
    // MCP server setup. 'MCP', 'Claude Desktop', 'Claude Code' and 'ChatGPT' are product names.
    mcp: {
        connection: {
            heading: 'Verbindung',
            description: 'GeoPulse betreibt einen MCP-Server, damit KI-Clients Fragen zu deinem Standortverlauf beantworten können. Er ist immer aktiv, und alle Werkzeuge lesen nur Daten.',
            serverUrl: {
                title: 'Server-URL',
                description: 'Der Streamable-HTTP-Endpunkt, mit dem sich dein MCP-Client verbindet.'
            },
            token: {
                title: 'API-Token',
                description: 'Clients melden sich mit einem API-Token mit Lesezugriff an. Erstelle für jeden Client ein eigenes Token, damit du sie einzeln widerrufen kannst.',
                create: 'Token mit Lesezugriff erstellen',
                manage: 'Tokens verwalten',
                name: 'MCP ({client})',
                shownOnce: 'Kopiere dieses Token jetzt. GeoPulse zeigt es nicht noch einmal an.'
            }
        },
        client: {
            heading: 'Client einrichten',
            description: 'Wähle deinen Client und kopiere die Konfiguration.',
            options: {
                claudeDesktop: 'Claude Desktop',
                claudeCode: 'Claude Code',
                other: 'Andere Clients'
            },
            hints: {
                claudeDesktop: 'Füge das in {file} ein und starte Claude Desktop neu. Für npx wird Node.js benötigt.',
                claudeCode: 'Führe das in einem Terminal aus und starte dann Claude Code.',
                other: 'Nutze diese Werte in jedem Client, der Streamable HTTP und eigene Header unterstützt. ChatGPT verbindet sich über einen Tunnel.',
                chatGptLink: 'ChatGPT einrichten'
            },
            placeholderHint: 'Ersetze {placeholder} durch dein API-Token oder erstelle oben ein Token, um es einzufügen.'
        },
        tools: {
            heading: 'Was dein KI-Client lesen kann',
            description: 'Die Werkzeuge können deine Zeitleiste, Standorte und Freigaben nicht ändern.',
            areas: {
                timeline: { title: 'Zeitleiste', example: '„Was habe ich letzten Samstag gemacht?“' },
                now: { title: 'Gerade jetzt', example: '„Wo bin ich?“' },
                places: { title: 'Orte', example: '„Welche Länder habe ich besucht?“' },
                summaries: { title: 'Zusammenfassungen', example: '„Fass meinen Oktober zusammen.“' },
                trips: { title: 'Reisen und Zeiträume', example: '„Wann war meine Japanreise?“' },
                notesPhotosWeather: { title: 'Notizen, Fotos und Wetter', example: '„Wie war das Wetter bei der Wanderung?“' },
                geofences: { title: 'Geofences', example: '„Wann habe ich zuletzt die Arbeit verlassen?“' },
                friends: { title: 'Freunde', example: '„Wo ist Alex gerade?“' }
            },
            requirements: 'Die Fotosuche braucht die Immich-Anbindung, Wetter muss auf dem Server aktiviert sein, ebenso die Abdeckung. Daten von Freunden folgen deinen bestehenden Freigaben.',
            docsLink: 'MCP-Dokumentation'
        },
        copy: 'Kopieren',
        toasts: {
            copied: 'In die Zwischenablage kopiert',
            copyFailed: 'Kopieren in die Zwischenablage fehlgeschlagen',
            createFailed: 'Token konnte nicht erstellt werden'
        }
    }
}
