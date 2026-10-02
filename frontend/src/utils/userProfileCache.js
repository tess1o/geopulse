const USER_INFO_KEY = 'userInfo'

// Cache-only profile bootstrap. Cookies and /users/me remain the auth/profile authority.
export function readCachedUserProfile() {
    try {
        return JSON.parse(localStorage.getItem(USER_INFO_KEY) || '{}')
    } catch (error) {
        console.warn('[userProfileCache] Failed to read cached user profile:', error)
        return {}
    }
}

// Expects the store's normalized, flat user (see normalizeUser in stores/auth.js).
export function writeCachedUserProfile(user) {
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(user))
}

export function clearCachedUserProfile() {
    localStorage.removeItem(USER_INFO_KEY)
}

/**
 * Update just the language on the cached profile.
 *
 * A merge rather than a full write: the language can change on its own (the profile language picker)
 * and must not blank out the rest of the cached profile, which the pre-Pinia bootstrap reads.
 */
export function writeCachedUserLanguage(language) {
    try {
        localStorage.setItem(USER_INFO_KEY, JSON.stringify({
            ...readCachedUserProfile(),
            language
        }))
    } catch (error) {
        console.warn('[userProfileCache] Failed to cache user language:', error)
    }
}
