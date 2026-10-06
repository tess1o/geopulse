import { describe, expect, it } from 'vitest'
import {
  UNSCHEDULED_KEY,
  applyReorderPayload,
  buildReorderPayload,
  buildSequenceMap,
  buildStraightLegs,
  comparePlanItems,
  groupPlanItemsByDay,
  hasPlanCoordinates,
  sortPlanItems
} from './tripPlanOrder'

const stop = (id, plannedDay, orderIndex, extra = {}) => ({ id, plannedDay, orderIndex, ...extra })

describe('tripPlanOrder', () => {
  it('orders by day first, then position, with unscheduled last', () => {
    // A later day with a lower position number must not jump ahead of an earlier day -
    // the issue #623 complaint about "order first, then date".
    const items = [
      stop(1, null, 0),
      stop(2, '2026-05-02', 0),
      stop(3, '2026-05-01', 7),
      stop(4, '2026-05-01', 2),
      stop(5, '2026-05-02', 0, { priority: 'MUST' })
    ]

    expect(sortPlanItems(items).map((item) => item.id)).toEqual([4, 3, 2, 5, 1])
  })

  it('does not rank MUST stops ahead of their position', () => {
    const a = stop(1, '2026-05-01', 0, { priority: 'OPTIONAL' })
    const b = stop(2, '2026-05-01', 1, { priority: 'MUST' })
    expect(comparePlanItems(a, b)).toBeLessThan(0)
  })

  it('groups by day and can add empty drop-target groups', () => {
    const sorted = sortPlanItems([stop(1, '2026-05-02', 0), stop(2, null, 1)])

    const groups = groupPlanItemsByDay(sorted, {
      extraDays: ['2026-05-01', '2026-05-02', '2026-05-03'],
      includeUnscheduled: true
    })

    expect(groups.map((group) => group.key)).toEqual(['2026-05-01', '2026-05-02', '2026-05-03', UNSCHEDULED_KEY])
    expect(groups.map((group) => group.items.length)).toEqual([0, 1, 0, 1])
    expect(groups[3].day).toBeNull()
  })

  it('builds the full reorder payload from the groups', () => {
    const groups = [
      { key: '2026-05-01', day: '2026-05-01', items: [stop(3), stop(1)] },
      { key: UNSCHEDULED_KEY, day: null, items: [stop(2)] }
    ]

    expect(buildReorderPayload(groups)).toEqual([
      { id: 3, plannedDay: '2026-05-01' },
      { id: 1, plannedDay: '2026-05-01' },
      { id: 2, plannedDay: null }
    ])
  })

  it('applies a reorder payload locally', () => {
    const items = [stop(1, null, 0, { title: 'A' }), stop(2, null, 1, { title: 'B' })]
    const next = applyReorderPayload(items, [{ id: 2, plannedDay: '2026-05-01' }, { id: 1, plannedDay: null }])

    expect(next).toEqual([
      stop(2, '2026-05-01', 0, { title: 'B' }),
      stop(1, null, 1, { title: 'A' })
    ])
  })

  it('numbers stops in plan order', () => {
    const map = buildSequenceMap([stop(9), stop(4), stop(7)])
    expect([...map.entries()]).toEqual([[9, 1], [4, 2], [7, 3]])
  })

  it('treats null and empty coordinates as missing', () => {
    expect(hasPlanCoordinates({ latitude: 0, longitude: 0 })).toBe(true)
    expect(hasPlanCoordinates({ latitude: null, longitude: 10 })).toBe(false)
    expect(hasPlanCoordinates({ latitude: '', longitude: 10 })).toBe(false)
    expect(hasPlanCoordinates({})).toBe(false)
  })

  it('connects consecutive located stops with straight legs, skipping unlocated ones', () => {
    const legs = buildStraightLegs([
      stop(1, null, 0, { latitude: 1, longitude: 2 }),
      stop(2, null, 1),
      stop(3, null, 2, { latitude: 3, longitude: 4 }),
      stop(4, null, 3, { latitude: 5, longitude: 6 })
    ])

    expect(legs).toEqual([
      { fromItemId: 1, toItemId: 3, routed: false, coordinates: [[1, 2], [3, 4]] },
      { fromItemId: 3, toItemId: 4, routed: false, coordinates: [[3, 4], [5, 6]] }
    ])
  })
})
