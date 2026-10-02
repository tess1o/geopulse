import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

// Guards the theming conventions from docs/CSS_THEMING_CLEANUP.md: every --gp-* token lives in tokens.css,
// dark mode is the `.p-dark` class on <html>, and only tokens.css gives a token a dark value.
// Reads the sources from disk: Vite would rewrite `?raw` / `new URL()` imports of .css files.

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const TOKENS_FILE = 'styles/tokens.css'

// --gp-* names set from JS (inline style), so they have no declaration in tokens.css.
const RUNTIME_TOKENS = {
  '--gp-friend-marker-color': 'maps/vector/layers/VectorFriendsLayer.vue',
  '--gp-navbar-datepicker-width': 'components/ui/layout/AppNavbarWithDatePicker.vue'
}

// PrimeVue 3 theme variables. PrimeVue 4 only defines --p-* names, so these resolve to nothing (or a stale fallback).
const PRIMEVUE_3_VARIABLE = new RegExp(`^--(${[
  'surface-[\\w-]+',
  'text-color(-secondary)?',
  'primary-color(-text)?',
  'highlight-(bg|text-color)',
  'focus-ring',
  'maskbg',
  'content-padding',
  'inline-spacing',
  'border-radius',
  'font-family',
  'disabled-opacity',
  '(primary|blue|green|yellow|cyan|pink|indigo|teal|orange|bluegray|purple|red|gray)-\\d+',
  // PrimeVue 3 names with the PrimeVue 4 prefix, which don't exist either
  'p-surface-(?!\\d)[\\w-]+',
  'p-text-color-secondary',
  'p-shadow-[\\w-]+'
].join('|')})$`)

const P_DARK = /(?<![\w-])\.p-dark(?![\w-])/

const walk = (dir) => readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
  const path = join(dir, entry.name)
  if (entry.isDirectory()) return walk(path)
  return /\.(vue|css|js)$/.test(entry.name) && !/\.test\.js$/.test(entry.name) ? [path] : []
})

