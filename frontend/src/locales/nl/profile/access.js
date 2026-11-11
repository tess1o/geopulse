/**
 * Security tab\'s access surfaces: OIDC sign-in providers and API tokens.
 *
 * EN values are verbatim from the previous literals.
 *
 * Toast pairs follow the page catalog\'s shape: a summary/detail pair is nested as `.title`/`.detail`,
 * while a summary shared by several toasts stays a flat key. 'OIDC', 'MCP' and 'API' stay as proper
 * nouns in every locale.
 */
export default {
    oidc: {
        heading: 'Gekoppelde accounts',
        description: 'Beheer sociale of bedrijfs OIDC-aanmeldingsmethoden.',
        linkedHeading: 'Gekoppelde accounts',
        // Screen-reader text for the provider avatar, e.g. 'GitHub icon'.
        providerIconAlt: '{provider} icon',
        unlink: 'Ontkoppelen',
        tooltips: {
            demoDisabled: 'Uitgeschakeld in demo-modus',
            unlinkBlocked: 'Kan de enige authenticatiemethode niet ontkoppelen zonder wachtwoord.',
            unlink: 'Dit account ontkoppelen'
        },
        linkHeading: 'Een ander account koppelen',
        link: 'Koppelen',
        noPasswordWarning: 'U heeft geen wachtwoord ingesteld. U moet eerst een andere aanmeldingsmethode toevoegen voordat u uw enige gekoppelde account ontkoppelt.',
        confirm: {
            header: 'Ontkoppelen bevestigen',
            message: 'Weet u zeker dat u uw {provider}-account wilt ontkoppelen? Deze actie kan niet ongedaan worden gemaakt.'
        },
        toasts: {
            // Summary shared by the load failure below.
            error: 'Fout',
            loadFailed: 'Informatie over gekoppelde accounts kon niet worden geladen.',
            linkFailed: {
                title: 'Koppelen mislukt',
                detail: 'Het starten van het koppelen voor {provider} is mislukt'
            },
            unlinked: {
                title: 'Account ontkoppeld',
                detail: 'Succesvol ontkoppeld van uw {provider}-account.'
            },
            unlinkFailed: {
                title: 'Ontkoppelen mislukt',
                detail: 'Account ontkoppelen is mislukt'
            }
        }
    },
    apiTokens: {
        heading: 'API-tokens',
        description: 'Maak benoemde tokens aan voor bots, MCP-clients en automatisering.',
        create: 'Token maken',
        columns: {
            name: 'Naam',
            status: 'Status',
            expires: 'Verloopt',
            lastUsed: 'Laatst gebruikt',
            actions: 'Acties',
            access: 'Toegang'
        },
        // Token scopes. Preset and scope titles also label tokens in the table.
        access: {
            label: 'Toegang',
            fixedHint: 'Toegang kan later niet worden gewijzigd. Om deze te wijzigen, maakt u een nieuwe token en trekt u deze in.',
            presets: {
                full: { title: 'Volledige toegang', description: 'Alles wat u kunt doen, inclusief het beheren van API-tokens en, voor beheerders, het beheerdersgebied.' },
                readOnly: { title: 'Alleen-lezen', description: 'Uw gegevens bekijken en MCP-clients gebruiken. Kan niets wijzigen.' },
                homeAssistant: { title: 'Home Assistant', description: 'Uw gegevens lezen en locaties sturen vanaf Home Assistant.' },
                backup: { title: 'Back-up', description: 'Uw gegevens lezen en exporteer maken en downloaden.' },
                custom: { title: 'Aangepast', description: 'Afzonderlijke machtigingen kiezen.' }
            },
            scopes: {
                read: { title: 'Lezen', description: 'Tijdlijn, plaatsen, statistieken, vrienden en instellingen bekijken.' },
                write: { title: 'Schrijven', description: 'Gegevens en instellingen wijzigen of verwijderen.' },
                gpsIngest: { title: 'Locaties sturen', description: 'GPS-punten met dit token sturen.' },
                export: { title: 'Export', description: 'Exporteer maken en downloaden.' },
                import: { title: 'Import', description: 'Bestanden importeren die gegevens toevoegen. Importeer die bestaande gegevens vervangen, hebben ook Schrijven nodig.' }
            }
        },
        // Stands in for a missing expiry or last-used timestamp in the table.
        never: 'Nooit',
        // Backend `TokenStatus` values rendered as a label. The enum itself is compared in code and is
        // never translated.
        status: {
            active: 'Actief',
            expired: 'Verlopen',
            revoked: 'Ingetrokken'
        },
        tooltips: {
            edit: 'Token bewerken',
            revoke: 'Token intrekken'
        },
        empty: 'Geen API-tokens aangemaakt.',
        // Shared by the edit and revoke dialogs.
        cancel: 'Annuleren',
        editDialog: {
            editHeader: 'API Token bewerken',
            createHeader: 'API Token maken',
            nameLabel: 'Naam',
            namePlaceholder: 'Automatiseringstoken',
            expirationLabel: 'Vervaldatum',
            expirationPlaceholder: 'Geen vervaldatum',
            expirationHint: 'Laat leeg voor geen vervaldatum.',
            save: 'Opslaan',
            create: 'Maken'
        },
        createdDialog: {
            header: 'API Token aangemaakt',
            message: 'Deze token wordt slechts één keer getoond. Bewaar deze veilig voordat u dit dialoogvenster sluit.',
            confirm: 'Ik heb deze token bewaard',
            homeAssistantNext: 'Volgende: voeg een Home Assistant-locatiebron toe voor deze token, zodat locaties van Home Assistant in GeoPulse verschijnen. Kopieer de token eerst; deze wordt slechts één keer getoond.',
            homeAssistantLink: 'Home Assistant-bron toevoegen'
        },
        revokeDialog: {
            header: 'API Token intrekken',
            // The token name sits between the two halves inside <strong>, so the sentence is split
            // around that element rather than interpolated -- see the template.
            confirmPrefix: 'Intrekken ',
            confirmSuffix: '? Automatisering die dit token gebruikt stopt onmiddellijk.',
            confirm: 'Intrekken'
        },
        validation: {
            nameRequired: 'Tokennaam is verplicht',
            scopeRequired: 'Kies ten minste één machtiging'
        },
        toasts: {
            // Summary shared by the failure toasts below.
            error: 'Fout',
            loadFailed: 'API-tokens laden is mislukt',
            saveFailed: 'API-token opslaan is mislukt',
            revokeFailed: 'API-token intrekken is mislukt',
            saved: {
                title: 'Opgeslagen',
                detail: 'API-token bijgewerkt'
            },
            created: {
                title: 'Gemaakt',
                detail: 'API-token gemaakt'
            },
            revoked: {
                title: 'Ingetrokken',
                detail: 'API-token ingetrokken'
            },
            // These two mirror `common.clipboard.copied`/`copyFailed`; the detail lines name the token,
            // so they stay here rather than in the shared block.
            copied: {
                title: 'Gekopieerd',
                detail: 'Token naar klembord gekopieerd'
            },
            copyFailed: {
                title: 'Kopiëren mislukt',
                detail: 'Selecteer de token en kopieer hem handmatig'
            }
        }
    }
}

