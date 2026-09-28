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

export function writeCachedUserProfile(user) {
    localStorage.setItem(USER_INFO_KEY, JSON.stringify({
        id: user.id,
        userId: user.id,
        fullName: user.fullName,
        email: user.email,
        avatar: user.avatar,
        timezone: user.timezone,
        createdAt: user.createdAt,
        hasPassword: user.hasPassword,
        customMapTileUrl: user.customMapTileUrl,
        customMapStyleUrl: user.customMapStyleUrl,
        mapRenderMode: user.mapRenderMode || 'VECTOR',
        distanceUnit: user.distanceUnit,
        temperatureUnit: user.temperatureUnit,
        defaultRedirectUrl: user.defaultRedirectUrl,
        dateFormat: user.dateFormat,
        timeFormat: user.timeFormat,
        language: user.language,
        defaultDateRangePreset: user.defaultDateRangePreset,
        autoShowTripReplayControls: user.autoShowTripReplayControls ?? true,
        enable3dBuildingsByDefault: user.enable3dBuildingsByDefault ?? false,
        mapMatchingEnabled: user.mapMatchingEnabled ?? false,
        mapMatchingExcludedMovementTypes: Array.isArray(user.mapMatchingExcludedMovementTypes)
            ? user.mapMatchingExcludedMovementTypes
            : [],
        mapMatchingAvailable: user.mapMatchingAvailable ?? false,
        demoMode: !!user.demoMode,
        canViewAdmin: !!user.canViewAdmin || user.role === 'ADMIN',
        adminReadOnly: !!user.adminReadOnly,
        role: user.role
    }))
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
