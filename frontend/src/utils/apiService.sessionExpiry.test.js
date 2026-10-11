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

const unauthorized = { message: 'Unauthorized', response: { status: 401, data: {} }, config: { url: '/streaks' } }

describe('api client on an expired session', () => {
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
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('sends a signed-in user to login on a 401', async () => {
    const { default: apiService } = await import('./apiService')
    vi.spyOn(apiService, 'hasCachedUserProfile').mockReturnValue(true)
    const redirect = vi.spyOn(apiService, 'redirectToLogin').mockImplementation(() => {})

    apiService.handleError(unauthorized)

    expect(redirect).toHaveBeenCalledTimes(1)
  })

  it('leaves guests where they are on a 401', async () => {
    const { default: apiService } = await import('./apiService')
    vi.spyOn(apiService, 'hasCachedUserProfile').mockReturnValue(false)
    const redirect = vi.spyOn(apiService, 'redirectToLogin').mockImplementation(() => {})

    apiService.handleError(unauthorized)

    expect(redirect).not.toHaveBeenCalled()
  })

  it('keeps a visitor with an expired session on a public page and drops the stale profile', async () => {
    const { default: apiService } = await import('./apiService')
    vi.spyOn(apiService, 'hasCachedUserProfile').mockReturnValue(true)
    const clearAuthData = vi.spyOn(apiService, 'clearAuthData')
    const redirect = vi.spyOn(apiService, 'redirectToLogin').mockImplementation(() => {})
    apiService.registerSessionRequiredCheck(() => false)

    apiService.handleError(unauthorized)

    expect(redirect).not.toHaveBeenCalled()
    expect(clearAuthData).toHaveBeenCalledTimes(1)
  })

  it('does not try to refresh a session on a 401', async () => {
    axiosMock.get.mockRejectedValue(unauthorized)
    const { default: apiService } = await import('./apiService')
    vi.spyOn(apiService, 'hasCachedUserProfile').mockReturnValue(true)
    const redirect = vi.spyOn(apiService, 'redirectToLogin').mockImplementation(() => {})

    await expect(apiService.get('/streaks')).rejects.toBe(unauthorized)

    expect(axiosMock.post).not.toHaveBeenCalled()
    expect(redirect).toHaveBeenCalledTimes(1)
  })

  it('returns the identity provider logout URL from logout', async () => {
    axiosMock.delete.mockResolvedValue({ data: { idpLogoutUrl: 'https://idp.example.com/end-session' } })
    const { default: apiService } = await import('./apiService')

    await expect(apiService.logout()).resolves.toBe('https://idp.example.com/end-session')
  })

  it('returns no identity provider logout URL when logout fails', async () => {
    axiosMock.delete.mockRejectedValue(new Error('network down'))
    const { default: apiService } = await import('./apiService')

    await expect(apiService.logout()).resolves.toBeNull()
  })
})
