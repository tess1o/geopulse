/**
 * i18n bootstrap for GeoPulse.
 *
 * The active locale is seeded from `resolvePreferredLocale()` at module-import time -- before Pinia is
 * installed -- so text is correct on first paint. This mirrors useTimezone, which does the same for
 * date/time preferences, and is why `readCachedUserProfile` whitelists `language`.
 *
 * English is imported statically: it is both the fallback locale and the locale tests are pinned to,
 * so it must never be absent. Every other locale is a dynamic import, which Vite emits as its own JS
 * chunk -- and because the PWA precache glob already covers `**\/*.js`, locale chunks are precached
 * for offline use without any workbox change.
 */
import { createI18n } from 'vue-i18n'
import en from './en/index.js'
import { readCachedUserProfile } from '@/utils/userProfileCache'
import { readCachedGuestLocale } from '@/utils/guestLocaleCache'

export const DEFAULT_LOCALE = 'en'
export const SUPPORTED_LOCALES = ['en', 'uk']

const CATALOG_LOADERS = {
    en: () => import('./en/index.js'),
    uk: () => import('./uk/index.js')
}

const PRIMEVUE_LOCALE_LOADERS = {
    en: () => import('primelocale/en.json'),
    uk: () => import('primelocale/uk.json')
}

export const isSupportedLocale = (value) =>
    SUPPORTED_LOCALES.includes(String(value || '').trim().toLowerCase())

export const normalizeLocale = (value) => {
    const normalized = String(value || '').trim().toLowerCase()
    return isSupportedLocale(normalized) ? normalized : DEFAULT_LOCALE
}

const detectBrowserLocale = () => {
    try {
        const candidates = Array.isArray(navigator?.languages) && navigator.languages.length
            ? navigator.languages
            : [navigator?.language].filter(Boolean)
        for (const candidate of candidates) {
            const primary = String(candidate || '').split('-')[0].toLowerCase()
            if (isSupportedLocale(primary)) {
                return primary
            }
        }
    } catch (error) {
        console.warn('Failed to detect browser locale:', error)
    }
    return null
}

/**
 * Resolve which locale to show, highest precedence first: a logged-in profile's saved language, this
 * browser's own manual guest choice, the browser's language settings, then the hard default. Exported
 * so the auth store can re-run this exact resolution after logout instead of hardcoding a locale.
 */
export const resolvePreferredLocale = () => {
    try {
        const cachedProfile = readCachedUserProfile()
        if (cachedProfile.language) {
            return normalizeLocale(cachedProfile.language)
        }
    } catch (error) {
        console.warn('Failed to get user language from cached profile:', error)
    }

    const guestLocale = readCachedGuestLocale()
    if (guestLocale && isSupportedLocale(guestLocale)) {
        return normalizeLocale(guestLocale)
    }

    return detectBrowserLocale() || DEFAULT_LOCALE
}

/**
 * Plural rules that vue-i18n does not ship.
 *
 * vue-i18n's built-in fallback for a three-form message is *not* the Ukrainian rule: it resolves
 * `0 -> one`, `21 -> few` and `3 -> many`, where Ukrainian needs `0 -> many`, `21 -> one` and
 * `3 -> few`. Without this, counts like 0, 3, 4, 21 and 22 would render visibly wrong Ukrainian.
 *
 * Index order matches the `|`-separated forms in the catalogs: 0 = one, 1 = few, 2 = many.
 * See CLDR plural rules for `uk`.
 */
const PLURAL_RULES = {
    uk: (choice) => {
        const n = Math.abs(choice)
        const mod10 = n % 10
        const mod100 = n % 100

        if (mod10 === 1 && mod100 !== 11) {
            return 0 // one -- 1, 21, 101
        }
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) {
            return 1 // few -- 2-4, 22-24
        }
        return 2 // many -- 0, 5-20, 11-14
    }
}

export const i18n = createI18n({
    // Composition API mode. v11 deprecates the Legacy API and removes `tc`/`$tc`, so pluralization
    // goes through `t(key, count)` with `|`-separated forms -- see the locale catalogs.
    legacy: false,
    globalInjection: true,
    locale: resolvePreferredLocale(),
    fallbackLocale: DEFAULT_LOCALE,
    messages: { en },
    // Note the name: with `legacy: false` the Composer option is `pluralRules`.
    // `pluralizationRules` is the Legacy-API alias and is silently ignored here.
    pluralRules: PLURAL_RULES,
    // Both warnings stay off. Dynamic backend keys are guarded by `te()` in formatMessageDescriptor,
    // and catalog gaps are caught by the en/uk parity test, so console warnings would only add noise
    // during the incremental migration.
    missingWarn: false,
    fallbackWarn: false
})

const loadedLocales = new Set(['en'])
const primeVueLocales = new Map()

/**
 * Load a locale's app catalog on demand. Idempotent, and a no-op for `en` (already bundled).
 */
export const loadLocale = async (locale) => {
    const target = normalizeLocale(locale)
    if (!loadedLocales.has(target)) {
        const messages = (await CATALOG_LOADERS[target]()).default
        i18n.global.setLocaleMessage(target, messages)
        loadedLocales.add(target)
    }
    return target
}

/**
 * Load PrimeVue's own locale pack (date pickers, paginator, confirm dialogs, empty messages).
 *
 * primelocale ships `{ <locale>: { ...72 keys } }`, so unwrap by the locale name and tolerate a
 * future shape that exports the object directly.
 */
export const loadPrimeVueLocale = async (locale) => {
    const target = normalizeLocale(locale)
    if (!primeVueLocales.has(target)) {
        const module = await PRIMEVUE_LOCALE_LOADERS[target]()
        const pack = module?.default ?? module
        primeVueLocales.set(target, pack[target] ?? pack)
    }
    return primeVueLocales.get(target)
}

/**
 * Translate from outside a component's setup scope (stores, utils, composables).
 *
 * These are plain wrappers rather than bound references so the locale is read at call time: called
 * inside a render function or `computed`, `i18n.global.t` reads the locale ref and therefore tracks
 * it, re-rendering on a locale change.
 */
export const t = (key, ...args) => i18n.global.t(key, ...args)

/**
 * Whether a key resolves in the active locale or the fallback.
 *
 * This is the guard that keeps backend-supplied keys safe: `t()` returns the raw key string when
 * nothing matches, so callers must check `te()` first and fall back to their own English text.
 * Returns true when the key exists in the active locale *or* the fallback locale.
 */
export const te = (key) => i18n.global.te(key)

export default i18n
