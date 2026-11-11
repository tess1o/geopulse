/**
 * Sign-in, registration, invitations, and the guest-facing entry pages.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy.
 *
 * `validation.*` and `toasts.registrationDisabled*` are shared by the sign-in and registration forms,
 * which showed the same sentences. `shareNote`-style duplication is avoided; update both call sites
 * if either form\'s wording needs to diverge.
 *
 * The clipboard toasts reuse `common.clipboard.*`, which already held those exact sentences.
 */
export default {
    validation: {
        emailRequired: 'E-mailadres is verplicht',
        emailInvalid: 'Voer een geldig e-mailadres in',
        passwordRequired: 'Wachtwoord is verplicht',
        confirmRequired: 'Bevestig uw wachtwoord',
        mismatch: 'Wachtwoorden komen niet overeen'
    },
    toasts: {
        registrationDisabled: 'Registratie uitgeschakeld',
        registrationDisabledDetail: 'Nieuwe gebruikersregistratie is momenteel uitgeschakeld. Log in als u al een account heeft.'
    },
    oidc: {
        continueWith: 'Doorgaan met {provider}',
        dividerDefault: 'Of doorgaan met',
        providerIconAlt: '{provider} pictogram'
    },
    callback: {
        processingTitle: 'Authenticatie afronden...',
        processingDescription: 'Even geduld terwijl we u veilig aanmelden.',
        errorTitle: 'Authenticatie mislukt',
        returnToLogin: 'Terug naar inloggen',
        missingParams: 'Ongeldig callback-verzoek. Vereiste parameters ontbreken.',
        genericError: 'Er is een onbekende authenticatiefout opgetreden.',
        toasts: {
            welcomeNew: 'Welkom bij GeoPulse!',
            welcomeBack: 'Welkom terug!',
            authenticated: 'Succesvol geauthenticeerd',
            linked: {
                title: 'Account succesvol gekoppeld!',
                detail: 'Uw accounts zijn gekoppeld en u bent nu aangemeld.'
            }
        }
    },
    linking: {
        dialogHeader: 'Uw account koppelen',
        title: 'Account bestaat al',
        description: 'Er bestaat al een account met {email}. Om door te gaan verifieert u uw identiteit en koppelen we uw {provider}-account.',
        verifyPassword: 'Verifieer met wachtwoord',
        linkAndContinue: 'Account koppelen & doorgaan',
        verifyWithLinked: 'Verifieer met gekoppeld account',
        or: 'OF',
        cancel: 'Annuleren',
        errors: {
            passwordVerificationFailed: 'Wachtwoordverificatie mislukt'
        }
    },
    login: {
        disabledMessage: 'Inloggen is momenteel uitgeschakeld. Neem contact op met uw beheerder.',
        emailDisabledMessage: 'E-mail/wachtwoord-inloggen is uitgeschakeld. Gebruik de OIDC-providers hieronder.',
        autoLaunchRedirecting: 'Doorverwijzen naar {provider}…',
        adminAccess: 'Beheerders toegang',
        adminBypassNotice: 'Beheerders toegang - inlogbeperkingen overgeslagen',
        demoTitle: 'Probeer GeoPulse',
        title: 'Welkom terug',
        demoSubtitle: 'Kies een demo-profiel',
        subtitle: 'Meld u aan om uw reis voort te zetten',
        emailLabel: 'E-mailadres',
        emailPlaceholder: 'Voer uw e-mailadres in',
        passwordLabel: 'Wachtwoord',
        passwordPlaceholder: 'Voer uw wachtwoord in',
        signIn: 'Aanmelden',
        noAccount: "Heeft u geen account?",
        createAccount: 'Account maken',
        copyReferenceAria: 'Foutreferentie-id kopiëren',
        toasts: {
            signedIn: {
                title: 'Welkom terug!',
                detail: 'U bent succesvol aangemeld'
            },
            demoFailed: 'Demo-inloggen mislukt',
            loginFailed: 'Inloggen mislukt',
            optionsLoadFailed: 'Kon inlogopties niet laden',
            optionsLoadFailedDetail: 'Het ophalen van externe inlogproviders is mislukt. U kunt nog steeds inloggen met e-mail en wachtwoord.',
            oidcInitFailed: 'Inloggen met {provider} initialiseren is mislukt. Probeer het opnieuw.'
        },
        errors: {
            invalidCredentials: 'Ongeldig e-mailadres of wachtwoord. Controleer uw inloggegevens en probeer het opnieuw.',
            lockedOut: 'Uw account is vergrendeld of geschorst. Neem contact op met de ondersteuning.',
            tooManyAttempts: 'Teveel inlogpogingen. Wacht een paar minuten voordat u het opnieuw probeert.',
            badFormat: 'Controleer het formaat van uw e-mailadres en wachtwoord.',
            chooseProfile: 'Kies een demo-profiel en probeer het opnieuw.',
            demoDisabled: 'Demo-inloggen is momenteel uitgeschakeld.',
            profileUnavailable: 'Dit demo-profiel is momenteel niet beschikbaar.',
            demoGeneric: 'Demo-inloggen is mislukt. Probeer het opnieuw.'
        }
    },
    register: {
        disabledMessage: 'Registreren met e-mail/wachtwoord is momenteel uitgeschakeld. Gebruik OIDC voor registratie',
        disabledMessageShort: 'Registreren met e-mail/wachtwoord is momenteel uitgeschakeld.',
        title: 'Account maken',
        subtitle: 'Sluit u aan bij GeoPulse om uw locatiereis te beginnen te volgen en analyseren',
        emailLabel: 'E-mailadres',
        fullNameLabel: 'Volledige naam',
        passwordLabel: 'Wachtwoord',
        confirmPasswordLabel: 'Wachtwoord bevestigen',
        emailPlaceholder: 'Voer uw e-mailadres in',
        fullNamePlaceholder: 'Voer uw volledige naam in',
        passwordPlaceholder: 'Maak een wachtwoord',
        confirmPasswordPlaceholder: 'Bevestig uw wachtwoord',
        createAccount: 'Account maken',
        alreadyHaveAccount: 'Heeft u al een account?',
        signIn: 'Aanmelden',
        localisationNote: 'Automatisch gedetecteerd vanuit uw browser. U kunt dit later wijzigen in uw profiel.',
        timezoneLabel: 'Tijdzone',
        validation: {
            fullNameRequired: 'Volledige naam is verplicht',
            fullNameTooShort: 'Volledige naam moet minstens 2 tekens bevatten',
            passwordTooShort: 'Wachtwoord moet minstens 6 tekens bevatten'
        },
        toasts: {
            created: {
                title: 'Welkom bij GeoPulse!',
                detail: 'Uw account is succesvol aangemaakt'
            },
            failed: 'Registratie mislukt',
            optionsLoadFailed: 'Kon registratieopties niet laden',
            optionsLoadFailedDetail: 'Het ophalen van de registratieconfiguratie is mislukt.',
            oidcInitFailed: 'Registratie met {provider} initialiseren is mislukt. Probeer het opnieuw.',
            // The account exists but the automatic sign-in after it did not go through.
            signInRequired: {
                title: 'Account aangemaakt',
                detail: 'Uw account is klaar. Meld u aan om door te gaan.'
            }
        },
        errors: {
            emailExists: 'Er bestaat al een account met dit e-mailadres',
            checkInformation: 'Controleer uw gegevens en probeer het opnieuw',
            serverError: 'Serverfout. Probeer het later opnieuw',
            failed: 'Registratie is mislukt. Probeer het opnieuw.',
            emailTooLong: 'E-mailadres is te lang (maximaal {max} tekens)',
            fullNameLength: 'Volledige naam moet tussen {min} en {max} tekens bevatten',
            passwordLength: 'Wachtwoord moet tussen {min} en {max} tekens bevatten'
        }
    },
    invitation: {
        brand: 'GeoPulse',
        title: 'Uw registratie voltooien',
        validating: 'Uitnodiging valideren...',
        invalidTitle: 'Ongeldige uitnodiging',
        invalidMessage: 'Deze uitnodiging is niet geldig',
        emailLabel: 'E-mail',
        fullNameLabel: 'Volledige naam',
        passwordLabel: 'Wachtwoord',
        confirmPasswordLabel: 'Wachtwoord bevestigen',
        timezoneLabel: 'Tijdzone',
        emailPlaceholder: 'Voer uw e-mailadres in',
        fullNamePlaceholder: 'Voer uw volledige naam in',
        passwordPlaceholder: 'Voer wachtwoord in',
        confirmPasswordPlaceholder: 'Bevestig wachtwoord',
        createAccount: 'Account maken',
        goToLogin: 'Ga naar inloggen',
        timezoneDetected: 'Gedetecteerd: {timezone}',
        loginLink: 'Heeft u al een account? Aanmelden',
        validation: {
            emailInvalid: 'Ongeldig e-mailformaat',
            passwordTooShort: 'Wachtwoord moet minstens 3 tekens bevatten'
        },
        toasts: {
            created: {
                title: 'Welkom bij GeoPulse!',
                detail: 'Uw account is succesvol aangemaakt'
            }
        },
        // Why an invitation can no longer be used, keyed by the backend\'s invitation status.
        status: {
            used: 'Deze uitnodiging is al gebruikt',
            expired: 'Deze uitnodiging is verlopen',
            revoked: 'Deze uitnodiging is ingetrokken'
        },
        errors: {
            validateFailed: 'Uitnodiging valideren is mislukt',
            notFound: 'Deze uitnodigingslink is niet geldig. Vraag uw beheerder om een nieuwe.'
        }
    }
}

