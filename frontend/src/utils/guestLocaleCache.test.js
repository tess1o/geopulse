import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.hoisted(() => {
  const storage = new Map()
  const shim = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: shim })
  Object.defineProperty(window, 'localStorage', { configurable: true, value: shim })
})

import { readCachedGuestLocale, writeCachedGuestLocale } from './guestLocaleCache'

describe('guestLocaleCache', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('returns null when nothing has been stored yet', () => {
    expect(readCachedGuestLocale()).toBeNull()
  })

  it('round-trips a written locale', () => {
    writeCachedGuestLocale('uk')
    expect(readCachedGuestLocale()).toBe('uk')
  })

  it('does not touch the cached user-profile key', () => {
    writeCachedGuestLocale('uk')
    expect(localStorage.getItem('userInfo')).toBeNull()
  })
})
