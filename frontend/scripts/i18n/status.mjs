#!/usr/bin/env node
/**
 * Translation progress per locale.
 *
 *   npm run i18n:status                 table for every locale
 *   npm run i18n:status -- de           table for one locale
 *   npm run i18n:status -- de --list    plus every untranslated key with its English text
 *
 * Exits non-zero when a `ready` locale is below MIN_READY_COVERAGE or has keys English no longer has,
 * so it can gate CI as well as guide a translator.
 */
import { parseArgs } from 'node:util'
import { LOCALE_REGISTRY, MIN_READY_COVERAGE } from '../../src/locales/registry.js'
import { computeCoverage, flattenValues } from './catalog.mjs'

const { values, positionals } = parseArgs({
    allowPositionals: true,
    options: { list: { type: 'boolean', default: false } }
})

const only = positionals[0]
const entries = LOCALE_REGISTRY.filter(entry => entry.code !== 'en' && (!only || entry.code === only))
if (only && entries.length === 0) {
    console.error(`error: no locale '${only}' in src/locales/registry.js`)
    process.exit(1)
}

const en = (await LOCALE_REGISTRY.find(entry => entry.code === 'en').loadCatalog()).default
const enValues = flattenValues(en)
const percent = (value) => `${(Math.floor(value * 1000) / 10).toFixed(1)}%`

const rows = []
const problems = []
const reports = []
for (const entry of entries) {
    const report = computeCoverage(en, (await entry.loadCatalog()).default)
    reports.push([entry, report])
    rows.push({
        locale: entry.code,
        name: entry.name,
        status: entry.status,
        coverage: percent(report.coverage),
        missing: report.missing.length,
        'same as en': report.identical.length,
        stale: report.extra.length
    })
    if (entry.status === 'ready' && report.coverage < MIN_READY_COVERAGE) {
        problems.push(`${entry.code} is 'ready' but only ${percent(report.coverage)} translated (minimum ${percent(MIN_READY_COVERAGE)})`)
    }
    if (report.extra.length) {
        problems.push(`${entry.code} has ${report.extra.length} key(s) English does not: ${report.extra.slice(0, 5).join(', ')}${report.extra.length > 5 ? ', ...' : ''}`)
    }
}

console.log(`${Object.keys(enValues).length} English keys; a locale ships when 'ready' and >= ${percent(MIN_READY_COVERAGE)} translated.\n`)
console.table(rows)

if (values.list) {
    for (const [entry, report] of reports) {
        console.log(`\n# ${entry.code}: missing (${report.missing.length})`)
        for (const key of report.missing) console.log(`${key} = ${JSON.stringify(enValues[key])}`)
        console.log(`\n# ${entry.code}: same as English (${report.identical.length}) -- fine for names like GPX, otherwise translate`)
        for (const key of report.identical) console.log(`${key} = ${JSON.stringify(enValues[key])}`)
        if (report.extra.length) {
            console.log(`\n# ${entry.code}: not in English (${report.extra.length}) -- delete`)
            for (const key of report.extra) console.log(key)
        }
    }
}

if (problems.length) {
    console.error(`\n${problems.map(problem => `error: ${problem}`).join('\n')}`)
    process.exit(1)
}
