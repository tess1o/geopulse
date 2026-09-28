/**
 * Security tab's access surfaces: OIDC sign-in providers and API tokens.
 *
 * EN values are verbatim from the previous literals.
 *
 * Toast pairs follow the page catalog's shape: a summary/detail pair is nested as `.title`/`.detail`,
 * while a summary shared by several toasts stays a flat key. 'OIDC', 'MCP' and 'API' stay as proper
 * nouns in every locale.
 */
export default {
    oidc: {
        heading: 'Connected accounts',
        description: 'Manage social or corporate OIDC sign-in methods.',
        linkedHeading: 'Linked accounts',
        // Screen-reader text for the provider avatar, e.g. 'GitHub icon'.
        providerIconAlt: '{provider} icon',
        unlink: 'Unlink',
        tooltips: {
            demoDisabled: 'Disabled in demo mode',
            unlinkBlocked: 'Cannot unlink the only authentication method without a password set.',
            unlink: 'Unlink this account'
        },
        linkHeading: 'Link another account',
        link: 'Link',
        noPasswordWarning: 'You have no password set. You must add another login method before unlinking your only connected account.',
        confirm: {
            header: 'Confirm Unlink',
            message: 'Are you sure you want to unlink your {provider} account? This action cannot be undone.'
        },
        toasts: {
            // Summary shared by the load failure below.
            error: 'Error',
            loadFailed: 'Could not load connected account information.',
            linkFailed: {
                title: 'Link Failed',
                detail: 'Failed to initiate linking for {provider}'
            },
            unlinked: {
                title: 'Account Unlinked',
                detail: 'Successfully unlinked your {provider} account.'
            },
            unlinkFailed: {
                title: 'Unlink Failed',
                detail: 'Failed to unlink account'
            }
        }
    },
    apiTokens: {
        heading: 'API tokens',
        description: 'Create named tokens for bots, MCP clients, and automation.',
        create: 'Create Token',
        columns: {
            name: 'Name',
            status: 'Status',
            expires: 'Expires',
            lastUsed: 'Last Used',
            actions: 'Actions'
        },
        // Stands in for a missing expiry or last-used timestamp in the table.
        never: 'Never',
        // Backend `TokenStatus` values rendered as a label. The enum itself is compared in code and is
        // never translated.
        status: {
            active: 'Active',
            expired: 'Expired',
            revoked: 'Revoked'
        },
        tooltips: {
            edit: 'Edit token',
            revoke: 'Revoke token'
        },
        empty: 'No API tokens created.',
        // Shared by the edit and revoke dialogs.
        cancel: 'Cancel',
        editDialog: {
            editHeader: 'Edit API Token',
            createHeader: 'Create API Token',
            nameLabel: 'Name',
            namePlaceholder: 'Automation token',
            expirationLabel: 'Expiration',
            expirationPlaceholder: 'No expiration',
            expirationHint: 'Leave empty for no expiration.',
            save: 'Save',
            create: 'Create'
        },
        createdDialog: {
            header: 'API Token Created',
            message: 'This token is shown once. Store it securely before closing this dialog.',
            confirm: 'I have stored this token'
        },
        revokeDialog: {
            header: 'Revoke API Token',
            // The token name sits between the two halves inside <strong>, so the sentence is split
            // around that element rather than interpolated -- see the template.
            confirmPrefix: 'Revoke ',
            confirmSuffix: '? Automation using this token will stop immediately.',
            confirm: 'Revoke'
        },
        validation: {
            nameRequired: 'Token name is required'
        },
        toasts: {
            // Summary shared by the failure toasts below.
            error: 'Error',
            loadFailed: 'Failed to load API tokens',
            saveFailed: 'Failed to save API token',
            revokeFailed: 'Failed to revoke API token',
            saved: {
                title: 'Saved',
                detail: 'API token updated'
            },
            created: {
                title: 'Created',
                detail: 'API token created'
            },
            revoked: {
                title: 'Revoked',
                detail: 'API token revoked'
            },
            // These two mirror `common.clipboard.copied`/`copyFailed`; the detail lines name the token,
            // so they stay here rather than in the shared block.
            copied: {
                title: 'Copied',
                detail: 'Token copied to clipboard'
            },
            copyFailed: {
                title: 'Copy failed',
                detail: 'Select the token and copy it manually'
            }
        }
    }
}
