/**
 * Personal settings page (UserProfilePage.vue): page header, tab navigation, save toasts, the
 * unsaved-changes confirmation, and the errors raised by the save handlers.
 *
 * EN values are verbatim from the previous literals.
 */
export default {
    page: {
        title: 'Persoonlijke instellingen',
        description: 'Beheer uw account, voorkeuren en verbonden apps',
        signedInAs: 'Aangemeld als {email}'
    },
    searchPlaceholder: 'Zoeken in profielinstellingen...',
    sectionsAria: 'Profielinstellingensecties',
    mobileSectionLabel: 'Instellingensectie',
    demoReadOnly: 'Demo-modus: profiel, beveiliging, weergave, AI, Immich en Memos-instellingen zijn alleen-lezen. Wijzigingen kunnen in deze demo niet worden opgeslagen.',
    demoReadOnlyToast: 'Profielwijzigingen zijn uitgeschakeld in demo-modus.',
    groups: {
        personal: 'Persoonlijk',
        experience: 'Ervaring',
        connectedApps: 'Verbonden Apps'
    },
    tabs: {
        general: 'Algemeen',
        security: 'Beveiliging',
        timeline: 'Tijdlijn & Kaart',
        appearance: 'Uiterlijk',
        notifications: 'Meldingen',
        connectedApps: 'Verbonden Apps'
    },
    jump: {
        notVisibleTitle: 'Instelling niet zichtbaar',
        notVisibleDetail: 'Deze instelling is momenteel niet zichtbaar. Schakel gerelateerde opties in om deze te bewerken.'
    },
    unsaved: {
        header: 'Niet-opgeslagen wijzigingen',
        message: 'U hebt niet-opgeslagen profielwijzigingen. Als u deze pagina verlaat, gaan deze wijzigingen verloren.',
        leave: 'Verlaten zonder opslaan',
        stay: 'Blijven'
    },
    save: {
        updateFailed: 'Bijwerken mislukt',
        saveFailed: 'Opslaan mislukt',
        notificationsSaved: 'Meldingsvoorkeuren opgeslagen',
        profileUpdated: {
            title: 'Profiel bijgewerkt',
            detail: 'Uw profiel is succesvol bijgewerkt'
        },
        displayUpdated: {
            title: 'Weergave-instellingen bijgewerkt',
            detail: 'Uw tijdlijnweergavevoorkeuren zijn opgeslagen. Wijzigingen zijn onmiddellijk zichtbaar.'
        },
        passwordChanged: {
            title: 'Wachtwoord gewijzigd',
            detail: 'Uw wachtwoord is succesvol gewijzigd'
        },
        passwordSet: {
            title: 'Wachtwoord ingesteld',
            detail: 'Uw wachtwoord is succesvol ingesteld'
        },
        passwordChangeFailed: 'Wachtwoord wijzigen mislukt',
        passwordSetFailed: 'Wachtwoord instellen mislukt',
        aiSaved: {
            title: 'Succes',
            detail: 'AI-instellingen succesvol opgeslagen'
        },
        aiError: 'Fout',
        immichUpdated: {
            title: 'Immich-instellingen bijgewerkt',
            detail: 'Uw Immich-integratie-instellingen zijn succesvol opgeslagen'
        },
        memosUpdated: {
            title: 'Memos-instellingen bijgewerkt',
            detail: 'Uw Memos-integratie-instellingen zijn succesvol opgeslagen'
        }
    },
    errors: {
        incorrectPassword: 'Huidig wachtwoord is onjuist',
        checkInformation: 'Controleer uw informatie en probeer het opnieuw'
    },
    cached: {
        title: 'Gecachete gegevens gebruiken',
        detail: 'Nieuwste profielgegevens konden niet worden opgehaald. Gecachete informatie wordt getoond.'
    }
}
