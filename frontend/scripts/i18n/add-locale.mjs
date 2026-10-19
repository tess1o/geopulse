#!/usr/bin/env node
/**
 * Scaffold a new UI language as a draft.
 *
 *   npm run i18n:add -- <code> --name <Endonym> --intl <bcp47>
 *   npm run i18n:add -- de --name Deutsch --intl de-DE
 *
 * Copies the English catalog to src/locales/<code>/, registers a `draft` entry in
 * src/locales/registry.js and adds the code to the backend SupportedLanguages enum. Drafts are hidden
 * from production builds until their status is flipped to `ready` -- see src/locales/README.md.
 */
import { cpSync, existsSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseArgs } from 'node:util'

const FRONTEND = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const LOCALES_DIR = join(FRONTEND, 'src', 'locales')
const REGISTRY = join(LOCALES_DIR, 'registry.js')
const BACKEND_ENUM = join(FRONTEND, '..', 'backend', 'src', 'main', 'java', 'org', 'github', 'tess1o',
    'geopulse', 'user', 'model', 'SupportedLanguages.java')
const MARKER = 'add-locale:entries'

const fail = (message) => {
    console.error(`error: ${message}`)
    process.exit(1)
}

const { values, positionals } = parseArgs({
    allowPositionals: true,
    options: {
        name: { type: 'string' },
        intl: { type: 'string' }
    }
})

const code = String(positionals[0] || '').trim()
const name = String(values.name || '').trim()
const intl = String(values.intl || '').trim()

if (!code || !name || !intl) {
    fail('usage: npm run i18n:add -- <code> --name <Endonym> --intl <bcp47>   (e.g. de --name Deutsch --intl de-DE)')
}
if (!/^[a-z]{2,3}$/.test(code)) {
    fail(`'${code}' is not a lowercase ISO 639 language code (2-3 letters)`)
}
if (!/^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$/.test(intl)) {
    fail(`'${intl}' is not a BCP 47 tag (e.g. de-DE)`)
}
try {
    new Intl.NumberFormat(intl)
} catch {
    fail(`Intl does not accept '${intl}'`)
}

const registrySource = readFileSync(REGISTRY, 'utf8')
const enumSource = readFileSync(BACKEND_ENUM, 'utf8')
if (new RegExp(`code: '${code}'`).test(registrySource) || existsSync(join(LOCALES_DIR, code))) {
    fail(`locale '${code}' already exists`)
}
if (new RegExp(`\\("${code}"\\)`).test(enumSource)) {
    fail(`backend SupportedLanguages already has '${code}'`)
}
if (!registrySource.includes(MARKER) || !enumSource.includes(MARKER)) {
    fail(`'${MARKER}' marker missing from registry.js or SupportedLanguages.java`)
}

// Third-party packs: fall back to English rather than refuse, so a language neither package covers can
// still be translated -- only date-picker/paginator text and relative-time words stay English.
const warnings = []
const hasPrimeVuePack = existsSync(join(FRONTEND, 'node_modules', 'primelocale', `${code}.json`))
const hasDayjsLocale = existsSync(join(FRONTEND, 'node_modules', 'dayjs', 'locale', `${code}.js`))
if (!hasPrimeVuePack) {
    warnings.push(`primelocale has no '${code}.json'; PrimeVue widgets (date pickers, paginator) will use English.`)
}
if (!hasDayjsLocale) {
    warnings.push(`dayjs has no 'locale/${code}.js'; dayjs month/relative-time words will use English.`)
}

// 1. Catalog: a copy of English, so every key is present and the translator edits values in place.
const target = join(LOCALES_DIR, code)
cpSync(join(LOCALES_DIR, 'en'), target, { recursive: true })
const draftNote = `// DRAFT (${name}): copied from English -- translate the values, keep the keys. Progress: npm run i18n:status -- ${code}\n`
const walk = (dir) => readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry)
    return statSync(full).isDirectory() ? walk(full) : [full]
})
const files = walk(target).filter(file => file.endsWith('.js'))
for (const file of files) {
    writeFileSync(file, draftNote + readFileSync(file, 'utf8'))
}

// 2. Frontend registry entry.
const entry = [
    '{',
    `        code: '${code}',`,
    `        name: '${name.replace(/'/g, "\\'")}',`,
    `        intl: '${intl}',`,
    `        status: 'draft',`,
    `        loadCatalog: () => import('./${code}/index.js'),`,
    `        loadPrimeVue: () => import('primelocale/${hasPrimeVuePack ? code : 'en'}.json'),`,
    `        loadDayjs: ${hasDayjsLocale ? `() => import('dayjs/locale/${code}')` : 'null'}`,
    '    },'
].join('\n')
writeFileSync(REGISTRY, registrySource.replace(/^(\s*)\/\/ add-locale:entries/m, `$1${entry}\n$1// add-locale:entries`))

// 3. Backend enum constant -- the server validates the stored profile language against it.
writeFileSync(BACKEND_ENUM, enumSource.replace(/^(\s*)\/\/ add-locale:entries/m, `$1${code.toUpperCase()}("${code}"),\n$1// add-locale:entries`))

const rel = (path) => relative(process.cwd(), path)
console.log(`Added '${code}' (${name}) as a draft.

  ${rel(target)}/  ${files.length} files copied from English
  ${rel(REGISTRY)}  draft entry
  ${rel(BACKEND_ENUM)}  ${code.toUpperCase()}("${code}")
`)
for (const warning of warnings) console.log(`warning: ${warning}`)
console.log(`
Next steps:
  1. Translate the values in ${rel(target)}/ (keys must stay identical to English).
     npm run i18n:status -- ${code} --list   prints every string still to translate.
  2. If the language has more than two plural forms, add a \`pluralRule\` to its registry entry
     and a test like the Ukrainian one in src/locales/locales.test.js.
  3. Preview with \`npm run dev\` -- drafts appear in the language selector there only.
  4. Optional: backend/src/main/resources/home-content_${code}.json for translated home-page tips.
  5. When coverage is at least 95%, set status: 'ready' in registry.js and run \`npm run test:run\`.`)
