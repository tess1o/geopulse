package org.github.tess1o.geopulse.admin.model;

/**
 * Types of admin actions for audit logging.
 */
public enum ActionType {
    // Settings
    SETTING_CHANGED,
    SETTING_RESET,

    // User management
    USER_ENABLED,
    USER_DISABLED,
    USER_DELETED,
    USER_ROLE_CHANGED,
    USER_PASSWORD_RESET,

    // OIDC providers
    OIDC_PROVIDER_CREATED,
    OIDC_PROVIDER_UPDATED,
    OIDC_PROVIDER_DELETED,
    OIDC_PROVIDER_RESET,

    // Invitations
    INVITATION_CREATED,
    INVITATION_REVOKED,

    // Admin actions
    ADMIN_LOGIN
}
