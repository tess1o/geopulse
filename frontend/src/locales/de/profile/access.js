/**
 * Security tab's access surfaces (German): OIDC sign-in providers and API tokens.
 *
 * 'OIDC', 'MCP' and 'API' stay as proper nouns.
 */
export default {
    oidc: {
        heading: 'Verknüpfte Konten',
        description: 'Verwalte Anmeldemethoden über soziale Netzwerke oder Firmen-OIDC.',
        linkedHeading: 'Verknüpfte Konten',
        // Screen-reader text for the provider avatar, e.g. 'GitHub icon'.
        providerIconAlt: '{provider}-Symbol',
        unlink: 'Trennen',
        tooltips: {
            demoDisabled: 'Im Demo-Modus deaktiviert',
            unlinkBlocked: 'Die einzige Anmeldemethode lässt sich nicht trennen, solange kein Passwort festgelegt ist.',
            unlink: 'Dieses Konto trennen'
        },
        linkHeading: 'Weiteres Konto verknüpfen',
        link: 'Verknüpfen',
        noPasswordWarning: 'Du hast kein Passwort festgelegt. Füge eine weitere Anmeldemethode hinzu, bevor du dein einziges verknüpftes Konto trennst.',
        confirm: {
            header: 'Trennen bestätigen',
            message: 'Möchtest du dein {provider}-Konto wirklich trennen? Das lässt sich nicht rückgängig machen.'
        },
        toasts: {
            // Summary shared by the load failure below.
            error: 'Fehler',
            loadFailed: 'Die verknüpften Konten konnten nicht geladen werden.',
            linkFailed: {
                title: 'Verknüpfen fehlgeschlagen',
                detail: 'Verknüpfen mit {provider} konnte nicht gestartet werden'
            },
            unlinked: {
                title: 'Konto getrennt',
                detail: 'Dein {provider}-Konto wurde getrennt.'
            },
            unlinkFailed: {
                title: 'Trennen fehlgeschlagen',
                detail: 'Konto konnte nicht getrennt werden'
            }
        }
    },
    apiTokens: {
        heading: 'API-Tokens',
        description: 'Erstelle benannte Tokens für Bots, MCP-Clients und Automatisierungen.',
        create: 'Token erstellen',
        columns: {
            name: 'Name',
            status: 'Status',
            expires: 'Läuft ab',
            lastUsed: 'Zuletzt verwendet',
            actions: 'Aktionen',
            access: 'Zugriff'
        },
        // Token scopes. Preset and scope titles also label tokens in the table.
        access: {
            label: 'Zugriff',
            fixedHint: 'Der Zugriff lässt sich später nicht ändern. Erstelle dafür ein neues Token und widerrufe dieses.',
            presets: {
                full: { title: 'Vollzugriff', description: 'Alles, was du tun kannst, auch API-Tokens verwalten und, für Admins, den Admin-Bereich.' },
                readOnly: { title: 'Nur lesen', description: 'Deine Daten ansehen und MCP-Clients nutzen. Kann nichts ändern.' },
                homeAssistant: { title: 'Home Assistant', description: 'Deine Daten lesen und Standorte aus Home Assistant senden.' },
                backup: { title: 'Backup', description: 'Deine Daten lesen sowie Exporte erstellen und herunterladen.' },
                custom: { title: 'Benutzerdefiniert', description: 'Einzelne Berechtigungen wählen.' }
            },
            scopes: {
                read: { title: 'Lesen', description: 'Zeitleiste, Orte, Statistiken, Freunde und Einstellungen ansehen.' },
                write: { title: 'Schreiben', description: 'Daten und Einstellungen ändern oder löschen.' },
                gpsIngest: { title: 'Standorte senden', description: 'GPS-Punkte mit diesem Token senden.' },
                export: { title: 'Export', description: 'Exporte erstellen und herunterladen.' },
                import: { title: 'Import', description: 'Dateien importieren, die Daten hinzufügen. Importe, die vorhandene Daten ersetzen, brauchen zusätzlich Schreiben.' }
            }
        },
        // Stands in for a missing expiry or last-used timestamp in the table.
        never: 'Nie',
        // Backend `TokenStatus` values rendered as a label.
        status: {
            active: 'Aktiv',
            expired: 'Abgelaufen',
            revoked: 'Widerrufen'
        },
        tooltips: {
            edit: 'Token bearbeiten',
            revoke: 'Token widerrufen'
        },
        empty: 'Noch keine API-Tokens erstellt.',
        // Shared by the edit and revoke dialogs.
        cancel: 'Abbrechen',
        editDialog: {
            editHeader: 'API-Token bearbeiten',
            createHeader: 'API-Token erstellen',
            nameLabel: 'Name',
            namePlaceholder: 'Token für Automatisierung',
            expirationLabel: 'Ablauf',
            expirationPlaceholder: 'Kein Ablauf',
            expirationHint: 'Leer lassen für kein Ablaufdatum.',
            save: 'Speichern',
            create: 'Erstellen'
        },
        createdDialog: {
            header: 'API-Token erstellt',
            message: 'Dieses Token wird nur einmal angezeigt. Bewahre es sicher auf, bevor du den Dialog schließt.',
            confirm: 'Ich habe das Token gespeichert',
            homeAssistantNext: 'Nächster Schritt: Lege für dieses Token eine Home-Assistant-Standortquelle an, damit Standorte aus Home Assistant in GeoPulse erscheinen. Kopiere vorher das Token, es wird nur einmal angezeigt.',
            homeAssistantLink: 'Home-Assistant-Quelle hinzufügen'
        },
        revokeDialog: {
            header: 'API-Token widerrufen',
            // The token name sits between the two halves inside <strong> -- see the template.
            confirmPrefix: 'Token ',
            confirmSuffix: ' widerrufen? Automatisierungen mit diesem Token funktionieren dann sofort nicht mehr.',
            confirm: 'Widerrufen'
        },
        validation: {
            nameRequired: 'Name des Tokens ist erforderlich',
            scopeRequired: 'Wähle mindestens eine Berechtigung'
        },
        toasts: {
            // Summary shared by the failure toasts below.
            error: 'Fehler',
            loadFailed: 'API-Tokens konnten nicht geladen werden',
            saveFailed: 'API-Token konnte nicht gespeichert werden',
            revokeFailed: 'API-Token konnte nicht widerrufen werden',
            saved: {
                title: 'Gespeichert',
                detail: 'API-Token aktualisiert'
            },
            created: {
                title: 'Erstellt',
                detail: 'API-Token erstellt'
            },
            revoked: {
                title: 'Widerrufen',
                detail: 'API-Token widerrufen'
            },
            copied: {
                title: 'Kopiert',
                detail: 'Token in die Zwischenablage kopiert'
            },
            copyFailed: {
                title: 'Kopieren fehlgeschlagen',
                detail: 'Markiere das Token und kopiere es manuell'
            }
        }
    }
}
