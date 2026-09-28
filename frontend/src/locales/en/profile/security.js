/**
 * Security tab: password form.
 *
 * 'Security' and 'Change password' are asserted verbatim by profileTabsDirtyState.test.js, so those
 * two values are a snapshot rather than a chance to reword.
 */
export default {
    title: 'Security',
    description: 'Manage passwords, connected sign-in methods, and API access.',
    changePasswordHeading: 'Change password',
    setPasswordHeading: 'Set password',
    changePasswordDescription: 'Update the password used to sign in to your account.',
    setPasswordDescription: 'Add a password as a sign-in method for your account.',
    currentPassword: {
        title: 'Current password',
        description: 'Confirm your existing password.',
        placeholder: 'Enter current password'
    },
    newPassword: {
        title: 'New password',
        description: 'Use at least six characters.',
        placeholder: 'Enter new password'
    },
    confirmPassword: {
        title: 'Confirm new password',
        description: 'Enter the same new password again.',
        placeholder: 'Confirm new password'
    },
    cancel: 'Cancel',
    changePasswordSubmit: 'Change Password',
    setPasswordSubmit: 'Set Password',
    validation: {
        currentRequired: 'Current password is required',
        newRequired: 'New password is required',
        newTooShort: 'Password must be at least 6 characters',
        confirmRequired: 'Please confirm your new password',
        mismatch: 'Passwords do not match'
    }
}
