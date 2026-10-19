import { describe, expect, it } from 'vitest'
import { usePendingGpsEdits } from './usePendingGpsEdits'

const point = (id, overrides = {}) => ({
  id,
  timestamp: '2026-08-13T10:18:57Z',
  coordinates: { lat: 50.45, lng: 30.52 },
  velocity: 4.8312,
  accuracy: 12,
  battery: 80,
  ...overrides
})

const changesFrom = (row, overrides = {}) => ({
  coordinates: { ...row.coordinates },
  velocity: row.velocity,
  accuracy: row.accuracy,
  ...overrides
})

describe('usePendingGpsEdits', () => {
  it('stages a change and overlays it on the loaded row', () => {
    const edits = usePendingGpsEdits()
    const row = point(1)

    expect(edits.stage(row, changesFrom(row, { velocity: 0 }))).toBe(true)

    expect(edits.pendingCount.value).toBe(1)
    expect(edits.hasPendingEdits.value).toBe(true)
    const shown = edits.applyTo(row)
    expect(shown.velocity).toBe(0)
    expect(shown.battery).toBe(80)
    expect(shown.pendingEdit).toBe(true)
    expect(edits.applyTo(point(2)).pendingEdit).toBeUndefined()
  })

  it('drops the entry when a change is edited back to the original', () => {
    const edits = usePendingGpsEdits()
    const row = point(1)
    edits.stage(row, changesFrom(row, { velocity: 0 }))

    // Re-editing from the overlaid row still compares against the server values
    const shown = edits.applyTo(row)
    expect(edits.stage(shown, changesFrom(row))).toBe(false)

    expect(edits.hasPendingEdits.value).toBe(false)
  })

  it('ignores a save without real changes', () => {
    const edits = usePendingGpsEdits()
    const row = point(1)

    expect(edits.stage(row, changesFrom(row, { coordinates: { lat: 50.4500001, lng: 30.52 } }))).toBe(false)
    expect(edits.pendingCount.value).toBe(0)
  })

  it('keeps the server row as original across repeated edits', () => {
    const edits = usePendingGpsEdits()
    const row = point(1)
    edits.stage(row, changesFrom(row, { velocity: 1 }))
    edits.stage(edits.applyTo(row), changesFrom(row, { velocity: 2 }))

    expect(edits.getOriginal(edits.applyTo(row))).toBe(row)
    expect(edits.getChanges(1).velocity).toBe(2)
  })

  it('reverts single points, deleted points, and everything', () => {
    const edits = usePendingGpsEdits()
    ;[1, 2, 3].forEach(id => edits.stage(point(id), changesFrom(point(id), { accuracy: 99 })))

    edits.revert(1)
    expect(edits.isPending(1)).toBe(false)
    edits.dropIds([2])
    expect(edits.pendingCount.value).toBe(1)
    edits.revertAll()
    expect(edits.hasPendingEdits.value).toBe(false)
  })

  it('builds the bulk request payload', () => {
    const edits = usePendingGpsEdits()
    const row = point(7)
    edits.stage(row, changesFrom(row, { coordinates: { lat: 51, lng: 31 }, velocity: undefined }))

    expect(edits.toRequestEdits()).toEqual([
      { id: 7, coordinates: { lat: 51, lng: 31 }, velocity: null, accuracy: 12 }
    ])
  })
})
