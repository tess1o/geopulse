/**
 * Catalog comparison shared by the i18n scripts and locales.test.js.
 *
 * Plain Node ESM with no Vite dependencies: the catalogs only import sibling files, so they load
 * directly in Node as well as under vitest.
 */

/**
 * Flatten a nested catalog into `{ 'dotted.key.path': value }`.
 */
export const flattenValues = (value, prefix = '', acc = {}) => {
    for (const [key, entry] of Object.entries(value)) {
        const path = prefix ? `${prefix}.${key}` : key
        if (entry && typeof entry === 'object' && !Array.isArray(entry)) {
            flattenValues(entry, path, acc)
        } else {
            acc[path] = entry
        }
    }
    return acc
}

/**
 * Whether an English value has anything a translator could translate. Pure placeholders and links
 * (`{start} - {end}`, `@:errors.session.message`) carry no words, so they are left out of coverage --
 * an identical copy of them is correct, not a gap.
 */
export const isTranslatable = (value) => {
    const words = String(value ?? '')
        .replace(/@:[\w.-]+/g, '')
        .replace(/\{[^}]*\}/g, '')
    return /\p{L}/u.test(words)
}

/**
 * Compare a catalog against English.
 *
 * - `missing`: English keys the catalog lacks (vue-i18n renders the English fallback).
 * - `identical`: translatable values still equal to English -- usually untranslated, sometimes
 *   legitimately the same (GPX, Immich, unit symbols).
 * - `extra`: keys English no longer has; always a mistake (stale or misspelled).
 * - `coverage`: translated share of translatable English values, 0..1.
 */
export const computeCoverage = (enCatalog, catalog) => {
    const en = flattenValues(enCatalog)
    const target = flattenValues(catalog)

    const missing = []
    const identical = []
    let translatable = 0

    for (const [key, enValue] of Object.entries(en)) {
        if (!(key in target)) {
            if (isTranslatable(enValue)) translatable++
            missing.push(key)
            continue
        }
        if (!isTranslatable(enValue)) continue
        translatable++
        if (target[key] === enValue) identical.push(key)
    }

    const untranslated = identical.length + missing.filter(key => isTranslatable(en[key])).length
    const extra = Object.keys(target).filter(key => !(key in en))

    return {
        total: translatable,
        missing,
        identical,
        extra,
        coverage: translatable === 0 ? 1 : (translatable - untranslated) / translatable
    }
}
