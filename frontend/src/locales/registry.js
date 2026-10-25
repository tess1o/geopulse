/**
 * Every UI language GeoPulse knows about -- the single list the rest of the i18n code is derived from.
 *
 * Adding a language: run `npm run i18n:add -- <code> --name <Endonym> --intl <bcp47>` (or, from the repo
 * root, `make i18n-add LOCALE=<code> NAME=<Endonym> INTL=<bcp47>`), which scaffolds the
 * catalog, inserts a `draft` entry here and registers the code with the backend. See README.md in this
 * directory for the full workflow.
 *
 * `status` gates the language selector:
 *   - `draft` -- never offered in production builds; visible under `npm run dev` so translators can
 *     preview their work. A saved preference or browser language pointing at a draft falls back to
 *     English.
 *   - `ready` -- offered everywhere. locales.test.js fails if a ready catalog drops below
 *     MIN_READY_COVERAGE, so a language cannot silently rot as English grows.
 *
 * Loaders are spelled out per entry rather than built from the code: Vite cannot resolve a bare-package
 * dynamic import (`primelocale/...`, `dayjs/locale/...`) whose path contains a variable.
 */

/**
 * Share of translatable English values a catalog must translate to ship. Values identical to English
 * count as untranslated, so the headroom absorbs strings that legitimately stay the same (GPX, Immich,
 * unit symbols).
 */
export const MIN_READY_COVERAGE = 0.95

/**
 * vue-i18n's built-in fallback for a three-form message is *not* the Ukrainian rule: it resolves
 * `0 -> one`, `21 -> few` and `3 -> many`, where Ukrainian needs `0 -> many`, `21 -> one` and
 * `3 -> few`. Without this, counts like 0, 3, 4, 21 and 22 would render visibly wrong Ukrainian.
 *
 * Index order matches the `|`-separated forms in the catalogs: 0 = one, 1 = few, 2 = many.
 * See CLDR plural rules for `uk`.
 */
const ukPluralRule = (choice) => {
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

export const LOCALE_REGISTRY = [
    {
        code: 'en',
        // Endonym: each language is named in itself and never translated -- see LOCALE_OPTIONS in useLocale.js.
        name: 'English',
        intl: 'en-US',
        status: 'ready',
        loadCatalog: () => import('./en/index.js'),
        loadPrimeVue: () => import('primelocale/en.json'),
        // English is built into dayjs.
        loadDayjs: null
    },
    {
        code: 'uk',
        name: 'Українська',
        intl: 'uk-UA',
        status: 'ready',
        loadCatalog: () => import('./uk/index.js'),
        loadPrimeVue: () => import('primelocale/uk.json'),
        loadDayjs: () => import('dayjs/locale/uk'),
        pluralRule: ukPluralRule
    },
    {
        code: 'de',
        name: 'Deutsch',
        intl: 'de-DE',
        status: 'draft',
        loadCatalog: () => import('./de/index.js'),
        loadPrimeVue: () => import('primelocale/de.json'),
        loadDayjs: () => import('dayjs/locale/de')
    },
    // add-locale:entries -- `npm run i18n:add` inserts new entries above this line.
]

/**
 * Locales a user may pick. Drafts are included only when previewing, never in a production build.
 */
export const selectableLocales = (registry, { showDrafts = false } = {}) =>
    registry.filter(entry => entry.status === 'ready' || (showDrafts && entry.status === 'draft'))

/**
 * Drafts are previewed under `npm run dev` only. Vitest also runs in dev mode, so `test` is excluded to
 * keep the suite seeing exactly what production ships.
 */
export const SHOW_DRAFT_LOCALES = Boolean(import.meta.env?.DEV) && import.meta.env?.MODE !== 'test'
