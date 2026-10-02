vi.hoisted(() => {
  const storage = new Map()
  const localStorageMock = {
    getItem: vi.fn((key) => storage.get(key) ?? null),
    setItem: vi.fn((key, value) => {
      storage.set(key, String(value))
    }),
    removeItem: vi.fn((key) => {
      storage.delete(key)
    }),
    clear: vi.fn(() => {
      storage.clear()
    })
  }

  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: localStorageMock
  })

  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: localStorageMock
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import apiService from '../utils/apiService'
import { useAuthStore } from './auth'

vi.mock('../utils/apiService', () => ({
  default: {
    isTokenExpired: vi.fn(),
    refreshToken: vi.fn(),
    get: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    clearAuthData: vi.fn(),
    handleError: vi.fn()
  }
}))

const user = (overrides = {}) => ({
  id: 'user-1',
  userId: 'user-1',
  fullName: 'Regular User',
  email: 'regular@example.com',
  avatar: null,
  timezone: 'UTC',
  createdAt: null,
  hasPassword: true,
  customMapTileUrl: '',
  customMapStyleUrl: '',
  mapRenderMode: 'VECTOR',
  distanceUnit: 'KILOMETERS',
  temperatureUnit: 'CELSIUS',
  defaultRedirectUrl: '',
  dateFormat: 'MDY',
  timeFormat: '24h',
  defaultDateRangePreset: '',
  autoShowTripReplayControls: true,
  enable3dBuildingsByDefault: false,
  mapMatchingEnabled: false,
  mapMatchingExcludedMovementTypes: [],
  mapMatchingAvailable: false,
  demoMode: false,
  canViewAdmin: false,
  adminReadOnly: false,
  role: 'USER',
  ...overrides
})

// /users/me and login payloads nest preferences; the store and cached profile keep them flat.
const serverUser = ({ uiPreferences = {}, preferences = {}, capabilities = {}, ...overrides } = {}) => ({
  userId: 'user-1',
  fullName: 'Regular User',
  email: 'regular@example.com',
  timezone: 'UTC',
  hasPassword: true,
  role: 'USER',
  uiPreferences: { distanceUnit: 'KILOMETERS', temperatureUnit: 'CELSIUS', timeFormat: '24h', language: 'en', ...uiPreferences },
  timelineDisplay: {
    preferences: { mapRenderMode: 'VECTOR', mapMatchingEnabled: false, mapMatchingExcludedMovementTypes: [], ...preferences },
    capabilities: { mapMatchingAvailable: false, panoramaxAvailable: false, ...capabilities }
  },
  ...overrides
})

const storeCachedProfile = (cachedProfile) => {
  localStorage.setItem('userInfo', JSON.stringify(cachedProfile))
}

const readCachedProfile = () => JSON.parse(localStorage.getItem('userInfo') || '{}')

