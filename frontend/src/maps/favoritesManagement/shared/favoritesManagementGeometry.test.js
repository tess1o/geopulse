import {describe, expect, it} from 'vitest'
import {getAreaBounds, getAreaPolygonLngLat, toLeafletPolygon, areaPayload, areaGeometryKey, polygonError, getAreaCenterLatLng} from './favoritesManagementGeometry'
const ring = [[30,50],[30.03,50],[30.03,50.01],[30.01,50.01],[30.01,50.03],[30,50.03],[30,50]]
const geometry = {type: 'Polygon', coordinates: [ring]}
describe('favorite polygons', () => {
  it('keeps concavity and longitude/latitude order through payload and both renderers', () => {
    const area = {geometry, northEastLat: 99}
    expect(areaPayload(area)).toEqual({geometry})
    expect(getAreaPolygonLngLat(area)).toEqual(ring)
    expect(toLeafletPolygon(area)).toEqual(ring.map(([lon,lat]) => [lat,lon]))
    expect(getAreaBounds(area)).toEqual({northEastLat:50.03,northEastLon:30.03,southWestLat:50,southWestLon:30})
    expect(getAreaCenterLatLng({...area,latitude:50.02,longitude:30.005})).toEqual({lat:50.02,lng:30.005})
  })
  it('centers pending polygon markers inside the shape instead of on its first corner', () => {
    const rectangle = {type:'Polygon',coordinates:[[[0,0],[4,0],[4,2],[0,2],[0,0]]]}
    expect(getAreaCenterLatLng({geometry:rectangle})).toEqual({lat:1,lng:2})
    const center = getAreaCenterLatLng({geometry})
    // The L's bounding-box center is outside; its vertical arm is inside.
    expect(center.lat).toBeCloseTo(50.02)
    expect(center.lng).toBeCloseTo(30.005)
    expect(center).not.toEqual({lat:ring[0][1],lng:ring[0][0]})
    const reversed = {type:'Polygon',coordinates:[[...ring].reverse()]}
    expect(getAreaCenterLatLng({geometry:reversed})).toEqual(center)
  })
  it('retains legacy rectangles and compares full geometry independently of ring order', () => {
    const bounds={northEastLat:51,northEastLon:31,southWestLat:50,southWestLon:30}
    expect(getAreaPolygonLngLat(bounds)).toHaveLength(5)
    expect(areaPayload(bounds)).toEqual(bounds)
    const reversed = [...ring].reverse()
    expect(areaGeometryKey({geometry})).toBe(areaGeometryKey({geometry:{type:'Polygon',coordinates:[reversed]}}))
    const vertices = ring.slice(0,-1), rotated=[...vertices.slice(2),...vertices.slice(0,2),vertices[2]]
    expect(areaGeometryKey({geometry})).toBe(areaGeometryKey({geometry:{type:'Polygon',coordinates:[rotated]}}))
    const changed=structuredClone(geometry); changed.coordinates[0][3]=[30.02,50.02]
    expect(areaGeometryKey({geometry})).not.toBe(areaGeometryKey({geometry:changed}))
  })
  it('validates simple closed shapes before submission', () => {
    expect(polygonError(ring)).toBeNull()
    expect(polygonError([])).toBe('vertices')
    expect(polygonError([[0,0],[1,1],[0,1],[1,0],[0,0]])).toBe('crossing')
    expect(polygonError([[0,0],[1,0],[2,0],[0,0]])).toBe('crossing')
    expect(polygonError([[179,0],[-179,0],[-179,1],[179,0]])).toBe('antimeridian')
    expect(polygonError([[0,0],[1,1],[0,1],[0,2]])).toBe('closed')
  })
})
