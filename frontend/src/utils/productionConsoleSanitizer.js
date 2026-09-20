import { productionErrorContext } from './apiErrorDetail'

const CONSOLE_METHODS = ['error', 'warn', 'log', 'info', 'debug']
const MAX_DEPTH = 3

const isTransportError = value => value instanceof Error
  || value?.isAxiosError === true
  || value?.response != null
  || value?.config != null

const isPlainObject = value => {
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}

const redactValue = (value, depth, seen) => {
  if (isTransportError(value)) return productionErrorContext(value)
  if (depth <= 0 || value === null || typeof value !== 'object') return value
  if (seen.has(value)) return '[circular]'

  seen.add(value)
  try {
    if (Array.isArray(value)) {
      return value.map(entry => redactValue(entry, depth - 1, seen))
    }
    if (isPlainObject(value)) {
      return Object.fromEntries(
        Object.entries(value).map(([key, entry]) => [key, redactValue(entry, depth - 1, seen)])
      )
    }
    return value
  } finally {
    seen.delete(value)
  }
}

/**
 * Replaces transport errors with the sanitized error context. Nested plain objects and arrays are
 * traversed too, so an error handed to the console inside another object (or in an array of
 * errors) is redacted as well - a top-level-only check would let those through untouched.
 * Non-plain objects (Map, Date, DOM nodes, class instances) are passed through unchanged.
 */
export const redactConsoleArguments = (values, depth = MAX_DEPTH) =>
  values.map(value => redactValue(value, depth, new Set()))

/**
 * Wraps the console methods of `target` so their arguments are redacted before printing.
 * Kept separate from installation so it can be exercised with a fake target.
 */
export const wrapConsoleMethods = (target = console) => {
  for (const method of CONSOLE_METHODS) {
    const original = target[method].bind(target)
    target[method] = (...values) => original(...redactConsoleArguments(values))
  }
  return target
}

export const installProductionConsoleSanitizer = () => {
  if (import.meta.env.DEV) return
  wrapConsoleMethods(console)
}
