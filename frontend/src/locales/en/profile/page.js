/**
 * Personal settings page (UserProfilePage.vue): page header, tab navigation, save toasts, the
 * unsaved-changes confirmation, and the errors raised by the save handlers.
 *
 * EN values are verbatim from the previous literals.
 */
export default {
    page: {
        title: 'Personal Settings',
        description: 'Manage your account, preferences, and connected apps',
        signedInAs: 'Signed in as {email}'
    },
    searchPlaceholder: 'Search profile settings...',
    sectionsAria: 'Personal settings sections',
    mobileSectionLabel: 'Settings section',
    demoReadOnly: 'Demo mode: profile, security, display, AI, Immich, and Memos settings are read-only. Changes cannot be saved in this demo.',
    demoReadOnlyToast: 'Profile changes are disabled in demo mode.',
    groups: {
        personal: 'Personal',
        experience: 'Experience',
        connectedApps: 'Connected Apps'
    },
    tabs: {
        general: 'General',
        security: 'Security',
        timeline: 'Timeline & Map',
        appearance: 'Appearance',
        notifications: 'Notifications',
        connectedApps: 'Connected Apps'
    },
    jump: {
        notVisibleTitle: 'Setting not visible',
        notVisibleDetail: 'This setting is not currently visible. Enable related options to edit it.'
    },
    unsaved: {
        header: 'Unsaved Changes',
        message: 'You have unsaved profile changes. If you leave this page, those changes will be lost.',
        leave: 'Leave without saving',
        stay: 'Stay'
    },
    save: {
        updateFailed: 'Update Failed',
        saveFailed: 'Save Failed',
        notificationsSaved: 'Notification preferences saved',
        profileUpdated: {
            title: 'Profile Updated',
            detail: 'Your profile has been updated successfully'
        },
        displayUpdated: {
            title: 'Display Settings Updated',
            detail: 'Your timeline display preferences have been saved. Changes are visible immediately.'
        },
        passwordChanged: {
            title: 'Password Changed',
            detail: 'Your password has been changed successfully'
        },
        passwordSet: {
            title: 'Password Set',
            detail: 'Your password has been set successfully'
        },
        passwordChangeFailed: 'Password Change Failed',
        passwordSetFailed: 'Password Set Failed',
        aiSaved: {
            title: 'Success',
            detail: 'AI settings saved successfully'
        },
        aiError: 'Error',
        immichUpdated: {
            title: 'Immich Settings Updated',
            detail: 'Your Immich integration settings have been saved successfully'
        },
        memosUpdated: {
            title: 'Memos Settings Updated',
            detail: 'Your Memos integration settings have been saved successfully'
        }
    },
    errors: {
        incorrectPassword: 'Current password is incorrect',
        checkInformation: 'Please check your information and try again'
    },
    cached: {
        title: 'Using Cached Data',
        detail: 'Unable to fetch latest profile data. Showing cached information.'
    }
}
