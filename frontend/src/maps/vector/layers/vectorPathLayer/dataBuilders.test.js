import { describe, expect, it } from 'vitest'
import { buildPathCollection } from './dataBuilders'

const point = (latitude, longitude, timestamp) => ({ latitude, longitude, timestamp })

describe('buildPathCollection', () => {
  const groups = [
    [point(50, 30, 't1'), point(50.1, 30.1, 't2'), point(50.2, 30.2, 't3')],
    [point(51, 31, 't4'), point(51.1, 31.1, 't5')]
  ]

  it('draws one uncolored feature per group by default', () => {
    const collection = buildPathCollection(groups)

    expect(collection.features).toHaveLength(2)
    expect(collection.features[0].properties).toEqual({ pathIndex: 0, pathRaw: JSON.stringify(groups[0]) })
  })

  it('draws day-colored pieces that keep their group index and points for clicks', () => {
    const pieces = [
      [
        { color: '#0072B2', points: groups[0].slice(0, 2) },
        { color: '#fde725', points: groups[0].slice(1) }
      ],
      [{ color: '#E69F00', points: groups[1] }]
    ]

    const collection = buildPathCollection(groups, pieces)

    expect(collection.features).toHaveLength(3)
    expect(collection.features.map((feature) => feature.properties.pathIndex)).toEqual([0, 0, 1])
    expect(collection.features[1].properties).toEqual({
      pathIndex: 0,
      pathRaw: JSON.stringify(groups[0]),
      color: '#fde725',
      outlineColor: '#111827'
    })
    expect(collection.features[0].properties.outlineColor).toBe('#ffffff')
    expect(collection.features[1].geometry.coordinates).toEqual([[30.1, 50.1], [30.2, 50.2]])
  })

  it('marks the pieces of days other than the focused one as muted', () => {
    const pieces = [
      [
        { color: null, muted: true, points: groups[0].slice(0, 2) },
        { color: null, muted: false, points: groups[0].slice(1) }
      ],
      [{ color: null, muted: true, points: groups[1] }]
    ]

    const properties = buildPathCollection(groups, pieces).features.map((feature) => feature.properties)

    expect(properties.map((entry) => entry.muted)).toEqual([true, undefined, true])
    expect(properties.every((entry) => entry.color === undefined)).toBe(true)
  })

  it('skips a group whose pieces were all too short', () => {
    expect(buildPathCollection(groups, [[], [{ color: '#E69F00', points: groups[1] }]]).features).toHaveLength(1)
  })
})
