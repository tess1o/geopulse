const USER_INFO_KEY = 'userInfo'

export function readUserSnapshot() {
    try {
        return JSON.parse(localStorage.getItem(USER_INFO_KEY) || '{}')
    } catch (error) {
        console.warn('[authSnapshotStorage] Failed to read user snapshot:', error)
        return {}
    }
}

export function writeUserSnapshot(user) {
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
        measureUnit: user.measureUnit,
        defaultRedirectUrl: user.defaultRedirectUrl,
        role: user.role
    }))
}

export function clearUserSnapshot() {
    localStorage.removeItem(USER_INFO_KEY)
}
