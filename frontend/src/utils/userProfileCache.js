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
        measureUnit: user.measureUnit,
        defaultRedirectUrl: user.defaultRedirectUrl,
        dateFormat: user.dateFormat,
        timeFormat: user.timeFormat,
        defaultDateRangePreset: user.defaultDateRangePreset,
        autoShowTripReplayControls: user.autoShowTripReplayControls ?? true,
        demoMode: !!user.demoMode,
        canViewAdmin: !!user.canViewAdmin || user.role === 'ADMIN',
        adminReadOnly: !!user.adminReadOnly,
        role: user.role
    }))
}

export function clearCachedUserProfile() {
    localStorage.removeItem(USER_INFO_KEY)
}
