vi.hoisted(() => {
  const values = new Map()
  const storage = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
    clear: () => values.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage })
  Object.defineProperty(window, 'localStorage', { configurable: true, value: storage })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import apiService from '@/utils/apiService'
import { useNotificationsStore } from './notifications'

vi.mock('@/utils/apiService', () => ({ default: { get: vi.fn(), patch: vi.fn() } }))
vi.mock('@/router', () => ({ default: { push: vi.fn() } }))
vi.mock('@/utils/userProfileCache', () => ({ readCachedUserProfile: () => ({ id: 'user-1' }) }))
vi.mock('@/stores/maintenance', () => ({
  interruptsApplicationRequests: () => false,
  isMaintenanceInterruption: () => false
}))

const POLL_INTERVAL_MS = 10000

const notification = id => ({ id, title: `Notification ${id}`, message: 'Body', seen: false, occurredAt: '2026-10-01T10:00:00Z' })

describe('notifications polling', () => {
  let server
  let hidden

  const listCalls = () => apiService.get.mock.calls.filter(([url]) => url === '/notifications').length
  const countCalls = () => apiService.get.mock.calls.filter(([url]) => url === '/notifications/unread-count').length
  const setHidden = value => {
    hidden = value
    document.dispatchEvent(new Event('visibilitychange'))
  }

  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    localStorage.clear()
    setActivePinia(createPinia())
    hidden = false
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden })
    server = { items: [notification(1)], count: 1, latestUnreadId: 1 }
    apiService.get.mockImplementation(async url => {
      if (url === '/notifications/unread-count') return { count: server.count, latestUnreadId: server.latestUnreadId }
      if (url === '/notifications') return { items: server.items }
      throw new Error(`unexpected ${url}`)
    })
  })

  afterEach(() => {
    useNotificationsStore().stopPolling()
    delete document.hidden
    vi.useRealTimers()
  })

  const startPolling = async () => {
    const store = useNotificationsStore()
    store.startPolling()
    await vi.advanceTimersByTimeAsync(0)
    return store
  }

  it('only checks the unread count while nothing changed', async () => {
    await startPolling()
    expect(listCalls()).toBe(1)
    expect(countCalls()).toBe(1)

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3)
    expect(countCalls()).toBe(4)
    expect(listCalls()).toBe(1)
  })

  it('reloads the list when a new unread notification appears', async () => {
    const store = await startPolling()
    server = { items: [notification(2), notification(1)], count: 2, latestUnreadId: 2 }

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    expect(listCalls()).toBe(2)
    expect(store.unreadCount).toBe(2)
    expect(store.items.map(item => item.id)).toEqual([2, 1])
  })

  it('pauses in a hidden tab without browser notifications and resumes when visible', async () => {
    await startPolling()
    setHidden(true)
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3)
    expect(countCalls()).toBe(1)

    setHidden(false)
    await vi.advanceTimersByTimeAsync(0)
    expect(listCalls()).toBe(2)
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS)
    expect(countCalls()).toBe(3)
  })

  it('keeps polling in a hidden tab when browser notifications are enabled', async () => {
    const store = await startPolling()
    store.browserNotificationsEnabled = true
    setHidden(true)
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2)
    expect(countCalls()).toBe(3)
  })
})
