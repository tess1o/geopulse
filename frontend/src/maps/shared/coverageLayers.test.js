import { mount } from '@vue/test-utils'
import { markRaw } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import L from 'leaflet'
import RasterCoverageLayer from '@/maps/raster/layers/RasterCoverageLayer.vue'
import VectorCoverageLayer from '@/maps/vector/layers/VectorCoverageLayer.vue'

describe('coverage cell squares', () => {
  let wrapper
  let rectangles
  let renderer

  beforeEach(() => {
    rectangles = []
    renderer = { remove: vi.fn() }
    vi.spyOn(L, 'canvas').mockReturnValue(renderer)
    vi.spyOn(L, 'layerGroup').mockImplementation(() => ({
      addTo() { return this }, clearLayers: vi.fn(), removeLayer: vi.fn()
    }))
    vi.spyOn(L, 'rectangle').mockImplementation((bounds, options) => {
      const rectangle = { bounds, options, addTo() { return this }, setBounds: vi.fn(), setStyle: vi.fn() }
      rectangles.push(rectangle)
      return rectangle
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.restoreAllMocks()
  })

  const cases = ['raster', 'vector'].flatMap(engine => [50, 60].flatMap(latitude =>
    [20, 250, 40000].map(grid => [engine, latitude, grid])))

  it.each(cases)('%s renders adjacent squares at latitude %s with %s Mercator metres per side', async (engine, latitude, grid) => {
    const origin = L.CRS.EPSG3857.project(L.latLng(latitude, -2))
    const cellX = Math.floor(origin.x / grid)
    const cellY = Math.floor(origin.y / grid)
    const cells = Object.freeze([[cellX, cellY, 1], [cellX + 1, cellY, 7], [cellX, cellY + 1, 3]])
    let squares
    let opacities

    if (engine === 'raster') {
      const map = markRaw({ removeLayer: vi.fn() })
      wrapper = mount(RasterCoverageLayer, { props: { map, gridMeters: grid } })
      await wrapper.setProps({ cells })
      squares = rectangles.map(({ bounds }) => [bounds.getSouthWest(), bounds.getNorthEast()])
      opacities = rectangles.map(({ options }) => options.fillOpacity)
      expect(L.canvas).toHaveBeenCalledExactlyOnceWith({ padding: 0.5 })
      expect(rectangles.every(({ options }) => options.renderer === renderer && options.interactive === false)).toBe(true)
    } else {
      const map = markRaw({
        style: {}, addSource: vi.fn(), getSource: vi.fn(), addLayer: vi.fn(), getLayer: vi.fn(), on: vi.fn(), off: vi.fn()
      })
      wrapper = mount(VectorCoverageLayer, { props: { map, gridMeters: grid, cells } })
      const features = map.addSource.mock.calls[0][1].data.features
      squares = features.map(({ geometry: { coordinates: [ring] } }) => {
        expect(ring).toHaveLength(5)
        expect(ring[0]).toEqual(ring[4])
        expect(ring[1]).toEqual([ring[2][0], ring[0][1]])
        expect(ring[3]).toEqual([ring[0][0], ring[2][1]])
        return [L.latLng(ring[0][1], ring[0][0]), L.latLng(ring[2][1], ring[2][0])]
      })
      opacities = features.map(({ properties }) => properties.opacity)
    }

    expect(squares).toHaveLength(3)
    squares.forEach(([sw, ne], index) => {
      const [x, y] = cells[index]
      const expectedSw = L.CRS.EPSG3857.unproject(L.point(x * grid, y * grid))
      const expectedNe = L.CRS.EPSG3857.unproject(L.point((x + 1) * grid, (y + 1) * grid))
      expect(sw.lat).toBeCloseTo(expectedSw.lat, 9)
      expect(sw.lng).toBeCloseTo(expectedSw.lng, 9)
      expect(ne.lat).toBeCloseTo(expectedNe.lat, 9)
      expect(ne.lng).toBeCloseTo(expectedNe.lng, 9)
      const projectedSw = L.CRS.EPSG3857.project(sw)
      const projectedNe = L.CRS.EPSG3857.project(ne)
      expect(projectedNe.x - projectedSw.x).toBeCloseTo(grid, 6)
      expect(projectedNe.y - projectedSw.y).toBeCloseTo(grid, 6)
    })
    // Equal boundaries and increasing opposite edges leave no gaps or overlap.
    expect(squares[0][1].lng).toBe(squares[1][0].lng)
    expect(squares[0][0].lat).toBe(squares[1][0].lat)
    expect(squares[0][1].lat).toBe(squares[2][0].lat)
    expect(squares[0][0].lng).toBe(squares[2][0].lng)
    expect(opacities[1]).toBe(0.85)
    expect(opacities[0]).toBeLessThan(opacities[2])
    expect(opacities[2]).toBeLessThan(opacities[1])
  })

  it('removes the raster canvas with its rectangles when hidden', async () => {
    const map = markRaw({ removeLayer: vi.fn() })
    wrapper = mount(RasterCoverageLayer, { props: { map, gridMeters: 20 } })
    await wrapper.setProps({ cells: [[1, 2, 3]] })
    await wrapper.setProps({ visible: false })
    expect(map.removeLayer).toHaveBeenCalledTimes(1)
    expect(renderer.remove).toHaveBeenCalledTimes(1)
  })
})
