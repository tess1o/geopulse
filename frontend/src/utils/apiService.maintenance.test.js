import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const axiosMock = vi.hoisted(() => ({
  get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn(),
  requestInterceptor: vi.fn(),
  responseInterceptor: vi.fn(),
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
    interceptors: { request: { use: axiosMock.requestInterceptor }, response: { use: axiosMock.responseInterceptor } }
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

  it('reclassifies a request failure that races with activation', async () => {
    let rejectRequest
    axiosMock.get.mockImplementation(() => new Promise((resolve, reject) => { rejectRequest = reject }))
    const state = await import('@/stores/maintenance')
    const { default: apiService } = await import('./apiService')
    const request = apiService.get('/notifications')
    await Promise.resolve()
    state.applyMaintenanceStatus({ state: 'ACTIVATING', blocked: true, message: 'Activating' })
    rejectRequest(new Error('backend exited'))
    await expect(request).rejects.toBeInstanceOf(state.MaintenanceInterruption)
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
    const config = requestInterceptor({ url: '/api/v1/auth/sessions/current' })
    expect(config.signal).toBeUndefined()
  })

  it('refreshes maintenance status when the restore guard rejects a request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ state: 'ACTIVATING', blocked: true, message: 'Activating' }) }))
    const state = await import('@/stores/maintenance')
    await import('./apiService')
    const onRejected = axiosMock.responseInterceptor.mock.calls.at(-1)[1]
    const blocked = { response: { status: 503, headers: { 'x-geopulse-restore-blocked': 'true' } } }
    await expect(onRejected(blocked)).rejects.toBe(blocked)
    await vi.waitFor(() => expect(state.maintenance.state).toBe('ACTIVATING'))
    expect(fetch).toHaveBeenCalledWith('/api/system/maintenance', expect.anything())
  })

  it('does not refresh maintenance status for ordinary failures', async () => {
    vi.stubGlobal('fetch', vi.fn())
    await import('./apiService')
    const onRejected = axiosMock.responseInterceptor.mock.calls.at(-1)[1]
    const failure = { response: { status: 500, headers: {} } }
    await expect(onRejected(failure)).rejects.toBe(failure)
    expect(fetch).not.toHaveBeenCalled()
  })
})
