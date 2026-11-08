/**
 * Admin pages: audit logs, user invitations, OIDC providers management.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    auditLogsPage: {
        title: 'Auditlogboeken',
        subtitle: 'Beheerdersacties en systeemwijzigingen controleren',
        filters: {
            dateRangeLabel: 'Datumbereik',
            dateRangePlaceholder: 'Datumbereik selecteren',
            actionTypeLabel: 'Actietype',
            actionTypePlaceholder: 'Alle acties',
            targetTypeLabel: 'Doeltype',
            targetTypePlaceholder: 'Alle doelen',
            adminEmailLabel: 'E-mail beheerder',
            adminEmailPlaceholder: 'Filter op e-mailadres beheerder...',
            clearFilters: 'Filters wissen'
        },
        actionTypes: {
            settingChanged: 'Instelling gewijzigd',
            settingReset: 'Instelling gereset',
            userEnabled: 'Gebruiker ingeschakeld',
            userDisabled: 'Gebruiker uitgeschakeld',
            userDeleted: 'Gebruiker verwijderd',
            userRoleChanged: 'Gebruikersrol gewijzigd',
            userPasswordReset: 'Gebruikerswachtwoord gereset',
            oidcProviderCreated: 'OIDC-provider gemaakt',
            oidcProviderUpdated: 'OIDC-provider bijgewerkt',
            oidcProviderDeleted: 'OIDC-provider verwijderd',
            oidcProviderReset: 'OIDC-provider gereset',
            invitationCreated: 'Uitnodiging gemaakt',
            invitationRevoked: 'Uitnodiging ingetrokken',
            timelineRegenerationCampaignCreated: 'Tijdlijn-regeneratiecampagne gemaakt',
            timelineRegenerationCampaignRetried: 'Tijdlijn-regeneratiecampagne opnieuw geprobeerd',
            adminLogin: 'Beheerdersinloggen'
        },
        targetTypes: {
            setting: 'Instelling',
            user: 'Gebruiker',
            oidcProvider: 'OIDC-provider',
            invitation: 'Uitnodiging',
            timelineRegenerationCampaign: 'Tijdlijn-regeneratiecampagne'
        },
        table: {
            currentPageReport: '{first} tot {last} van {totalRecords} auditlogboeken tonen',
            columns: {
                timestamp: 'Tijdstip',
                admin: 'Beheerder',
                action: 'Actie',
                targetType: 'Doeltype',
                targetId: 'Doel-ID',
                ipAddress: 'IP-adres'
            },
            empty: 'Geen auditlogboeken gevonden.'
        },
        expansion: {
            detailsHeader: 'Details',
            noDetails: 'Geen aanvullende details beschikbaar',
            adminUserId: 'Beheerdersgebruikers-ID:',
            timestampLabel: 'Tijdstip:'
        },
        mobile: {
            targetId: 'Doel-ID:',
            ipAddress: 'IP-adres:',
            detailsLabel: 'Details'
        },
        toasts: {
            loadFailedDetail: 'Auditlogboeken laden is mislukt'
        }
    },
    invitationsPage: {
        title: 'Gebruikersuitnodigingen',
        subtitle: 'Gebruikersuitnodigingslinks beheren',
        breadcrumb: 'Uitnodigingen',
        createInvitation: 'Uitnodiging maken',
        statusFilterPlaceholder: 'Filter op status',
        statuses: {
            all: 'Alle statussen',
            pending: 'In afwachting',
            used: 'Gebruikt',
            expired: 'Verlopen',
            revoked: 'Ingetrokken'
        },
        usedByDeletedUser: 'Verwijderde gebruiker',
        table: {
            currentPageReport: '{first} tot {last} van {totalRecords} uitnodigingen tonen',
            columns: {
                token: 'Token',
                createdBy: 'Gemaakt door',
                created: 'Gemaakt',
                expires: 'Verloopt',
                status: 'Status',
                usedBy: 'Gebruikt door',
                actions: 'Acties'
            },
            empty: 'Geen uitnodigingen gevonden.',
            copyLinkTooltip: 'Link kopiëren',
            revokeTooltip: 'Uitnodiging intrekken'
        },
        mobile: {
            usedByPrefix: 'Gebruikt door: {value}',
            copyLink: 'Link kopiëren',
            revoke: 'Intrekken'
        },
        createDialog: {
            expirationDateLabel: 'Vervaldatum',
            expirationDatePlaceholder: 'Vervaldatum selecteren'
        },
        linkDialog: {
            header: 'Uitnodigingslink gemaakt',
            shareText: 'Deel deze link met de gebruiker die u wilt uitnodigen:',
            copyTooltip: 'Naar klembord kopiëren',
            expiresLabel: 'Verloopt: {date}',
            message: 'Deze link kan slechts één keer worden gebruikt en verloopt op de hierboven getoonde datum.',
            close: 'Sluiten'
        },
        revokeDialog: {
            header: 'Intrekken bevestigen',
            message: 'Weet u zeker dat u deze uitnodiging wilt intrekken?',
            note: 'De uitnodigingslink is niet langer bruikbaar.',
            confirm: 'Intrekken'
        },
        toasts: {
            loadFailedDetail: 'Uitnodigingen laden is mislukt',
            createdDetail: 'Uitnodiging succesvol gemaakt',
            createFailedFallback: 'Uitnodiging maken is mislukt',
            copiedDetail: 'Link naar klembord gekopieerd',
            copyFailedDetail: 'Link kopiëren is mislukt',
            revokedDetail: 'Uitnodiging succesvol ingetrokken',
            revokeFailedFallback: 'Uitnodiging intrekken is mislukt'
        }
    },
    oidcProvidersPage: {
        title: 'OIDC-providers',
        subtitle: 'OAuth/OIDC-authenticatieproviders beheren',
        addProvider: 'Provider toevoegen',
        table: {
            headerTitle: 'Geconfigureerde providers',
            columns: {
                name: 'Naam',
                displayName: 'Weergavenaam',
                enabled: 'Ingeschakeld',
                source: 'Bron',
                metadata: 'Metadata',
                clientId: 'Client ID',
                actions: 'Acties'
            },
            enabledYes: 'Ja',
            enabledNo: 'Nee',
            sourceEnvironment: 'Omgeving',
            sourceCustom: 'Aangepast',
            metadataCached: 'In cache',
            metadataNotCached: 'Niet in cache',
            empty: 'Geen OIDC-providers geconfigureerd.',
            editTooltip: 'Provider bewerken',
            disableTooltip: 'Provider uitschakelen',
            enableTooltip: 'Provider inschakelen',
            testTooltip: 'Verbinding testen',
            cannotDeleteTooltip: 'Omgevingsproviders kunnen niet worden verwijderd. Verwijder omgevingsvariabelen om te verwijderen.',
            revertTooltip: 'Aangepaste configuratie verwijderen en terugkeren naar omgevingsstandaarden',
            deleteTooltip: 'Deze aangepaste provider permanent verwijderen'
        },
        mobile: {
            enabledBadge: 'Ingeschakeld',
            disabledBadge: 'Uitgeschakeld',
            edit: 'Bewerken',
            disable: 'Uitschakelen',
            enable: 'Ins schakelen',
            test: 'Testen',
            delete: 'Verwijderen'
        },
        deleteDialog: {
            header: 'Verwijderen bevestigen',
            confirmMessage: 'Weet u zeker dat u provider {name} wilt verwijderen?',
            envNoteLabel: 'ℹ️ Let op:',
            envNoteText: 'Deze provider is ook gedefinieerd in omgevingsvariabelen.',
            envNoteDetail: 'Verwijderen verwijdert de aangepaste databaseconfiguratie en keert terug naar omgevingsstandaarden.',
            customWarningLabel: '⚠️ Waarschuwing:',
            customWarningText: 'Dit is een aangepaste provider. Verwijdering is permanent.',
            revertButton: 'Terug naar omgeving',
            deleteButton: 'Verwijderen'
        },
        testResultDialog: {
            header: 'Provider-testresultaat',
            successMessage: 'Succesvol verbonden met OIDC-provider',
            endpointsTitle: 'Ontdekte eindpunten:',
            authorization: 'Autorisatie:',
            token: 'Token:',
            userinfo: 'UserInfo:',
            jwks: 'JWKS:',
            issuer: 'Uitgever:',
            errorDetailsTitle: 'Foutdetails:',
            errorType: 'Type:',
            errorDetailsLabel: 'Details:',
            close: 'Sluiten'
        },
        toasts: {
            loadFailedDetail: 'OIDC-providers laden is mislukt',
            updatedDetail: 'Provider succesvol bijgewerkt',
            createdDetail: 'Provider succesvol gemaakt',
            saveFailedFallback: 'Provider opslaan is mislukt',
            revertedDetail: 'Aangepaste configuratie verwijderd. Provider teruggezet naar omgevingsstandaarden.',
            deletedDetail: 'Provider succesvol verwijderd',
            deleteFailedFallback: 'Provider verwijderen is mislukt',
            connectionSuccessSummary: 'Verbinding geslaagd',
            connectionSuccessDetail: 'Providerverbinding succesvol getest',
            connectionFailedSummary: 'Verbinding mislukt',
            connectionFailedDetail: 'Verbinden met provider is mislukt',
            testErrorDetail: 'Providerverbinding testen is mislukt',
            statusEnabledDetail: 'Provider succesvol ingeschakeld',
            statusDisabledDetail: 'Provider succesvol uitgeschakeld',
            updateStatusFailedFallback: 'Providerstatus bijwerken is mislukt'
        }
    }
}
