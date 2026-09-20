import { describe, expect, it, vi } from 'vitest'
import {
  installProductionConsoleSanitizer,
  redactConsoleArguments,
  wrapConsoleMethods
} from './productionConsoleSanitizer'

const transportError = () => {
  const error = new Error('Request failed with status code 401')
  error.isAxiosError = true
  error.config = {
    url: '/api/v1/public/share-links/token-in-url',
    headers: {
      Authorization: 'Bearer share-access-token-secret',
      'X-CSRF-Token': 'csrf-secret'
    }
  }
  error.response = {
    status: 401,
    data: {
      code: 'AUTHENTICATION_REQUIRED',
      requestId: 'req-1',
      errorId: 'err-1',
      secret: 'response-body-secret'
    }
  }
  return error
}

const sanitized = {
  name: 'Error',
  status: 401,
  code: 'AUTHENTICATION_REQUIRED',
  requestId: 'req-1',
  errorId: 'err-1'
}

describe('productionConsoleSanitizer', () => {
  it('redacts a transport error passed at the top level', () => {
    const [redacted] = redactConsoleArguments([transportError()])

    expect(redacted).toEqual(sanitized)
    expect(JSON.stringify(redacted)).not.toContain('secret')
  })

  it('redacts a transport error nested inside a plain object', () => {
    const redacted = redactConsoleArguments([
      '[RasterMapHost] fitBounds failed',
      { error: transportError(), bounds: [1, 2], options: { padding: 10 } }
    ])

    expect(redacted[0]).toBe('[RasterMapHost] fitBounds failed')
    expect(redacted[1].error).toEqual(sanitized)
    expect(redacted[1].bounds).toEqual([1, 2])
    expect(JSON.stringify(redacted)).not.toContain('secret')
  })

  it('redacts transport errors inside arrays and deeper nesting', () => {
    const [redacted] = redactConsoleArguments([
      { failures: [transportError(), { cause: transportError() }] }
    ])

    expect(redacted.failures[0]).toEqual(sanitized)
    expect(redacted.failures[1].cause).toEqual(sanitized)
    expect(JSON.stringify(redacted)).not.toContain('secret')
  })

  it('survives circular references without recursing forever', () => {
    const node = { name: 'node' }
    node.self = node
    node.error = transportError()

    const [redacted] = redactConsoleArguments([node])

    expect(redacted.self).toBe('[circular]')
    expect(redacted.error).toEqual(sanitized)
  })

  it('leaves plain values and non-plain objects untouched', () => {
    const when = new Date('2026-01-01T00:00:00Z')
    const map = new Map([['key', 'value']])

    const [message, count, untouchedDate, untouchedMap] = redactConsoleArguments([
      'plain message', 42, when, map
    ])

    expect(message).toBe('plain message')
    expect(count).toBe(42)
    expect(untouchedDate).toBe(when)
    expect(untouchedMap).toBe(map)
  })

  it('wraps every console method so redaction applies to log helpers too', () => {
    const warnSpy = vi.fn()
    const logSpy = vi.fn()
    const target = {
      error: vi.fn(),
      warn: warnSpy,
      log: logSpy,
      info: vi.fn(),
      debug: vi.fn()
    }
    const originals = { ...target }

    wrapConsoleMethods(target)
    target.warn('Refresh failed:', transportError())
    target.log('Nested:', { error: transportError() })

    for (const method of ['error', 'warn', 'log', 'info', 'debug']) {
      expect(target[method]).not.toBe(originals[method])
    }
    expect(warnSpy).toHaveBeenCalledWith('Refresh failed:', sanitized)
    expect(logSpy).toHaveBeenCalledWith('Nested:', { error: sanitized })
  })

  it('does not touch the real console during development', () => {
    const original = console.error

    installProductionConsoleSanitizer()

    expect(console.error).toBe(original)
  })
})
