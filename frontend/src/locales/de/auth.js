/**
 * Sign-in, registration, invitations, and the guest-facing entry pages (German).
 *
 * `validation.*` and `toasts.registrationDisabled*` are shared by the sign-in and registration forms.
 */
export default {
    validation: {
        emailRequired: 'E-Mail-Adresse ist erforderlich',
        emailInvalid: 'Bitte gib eine gültige E-Mail-Adresse ein',
        passwordRequired: 'Passwort ist erforderlich',
        confirmRequired: 'Bitte bestätige dein Passwort',
        mismatch: 'Die Passwörter stimmen nicht überein'
    },
    toasts: {
        registrationDisabled: 'Registrierung deaktiviert',
        registrationDisabledDetail: 'Neue Registrierungen sind derzeit deaktiviert. Wenn du schon ein Konto hast, melde dich an.'
    },
    oidc: {
        continueWith: 'Weiter mit {provider}',
        dividerDefault: 'Oder weiter mit',
        providerIconAlt: '{provider}-Symbol'
    },
    callback: {
        processingTitle: 'Anmeldung wird abgeschlossen …',
        processingDescription: 'Einen Moment, du wirst sicher angemeldet.',
        errorTitle: 'Anmeldung fehlgeschlagen',
        returnToLogin: 'Zurück zur Anmeldung',
        missingParams: 'Ungültige Callback-Anfrage. Erforderliche Parameter fehlen.',
        genericError: 'Bei der Anmeldung ist ein unbekannter Fehler aufgetreten.',
        toasts: {
            welcomeNew: 'Willkommen bei GeoPulse!',
            welcomeBack: 'Willkommen zurück!',
            authenticated: 'Erfolgreich angemeldet',
            linked: {
                title: 'Konten verknüpft!',
                detail: 'Deine Konten wurden verknüpft und du bist jetzt angemeldet.'
            }
        }
    },
    linking: {
        dialogHeader: 'Konto verknüpfen',
        title: 'Konto existiert bereits',
        description: 'Es gibt bereits ein Konto mit {email}. Bestätige deine Identität, dann verknüpfen wir dein {provider}-Konto damit.',
        verifyPassword: 'Mit Passwort bestätigen',
        linkAndContinue: 'Konto verknüpfen & weiter',
        verifyWithLinked: 'Mit verknüpftem Konto bestätigen',
        or: 'ODER',
        cancel: 'Abbrechen',
        errors: {
            passwordVerificationFailed: 'Passwort konnte nicht bestätigt werden'
        }
    },
    login: {
        disabledMessage: 'Die Anmeldung ist derzeit deaktiviert. Wende dich an deine Administration.',
        emailDisabledMessage: 'Die Anmeldung mit E-Mail und Passwort ist deaktiviert. Nutze einen der OIDC-Anbieter unten.',
        autoLaunchRedirecting: 'Weiterleitung zu {provider} …',
        adminAccess: 'Administratorzugang',
        adminBypassNotice: 'Administratorzugang – Anmeldebeschränkungen werden umgangen',
        demoTitle: 'GeoPulse ausprobieren',
        title: 'Willkommen zurück',
        demoSubtitle: 'Wähle ein Demo-Profil',
        subtitle: 'Melde dich an, um weiterzumachen',
        emailLabel: 'E-Mail-Adresse',
        emailPlaceholder: 'Deine E-Mail-Adresse',
        passwordLabel: 'Passwort',
        passwordPlaceholder: 'Dein Passwort',
        signIn: 'Anmelden',
        noAccount: 'Noch kein Konto?',
        createAccount: 'Konto erstellen',
        copyReferenceAria: 'Fehler-Referenz-ID kopieren',
        toasts: {
            signedIn: {
                title: 'Willkommen zurück!',
                detail: 'Du bist jetzt angemeldet'
            },
            demoFailed: 'Demo-Anmeldung fehlgeschlagen',
            loginFailed: 'Anmeldung fehlgeschlagen',
            optionsLoadFailed: 'Anmeldeoptionen konnten nicht geladen werden',
            optionsLoadFailedDetail: 'Externe Anmeldeanbieter konnten nicht abgerufen werden. Du kannst dich weiterhin mit E-Mail und Passwort anmelden.',
            oidcInitFailed: 'Anmeldung mit {provider} konnte nicht gestartet werden. Bitte versuch es noch einmal.'
        },
        errors: {
            invalidCredentials: 'E-Mail-Adresse oder Passwort ist falsch. Prüf deine Angaben und versuch es noch einmal.',
            lockedOut: 'Dein Konto ist gesperrt. Bitte wende dich an den Support.',
            tooManyAttempts: 'Zu viele Anmeldeversuche. Warte ein paar Minuten und versuch es dann noch einmal.',
            badFormat: 'Prüf das Format von E-Mail-Adresse und Passwort.',
            chooseProfile: 'Wähle ein Demo-Profil und versuch es noch einmal.',
            demoDisabled: 'Die Demo-Anmeldung ist derzeit deaktiviert.',
            profileUnavailable: 'Dieses Demo-Profil ist gerade nicht verfügbar.',
            demoGeneric: 'Demo-Anmeldung fehlgeschlagen. Bitte versuch es noch einmal.'
        }
    },
    register: {
        disabledMessage: 'Die Registrierung mit E-Mail und Passwort ist derzeit deaktiviert. Registriere dich über OIDC.',
        disabledMessageShort: 'Die Registrierung mit E-Mail und Passwort ist derzeit deaktiviert.',
        title: 'Konto erstellen',
        subtitle: 'Registriere dich bei GeoPulse und behalte deine Wege im Blick',
        emailLabel: 'E-Mail-Adresse',
        fullNameLabel: 'Vollständiger Name',
        passwordLabel: 'Passwort',
        confirmPasswordLabel: 'Passwort bestätigen',
        emailPlaceholder: 'Deine E-Mail-Adresse',
        fullNamePlaceholder: 'Dein vollständiger Name',
        passwordPlaceholder: 'Passwort festlegen',
        confirmPasswordPlaceholder: 'Passwort wiederholen',
        createAccount: 'Konto erstellen',
        alreadyHaveAccount: 'Schon ein Konto?',
        signIn: 'Anmelden',
        localisationNote: 'Automatisch aus deinem Browser übernommen. Du kannst das später im Profil ändern.',
        timezoneLabel: 'Zeitzone',
        validation: {
            fullNameRequired: 'Name ist erforderlich',
            fullNameTooShort: 'Der Name muss mindestens 2 Zeichen lang sein',
            passwordTooShort: 'Das Passwort muss mindestens 6 Zeichen lang sein'
        },
        toasts: {
            created: {
                title: 'Willkommen bei GeoPulse!',
                detail: 'Dein Konto wurde erstellt'
            },
            failed: 'Registrierung fehlgeschlagen',
            optionsLoadFailed: 'Registrierungsoptionen konnten nicht geladen werden',
            optionsLoadFailedDetail: 'Die Registrierungseinstellungen konnten nicht abgerufen werden.',
            oidcInitFailed: 'Registrierung mit {provider} konnte nicht gestartet werden. Bitte versuch es noch einmal.',
            // The account exists but the automatic sign-in after it did not go through.
            signInRequired: {
                title: 'Konto erstellt',
                detail: 'Dein Konto ist bereit. Bitte melde dich an, um weiterzumachen.'
            }
        },
        errors: {
            emailExists: 'Es gibt bereits ein Konto mit dieser E-Mail-Adresse',
            checkInformation: 'Prüf deine Angaben und versuch es noch einmal',
            serverError: 'Serverfehler. Bitte versuch es später noch einmal',
            failed: 'Registrierung fehlgeschlagen. Bitte versuch es noch einmal.',
            emailTooLong: 'Die E-Mail-Adresse ist zu lang (höchstens {max} Zeichen)',
            fullNameLength: 'Der Name muss zwischen {min} und {max} Zeichen lang sein',
            passwordLength: 'Das Passwort muss zwischen {min} und {max} Zeichen lang sein'
        }
    },
    invitation: {
        brand: 'GeoPulse',
        title: 'Registrierung abschließen',
        validating: 'Einladung wird geprüft …',
        invalidTitle: 'Ungültige Einladung',
        invalidMessage: 'Diese Einladung ist nicht gültig',
        emailLabel: 'E-Mail-Adresse',
        fullNameLabel: 'Vollständiger Name',
        passwordLabel: 'Passwort',
        confirmPasswordLabel: 'Passwort bestätigen',
        timezoneLabel: 'Zeitzone',
        emailPlaceholder: 'Deine E-Mail-Adresse',
        fullNamePlaceholder: 'Dein vollständiger Name',
        passwordPlaceholder: 'Passwort eingeben',
        confirmPasswordPlaceholder: 'Passwort wiederholen',
        createAccount: 'Konto erstellen',
        goToLogin: 'Zur Anmeldung',
        timezoneDetected: 'Erkannt: {timezone}',
        loginLink: 'Schon ein Konto? Anmelden',
        validation: {
            emailInvalid: 'Ungültiges E-Mail-Format',
            passwordTooShort: 'Das Passwort muss mindestens 3 Zeichen lang sein'
        },
        toasts: {
            created: {
                title: 'Willkommen bei GeoPulse!',
                detail: 'Dein Konto wurde erstellt'
            }
        },
        // Why an invitation can no longer be used, keyed by the backend's invitation status.
        status: {
            used: 'Diese Einladung wurde bereits verwendet',
            expired: 'Diese Einladung ist abgelaufen',
            revoked: 'Diese Einladung wurde widerrufen'
        },
        errors: {
            validateFailed: 'Einladung konnte nicht geprüft werden',
            notFound: 'Dieser Einladungslink ist nicht gültig. Bitte deine Administration um einen neuen.'
        }
    }
}
