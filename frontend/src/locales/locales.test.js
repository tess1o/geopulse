import { describe, expect, it, beforeEach } from 'vitest'
import en from './en/index.js'
import uk from './uk/index.js'
import { DEFAULT_LOCALE, SUPPORTED_LOCALES, isSupportedLocale, normalizeLocale, loadLocale, loadPrimeVueLocale, i18n, te, t } from './index.js'

/**
 * Flatten a nested catalog into dotted key paths, so catalog shapes can be compared as sets.
 */
const flattenKeys = (value, prefix = '') =>
    Object.entries(value).flatMap(([key, entry]) => {
        const path = prefix ? `${prefix}.${key}` : key
        return entry && typeof entry === 'object' && !Array.isArray(entry)
            ? flattenKeys(entry, path)
            : [path]
    })

const flattenValues = (value, prefix = '') =>
    Object.entries(value).reduce((acc, [key, entry]) => {
        const path = prefix ? `${prefix}.${key}` : key
        if (entry && typeof entry === 'object' && !Array.isArray(entry)) {
            Object.assign(acc, flattenValues(entry, path))
        } else {
            acc[path] = entry
        }
        return acc
    }, {})

describe('locale catalogs', () => {
    const enKeys = flattenKeys(en)
    const ukKeys = flattenKeys(uk)

    it('exposes at least the fallback locale', () => {
        expect(SUPPORTED_LOCALES).toContain(DEFAULT_LOCALE)
    })

    it('keeps en and uk key sets identical', () => {
        expect([...ukKeys].sort()).toEqual([...enKeys].sort())
    })

    it('has no empty translation values', () => {
        const empty = Object.entries(flattenValues(uk)).filter(([, value]) => !String(value ?? '').trim())
        expect(empty).toEqual([])
    })

    it('resolves linked message references instead of leaking them', async () => {
        // A `@:` reference is legitimate only if translating it yields real text. Checked for every
        // locale by translating each key and asserting no `@:` survives -- a broken or circular link
        // would otherwise render the reference verbatim in the UI.
        const locales = { en, uk }
        const unresolved = []
        for (const [locale, catalog] of Object.entries(locales)) {
            // Load first: uk is lazily imported, and without it i18n would resolve these keys through
            // the English fallback, so the test would pass without ever exercising the uk links.
            await loadLocale(locale)
            const previous = i18n.global.locale.value
            i18n.global.locale.value = locale
            for (const [key, value] of Object.entries(flattenValues(catalog))) {
                if (!String(value).includes('@:')) continue
                const translated = String(t(key))
                if (translated.includes('@:') || translated === key) {
                    unresolved.push(`${locale}:${key}`)
                }
            }
            i18n.global.locale.value = previous
        }
        expect(unresolved).toEqual([])

        expect(t('errors.http.401.message')).toBe(t('errors.session.message'))
        expect(t('errors.http.503.message')).toBe(t('errors.http.502.message'))
    })

    it('interpolates named parameters', () => {
        expect(t('nav.theme.label', { mode: 'Світла' })).toContain('Світла')
        expect(t('errors.http.unknown.title', { status: 418 })).toContain('418')
    })
})

describe('message syntax', () => {
  // A malformed message -- a stray `{{`, an unbalanced quote -- throws at COMPILE time. In the
  // browser that surfaces as a crash deep inside a render, far from the catalog that caused it, so
  // every message in every locale is compiled here instead.
  it('compiles every message in every locale', async () => {
    const locales = { en, uk }
    const failures = []

    for (const [locale, catalog] of Object.entries(locales)) {
      await loadLocale(locale)
      const previous = i18n.global.locale.value
      i18n.global.locale.value = locale
      for (const key of flattenKeys(catalog)) {
        try {
          t(key)
        } catch (error) {
          failures.push(`${locale}:${key} -> ${String(error.message).split('\n')[0]}`)
        }
      }
      i18n.global.locale.value = previous
    }

    expect(failures).toEqual([])
  })

  // Regression: vue-i18n reads `{z}` as an interpolation placeholder, so a literal brace in copy
  // must be written {'{z}'}. Left raw it renders as a blank, which is silent -- the URL-template help
  // text and its validation message both lost their {z}, {x}, {y} placeholders this way.
  it('renders literal braces in the tile-URL copy', () => {
    for (const locale of ['en', 'uk']) {
      const previous = i18n.global.locale.value
      i18n.global.locale.value = locale
      for (const key of ['profile.timeline.validation.tilePlaceholders', 'profile.timeline.sources.rasterTiles.details']) {
        const rendered = String(t(key))
        expect(rendered).toContain('{z}')
        expect(rendered).toContain('{x}')
        expect(rendered).toContain('{y}')
        expect(rendered).not.toContain("'{")
      }
      i18n.global.locale.value = previous
    }
  })
})

