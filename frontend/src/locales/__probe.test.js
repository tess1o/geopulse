import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { i18n as appI18n, loadLocale } from './index.js'

const PLURAL_RULES = {
    uk: (choice) => {
        const n = Math.abs(choice)
        const mod10 = n % 10
        const mod100 = n % 100
        if (mod10 === 1 && mod100 !== 11) return 0
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) return 1
        return 2
    }
}

describe('plural probe', () => {
    it('one en form with a plural argument', () => {
        const i = createI18n({
            legacy: false,
            locale: 'en',
            messages: { en: { k: { single: 'of {count} points' } } },
            pluralRules: PLURAL_RULES
        })
        let out
        try {
            out = i.global.t('k.single', { count: 3 }, 3)
        } catch (error) {
            out = 'THREW: ' + error.message.split('\n')[0]
        }
        console.log('ONE-FORM WITH PLURAL ARG ->', JSON.stringify(out))
        expect(out).toBeTruthy()
    })

    it('uk three forms with a plural argument', async () => {
        await loadLocale('uk')
        const previous = appI18n.global.locale.value
        appI18n.global.locale.value = 'uk'
        const out = []
        for (const n of [1, 3, 5, 21]) {
            out.push(`${n}:${appI18n.global.t('insights.places.visits', { count: n }, n)}`)
        }
        appI18n.global.locale.value = previous
        console.log('UK THREE FORMS ->', out.join(' | '))
        expect(out.length).toBe(4)
    })

    it('en two forms vs uk three forms on one key', async () => {
        const i = createI18n({
            legacy: false,
            locale: 'uk',
            messages: {
                en: { k: { p: 'of {count} point | of {count} points' } },
                uk: { k: { p: 'із {count} точки | із {count} точок | із {count} точок' } }
            },
            pluralRules: PLURAL_RULES
        })
        for (const n of [1, 2, 5]) {
            console.log('EN', n, '->', i.global.t('k.p', { count: n }, n))
        }
        i.global.locale.value = 'uk'
        for (const n of [1, 2, 5]) {
            console.log('UK', n, '->', i.global.t('k.p', { count: n }, n))
        }
        expect(true).toBe(true)
    })
})
