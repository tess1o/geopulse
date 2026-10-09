import { describe, expect, it } from 'vitest'
import { buildLoginLocation, buildLoginUrl, getSafeLoginRedirect } from './loginRedirect'

describe('getSafeLoginRedirect', () => {
  it('keeps in-app paths with their query', () => {
    expect(getSafeLoginRedirect('/app/profile?tab=general&setting=timeDisplayMode'))
      .toBe('/app/profile?tab=general&setting=timeDisplayMode')
    expect(getSafeLoginRedirect('/app')).toBe('/app')
    expect(getSafeLoginRedirect(['/app/timeline'])).toBe('/app/timeline')
  })

  it('rejects anything outside the app', () => {
    for (const value of [
      undefined, null, '', '/', '/login', '/application', 'app/timeline',
      'https://example.com/app', '//example.com/app', '/\\\\example.com', '/app\\\\..\\\\x'
    ]) {
      expect(getSafeLoginRedirect(value)).toBeNull()
    }
  })
})

describe('buildLoginLocation', () => {
  it('carries the requested app page to the login route', () => {
    expect(buildLoginLocation({ fullPath: '/app/profile?tab=appearance' }))
      .toEqual({ path: '/login', query: { redirect: '/app/profile?tab=appearance' } })
  })

  it('falls back to plain login without a usable path', () => {
    expect(buildLoginLocation(undefined)).toBe('/login')
    expect(buildLoginLocation({ fullPath: '/' })).toBe('/login')
  })
})

describe('buildLoginUrl', () => {
  it('encodes the current app page as the return path', () => {
    expect(buildLoginUrl('/app/coverage?from=2026-01-01&to=2026-01-31'))
      .toBe('/login?redirect=%2Fapp%2Fcoverage%3Ffrom%3D2026-01-01%26to%3D2026-01-31')
  })

  it('falls back to plain login outside the app', () => {
    expect(buildLoginUrl('/')).toBe('/login')
    expect(buildLoginUrl('/shared/abc')).toBe('/login')
  })
})
