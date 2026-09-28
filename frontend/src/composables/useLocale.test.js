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

/**
 * The application boot path for a returning user.
 *
 * `main.js` seeds the locale from the cached profile at module-import time and then calls
 * `setLocale(that same locale, { persist: false })`. Because the target equals the active locale,
 * that call is the one place where no locale CHANGE happens -- and it is exactly where the catalog
 * for the active locale has to be fetched.
 *
 * The regression this guards: an early return for "target already active" skipped the catalog load,
 * so a Ukrainian user's UI silently rendered English on every reload (English is the bundled
 * fallback), while switching language by hand in the profile appeared to fix it.
 *
 * The module graph is reset so this sees a fresh `locales/index.js`, which is the only way to
 * reproduce startup: in a reused graph `uk` is already loaded by earlier tests.
 */
describe('boot with a cached non-default locale', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('renders the cached locale, not the English fallback', async () => {
    // A returning Ukrainian user: the profile cache holds their choice before any module loads.
    localStorage.setItem('userInfo', JSON.stringify({ language: 'uk' }))

    vi.resetModules()
    const locales = await import('@/locales')
    const useLocale = await import('@/composables/useLocale')

    // The locale is seeded from the cache, before Pinia exists.
    expect(locales.i18n.global.locale.value).toBe('uk')

    // Mirror main.js: same locale, persist:false -- it came from the profile, not from a user action.
    await useLocale.setLocale('uk', { persist: false })

    // Ukrainian, not the bundled English fallback.
    expect(locales.t('nav.logout')).toBe('Вийти')
    expect(locales.t('profile.page.title')).toBe('Персональні налаштування')
  })

  it('falls back to English for a user with no cached language', async () => {
    vi.resetModules()
    const locales = await import('@/locales')
    const useLocale = await import('@/composables/useLocale')

    expect(locales.i18n.global.locale.value).toBe('en')
    await useLocale.setLocale('en', { persist: false })
    expect(locales.t('nav.logout')).toBe('Logout')
  })
})

/**
 * `resolvePreferredLocale()` is the tiered fallback used both at boot and by the auth store's
 * `clearUser()` after logout: a logged-in profile's language outranks a guest's own manual choice,
 * which outranks the browser's language settings, which outranks the hard default.
 */
describe('resolvePreferredLocale precedence', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('prefers the cached profile language over a guest choice', async () => {
    localStorage.setItem('userInfo', JSON.stringify({ language: 'uk' }))
    localStorage.setItem('guestLocale', 'en')

    vi.resetModules()
    const locales = await import('@/locales')

    expect(locales.resolvePreferredLocale()).toBe('uk')
  })

  it('prefers a guest choice over browser detection when there is no cached profile', async () => {
    localStorage.setItem('guestLocale', 'uk')

    vi.resetModules()
    const locales = await import('@/locales')

    expect(locales.resolvePreferredLocale()).toBe('uk')
  })

  it('falls back to the default locale when nothing is cached and the browser language is unsupported', async () => {
    const languageGetter = vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('fr-FR')
    const languagesGetter = vi.spyOn(window.navigator, 'languages', 'get').mockReturnValue(['fr-FR'])

    vi.resetModules()
    const locales = await import('@/locales')

    expect(locales.resolvePreferredLocale()).toBe('en')

    languageGetter.mockRestore()
    languagesGetter.mockRestore()
  })
})

/**
 * Regression coverage for the fix to `clearUser()`: signing out must not hardcode English over a
 * guest's own manual choice.
 */
describe('setLocale persistence target', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('defaults to writing the guest-locale key, leaving the cached profile untouched', async () => {
    vi.resetModules()
    const useLocale = await import('@/composables/useLocale')

    await useLocale.setLocale('uk')

    expect(localStorage.getItem('guestLocale')).toBe('uk')
    expect(localStorage.getItem('userInfo')).toBeNull()
  })

  it('writes the cached profile when persistTo is "profile"', async () => {
    vi.resetModules()
    const useLocale = await import('@/composables/useLocale')

    await useLocale.setLocale('uk', { persistTo: 'profile' })

    expect(JSON.parse(localStorage.getItem('userInfo') || '{}').language).toBe('uk')
    expect(localStorage.getItem('guestLocale')).toBeNull()
  })
})
