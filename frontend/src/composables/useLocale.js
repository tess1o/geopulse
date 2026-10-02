/**
 * Active UI locale.
 *
 * A module-level singleton, mirroring useTimezone: the locale is seeded from the cached user profile
 * at import time (before Pinia exists) so text is correct on the first paint, and the state is shared
 * by every caller rather than re-created per component.
 *
 * `i18n.global.locale` is the single source of truth -- this composable exposes a read-only view of it
 * and a `setLocale` that also propagates the change to the pieces that render text outside vue-i18n
 * (dayjs, PrimeVue, the format memoizers, `<html lang>`, the tab title).
 */
import { computed } from 'vue'
import dayjs from 'dayjs'
import 'dayjs/locale/uk'
import { i18n, loadLocale, loadPrimeVueLocale, normalizeLocale } from '@/locales'
import { clearAllFormatCaches } from '@/utils/formatMemoizer'
import { useTimezone } from '@/composables/useTimezone'
import { writeCachedUserLanguage } from '@/utils/userProfileCache'
import { writeCachedGuestLocale } from '@/utils/guestLocaleCache'

/**
 * BCP 47 tags for `Intl` formatting.
 *
 * The UI language drives number/date formatting because the profile has no separate region setting;
 * this is a simplification to revisit if a region preference is ever added.
 */
const INTL_LOCALES = {
    en: 'en-US',
    uk: 'uk-UA'
}

/**
 * Language names are endonyms -- each written in its own language and never translated. Translating
 * them into the *current* locale is actively harmful here: someone who cannot read the active
 * language would see every option rendered in a script they cannot parse.
 */
export const LOCALE_OPTIONS = [
    { value: 'en', label: 'English' },
    { value: 'uk', label: 'Українська' }
]

let primeVueConfig = null
let refreshDocumentTitle = null

/**
 * Hand useLocale the PrimeVue config object so it can swap component-level text (date pickers,
 * paginator, confirm dialogs). Registered from main.js, which is where the app instance lives --
 * PrimeVue's own `usePrimeVue()` only works inside a setup scope.
 */
export const registerPrimeVueConfig = (config) => {
    primeVueConfig = config
}

/**
 * Register how to refresh the browser tab title. main.js owns the router, so it supplies the closure;
 * this keeps useLocale free of a router import and the import cycle that would come with it.
 */
export const registerDocumentTitleRefresher = (refresher) => {
    refreshDocumentTitle = refresher
}

const applyPrimeVueLocale = async (locale) => {
    if (!primeVueConfig) {
        return
    }
    const pack = await loadPrimeVueLocale(locale)
    // `firstDayOfWeek` is derived from the user's date-format preference, not their language, so it is
    // preserved rather than taken from the pack -- otherwise switching language would silently change
    // which day the calendar weeks start on.
    primeVueConfig.locale = {
        ...pack,
        firstDayOfWeek: useTimezone().getPrimeVueFirstDayOfWeek()
    }
}

/**
 * Propagate the active locale to everything that renders text outside vue-i18n.
 */
export const applyLocale = async (locale) => {
    const target = normalizeLocale(locale)
    await loadLocale(target)
    i18n.global.locale.value = target

    dayjs.locale(target)
    await applyPrimeVueLocale(target)

    // Formatted dates, durations and distances are memoized, so without this the UI would keep showing
    // strings built under the previous locale.
    clearAllFormatCaches()

    document.documentElement.lang = target
    refreshDocumentTitle?.()
}

/**
 * Switch the active locale and remember the choice.
 *
 * `persist: false` is for values that came *from* the server profile: re-writing them would be a
 * pointless round trip, and during sign-in it would race the profile fetch.
 *
 * `persistTo` picks where a persisted choice is written: `'guest'` (the default) writes the
 * browser-local guest-locale key, for the pre-auth locale switcher; `'profile'` writes the cached
 * user-profile blob, for the authenticated profile settings form.
 *
 * Note there is deliberately NO "already active, skip" shortcut. That shortcut is what broke the
 * startup path: `main.js` seeds the locale from the cached profile and then calls this with that same
 * locale, so the one call that had to fetch the catalog for the active locale was the one call that
 * skipped doing so -- leaving a Ukrainian user on the bundled English fallback after every reload.
 * `applyLocale` is idempotent, so running it unconditionally is safe and cheap.
 */
export const setLocale = async (locale, { persist = true, persistTo = 'guest' } = {}) => {
    const target = normalizeLocale(locale)
    await applyLocale(target)

    if (persist) {
        if (persistTo === 'profile') {
            writeCachedUserLanguage(target)
        } else {
            writeCachedGuestLocale(target)
        }
    }
    return target
}

export function useLocale() {
    return {
        locale: computed(() => i18n.global.locale.value),
        localeOptions: LOCALE_OPTIONS,
        numberLocale: computed(() => INTL_LOCALES[i18n.global.locale.value] || INTL_LOCALES.en),
        setLocale,
        applyLocale
    }
}
