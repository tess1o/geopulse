import { describe, expect, it } from 'vitest'
import {
  computeCrossTypeCollisions,
  estimateChipSize
} from './crossTypeMarkerCollision'

// 1 degree = 1000px on each axis, keeps the math obvious.
const makeMap = () => ({
  project: ([lng, lat]) => ({ x: lng * 1000, y: lat * 1000 })
})

const entity = (indices, longitude, latitude = 0) => ({
  indices,
  longitude,
  latitude,
  isCluster: indices.length > 1
})

// Builds { groups, entities } where every group sits at its entity's position.
const source = (entities, makeGroup = () => ({})) => {
  const groups = []
  entities.forEach((item) => item.indices.forEach((index) => {
    groups[index] = { ...makeGroup(index), longitude: item.longitude, latitude: item.latitude }
  }))
  return { groups, entities }
}

describe('computeCrossTypeCollisions', () => {
  it('merges nearby entities from different types into one collision', () => {
    const { collisions, excluded } = computeCrossTypeCollisions({
      sources: {
        timeline: source([entity([0], 0)], () => ({ items: [{}] })),
        notes: source([entity([3], 0.005)], () => ({ notes: [{}] })),
        photos: source([entity([1], 0.01)], () => ({ count: 1 }))
      },
      mapInstance: makeMap(),
      radiusPx: 26
    })

    expect(collisions).toHaveLength(1)
    expect(collisions[0].members.map((member) => member.type)).toEqual(['timeline', 'notes', 'photos'])
    expect([...excluded.notes]).toEqual([3])
  })

  it('never merges entities of the same type on their own', () => {
    const { collisions } = computeCrossTypeCollisions({
      sources: {
        notes: source([entity([0], 0), entity([1], 0.001)]),
        timeline: source([entity([0], 5)])
      },
      mapInstance: makeMap()
    })

    expect(collisions).toHaveLength(0)
  })

  it('ignores far-apart entities of different types', () => {
    const { collisions } = computeCrossTypeCollisions({
      sources: {
        timeline: source([entity([0], 0)]),
        notes: source([entity([0], 1)])
      },
      mapInstance: makeMap()
    })

    expect(collisions).toHaveLength(0)
  })

  it('merges a same-type cluster with every group it contains', () => {
    const { collisions, excluded } = computeCrossTypeCollisions({
      sources: {
        notes: source([entity([0], 0)], () => ({ notes: [{}] })),
        photos: source([entity([0, 1, 2], 0.01)], () => ({ count: 1 }))
      },
      mapInstance: makeMap()
    })

    expect(collisions).toHaveLength(1)
    expect([...excluded.photos].sort()).toEqual([0, 1, 2])
    expect(collisions[0].hasCluster).toBe(true)
  })

  it('marks collisions of standalone markers only as not holding a cluster', () => {
    const { collisions } = computeCrossTypeCollisions({
      sources: {
        timeline: source([entity([0], 0)], () => ({ items: [{}] })),
        photos: source([entity([0], 0.01)], () => ({ count: 1 }))
      },
      mapInstance: makeMap()
    })

    expect(collisions).toHaveLength(1)
    expect(collisions[0].hasCluster).toBe(false)
  })

  it('absorbs a marker the wide chip covers even beyond the collision radius', () => {
    // Timeline+notes collide at x=0; the chip is ~70px wide, so a photo 40px
    // to the side sits under it despite being outside the 32px radius.
    const { collisions, excluded } = computeCrossTypeCollisions({
      sources: {
        timeline: source([entity([0], 0)], () => ({ items: [{}] })),
        notes: source([entity([0], 0.001)], () => ({ notes: [{}] })),
        photos: source([entity([0], 0.04)], () => ({ count: 2 }))
      },
      mapInstance: makeMap()
    })

    expect(collisions).toHaveLength(1)
    expect([...excluded.photos]).toEqual([0])
  })

  it('leaves markers clear of the chip alone', () => {
    const { excluded } = computeCrossTypeCollisions({
      sources: {
        timeline: source([entity([0], 0)], () => ({ items: [{}] })),
        notes: source([entity([0], 0.001)], () => ({ notes: [{}] })),
        photos: source([entity([0], 0.2)], () => ({ count: 2 }))
      },
      mapInstance: makeMap()
    })

    expect(excluded.photos.size).toBe(0)
  })
})

describe('computeCrossTypeCollisions with a focus', () => {
  it('moves whatever sits under a trip endpoint into a chip beside it', () => {
    // Photos cluster exactly at the trip start (x=0), endpoint marker is 40px.
    const { collisions, excluded, focusActive } = computeCrossTypeCollisions({
      sources: {
        photos: source([entity([0, 1], 0)], () => ({ count: 1 }))
      },
      obstacles: [{ latitude: 0, longitude: 0, size: 40 }],
      mapInstance: makeMap()
    })

    expect(focusActive).toBe(true)
    expect(collisions).toHaveLength(1)
    expect([...excluded.photos].sort()).toEqual([0, 1])

    // Chip's left edge lands just right of the endpoint's right edge (20px).
    const [dx, dy] = collisions[0].offset
    const { width } = estimateChipSize({ photos: 2 })
    expect(dx - (width / 2)).toBeGreaterThan(20)
    expect(dy).toBe(0)
  })

  it('never merges the highlighted marker; neighbours move beside it instead', () => {
    const timeline = source([{ ...entity([0], 0), isFocused: true }], () => ({ items: [{}] }))
    const { collisions, excluded } = computeCrossTypeCollisions({
      sources: {
        timeline,
        notes: source([entity([0], 0.005)], () => ({ notes: [{}] }))
      },
      mapInstance: makeMap()
    })

    expect(excluded.timeline.size).toBe(0)
    expect([...excluded.notes]).toEqual([0])
    expect(collisions[0].members.map((member) => member.type)).toEqual(['notes'])
    expect(collisions[0].offset[0]).toBeGreaterThan(0)
  })

  it('steps past a second endpoint next to the first one', () => {
    const { collisions } = computeCrossTypeCollisions({
      sources: {
        photos: source([entity([0], 0)], () => ({ count: 1 }))
      },
      obstacles: [
        { latitude: 0, longitude: 0, size: 40, offsetX: -14 },
        { latitude: 0, longitude: 0, size: 40, offsetX: 14 }
      ],
      mapInstance: makeMap()
    })

    const { width } = estimateChipSize({ photos: 1 })
    // Right edge of the second endpoint is 14 + 20 = 34px.
    expect(collisions[0].offset[0] - (width / 2)).toBeGreaterThan(34)
  })

  it('leaves markers alone when nothing is focused', () => {
    const { collisions, focusActive } = computeCrossTypeCollisions({
      sources: {
        photos: source([entity([0], 0)], () => ({ count: 1 }))
      },
      mapInstance: makeMap()
    })

    expect(focusActive).toBe(false)
    expect(collisions).toHaveLength(0)
  })
})

describe('estimateChipSize', () => {
  it('grows with the number of types and digits', () => {
    const one = estimateChipSize({ notes: 3 })
    const two = estimateChipSize({ notes: 3, photos: 1 })
    const wide = estimateChipSize({ notes: 3, photos: 120 })

    expect(two.width).toBeGreaterThan(one.width)
    expect(wide.width).toBeGreaterThan(two.width)
    expect(one.height).toBe(28)
  })
})
