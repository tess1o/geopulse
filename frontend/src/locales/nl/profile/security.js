/**
 * Security tab: password form.
 *
 * 'Security' and 'Change password' are asserted verbatim by profileTabsDirtyState.test.js, so those
 * two values are a snapshot rather than a chance to reword.
 */
export default {
    title: 'Beveiliging',
    description: 'Beheer wachtwoorden, verbonden aanmeldingsmethoden en API-toegang.',
    changePasswordHeading: 'Wachtwoord wijzigen',
    setPasswordHeading: 'Wachtwoord instellen',
    changePasswordDescription: 'Werk het wachtwerk bij dat u gebruikt om in te loggen op uw account.',
    setPasswordDescription: 'Voeg een wachtwoord toe als aanmeldingsmethode voor uw account.',
    currentPassword: {
        title: 'Huidig wachtwoord',
        description: 'Bevestig uw bestaande wachtwoord.',
        placeholder: 'Voer huidig wachtwoord in'
    },
    newPassword: {
        title: 'Nieuw wachtwoord',
        description: 'Gebruik minstens zes tekens.',
        placeholder: 'Voer nieuw wachtwoord in'
    },
    confirmPassword: {
        title: 'Nieuw wachtwoord bevestigen',
        description: 'Voer hetzelfde nieuwe wachtwoord nogmaals in.',
        placeholder: 'Bevestig nieuw wachtwoord'
    },
    cancel: 'Annuleren',
    changePasswordSubmit: 'Wachtwoord wijzigen',
    setPasswordSubmit: 'Wachtwoord instellen',
    validation: {
        currentRequired: 'Huidig wachtwoord is verplicht',
        newRequired: 'Nieuw wachtwoord is verplicht',
        newTooShort: 'Wachtwoord moet minstens 6 tekens bevatten',
        confirmRequired: 'Bevestig alstublieft uw nieuwe wachtwoord',
        mismatch: 'Wachtwoorden komen niet overeen'
    }
}