describe('pluralization', () => {
  // uk is loaded on demand, and the global setup pins the locale to en, so both are needed here
  // before any Ukrainian form can be resolved.
  beforeEach(async () => {
    await loadLocale('uk')
    i18n.global.locale.value = 'uk'
  })

  // The regression this guards: vue-i18n ships no Ukrainian rule, and its three-form default is
  // wrong for uk -- it yields `0 -> one`, `21 -> few`, `3 -> many`. A missing or misnamed
  // `pluralRules` option falls back to that silently, so every form is asserted explicitly.
  const expectForm = (count, word) => {
    const rendered = t('insights.places.visits', { count }, count)
    expect(rendered).toBe(`${count} ${word}`)
  }

  it('selects the Ukrainian form for each count class', () => {
    expectForm(1, 'візит') // one
    expectForm(21, 'візит') // one, despite ending in 1 above the teens
    expectForm(101, 'візит') // one
    expectForm(2, 'візити') // few
    expectForm(3, 'візити') // few
    expectForm(4, 'візити') // few
    expectForm(22, 'візити') // few
    expectForm(0, 'візитів') // many
    expectForm(5, 'візитів') // many
    expectForm(11, 'візитів') // many -- the teens are the trap
    expectForm(12, 'візитів') // many
    expectForm(25, 'візитів') // many
    expectForm(111, 'візитів') // many
  })

  it('leaves English on its own two-form rule', () => {
    const previous = i18n.global.locale.value
    i18n.global.locale.value = 'en'
    expect(t('insights.places.visits', { count: 1 }, 1)).toBe('1 visit')
    expect(t('insights.places.visits', { count: 2 }, 2)).toBe('2 visits')
    expect(t('insights.places.visits', { count: 0 }, 0)).toBe('0 visits')
    i18n.global.locale.value = previous
  })
})

describe('locale resolution', () => {
    it('accepts supported locale tags', () => {
        expect(isSupportedLocale('en')).toBe(true)
        expect(isSupportedLocale('uk')).toBe(true)
        expect(isSupportedLocale('UK')).toBe(true)
        expect(isSupportedLocale('de')).toBe(false)
    })

    it('falls back to the default for anything unsupported', () => {
        expect(normalizeLocale('de')).toBe(DEFAULT_LOCALE)
        expect(normalizeLocale('')).toBe(DEFAULT_LOCALE)
        expect(normalizeLocale(null)).toBe(DEFAULT_LOCALE)
        expect(normalizeLocale(undefined)).toBe(DEFAULT_LOCALE)
    })
})

describe('te() guard', () => {
    it('reports a key that exists in the catalog', () => {
        expect(te('nav.items.timeline')).toBe(true)
    })

    it('reports a key that exists only in the fallback locale', () => {
        // uk is a strict copy of en today, so this asserts the fallback contract itself rather than
        // a real gap: te() must consult the fallback locale, or backend keys would degrade to raw
        // dotted strings in any locale that lags behind en.
        expect(te('nav.logout')).toBe(true)
    })

    it('reports false for keys with no catalog entry, so callers keep their English fallback', () => {
        // These are exactly the backend-emitted keys this pilot deliberately leaves untranslated.
        expect(te('validation.notBlank')).toBe(false)
        expect(te('badges.busy_bee.title')).toBe(false)
        expect(te('digest.milestone.some_id.title')).toBe(false)
    })

    it('is what stops an unmapped key leaking to the user', () => {
        // Documents why callers must check te() first: t() returns the key itself.
        expect(t('validation.notBlank')).toBe('validation.notBlank')
    })
})

describe('lazy locale loading', () => {
    it('loads a catalog and makes it active', async () => {
        const previous = i18n.global.locale.value
        await loadLocale('uk')
        i18n.global.locale.value = 'uk'
        expect(t('nav.logout')).toBe('Вийти')
        i18n.global.locale.value = previous
    })

    it('loads the PrimeVue locale pack for each supported locale', async () => {
        for (const locale of SUPPORTED_LOCALES) {
            const pack = await loadPrimeVueLocale(locale)
            expect(pack).toBeTruthy()
            expect(pack.accept).toBeTruthy()
            expect(Array.isArray(pack.monthNames)).toBe(true)
            expect(pack.monthNames).toHaveLength(12)
        }
    })

    it('falls back to the default locale for an unsupported request', async () => {
        await expect(loadLocale('de')).resolves.toBe(DEFAULT_LOCALE)
    })
})
