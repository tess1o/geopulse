import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const axiosMock = vi.hoisted(() => ({
  get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn(),
  requestInterceptor: vi.fn(),
  isCancel: vi.fn(error => error?.code === 'ERR_CANCELED')
}))

vi.mock('axios', () => ({
  default: {
    get: axiosMock.get,
    post: axiosMock.post,
    put: axiosMock.put,
    patch: axiosMock.patch,
    delete: axiosMock.delete,
    isCancel: axiosMock.isCancel,
    interceptors: { request: { use: axiosMock.requestInterceptor } }
  }
}))

describe('api transport during restore activation', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    const storage = () => {
      const values = new Map()
      return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)), removeItem: key => values.delete(key) }
    }
    vi.stubGlobal('sessionStorage', storage())
    vi.stubGlobal('localStorage', storage())
    vi.stubGlobal('BroadcastChannel', undefined)
    window.VUE_APP_CONFIG = { API_BASE_URL: '/api' }
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('does not start refresh-cookie after activation is known', async () => {
    const state = await import('@/stores/maintenance')
    const { default: apiService } = await import('./apiService')
    state.applyMaintenanceStatus({ state: 'ACTIVATING', blocked: true, message: 'Activating' })
    await expect(apiService.refreshToken()).rejects.toBeInstanceOf(state.MaintenanceInterruption)
    expect(axiosMock.post).not.toHaveBeenCalled()
  })

  it('reclassifies a refresh failure that races with activation', async () => {
    let rejectRefresh
    axiosMock.post.mockImplementation(() => new Promise((resolve, reject) => { rejectRefresh = reject }))
    const state = await import('@/stores/maintenance')
    const { default: apiService } = await import('./apiService')
    const refresh = apiService.refreshToken()
    await Promise.resolve()
    state.applyMaintenanceStatus({ state: 'ACTIVATING', blocked: true, message: 'Activating' })
    rejectRefresh(new Error('backend exited'))
    await expect(refresh).rejects.toBeInstanceOf(state.MaintenanceInterruption)
  })

  it('uses restart mode instead of the error page for a proxy outage after preparation was known', async () => {
    const state = await import('@/stores/maintenance')
    const { default: apiService } = await import('./apiService')
    const redirect = vi.spyOn(apiService, 'redirectToErrorPage')
    state.applyMaintenanceStatus({ state: 'PREPARING', blocked: false, warning: true, message: 'Preparing' })
    apiService.handleError({ message: 'Bad Gateway', response: { status: 502, data: {} }, config: { url: '/notifications' } })
    expect(state.maintenance.unavailable).toBe(true)
    expect(redirect).not.toHaveBeenCalled()
  })

  it('allows completion logout after the activation abort signal fired', async () => {
    const state = await import('@/stores/maintenance')
    await import('./apiService')
    state.applyMaintenanceStatus({ state: 'ACTIVATING', blocked: true, message: 'Activating' })
    state.applyMaintenanceStatus({ state: 'COMPLETED', blocked: false, completedAt: '2026-09-01T10:00:00Z' })

    const requestInterceptor = axiosMock.requestInterceptor.mock.calls[0][0]
    const config = requestInterceptor({ url: '/api/auth/logout' })
    expect(config.signal).toBeUndefined()
  })
})