describe('auth store cached profile reconciliation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    apiService.isTokenExpired.mockReturnValue(false)
    apiService.refreshToken.mockResolvedValue(true)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('replaces stale cached demo mode with the current server user', async () => {
    storeCachedProfile(user({
      fullName: 'Demo User',
      email: 'demo@example.com',
      demoMode: true
    }))
    apiService.get.mockResolvedValue(serverUser({ demoMode: false }))

    const authStore = useAuthStore()
    await authStore.checkAuth()

    expect(apiService.get).toHaveBeenCalledWith('/users/me')
    expect(authStore.isAuthenticated).toBe(true)
    expect(authStore.demoModeEnabled).toBe(false)
    expect(readCachedProfile().demoMode).toBe(false)
  })

  it('replaces stale cached preferences with the current server user', async () => {
    storeCachedProfile(user({
      timezone: 'America/New_York',
      mapRenderMode: 'RASTER',
      mapMatchingAvailable: false
    }))
    apiService.get.mockResolvedValue(serverUser({
      timezone: 'Europe/London',
      preferences: { mapRenderMode: 'VECTOR', mapMatchingExcludedMovementTypes: ['WALK'] },
      capabilities: { mapMatchingAvailable: true }
    }))

    const authStore = useAuthStore()
    await authStore.checkAuth()

    expect(authStore.userTimezone).toBe('Europe/London')
    expect(authStore.mapRenderMode).toBe('VECTOR')
    expect(authStore.mapMatchingAvailable).toBe(true)
    expect(authStore.mapMatchingExcludedMovementTypes).toEqual(['WALK'])
    expect(readCachedProfile()).toMatchObject({
      timezone: 'Europe/London',
      mapRenderMode: 'VECTOR',
      mapMatchingExcludedMovementTypes: ['WALK'],
      mapMatchingAvailable: true
    })
  })

  it('patches and caches map matching movement exclusions after saving display preferences', async () => {
    const authStore = useAuthStore()
    authStore.setUser(user())
    apiService.put.mockResolvedValue({
      preferences: {
        mapMatchingEnabled: true,
        mapMatchingExcludedMovementTypes: ['WALK', 'BICYCLE']
      },
      capabilities: { mapMatchingAvailable: true, panoramaxAvailable: false }
    })

    const saved = await authStore.updateTimelineDisplayPreferences({
      mapMatchingEnabled: true,
      mapMatchingExcludedMovementTypes: ['WALK', 'BICYCLE']
    })

    expect(saved).toMatchObject({
      mapMatchingEnabled: true,
      mapMatchingExcludedMovementTypes: ['WALK', 'BICYCLE'],
      mapMatchingAvailable: true
    })
    expect(authStore.mapMatchingExcludedMovementTypes).toEqual(['WALK', 'BICYCLE'])
    expect(readCachedProfile().mapMatchingExcludedMovementTypes).toEqual(['WALK', 'BICYCLE'])
  })

  it('resets a display preference the server no longer returns back to its default', async () => {
    const authStore = useAuthStore()
    authStore.setUser(user({ defaultPathColor: '#112233' }))
    // Unset values are omitted from the response, so an absent key means "back to default".
    apiService.put.mockResolvedValue({ preferences: { mapRenderMode: 'VECTOR' }, capabilities: {} })

    await authStore.updateTimelineDisplayPreferences({ defaultPathColor: '' })

    expect(authStore.defaultPathColor).toBe('')
  })

  it('keeps appearance preferences on the user and resolves unset ones to follow the color scheme', async () => {
    const authStore = useAuthStore()
    authStore.setUser(user({ defaultPathColor: '#112233', speedBandPalette: 'OFF' }))
    apiService.put.mockResolvedValue({
      preferences: { colorScheme: 'RED_GREEN_SAFE', pathOutlineEnabled: true, pathWidth: 6 },
      capabilities: {}
    })

    await authStore.updateTimelineDisplayPreferences({ colorScheme: 'RED_GREEN_SAFE', defaultPathColor: '', speedBandPalette: '' })

    expect(authStore.user).toMatchObject({
      colorScheme: 'RED_GREEN_SAFE',
      defaultPathColor: '',
      speedBandPalette: '',
      heatmapGradient: '',
      pathOutlineEnabled: true,
      pathWidth: 6
    })
    expect(readCachedProfile()).toMatchObject({ colorScheme: 'RED_GREEN_SAFE', pathWidth: 6 })
  })

  it('refreshes the in-memory user when timeline display preferences are fetched', async () => {
    const authStore = useAuthStore()
    authStore.setUser(user())
    apiService.get.mockResolvedValue({
      preferences: { colorScheme: 'HIGH_CONTRAST', activePathColor: '#ffcc00' },
      capabilities: {}
    })

    await authStore.fetchTimelineDisplayPreferences()

    expect(authStore.user).toMatchObject({ colorScheme: 'HIGH_CONTRAST', activePathColor: '#ffcc00' })
  })

  it('sends UI preferences nested and flattens the updated profile', async () => {
    const authStore = useAuthStore()
    authStore.setUser(user())
    apiService.patch.mockResolvedValue(serverUser({
      fullName: 'Renamed',
      uiPreferences: { distanceUnit: 'MILES', dateFormat: 'DMY' }
    }))

    await authStore.updateProfile({
      fullName: 'Renamed',
      avatar: null,
      timezone: 'UTC',
      distanceUnit: 'MILES',
      dateFormat: 'DMY'
    })

    expect(apiService.patch).toHaveBeenCalledWith('/users/me', {
      fullName: 'Renamed',
      avatar: null,
      timezone: 'UTC',
      uiPreferences: { distanceUnit: 'MILES', dateFormat: 'DMY' }
    })
    expect(authStore.userName).toBe('Renamed')
    expect(authStore.distanceUnit).toBe('MILES')
    expect(authStore.dateFormat).toBe('DMY')
    expect(readCachedProfile()).toMatchObject({ distanceUnit: 'MILES', dateFormat: 'DMY' })
  })

  it('flattens fetched timeline display settings for callers', async () => {
    const authStore = useAuthStore()
    apiService.get.mockResolvedValue({
      preferences: { pathSimplificationTolerance: 15 },
      capabilities: { panoramaxAvailable: true, panoramaxEndpoint: 'https://example.com/api' }
    })

    await expect(authStore.fetchTimelineDisplayPreferences()).resolves.toEqual({
      pathSimplificationTolerance: 15,
      panoramaxAvailable: true,
      panoramaxEndpoint: 'https://example.com/api'
    })
  })

  it('refreshes an expired cookie session before reconciling the cached profile', async () => {
    storeCachedProfile(user({ demoMode: true }))
    apiService.isTokenExpired.mockReturnValue(true)
    apiService.refreshToken.mockResolvedValue(true)
    apiService.get.mockResolvedValue(user({ demoMode: false }))

    const authStore = useAuthStore()
    await authStore.checkAuth()

    expect(apiService.refreshToken).toHaveBeenCalledTimes(1)
    expect(apiService.get).toHaveBeenCalledWith('/users/me')
    expect(authStore.demoModeEnabled).toBe(false)
  })

  it('clears stale browser auth state when backend rejects the session', async () => {
    storeCachedProfile(user({ demoMode: true }))
    apiService.get.mockRejectedValue({ response: { status: 401 } })
    vi.spyOn(console, 'error').mockImplementation(() => {})

    const authStore = useAuthStore()
    const result = await authStore.checkAuth()

    expect(result).toBeNull()
    expect(authStore.isAuthenticated).toBe(false)
    expect(localStorage.getItem('userInfo')).toBeNull()
    expect(apiService.clearAuthData).toHaveBeenCalledTimes(1)
  })

  it('preserves the hydrated cached profile when backend reconciliation has a server failure', async () => {
    const cachedProfile = user({ demoMode: true })
    storeCachedProfile(cachedProfile)
    apiService.get.mockRejectedValue({ response: { status: 500 }, message: 'Server error' })
    vi.spyOn(console, 'error').mockImplementation(() => {})

    const authStore = useAuthStore()
    const result = await authStore.checkAuth()

    expect(result).toMatchObject({ id: cachedProfile.id, demoMode: true })
    expect(authStore.isAuthenticated).toBe(true)
    expect(authStore.demoModeEnabled).toBe(true)
    expect(readCachedProfile().demoMode).toBe(true)
    expect(apiService.clearAuthData).not.toHaveBeenCalled()
  })

  // Regression test: clearUser() used to hardcode the locale back to English on sign-out, which
  // wiped a Ukrainian-speaking user's language on every public page the moment they logged out.
  it('falls back to the guest locale choice on sign-out, not hardcoded English', async () => {
    storeCachedProfile(user({ language: 'uk' }))
    localStorage.setItem('guestLocale', 'uk')
    apiService.get.mockResolvedValue(serverUser({ uiPreferences: { language: 'uk' } }))

    const { i18n } = await import('@/locales')

    const authStore = useAuthStore()
    await authStore.checkAuth()
    await vi.waitFor(() => expect(i18n.global.locale.value).toBe('uk'))

    // Force a distinguishable starting point so the assertion below proves clearUser() actively
    // re-applies 'uk' (via resolvePreferredLocale), rather than trivially observing a value that
    // was already correct before clearUser() ran.
    i18n.global.locale.value = 'en'

    authStore.clearUser()

    // Applying the fallback locale is fire-and-forget (see _applyUserState/clearUser), so poll
    // rather than assume it has landed synchronously.
    await vi.waitFor(() => expect(i18n.global.locale.value).toBe('uk'))
    expect(authStore.isAuthenticated).toBe(false)
  })
})
