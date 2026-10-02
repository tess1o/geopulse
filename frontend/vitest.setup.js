/**
 * Vitest global setup.
 *
 * Installs the i18n plugin on every `mount()` so components using `$t` / `useI18n` render, and pins
 * the active locale to English.
 *
 * The pin is what lets the existing suite keep its 129 assertions on exact English copy: the `en`
 * catalog is a verbatim snapshot of the strings those tests were written against. It must not be
 * reworded without updating them.
 */
import { config } from '@vue/test-utils'
import { afterEach, beforeEach } from 'vitest'
import { i18n, DEFAULT_LOCALE } from '@/locales'

config.global.plugins = [...(config.global.plugins || []), i18n]

beforeEach(() => {
    // A test may switch locale to exercise the Ukrainian catalog; reset so the next test starts from
    // the pinned default rather than inheriting it.
    i18n.global.locale.value = DEFAULT_LOCALE
})

afterEach(() => {
    i18n.global.locale.value = DEFAULT_LOCALE
    document.documentElement.lang = DEFAULT_LOCALE
})
