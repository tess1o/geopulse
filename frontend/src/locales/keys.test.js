import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { describe, expect, it } from 'vitest'
import en from './en/index.js'

/**
 * Every translation key named statically in source must exist in the catalog.
 *
 * A key that does not resolve renders as its own dotted path, which looks like a bug to a user and is
 * easy to introduce when a component is translated against a catalog that is still being written --
 * nothing else catches it, because the component still renders and no test asserts on that branch.
 *
 * Dynamic keys (`t(`prefix.${x}`)`) cannot be checked this way; those are covered by the component
 * tests that exercise them.
 */

// vitest runs with the project root as cwd, so a relative path is stable -- import.meta.url is not
// usable here (it does not resolve to a real filesystem path under the test environment).
const SRC = join(process.cwd(), 'src')
const SCAN_DIRS = ['components', 'views', 'composables', 'utils', 'stores', 'constants']
const SKIP_DIRS = new Set(['node_modules', 'dist'])

const flattenKeys = (value, prefix = '') =>
  Object.entries(value).flatMap(([key, entry]) => {
    const path = prefix ? `${prefix}.${key}` : key
    return entry && typeof entry === 'object' && !Array.isArray(entry)
      ? flattenKeys(entry, path)
      : [path]
  })

const walk = (dir, out = []) => {
  for (const entry of readdirSync(dir)) {
    if (SKIP_DIRS.has(entry)) continue
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      walk(full, out)
    } else if (/\.(vue|js)$/.test(entry) && !/\.test\.js$/.test(entry)) {
      out.push(full)
    }
  }
  return out
}

describe('translation keys referenced in source', () => {
  const catalogKeys = new Set(flattenKeys(en))

  it('all resolve against the English catalog', () => {
    const files = SCAN_DIRS.flatMap((dir) => walk(join(SRC, dir)))
    const missing = new Map()

    for (const file of files) {
      const source = readFileSync(file, 'utf8')
      for (const match of source.matchAll(/\bt\(\s*'([a-zA-Z][\w.-]*)'/g)) {
        const key = match[1]
        if (!catalogKeys.has(key)) {
          if (!missing.has(key)) missing.set(key, new Set())
          missing.get(key).add(relative(SRC, file))
        }
      }
    }

    expect(
      [...missing].map(([key, where]) => `${key} <- ${[...where].join(', ')}`)
    ).toEqual([])
  })

  it('scans a meaningful number of files, so a broken path cannot pass vacuously', () => {
    const files = SCAN_DIRS.flatMap((dir) => walk(join(SRC, dir)))
    expect(files.length).toBeGreaterThan(100)
  })

  // A file that calls t() without pulling it in compiles fine and only fails when rendered, so the
  // mistake survives a build and a type-free lint. Cheap to check, and it is the exact error the
  // mechanical "replace every literal with t('key')" step tends to leave behind.
  it('every file calling t() actually has t in scope', () => {
    const files = SCAN_DIRS.flatMap((dir) => walk(join(SRC, dir)))
    const offenders = []

    for (const file of files) {
      const source = readFileSync(file, 'utf8')
      // `$t(` is the globally-injected form and needs no import; `something.t(` is a member access.
      if (!/(?<![\w$.])t\(\s*'/.test(source)) continue

      const hasComposer = /\buseI18n\s*\(/.test(source)
      const hasNamedImport = /import\s*\{[^}]*\bt\b[^}]*\}\s*from\s*'@\/locales'/.test(source)
      if (!hasComposer && !hasNamedImport) {
        offenders.push(relative(SRC, file))
      }
    }

    expect(offenders).toEqual([])
  })
})