// Blanks /* comments */ but keeps their newlines, so offsets and line numbers still match the file.
const blankComments = (css) => css.replace(/\/\*[\s\S]*?\*\//g, (comment) => comment.replace(/[^\n]/g, ' '))

const files = walk(SRC).map((path) => ({ path: relative(SRC, path), text: readFileSync(path, 'utf8') }))
const fileByPath = new Map(files.map((file) => [file.path, file]))

// Every piece of CSS: whole .css files and the <style> blocks of .vue files. A `<style scoped src="…">` block makes
// its .css file scoped as well.
const cssBlocks = files.flatMap((file) => {
  if (file.path.endsWith('.css')) return [{ file, offset: 0, css: blankComments(file.text), scoped: false }]
  if (!file.path.endsWith('.vue')) return []
  return [...file.text.matchAll(/<style\b([^>]*)>([\s\S]*?)<\/style>/g)].flatMap((match) => {
    const scoped = /\bscoped\b/.test(match[1])
    const src = match[1].match(/\bsrc=["']([^"']+)["']/)?.[1]
    const blocks = [{ file, offset: match.index + match[0].indexOf('>') + 1, css: blankComments(match[2]), scoped }]
    const srcFile = src && fileByPath.get(relative(SRC, resolve(SRC, dirname(file.path), src)))
    if (srcFile) blocks.push({ file: srcFile, offset: 0, css: blankComments(srcFile.text), scoped })
    return blocks
  })
})

const location = (file, index) => `${file.path}:${file.text.slice(0, index).split('\n').length}`

const cssMatches = (regex, blocks = cssBlocks) => blocks.flatMap(({ file, offset, css }) =>
  [...css.matchAll(regex)].map((match) => `${location(file, offset + match.index)} ${match[0].trim()}`))

// Custom properties used anywhere: var() in CSS, templates and inline styles, plus '--x' string literals in scripts
// (readCssToken, setProperty, :style keys).
const customPropertyUses = () => files.flatMap((file) => {
  const text = file.path.endsWith('.css') ? blankComments(file.text) : file.text
  const uses = [...text.matchAll(/var\(\s*(--[\w-]+)/g)].map((match) => ({ name: match[1], index: match.index }))
  if (!file.path.endsWith('.css')) {
    uses.push(...[...text.matchAll(/(['"`])(--[\w-]+)\1/g)].map((match) => ({ name: match[2], index: match.index })))
  }
  return uses.map((use) => ({ ...use, at: location(file, use.index) }))
})

// Custom property declarations inside a rule whose selector, or an enclosing one, mentions .p-dark.
const darkCustomPropertyDeclarations = ({ file, offset, css }) => {
  const found = []
  const selectors = []
  let start = 0
  for (let i = 0; i < css.length; i++) {
    const char = css[i]
    if (char === '"' || char === "'") {
      i = css.indexOf(char, i + 1)
      if (i === -1) break
      continue
    }
    if (char !== '{' && char !== '}' && char !== ';') continue
    const chunk = css.slice(start, i)
    if (char === '{') {
      selectors.push(chunk)
    } else {
      if (/^\s*--[\w-]+\s*:/.test(chunk) && selectors.some((selector) => P_DARK.test(selector))) {
        found.push(`${location(file, offset + start + chunk.search(/\S/))} ${chunk.trim()}`)
      }
      if (char === '}') selectors.pop()
    }
    start = i + 1
  }
  return found
}

const tokensBlock = cssBlocks.find((block) => block.file.path === TOKENS_FILE)
const otherCssBlocks = cssBlocks.filter((block) => block.file.path !== TOKENS_FILE)
const definedTokens = new Set([
  ...[...tokensBlock.css.matchAll(/(?<![\w-])(--gp-[\w-]+)\s*:/g)].map((match) => match[1]),
  ...Object.keys(RUNTIME_TOKENS)
])

describe('CSS theming conventions', () => {
  it('uses no undefined --gp-* names', () => {
    const undefinedUses = customPropertyUses()
      .filter(({ name }) => name.startsWith('--gp-') && !definedTokens.has(name))
      .map(({ at, name }) => `${at} ${name}`)
    expect(undefinedUses).toEqual([])
  })

  it('still sets the runtime --gp-* names it allows', () => {
    const stale = Object.entries(RUNTIME_TOKENS)
      .filter(([name, path]) => !fileByPath.get(path)?.text.includes(`'${name}'`))
      .map(([name, path]) => `${name} is no longer set in ${path}`)
    expect(stale).toEqual([])
  })

  it('declares --gp-* tokens only in tokens.css', () => {
    expect(cssMatches(/(?<![\w-])--gp-[\w-]+\s*:/g, otherCssBlocks)).toEqual([])
  })

  it('uses no PrimeVue 3 variables', () => {
    const primeVue3Uses = customPropertyUses()
      .filter(({ name }) => PRIMEVUE_3_VARIABLE.test(name))
      .map(({ at, name }) => `${at} ${name}`)
    expect(primeVue3Uses).toEqual([])
  })

  it('switches dark mode only through the .p-dark class', () => {
    expect(cssMatches(/html\.dark|\[data-theme|(?<![\w-])\.dark(?![\w-])|prefers-color-scheme/g)).toEqual([])
  })

  it('has no :global(.p-dark) or :deep() dark selectors that compile wrong', () => {
    // `:global(.p-dark) .x` compiles to a bare `.p-dark {}` that styles <html>; `.p-dark :deep(…)` never matches.
    expect(cssMatches(/:global\(\s*\.p-dark\s*\)\s*[^\s{,][^{,]*|\.p-dark\s*:deep\(|:deep\(\s*\.p-dark/g)).toEqual([])
  })

  it('has no :root inside <style scoped>', () => {
    // Compiles to `[data-v-…]:root`, which never matches.
    expect(cssMatches(/:root(?![\w-])/g, cssBlocks.filter((block) => block.scoped))).toEqual([])
  })

  it('gives custom properties a dark value only in tokens.css', () => {
    expect(otherCssBlocks.flatMap(darkCustomPropertyDeclarations)).toEqual([])
  })

  it('parses tokens.css', () => {
    // Sanity check for the helpers above, so an empty match can't pass the other tests silently.
    expect(definedTokens.has('--gp-surface-card')).toBe(true)
    expect(darkCustomPropertyDeclarations(tokensBlock).length).toBeGreaterThan(50)
    expect(cssBlocks.some((block) => block.scoped && block.file.path.endsWith('timeline-card.css'))).toBe(true)
  })
})
