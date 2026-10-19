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
import { LOCALE_REGISTRY, SHOW_DRAFT_LOCALES, selectableLocales } from './registry.js'

export const DEFAULT_LOCALE = 'en'

const registryEntry = (locale) => LOCALE_REGISTRY.find(entry => entry.code === locale)

/**
 * Locales a user can select: every `ready` entry in registry.js, plus drafts under `npm run dev`.
 * Everything else -- a saved preference or browser language pointing at a draft -- normalizes to English.
 */
export const SUPPORTED_LOCALES = selectableLocales(LOCALE_REGISTRY, { showDrafts: SHOW_DRAFT_LOCALES })
    .map(entry => entry.code)

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
 * Plural rules that vue-i18n does not ship, keyed by locale -- each defined next to its entry in
 * registry.js (see the Ukrainian rule there for why one is needed).
 */
const PLURAL_RULES = Object.fromEntries(
    LOCALE_REGISTRY.filter(entry => entry.pluralRule).map(entry => [entry.code, entry.pluralRule])
)

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
    // and catalog gaps are caught by the coverage tests in locales.test.js (a `ready` locale may lag
    // English slightly and fall back to it), so console warnings would only add noise.
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
        const messages = (await registryEntry(target).loadCatalog()).default
        i18n.global.setLocaleMessage(target, messages)
        loadedLocales.add(target)
    }
    return target
}

/**
 * Load PrimeVue's own locale pack (date pickers, paginator, confirm dialogs, empty messages).
 *
 * primelocale ships `{ <locale>: { ...72 keys } }`, so unwrap by the locale name and tolerate a
 * future shape that exports the object directly. A language primelocale lacks is registered with the
 * English pack (see `npm run i18n:add`), whose wrapper key is `en`, so that is unwrapped too.
 */
export const loadPrimeVueLocale = async (locale) => {
    const target = normalizeLocale(locale)
    if (!primeVueLocales.has(target)) {
        const module = await registryEntry(target).loadPrimeVue()
        const pack = module?.default ?? module
        primeVueLocales.set(target, pack[target] ?? pack[DEFAULT_LOCALE] ?? pack)
    }
    return primeVueLocales.get(target)
}

const dayjsLocales = new Set()

/**
 * Load dayjs's locale data. Idempotent; a no-op for locales dayjs has built in (`loadDayjs: null`).
 * Importing a dayjs locale file registers it globally, so callers can `dayjs.locale(code)` afterwards.
 */
export const loadDayjsLocale = async (locale) => {
    const target = normalizeLocale(locale)
    const loader = registryEntry(target).loadDayjs
    if (loader && !dayjsLocales.has(target)) {
        await loader()
        dayjsLocales.add(target)
    }
    return target
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

/**
 * BCP 47 tags for `Intl` formatting.
 *
 * The UI language drives number/date formatting because the profile has no separate region setting;
 * this is a simplification to revisit if a region preference is ever added.
 */
const INTL_LOCALES = Object.fromEntries(LOCALE_REGISTRY.map(entry => [entry.code, entry.intl]))

/** The `Intl` tag for the active UI locale; read at call time, so it tracks the locale like `t()`. */
export const intlLocale = () => INTL_LOCALES[i18n.global.locale.value] || INTL_LOCALES.en

export default i18n
