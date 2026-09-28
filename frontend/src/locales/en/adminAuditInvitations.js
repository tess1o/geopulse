/**
 * Admin pages: audit logs, user invitations, OIDC providers management.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    auditLogsPage: {
        title: 'Audit Logs',
        subtitle: 'Review administrative actions and system changes',
        filters: {
            dateRangeLabel: 'Date Range',
            dateRangePlaceholder: 'Select date range',
            actionTypeLabel: 'Action Type',
            actionTypePlaceholder: 'All Actions',
            targetTypeLabel: 'Target Type',
            targetTypePlaceholder: 'All Targets',
            adminEmailLabel: 'Admin Email',
            adminEmailPlaceholder: 'Filter by admin email...',
            clearFilters: 'Clear Filters'
        },
        actionTypes: {
            settingChanged: 'Setting Changed',
            settingReset: 'Setting Reset',
            userEnabled: 'User Enabled',
            userDisabled: 'User Disabled',
            userDeleted: 'User Deleted',
            userRoleChanged: 'User Role Changed',
            userPasswordReset: 'User Password Reset',
            oidcProviderCreated: 'OIDC Provider Created',
            oidcProviderUpdated: 'OIDC Provider Updated',
            oidcProviderDeleted: 'OIDC Provider Deleted',
            oidcProviderReset: 'OIDC Provider Reset',
            invitationCreated: 'Invitation Created',
            invitationRevoked: 'Invitation Revoked',
            timelineRegenerationCampaignCreated: 'Timeline Regeneration Campaign Created',
            timelineRegenerationCampaignRetried: 'Timeline Regeneration Campaign Retried',
            adminLogin: 'Admin Login'
        },
        targetTypes: {
            setting: 'Setting',
            user: 'User',
            oidcProvider: 'OIDC Provider',
            invitation: 'Invitation',
            timelineRegenerationCampaign: 'Timeline Regeneration Campaign'
        },
        table: {
            currentPageReport: 'Showing {first} to {last} of {totalRecords} audit logs',
            columns: {
                timestamp: 'Timestamp',
                admin: 'Admin',
                action: 'Action',
                targetType: 'Target Type',
                targetId: 'Target ID',
                ipAddress: 'IP Address'
            },
            empty: 'No audit logs found.'
        },
        expansion: {
            detailsHeader: 'Details',
            noDetails: 'No additional details available',
            adminUserId: 'Admin User ID:',
            timestampLabel: 'Timestamp:'
        },
        mobile: {
            targetId: 'Target ID:',
            ipAddress: 'IP Address:',
            detailsLabel: 'Details'
        },
        toasts: {
            loadFailedDetail: 'Failed to load audit logs'
        }
    },
    invitationsPage: {
        title: 'User Invitations',
        subtitle: 'Manage user invitation links',
        breadcrumb: 'Invitations',
        createInvitation: 'Create Invitation',
        statusFilterPlaceholder: 'Filter by status',
        statuses: {
            all: 'All Statuses',
            pending: 'Pending',
            used: 'Used',
            expired: 'Expired',
            revoked: 'Revoked'
        },
        usedByDeletedUser: 'Deleted user',
        table: {
            currentPageReport: 'Showing {first} to {last} of {totalRecords} invitations',
            columns: {
                token: 'Token',
                createdBy: 'Created By',
                created: 'Created',
                expires: 'Expires',
                status: 'Status',
                usedBy: 'Used By',
                actions: 'Actions'
            },
            empty: 'No invitations found.',
            copyLinkTooltip: 'Copy Link',
            revokeTooltip: 'Revoke Invitation'
        },
        mobile: {
            usedByPrefix: 'Used by: {value}',
            copyLink: 'Copy Link',
            revoke: 'Revoke'
        },
        createDialog: {
            expirationDateLabel: 'Expiration Date',
            expirationDatePlaceholder: 'Select expiration date'
        },
        linkDialog: {
            header: 'Invitation Link Created',
            shareText: 'Share this link with the user you want to invite:',
            copyTooltip: 'Copy to clipboard',
            expiresLabel: 'Expires: {date}',
            message: 'This link can only be used once and will expire on the date shown above.',
            close: 'Close'
        },
        revokeDialog: {
            header: 'Confirm Revoke',
            message: 'Are you sure you want to revoke this invitation?',
            note: 'The invitation link will no longer be usable.',
            confirm: 'Revoke'
        },
        toasts: {
            loadFailedDetail: 'Failed to load invitations',
            createdDetail: 'Invitation created successfully',
            createFailedFallback: 'Failed to create invitation',
            copiedDetail: 'Link copied to clipboard',
            copyFailedDetail: 'Failed to copy to clipboard',
            revokedDetail: 'Invitation revoked successfully',
            revokeFailedFallback: 'Failed to revoke invitation'
        }
    },
    oidcProvidersPage: {
        title: 'OIDC Providers',
        subtitle: 'Manage OAuth/OIDC authentication providers',
        addProvider: 'Add Provider',
        table: {
            headerTitle: 'Configured Providers',
            columns: {
                name: 'Name',
                displayName: 'Display Name',
                enabled: 'Enabled',
                source: 'Source',
                metadata: 'Metadata',
                clientId: 'Client ID',
                actions: 'Actions'
            },
            enabledYes: 'Yes',
            enabledNo: 'No',
            sourceEnvironment: 'Environment',
            sourceCustom: 'Custom',
            metadataCached: 'Cached',
            metadataNotCached: 'Not Cached',
            empty: 'No OIDC providers configured.',
            editTooltip: 'Edit Provider',
            disableTooltip: 'Disable Provider',
            enableTooltip: 'Enable Provider',
            testTooltip: 'Test Connection',
            cannotDeleteTooltip: 'Environment-only providers cannot be deleted. Remove from env vars to delete.',
            revertTooltip: 'Delete custom config and revert to environment defaults',
            deleteTooltip: 'Permanently delete this custom provider'
        },
        mobile: {
            enabledBadge: 'Enabled',
            disabledBadge: 'Disabled',
            edit: 'Edit',
            disable: 'Disable',
            enable: 'Enable',
            test: 'Test',
            delete: 'Delete'
        },
        deleteDialog: {
            header: 'Confirm Delete',
            confirmMessage: 'Are you sure you want to delete provider {name}?',
            envNoteLabel: 'ℹ️ Note:',
            envNoteText: 'This provider is also defined in environment variables.',
            envNoteDetail: 'Deleting will remove the custom database configuration and revert to environment defaults.',
            customWarningLabel: '⚠️ Warning:',
            customWarningText: 'This is a custom provider. Deletion is permanent.',
            revertButton: 'Revert to Environment',
            deleteButton: 'Delete'
        },
        testResultDialog: {
            header: 'Provider Test Result',
            successMessage: 'Successfully connected to OIDC provider',
            endpointsTitle: 'Discovered Endpoints:',
            authorization: 'Authorization:',
            token: 'Token:',
            userinfo: 'UserInfo:',
            jwks: 'JWKS:',
            issuer: 'Issuer:',
            errorDetailsTitle: 'Error Details:',
            errorType: 'Type:',
            errorDetailsLabel: 'Details:',
            close: 'Close'
        },
        toasts: {
            loadFailedDetail: 'Failed to load OIDC providers',
            updatedDetail: 'Provider updated successfully',
            createdDetail: 'Provider created successfully',
            saveFailedFallback: 'Failed to save provider',
            revertedDetail: 'Custom configuration removed. Provider reverted to environment defaults.',
            deletedDetail: 'Provider deleted successfully',
            deleteFailedFallback: 'Failed to delete provider',
            connectionSuccessSummary: 'Connection Successful',
            connectionSuccessDetail: 'Provider connection tested successfully',
            connectionFailedSummary: 'Connection Failed',
            connectionFailedDetail: 'Failed to connect to provider',
            testErrorDetail: 'Failed to test provider connection',
            statusEnabledDetail: 'Provider enabled successfully',
            statusDisabledDetail: 'Provider disabled successfully',
            updateStatusFailedFallback: 'Failed to update provider status'
        }
    }
}
