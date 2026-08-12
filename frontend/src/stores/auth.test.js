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
  measureUnit: 'METRIC',
  defaultRedirectUrl: '',
  dateFormat: 'MDY',
  timeFormat: '24h',
  defaultDateRangePreset: '',
  autoShowTripReplayControls: true,
  demoMode: false,
  canViewAdmin: false,
  adminReadOnly: false,
  role: 'USER',
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
    apiService.get.mockResolvedValue({ data: user({ demoMode: false }) })

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
      mapRenderMode: 'RASTER'
    }))
    apiService.get.mockResolvedValue({
      data: user({
        timezone: 'Europe/London',
        mapRenderMode: 'VECTOR'
      })
    })

    const authStore = useAuthStore()
    await authStore.checkAuth()

    expect(authStore.userTimezone).toBe('Europe/London')
    expect(authStore.mapRenderMode).toBe('VECTOR')
    expect(readCachedProfile()).toMatchObject({
      timezone: 'Europe/London',
      mapRenderMode: 'VECTOR'
    })
  })

  it('refreshes an expired cookie session before reconciling the cached profile', async () => {
    storeCachedProfile(user({ demoMode: true }))
    apiService.isTokenExpired.mockReturnValue(true)
    apiService.refreshToken.mockResolvedValue(true)
    apiService.get.mockResolvedValue({ data: user({ demoMode: false }) })

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
})
