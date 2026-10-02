/**
 * Admin pages: timeline regeneration campaigns, user details.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    campaignsPage: {
        breadcrumb: {
            title: 'Timeline Regeneration'
        },
        header: {
            title: 'Timeline Regeneration',
            subtitle: 'Create and monitor forced timeline regeneration campaigns'
        },
        createCampaign: 'Create Campaign',
        labels: {
            campaign: 'Campaign',
            source: 'Source',
            status: 'Status',
            affectedFrom: 'Affected From',
            progress: 'Progress',
            reason: 'Reason',
            affectedUsers: 'Affected Users',
            total: 'Total',
            pending: 'Pending',
            running: 'Running',
            completed: 'Completed',
            failed: 'Failed',
            skipped: 'Skipped',
            created: 'Created',
            updated: 'Updated',
            actions: 'Actions',
            done: 'Done'
        },
        table: {
            currentPageReport: 'Showing {first} to {last} of {totalRecords} campaigns',
            empty: 'No timeline regeneration campaigns found.',
            retryTooltip: 'Retry Failed Users',
            progressText: '{processed} / {total} processed',
            pendingCount: '{count} pending',
            runningCount: '{count} running',
            doneCount: '{count} done'
        },
        mobile: {
            detailsButton: 'Details',
            retryButton: 'Retry Failed'
        },
        createDialog: {
            header: 'Create Timeline Regeneration Campaign',
            campaignKeyLabel: 'Campaign Key',
            campaignKeyPlaceholder: 'july-12-timeline-repair',
            regenerateFromLabel: 'Regenerate From',
            regenerateFromPlaceholder: 'Select cutoff date and time',
            reasonLabel: 'Reason',
            reasonPlaceholder: 'Explain why timelines must be regenerated. Users will see this message.',
            previewRequired: 'Preview required',
            previewHelp: 'Preview counts users with GPS data at or after the selected timestamp.',
            runPreview: 'Run Preview',
            reviewCreate: 'Review Create'
        },
        confirmDialog: {
            header: 'Confirm Timeline Regeneration'
        },
        detailsDialog: {
            header: 'Timeline Regeneration Details',
            failedUsersTitle: 'Failed Users',
            retryButton: 'Retry Failed',
            table: {
                email: 'Email',
                attempts: 'Attempts',
                lastError: 'Last Error',
                empty: 'No failed users.'
            },
            close: 'Close'
        },
        toasts: {
            loadFailed: 'Failed to load timeline regeneration campaigns',
            previewFailedSummary: 'Preview Failed',
            previewFailedDetail: 'Failed to preview affected users',
            createdSummary: 'Campaign Created',
            createdDetail: 'Timeline regeneration campaign was created.',
            createFailedSummary: 'Create Failed',
            createFailedDetail: 'Failed to create timeline regeneration campaign',
            detailsLoadFailed: 'Failed to load campaign details',
            retryQueuedSummary: 'Retry Queued',
            retryQueuedDetail: 'Failed campaign users were queued for retry.',
            retryFailedSummary: 'Retry Failed',
            retryFailedDetail: 'Failed to retry campaign users'
        }
    },
    userDetailsPage: {
        title: 'User Details',
        breadcrumb: {
            users: 'Users',
            loading: 'Loading...',
            notFound: 'User Not Found'
        },
        noName: 'No name',
        never: 'Never',
        infoCard: {
            title: 'User Information',
            authentication: 'Authentication',
            password: 'Password',
            oidcOnly: 'OIDC only',
            timezone: 'Timezone',
            linkedOidcProviders: 'Linked OIDC Providers'
        },
        statsCard: {
            title: 'Activity & Statistics',
            gpsPoints: 'GPS Points',
            lastGpsPoint: 'Last GPS Point',
            accountCreated: 'Account Created',
            lastUpdated: 'Last Updated'
        },
        actionsCard: {
            title: 'Administrative Actions',
            demoteToUser: 'Demote to User',
            promoteToAdmin: 'Promote to Admin',
            resetPassword: 'Reset Password',
            warningMessage: 'You cannot disable or delete your own account.'
        },
        apiTokensCard: {
            title: 'API Tokens',
            columnName: 'Name',
            columnStatus: 'Status',
            columnExpires: 'Expires',
            columnLastUsed: 'Last Used',
            columnActions: 'Actions',
            revokeTooltip: 'Revoke token',
            empty: 'No API tokens found.',
            status: {
                active: 'Active',
                expired: 'Expired',
                revoked: 'Revoked'
            }
        },
        notFound: {
            message: 'User not found',
            backButton: 'Back to Users'
        },
        deleteDialog: {
            message: 'Are you sure you want to delete user {email}?',
            detail: 'This will permanently delete all their data.'
        },
        passwordDialog: {
            header: 'Password Reset',
            message: 'Temporary password for {email}:',
            copyHint: 'Share this password with the user securely.',
            close: 'Close'
        },
        revokeDialog: {
            header: 'Revoke API Token',
            message: 'Revoke {tokenName} for {email}? Automation using this token will stop immediately.',
            revokeButton: 'Revoke'
        },
        toasts: {
            loadUserFailed: 'Failed to load user details',
            loadTokensFailed: 'Failed to load API tokens',
            roleChanged: 'User role changed to {role}',
            roleChangeFailed: 'Failed to change user role',
            passwordResetSuccess: 'Password reset successfully',
            passwordResetFailed: 'Failed to reset password',
            passwordCopied: 'Password copied to clipboard',
            passwordCopyFailed: 'Failed to copy password to clipboard',
            tokenRevokedSummary: 'Revoked',
            tokenRevokedDetail: 'API token revoked',
            tokenRevokeFailed: 'Failed to revoke API token'
        }
    }
}
