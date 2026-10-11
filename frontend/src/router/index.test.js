import { describe, expect, it } from 'vitest'
import router, { routeRequiresSession } from './index'

describe('routeRequiresSession', () => {
  it.each([
    '/app/timeline',
    '/app/trips/12',
    '/app/share-links',
    '/app/admin/users'
  ])('is true for the signed-in page %s', (path) => {
    expect(routeRequiresSession(router.resolve(path))).toBe(true)
  })

  it.each([
    '/',
    '/login',
    '/shared/11111111-1111-1111-1111-111111111111',
    '/shared-timeline/11111111-1111-1111-1111-111111111111',
    '/error'
  ])('is false for the public page %s', (path) => {
    expect(routeRequiresSession(router.resolve(path))).toBe(false)
  })

  it('is false before the first navigation resolves', () => {
    expect(routeRequiresSession(router.currentRoute.value)).toBe(false)
  })
})
