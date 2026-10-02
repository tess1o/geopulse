const GUEST_LOCALE_KEY = 'guestLocale'

/**
 * A visitor's manually chosen locale, independent of the cached user profile.
 *
 * `userInfo` (see userProfileCache.js) is wiped on logout and fully overwritten on login, so a guest's
 * manual choice stored there would be destroyed by either transition. This dedicated key survives both.
 */
export function readCachedGuestLocale() {
    try {
        return localStorage.getItem(GUEST_LOCALE_KEY) || null
    } catch (error) {
        console.warn('[guestLocaleCache] Failed to read cached guest locale:', error)
        return null
    }
}

export function writeCachedGuestLocale(locale) {
    try {
        localStorage.setItem(GUEST_LOCALE_KEY, locale)
    } catch (error) {
        console.warn('[guestLocaleCache] Failed to persist guest locale:', error)
    }
}
