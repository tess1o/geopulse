import { describe, expect, it } from 'vitest'
import { stayPlace, placePayload, placeKey, samePlace, canMergeTimelineEvents } from './timelineEditPlace'

const point = { latitude: 0, longitude: 0, locationName: 'Home' }

describe('timeline edit places', () => {
  it.each([
    [{ ...point, favoriteId: 1, geocodingId: 2 }, 'favorite'],
    [{ ...point, geocodingId: 2 }, 'known'],
    [point, 'custom']
  ])('extracts a stay place with source %s', (stay, source) => {
    expect(stayPlace(stay)).toEqual({ ...point, favoriteId: stay.favoriteId ?? null, geocodingId: stay.geocodingId ?? null, source })
  })
  it('normalizes absent stay coordinates and references', () => {
    expect(stayPlace({ locationName: 'Unknown' })).toEqual({ favoriteId: null, geocodingId: null, latitude: null, longitude: null, locationName: 'Unknown', source: 'custom' })
  })
  it.each(['', 'right', 'start', 'end'])('builds reference-only or custom payloads with prefix %s', prefix => {
    const key = name => prefix ? prefix + name[0].toUpperCase() + name.slice(1) : name
    expect(placePayload({ ...point, favoriteId: 1, geocodingId: 2 }, prefix)).toEqual({ [key('favoriteId')]: 1 })
    expect(placePayload({ ...point, geocodingId: 2 }, prefix)).toEqual({ [key('geocodingId')]: 2 })
    expect(placePayload(point, prefix)).toEqual({ [key('locationName')]: 'Home', [key('latitude')]: 0, [key('longitude')]: 0 })
  })
  it('handles absent payloads and stable place identity', () => {
    expect(placePayload(null)).toEqual({ locationName: undefined, latitude: undefined, longitude: undefined })
    expect(placeKey({ favoriteId: 1, geocodingId: 2 })).toBe('favorite:1')
    expect(placeKey({ geocodingId: 2 })).toBe('geocoding:2')
    expect(placeKey(point)).toBe('point:0:0:Home')
    expect(placeKey(null)).toBe('point:undefined:undefined:undefined')
    expect(samePlace(null, null)).toBe(false)
    expect(samePlace(point, undefined)).toBe(false)
    expect(samePlace(point, { ...point })).toBe(true)
    expect(samePlace(point, { ...point, locationName: 'Office' })).toBe(false)
    expect(samePlace({ favoriteId: 1 }, { favoriteId: 1, ...point })).toBe(true)
    expect(samePlace({ geocodingId: 1 }, { favoriteId: 1 })).toBe(false)
  })
})

describe('timeline merge eligibility', () => {
  it('requires two events of the same type', () => {
    expect(canMergeTimelineEvents(null, { type: 'stay' })).toBe(false)
    expect(canMergeTimelineEvents({ type: 'stay' }, undefined)).toBe(false)
    expect(canMergeTimelineEvents({ type: 'trip' }, { type: 'stay' })).toBe(false)
    expect(canMergeTimelineEvents({ type: 'dataGap' }, { type: 'dataGap' })).toBe(true)
  })
  it('requires matching trip movement types', () => {
    expect(canMergeTimelineEvents({ type: 'trip', movementType: 'WALK' }, { type: 'trip', movementType: 'WALK' })).toBe(true)
    expect(canMergeTimelineEvents({ type: 'trip', movementType: 'WALK' }, { type: 'trip', movementType: 'CAR' })).toBe(false)
  })
  it.each([
    [{ favoriteId: 1 }, { favoriteId: 1 }, true],
    [{ favoriteId: 1 }, { favoriteId: 2 }, false],
    [{ favoriteId: 1 }, {}, false],
    [{}, { favoriteId: 1 }, false],
    [{ favoriteId: 1, geocodingId: 2 }, { geocodingId: 2 }, false],
    [{ geocodingId: 2 }, { geocodingId: 2 }, true],
    [{ geocodingId: 2 }, { geocodingId: 3 }, false],
    [{ geocodingId: 2 }, {}, false],
    [{}, { geocodingId: 2 }, false]
  ])('matches stay references before distance', (first, second, expected) => {
    expect(canMergeTimelineEvents({ type: 'stay', ...point, ...first }, { type: 'stay', ...point, ...second })).toBe(expected)
  })
  it.each([[199.999, true], [200, true], [200.001, false]])('merges unreferenced stays %s metres apart: %s', (meters, expected) => {
    const second = { ...point, latitude: meters / 6371000 * 180 / Math.PI }
    expect(canMergeTimelineEvents({ type: 'stay', ...point }, { type: 'stay', ...second })).toBe(expected)
  })
})
