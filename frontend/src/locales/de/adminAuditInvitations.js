/**
 * Admin pages (German): audit logs, user invitations, OIDC providers management.
 */
export default {
    auditLogsPage: {
        title: 'Audit-Logs',
        subtitle: 'Administrative Aktionen und Systemänderungen nachvollziehen',
        filters: {
            dateRangeLabel: 'Zeitraum',
            dateRangePlaceholder: 'Zeitraum wählen',
            actionTypeLabel: 'Aktion',
            actionTypePlaceholder: 'Alle Aktionen',
            targetTypeLabel: 'Zieltyp',
            targetTypePlaceholder: 'Alle Ziele',
            adminEmailLabel: 'E-Mail des Admins',
            adminEmailPlaceholder: 'Nach E-Mail des Admins filtern …',
            clearFilters: 'Filter zurücksetzen'
        },
        actionTypes: {
            settingChanged: 'Einstellung geändert',
            settingReset: 'Einstellung zurückgesetzt',
            userEnabled: 'Benutzer aktiviert',
            userDisabled: 'Benutzer deaktiviert',
            userDeleted: 'Benutzer gelöscht',
            userRoleChanged: 'Rolle geändert',
            userPasswordReset: 'Passwort zurückgesetzt',
            oidcProviderCreated: 'OIDC-Anbieter erstellt',
            oidcProviderUpdated: 'OIDC-Anbieter aktualisiert',
            oidcProviderDeleted: 'OIDC-Anbieter gelöscht',
            oidcProviderReset: 'OIDC-Anbieter zurückgesetzt',
            invitationCreated: 'Einladung erstellt',
            invitationRevoked: 'Einladung widerrufen',
            timelineRegenerationCampaignCreated: 'Neuberechnungskampagne erstellt',
            timelineRegenerationCampaignRetried: 'Neuberechnungskampagne wiederholt',
            adminLogin: 'Admin-Anmeldung'
        },
        targetTypes: {
            setting: 'Einstellung',
            user: 'Benutzer',
            oidcProvider: 'OIDC-Anbieter',
            invitation: 'Einladung',
            timelineRegenerationCampaign: 'Neuberechnungskampagne'
        },
        table: {
            currentPageReport: '{first} bis {last} von {totalRecords} Einträgen',
            columns: {
                timestamp: 'Zeitpunkt',
                admin: 'Admin',
                action: 'Aktion',
                targetType: 'Zieltyp',
                targetId: 'Ziel-ID',
                ipAddress: 'IP-Adresse'
            },
            empty: 'Keine Audit-Logs gefunden.'
        },
        expansion: {
            detailsHeader: 'Details',
            noDetails: 'Keine weiteren Details vorhanden',
            adminUserId: 'Benutzer-ID des Admins:',
            timestampLabel: 'Zeitpunkt:'
        },
        mobile: {
            targetId: 'Ziel-ID:',
            ipAddress: 'IP-Adresse:',
            detailsLabel: 'Details'
        },
        toasts: {
            loadFailedDetail: 'Audit-Logs konnten nicht geladen werden'
        }
    },
    invitationsPage: {
        title: 'Einladungen',
        subtitle: 'Einladungslinks für neue Benutzer verwalten',
        breadcrumb: 'Einladungen',
        createInvitation: 'Einladung erstellen',
        statusFilterPlaceholder: 'Nach Status filtern',
        statuses: {
            all: 'Alle Status',
            pending: 'Offen',
            used: 'Verwendet',
            expired: 'Abgelaufen',
            revoked: 'Widerrufen'
        },
        usedByDeletedUser: 'Gelöschter Benutzer',
        table: {
            currentPageReport: '{first} bis {last} von {totalRecords} Einladungen',
            columns: {
                token: 'Token',
                createdBy: 'Erstellt von',
                created: 'Erstellt',
                expires: 'Läuft ab',
                status: 'Status',
                usedBy: 'Verwendet von',
                actions: 'Aktionen'
            },
            empty: 'Keine Einladungen gefunden.',
            copyLinkTooltip: 'Link kopieren',
            revokeTooltip: 'Einladung widerrufen'
        },
        mobile: {
            usedByPrefix: 'Verwendet von: {value}',
            copyLink: 'Link kopieren',
            revoke: 'Widerrufen'
        },
        createDialog: {
            expirationDateLabel: 'Ablaufdatum',
            expirationDatePlaceholder: 'Ablaufdatum wählen'
        },
        linkDialog: {
            header: 'Einladungslink erstellt',
            shareText: 'Teile diesen Link mit der Person, die du einladen möchtest:',
            copyTooltip: 'In die Zwischenablage kopieren',
            expiresLabel: 'Läuft ab: {date}',
            message: 'Dieser Link kann nur einmal verwendet werden und läuft zum oben genannten Datum ab.',
            close: 'Schließen'
        },
        revokeDialog: {
            header: 'Widerrufen bestätigen',
            message: 'Möchtest du diese Einladung wirklich widerrufen?',
            note: 'Der Einladungslink ist danach nicht mehr verwendbar.',
            confirm: 'Widerrufen'
        },
        toasts: {
            loadFailedDetail: 'Einladungen konnten nicht geladen werden',
            createdDetail: 'Einladung erstellt',
            createFailedFallback: 'Einladung konnte nicht erstellt werden',
            copiedDetail: 'Link in die Zwischenablage kopiert',
            copyFailedDetail: 'Kopieren in die Zwischenablage fehlgeschlagen',
            revokedDetail: 'Einladung widerrufen',
            revokeFailedFallback: 'Einladung konnte nicht widerrufen werden'
        }
    },
    oidcProvidersPage: {
        title: 'OIDC-Anbieter',
        subtitle: 'OAuth/OIDC-Anbieter für die Anmeldung verwalten',
        addProvider: 'Anbieter hinzufügen',
        table: {
            headerTitle: 'Eingerichtete Anbieter',
            columns: {
                name: 'Name',
                displayName: 'Anzeigename',
                enabled: 'Aktiviert',
                source: 'Quelle',
                metadata: 'Metadaten',
                clientId: 'Client-ID',
                actions: 'Aktionen'
            },
            enabledYes: 'Ja',
            enabledNo: 'Nein',
            sourceEnvironment: 'Umgebung',
            sourceCustom: 'Eigene',
            metadataCached: 'Zwischengespeichert',
            metadataNotCached: 'Nicht zwischengespeichert',
            empty: 'Keine OIDC-Anbieter eingerichtet.',
            editTooltip: 'Anbieter bearbeiten',
            disableTooltip: 'Anbieter deaktivieren',
            enableTooltip: 'Anbieter aktivieren',
            testTooltip: 'Verbindung testen',
            cannotDeleteTooltip: 'Anbieter, die nur über Umgebungsvariablen definiert sind, lassen sich hier nicht löschen. Entferne sie aus den Umgebungsvariablen.',
            revertTooltip: 'Eigene Konfiguration löschen und auf die Umgebungsvariablen zurücksetzen',
            deleteTooltip: 'Diesen eigenen Anbieter dauerhaft löschen'
        },
        mobile: {
            enabledBadge: 'Aktiviert',
            disabledBadge: 'Deaktiviert',
            edit: 'Bearbeiten',
            disable: 'Deaktivieren',
            enable: 'Aktivieren',
            test: 'Testen',
            delete: 'Löschen'
        },
        deleteDialog: {
            header: 'Löschen bestätigen',
            confirmMessage: 'Möchtest du den Anbieter {name} wirklich löschen?',
            envNoteLabel: 'ℹ️ Hinweis:',
            envNoteText: 'Dieser Anbieter ist auch in Umgebungsvariablen definiert.',
            envNoteDetail: 'Beim Löschen wird die eigene Konfiguration in der Datenbank entfernt und auf die Umgebungsvariablen zurückgesetzt.',
            customWarningLabel: '⚠️ Achtung:',
            customWarningText: 'Das ist ein eigener Anbieter. Das Löschen ist endgültig.',
            revertButton: 'Auf Umgebung zurücksetzen',
            deleteButton: 'Löschen'
        },
        testResultDialog: {
            header: 'Testergebnis des Anbieters',
            successMessage: 'Verbindung zum OIDC-Anbieter erfolgreich',
            endpointsTitle: 'Gefundene Endpunkte:',
            authorization: 'Authorization:',
            token: 'Token:',
            userinfo: 'UserInfo:',
            jwks: 'JWKS:',
            issuer: 'Issuer:',
            errorDetailsTitle: 'Fehlerdetails:',
            errorType: 'Typ:',
            errorDetailsLabel: 'Details:',
            close: 'Schließen'
        },
        toasts: {
            loadFailedDetail: 'OIDC-Anbieter konnten nicht geladen werden',
            updatedDetail: 'Anbieter aktualisiert',
            createdDetail: 'Anbieter erstellt',
            saveFailedFallback: 'Anbieter konnte nicht gespeichert werden',
            revertedDetail: 'Eigene Konfiguration entfernt. Der Anbieter nutzt wieder die Umgebungsvariablen.',
            deletedDetail: 'Anbieter gelöscht',
            deleteFailedFallback: 'Anbieter konnte nicht gelöscht werden',
            connectionSuccessSummary: 'Verbindung erfolgreich',
            connectionSuccessDetail: 'Verbindung zum Anbieter erfolgreich getestet',
            connectionFailedSummary: 'Verbindung fehlgeschlagen',
            connectionFailedDetail: 'Verbindung zum Anbieter fehlgeschlagen',
            testErrorDetail: 'Verbindung zum Anbieter konnte nicht getestet werden',
            statusEnabledDetail: 'Anbieter aktiviert',
            statusDisabledDetail: 'Anbieter deaktiviert',
            updateStatusFailedFallback: 'Status des Anbieters konnte nicht aktualisiert werden'
        }
    }
}
