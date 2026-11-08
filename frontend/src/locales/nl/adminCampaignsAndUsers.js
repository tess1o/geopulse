/**
 * Admin pages: timeline regeneration campaigns, user details.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    campaignsPage: {
        breadcrumb: {
            title: 'Tijdlijnregeneratie'
        },
        header: {
            title: 'Tijdlijnregeneratie',
            subtitle: 'Geforceerde tijdlijnregeneratiecampagnes maken en bewaken'
        },
        createCampaign: 'Campagne maken',
        labels: {
            campaign: 'Campagne',
            source: 'Bron',
            status: 'Status',
            affectedFrom: 'Gekoppeld vanaf',
            progress: 'Voortgang',
            reason: 'Reden',
            affectedUsers: 'Gekoppelde gebruikers',
            total: 'Totaal',
            pending: 'In afwachting',
            running: 'Bezig',
            completed: 'Voltooid',
            failed: 'Mislukt',
            skipped: 'Overgeslagen',
            created: 'Gemaakt',
            updated: 'Bijgewerkt',
            actions: 'Acties',
            done: 'Gereed'
        },
        table: {
            currentPageReport: '{first} tot {last} van {totalRecords} campagnes tonen',
            empty: 'Geen tijdlijnregeneratiecampagnes gevonden.',
            retryTooltip: 'Mislukte gebruikers opnieuw proberen',
            progressText: '{processed} / {total} verwerkt',
            pendingCount: '{count} in afwachting',
            runningCount: '{count} bezig',
            doneCount: '{count} gereed'
        },
        mobile: {
            detailsButton: 'Details',
            retryButton: 'Mislukte opnieuw proberen'
        },
        createDialog: {
            header: 'Tijdlijnregeneratiecampagne maken',
            campaignKeyLabel: 'Campagnesleutel',
            campaignKeyPlaceholder: 'juli-12-tijdlijnherstel',
            regenerateFromLabel: 'Regenereren vanaf',
            regenerateFromPlaceholder: 'Selecteer cutoffdatum en -tijd',
            reasonLabel: 'Reden',
            reasonPlaceholder: 'Leg uit waarom tijdlijnen moeten worden geregenereerd. Gebruikers zien dit bericht.',
            previewRequired: 'Voorbeeld vereist',
            previewHelp: 'Voorbeeld telt gebruikers met GPS-gegevens op of na de geselecteerde tijdstempel.',
            runPreview: 'Voorbeeld uitvoeren',
            reviewCreate: 'Maken beoordelen'
        },
        confirmDialog: {
            header: 'Tijdlijnregeneratie bevestigen'
        },
        detailsDialog: {
            header: 'Tijdlijnregeneratiedetails',
            usersTitle: 'Gebruikers die lopen, opnieuw proberen of mislukt zijn',
            retryButton: 'Mislukte opnieuw proberen',
            table: {
                email: 'E-mail',
                attempts: 'Pogingen',
                lastError: 'Laatste fout',
                nextAttempt: 'Volgende poging',
                empty: 'Er zijn geen gebruikers die lopen, wachten op opnieuw proberen of mislukt zijn.'
            },
            close: 'Sluiten'
        },
        toasts: {
            loadFailed: 'Tijdlijnregeneratiecampagnes laden is mislukt',
            previewFailedSummary: 'Voorbeeld mislukt',
            previewFailedDetail: 'Gekoppelde gebruikers voorbeeld tonen is mislukt',
            createdSummary: 'Campagne gemaakt',
            createdDetail: 'Tijdlijnregeneratiecampagne werd gemaakt.',
            createFailedSummary: 'Maken mislukt',
            createFailedDetail: 'Tijdlijnregeneratiecampagne maken is mislukt',
            detailsLoadFailed: 'Campagnedetails laden is mislukt',
            retryQueuedSummary: 'Opnieuw in wachtrij geplaatst',
            retryQueuedDetail: 'Mislukte campagnegebruikers werden opnieuw in de wachtrij geplaatst.',
            retryFailedSummary: 'Opnieuw proberen mislukt',
            retryFailedDetail: 'Campagnegebruikers opnieuw proberen is mislukt'
        }
    },
    userDetailsPage: {
        title: 'Gebruikersdetails',
        breadcrumb: {
            users: 'Gebruikers',
            loading: 'Laden...',
            notFound: 'Gebruiker niet gevonden'
        },
        noName: 'Geen naam',
        never: 'Nooit',
        infoCard: {
            title: 'Gebruikersinformatie',
            authentication: 'Authenticatie',
            password: 'Wachtwoord',
            oidcOnly: 'Alleen OIDC',
            timezone: 'Tijdzone',
            linkedOidcProviders: 'Gekoppelde OIDC-providers'
        },
        statsCard: {
            title: 'Activiteit & Statistieken',
            gpsPoints: 'GPS-punten',
            lastGpsPoint: 'Laatste GPS-punt',
            accountCreated: 'Account gemaakt',
            lastUpdated: 'Laatst bijgewerkt'
        },
        actionsCard: {
            title: 'Beheerdersacties',
            demoteToUser: 'Degraderen naar gebruiker',
            promoteToAdmin: 'Promoveren naar beheerder',
            resetPassword: 'Wachtwoord resetten',
            warningMessage: 'U kunt uw eigen account niet uitschakelen of verwijderen.'
        },
        apiTokensCard: {
            title: 'API-tokens',
            columnName: 'Naam',
            columnStatus: 'Status',
            columnExpires: 'Verloopt',
            columnLastUsed: 'Laatst gebruikt',
            columnActions: 'Acties',
            revokeTooltip: 'Token intrekken',
            empty: 'Geen API-tokens gevonden.',
            status: {
                active: 'Actief',
                expired: 'Verlopen',
                revoked: 'Ingetrokken'
            }
        },
        notFound: {
            message: 'Gebruiker niet gevonden',
            backButton: 'Terug naar gebruikers'
        },
        deleteDialog: {
            message: 'Weet u zeker dat u gebruiker {email} wilt verwijderen?',
            detail: 'Dit verwijdert permanent al hun gegevens.'
        },
        passwordDialog: {
            header: 'Wachtwoord resetten',
            message: 'Tijdelijk wachtwoord voor {email}:',
            copyHint: 'Deel dit wachtwoord veilig met de gebruiker.',
            close: 'Sluiten'
        },
        revokeDialog: {
            header: 'API-token intrekken',
            message: '{tokenName} voor {email} intrekken? Automatisering die dit token gebruikt stopt onmiddellijk.',
            revokeButton: 'Intrekken'
        },
        toasts: {
            loadUserFailed: 'Gebruikersdetails laden is mislukt',
            loadTokensFailed: 'API-tokens laden is mislukt',
            roleChanged: 'Gebruikersrol gewijzigd naar {role}',
            roleChangeFailed: 'Gebruikersrol wijzigen is mislukt',
            passwordResetSuccess: 'Wachtwoord succesvol gereset',
            passwordResetFailed: 'Wachtwoord resetten is mislukt',
            passwordCopied: 'Wachtwoord naar klembord gekopieerd',
            passwordCopyFailed: 'Wachtwoord kopiëren is mislukt',
            tokenRevokedSummary: 'Ingetrokken',
            tokenRevokedDetail: 'API-token ingetrokken',
            tokenRevokeFailed: 'API-token intrekken is mislukt'
        }
    }
}
