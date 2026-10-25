/**
 * Admin pages (German): timeline regeneration campaigns, user details.
 */
export default {
    campaignsPage: {
        breadcrumb: {
            title: 'Zeitleisten-Neuberechnung'
        },
        header: {
            title: 'Zeitleisten-Neuberechnung',
            subtitle: 'Kampagnen zur erzwungenen Neuberechnung der Zeitleisten erstellen und überwachen'
        },
        createCampaign: 'Kampagne erstellen',
        labels: {
            campaign: 'Kampagne',
            source: 'Quelle',
            status: 'Status',
            affectedFrom: 'Betroffen ab',
            progress: 'Fortschritt',
            reason: 'Grund',
            affectedUsers: 'Betroffene Benutzer',
            total: 'Gesamt',
            pending: 'Ausstehend',
            running: 'Läuft',
            completed: 'Abgeschlossen',
            failed: 'Fehlgeschlagen',
            skipped: 'Übersprungen',
            created: 'Erstellt',
            updated: 'Aktualisiert',
            actions: 'Aktionen',
            done: 'Fertig'
        },
        table: {
            currentPageReport: '{first} bis {last} von {totalRecords} Kampagnen',
            empty: 'Keine Kampagnen zur Neuberechnung gefunden.',
            retryTooltip: 'Fehlgeschlagene Benutzer erneut versuchen',
            progressText: '{processed} / {total} verarbeitet',
            pendingCount: '{count} ausstehend',
            runningCount: '{count} laufen',
            doneCount: '{count} fertig'
        },
        mobile: {
            detailsButton: 'Details',
            retryButton: 'Fehlgeschlagene wiederholen'
        },
        createDialog: {
            header: 'Kampagne zur Neuberechnung erstellen',
            campaignKeyLabel: 'Schlüssel der Kampagne',
            campaignKeyPlaceholder: 'july-12-timeline-repair',
            regenerateFromLabel: 'Neu berechnen ab',
            regenerateFromPlaceholder: 'Stichtag und Uhrzeit wählen',
            reasonLabel: 'Grund',
            reasonPlaceholder: 'Erkläre, warum die Zeitleisten neu berechnet werden müssen. Die Benutzer sehen diese Nachricht.',
            previewRequired: 'Vorschau erforderlich',
            previewHelp: 'Die Vorschau zählt Benutzer mit GPS-Daten ab dem gewählten Zeitpunkt.',
            runPreview: 'Vorschau starten',
            reviewCreate: 'Prüfen & erstellen'
        },
        confirmDialog: {
            header: 'Neuberechnung bestätigen'
        },
        detailsDialog: {
            header: 'Details zur Neuberechnung',
            usersTitle: 'Laufende, wartende und fehlgeschlagene Benutzer',
            retryButton: 'Fehlgeschlagene wiederholen',
            table: {
                email: 'E-Mail',
                attempts: 'Versuche',
                lastError: 'Letzter Fehler',
                nextAttempt: 'Nächster Versuch',
                empty: 'Keine Benutzer laufen, warten auf einen erneuten Versuch oder sind fehlgeschlagen.'
            },
            close: 'Schließen'
        },
        toasts: {
            loadFailed: 'Kampagnen zur Neuberechnung konnten nicht geladen werden',
            previewFailedSummary: 'Vorschau fehlgeschlagen',
            previewFailedDetail: 'Betroffene Benutzer konnten nicht ermittelt werden',
            createdSummary: 'Kampagne erstellt',
            createdDetail: 'Die Kampagne zur Neuberechnung wurde erstellt.',
            createFailedSummary: 'Erstellen fehlgeschlagen',
            createFailedDetail: 'Kampagne zur Neuberechnung konnte nicht erstellt werden',
            detailsLoadFailed: 'Details zur Kampagne konnten nicht geladen werden',
            retryQueuedSummary: 'Wiederholung eingeplant',
            retryQueuedDetail: 'Die fehlgeschlagenen Benutzer wurden für einen erneuten Versuch eingeplant.',
            retryFailedSummary: 'Wiederholung fehlgeschlagen',
            retryFailedDetail: 'Benutzer der Kampagne konnten nicht erneut versucht werden'
        }
    },
    userDetailsPage: {
        title: 'Benutzerdetails',
        breadcrumb: {
            users: 'Benutzer',
            loading: 'Wird geladen …',
            notFound: 'Benutzer nicht gefunden'
        },
        noName: 'Kein Name',
        never: 'Nie',
        infoCard: {
            title: 'Benutzerinformationen',
            authentication: 'Anmeldung',
            password: 'Passwort',
            oidcOnly: 'Nur OIDC',
            timezone: 'Zeitzone',
            linkedOidcProviders: 'Verknüpfte OIDC-Anbieter'
        },
        statsCard: {
            title: 'Aktivität & Statistik',
            gpsPoints: 'GPS-Punkte',
            lastGpsPoint: 'Letzter GPS-Punkt',
            accountCreated: 'Konto erstellt',
            lastUpdated: 'Zuletzt aktualisiert'
        },
        actionsCard: {
            title: 'Administrative Aktionen',
            demoteToUser: 'Zum Benutzer herabstufen',
            promoteToAdmin: 'Zum Admin machen',
            resetPassword: 'Passwort zurücksetzen',
            warningMessage: 'Dein eigenes Konto kannst du nicht deaktivieren oder löschen.'
        },
        apiTokensCard: {
            title: 'API-Tokens',
            columnName: 'Name',
            columnStatus: 'Status',
            columnExpires: 'Läuft ab',
            columnLastUsed: 'Zuletzt verwendet',
            columnActions: 'Aktionen',
            revokeTooltip: 'Token widerrufen',
            empty: 'Keine API-Tokens gefunden.',
            status: {
                active: 'Aktiv',
                expired: 'Abgelaufen',
                revoked: 'Widerrufen'
            }
        },
        notFound: {
            message: 'Benutzer nicht gefunden',
            backButton: 'Zurück zu den Benutzern'
        },
        deleteDialog: {
            message: 'Möchtest du den Benutzer {email} wirklich löschen?',
            detail: 'Dabei werden alle Daten dauerhaft gelöscht.'
        },
        passwordDialog: {
            header: 'Passwort zurückgesetzt',
            message: 'Temporäres Passwort für {email}:',
            copyHint: 'Gib dieses Passwort sicher an die Person weiter.',
            close: 'Schließen'
        },
        revokeDialog: {
            header: 'API-Token widerrufen',
            message: '{tokenName} von {email} widerrufen? Automatisierungen mit diesem Token funktionieren dann sofort nicht mehr.',
            revokeButton: 'Widerrufen'
        },
        toasts: {
            loadUserFailed: 'Benutzerdetails konnten nicht geladen werden',
            loadTokensFailed: 'API-Tokens konnten nicht geladen werden',
            roleChanged: 'Rolle des Benutzers auf {role} geändert',
            roleChangeFailed: 'Rolle des Benutzers konnte nicht geändert werden',
            passwordResetSuccess: 'Passwort zurückgesetzt',
            passwordResetFailed: 'Passwort konnte nicht zurückgesetzt werden',
            passwordCopied: 'Passwort in die Zwischenablage kopiert',
            passwordCopyFailed: 'Passwort konnte nicht in die Zwischenablage kopiert werden',
            tokenRevokedSummary: 'Widerrufen',
            tokenRevokedDetail: 'API-Token widerrufen',
            tokenRevokeFailed: 'API-Token konnte nicht widerrufen werden'
        }
    }
}
