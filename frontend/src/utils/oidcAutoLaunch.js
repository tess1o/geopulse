/**
 * OIDC auto-launch: with password login off and a single provider, the login page starts that
 * provider's sign-in on its own. The provider often still has a session, so launching it at the
 * wrong moment signs the user straight back in. These rules decide when the page must show the
 * button instead.
 *
 * - Signed out on purpose (any tab): suppressed until the next successful sign-in, unless the user
 *   pressed a "Sign in" link (SIGN_IN_LOCATION): asking to sign in is not an accidental launch.
 *   Shared through localStorage so a tab that hits an expired session after a logout elsewhere does
 *   not undo it.
 * - Already launched in this tab without signing in (the user came back from the provider, e.g. with
 *   the Back button): suppressed for this tab until the next successful sign-in.
 * - `?autoLaunch=0` (admin break-glass, "Return to login" after an error) or any `reason`/`error`
 *   query: suppressed for that visit.
 * - Storage unreadable: suppressed. A failure costs one click, never a redirect loop.
 */

const SIGNED_OUT_KEY = 'oidcAutoLaunchSignedOut'
const ATTEMPTED_KEY = 'oidcAutoLaunchAttempted'

export const AUTO_LAUNCH_OFF_QUERY = { autoLaunch: '0' }

/** Where explicit "Sign in" links go: launches the provider even right after a deliberate sign-out. */
export const SIGN_IN_LOCATION = { path: '/login', query: { autoLaunch: '1' } }

const readFlag = (storage, key) => {
    try {
        return storage.getItem(key) !== null
    } catch (error) {
        console.warn('[oidcAutoLaunch] Failed to read storage:', error)
        return null
    }
}

const writeFlag = (storage, key, value) => {
    try {
        if (value) {
            storage.setItem(key, '1')
        } else {
            storage.removeItem(key)
        }
    } catch (error) {
        console.warn('[oidcAutoLaunch] Failed to write storage:', error)
    }
}

/** A deliberate sign-out: keep the login page from signing the user straight back in. */
export function markSignedOut() {
    writeFlag(localStorage, SIGNED_OUT_KEY, true)
}

/** Recorded right before the page leaves for the provider on its own. */
export function markAutoLaunchAttempted() {
    writeFlag(sessionStorage, ATTEMPTED_KEY, true)
}

/** A successful sign-in re-arms auto-launch for the next time the session ends on its own. */
export function clearAutoLaunchSuppression() {
    writeFlag(localStorage, SIGNED_OUT_KEY, false)
    writeFlag(sessionStorage, ATTEMPTED_KEY, false)
}

/**
 * The provider the login page should launch on its own, or null to show the normal page.
 * @param {Object} status - Auth status from GET /auth/sessions/current
 * @param {Array} providers - Enabled OIDC providers
 * @param {Object} query - Login route query
 */
export function resolveAutoLaunchProvider(status, providers, query = {}) {
    if (!status?.oidcAutoLaunchEnabled
        || status.demoModeEnabled
        || status.passwordLoginEnabled
        || !status.oidcLoginEnabled
        || !Array.isArray(providers)
        || providers.length !== 1) {
        return null
    }

    if (query.autoLaunch === '0' || query.reason || query.error) {
        return null
    }

    const askedToSignIn = query.autoLaunch === '1'
    const signedOut = readFlag(localStorage, SIGNED_OUT_KEY)
    const attempted = readFlag(sessionStorage, ATTEMPTED_KEY)
    if ((signedOut !== false && !askedToSignIn) || attempted !== false) {
        return null
    }

    return providers[0]
}
