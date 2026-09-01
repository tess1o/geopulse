import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

describe('online restore maintenance lifecycle', () => {
  beforeEach(() => {
    vi.resetModules()
    const storage = () => {
      const values = new Map()
      return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)), removeItem: key => values.delete(key) }
    }
    vi.stubGlobal('sessionStorage', storage())
    vi.stubGlobal('localStorage', storage())
    vi.stubGlobal('BroadcastChannel', undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  const preparing = {
    state: 'PREPARING', blocked: false, warning: true,
    message: 'Restoration is being prepared in the background.'
  }
  const swapped = {
    state: 'SWAPPED_PENDING_RESTART', blocked: true, warning: false,
    message: 'Restored data was activated. GeoPulse is stopping the backend to complete restoration.'
  }
  const respond = data => vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data }) }))

  it('checks public status without authentication cookies', async () => {
    respond(preparing)
    const module = await import('./maintenance')
    await module.refreshMaintenance()
    expect(fetch).toHaveBeenCalledWith('/api/maintenance/status', expect.objectContaining({ credentials: 'omit', cache: 'no-store' }))
    expect(module.maintenance.warning).toBe(true)
    expect(module.maintenance.blocked).toBe(false)
  })

  it('turns a known preparing restore proxy outage into restart mode', async () => {
    const module = await import('./maintenance')
    module.applyMaintenanceStatus(preparing)
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('proxy 502')))
    await module.refreshMaintenance()
    expect(module.maintenance.unavailable).toBe(true)
    expect(module.maintenance.blocked).toBe(true)
    expect(module.interruptsApplicationRequests()).toBe(true)
  })

  it('retains restart state across a browser reload while the backend is unavailable', async () => {
    let module = await import('./maintenance')
    module.applyMaintenanceStatus(swapped)
    module.markMaintenanceUnavailable()
    vi.resetModules()
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('connection closed')))
    module = await import('./maintenance')
    await module.refreshMaintenance()
    expect(module.maintenance.state).toBe('SWAPPED_PENDING_RESTART')
    expect(module.maintenance.unavailable).toBe(true)
    expect(module.maintenance.message).toBe(swapped.message)
  })

  it('shares an active restore with a newly opened tab', async () => {
    let module = await import('./maintenance')
    module.applyMaintenanceStatus(swapped)
    const values = new Map()
    vi.stubGlobal('sessionStorage', { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)), removeItem: key => values.delete(key) })
    vi.resetModules()
    module = await import('./maintenance')
    expect(module.maintenance.state).toBe('SWAPPED_PENDING_RESTART')
    expect(module.interruptsApplicationRequests()).toBe(true)
  })

  it('keeps completion logout blocking across a transient backend failure and reload', async () => {
    let module = await import('./maintenance')
    module.applyMaintenanceStatus({ state: 'COMPLETED', completedAt: '2026-09-01T00:00:00Z', blocked: false })
    module.markMaintenanceUnavailable()
    vi.resetModules()
    module = await import('./maintenance')
    expect(module.maintenance.activated).toBe(true)
    expect(module.interruptsApplicationRequests()).toBe(true)
  })

  it('shows manual restart guidance after sixty seconds offline', async () => {
    vi.useFakeTimers()
    const module = await import('./maintenance')
    module.applyMaintenanceStatus(swapped)
    module.markMaintenanceUnavailable()
    await vi.advanceTimersByTimeAsync(60000)
    expect(module.maintenance.manualRestartRequired).toBe(true)
  })

  it.each(['PREPARATION_FAILED', 'ACTIVATION_RETRYABLE', 'DISCARDED'])('resumes the original application after %s', async state => {
    const module = await import('./maintenance')
    module.applyMaintenanceStatus(swapped)
    module.applyMaintenanceStatus({ state, blocked: false, warning: false, message: 'Original data retained' })
    expect(module.maintenance.blocked).toBe(false)
    expect(module.maintenance.activated).toBe(false)
    expect(sessionStorage.getItem('geopulse.restore-maintenance')).toBeNull()
  })

  it('requires a server-confirmed logout once after completed activation', async () => {
    let module = await import('./maintenance')
    const completed = { state: 'COMPLETED', completedAt: '2026-09-01T00:00:00Z', blocked: false }
    module.applyMaintenanceStatus(completed)
    expect(module.maintenance.activated).toBe(true)
    module.acknowledgeActivation()
    vi.resetModules()
    module = await import('./maintenance')
    module.applyMaintenanceStatus(completed)
    expect(module.maintenance.activated).toBe(false)
  })

  it('keeps application requests blocked on activation identity failure', async () => {
    const module = await import('./maintenance')
    module.applyMaintenanceStatus({ state: 'ACTIVATION_FAILED', blocked: true, message: 'Administrator action is required.' })
    expect(module.maintenanceScreenVisible()).toBe(true)
    expect(module.interruptsApplicationRequests()).toBe(true)
  })
})
