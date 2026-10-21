import { createFeatureCollection, toFiniteNumber } from '@/maps/vector/utils/maplibreLayerUtils'
import { getOutlineColor } from '@/maps/shared/mapAppearance'
import {
  buildHighlightedTripData,
  createEmptyHighlightedTripData
} from '@/maps/shared/highlightedTripData'

const createEmptyHighlightedData = () => ({
  lineCollection: createFeatureCollection([]),
  ...createEmptyHighlightedTripData(),
  fullLineCoordinates: []
})

export const normalizePathCoordinates = (pathGroup) => {
  if (!Array.isArray(pathGroup)) {
    return []
  }

  return pathGroup
    .map((point) => {
      const latitude = toFiniteNumber(point?.latitude)
      const longitude = toFiniteNumber(point?.longitude)

      if (latitude === null || longitude === null) {
        return null
      }

      return [longitude, latitude]
    })
    .filter(Boolean)
}

// pathPieces: per group, its day pieces ({ color, muted, points }). Pieces of one group share its pathIndex and
// pathRaw, so clicks still report the whole group; `color`/`outlineColor`/`muted` drive the line paint.
export const buildPathCollection = (pathData, pathPieces = null) => {
  const features = []

  ;(Array.isArray(pathData) ? pathData : []).forEach((pathGroup, pathIndex) => {
    const pieces = pathPieces?.[pathIndex]
    const lines = (pieces || [{ points: pathGroup, color: null }])
      .map(({ points, color, muted }) => ({ coordinates: normalizePathCoordinates(points), color, muted }))
      .filter(({ coordinates }) => coordinates.length >= 2)
    if (!lines.length) {
      return
    }

    const pathRaw = JSON.stringify(pathGroup || [])
    lines.forEach(({ coordinates, color, muted }) => {
      features.push({
        type: 'Feature',
        geometry: {
          type: 'LineString',
          coordinates
        },
        properties: {
          pathIndex,
          pathRaw,
          ...(color ? { color, outlineColor: getOutlineColor(color) } : {}),
          ...(muted ? { muted: true } : {})
        }
      })
    })
  })

  return createFeatureCollection(features)
}

export const buildHighlightedData = ({
  highlightedTrip,
  pathData,
  allowPathDataFallback = false
}) => {
  if (!highlightedTrip || highlightedTrip.type !== 'trip') {
    return createEmptyHighlightedData()
  }

  const highlightedData = buildHighlightedTripData({
    highlightedTrip,
    pathData,
    allowPathDataFallback
  })
  if (!highlightedData.renderedTripPoints.length) {
    return createEmptyHighlightedData()
  }

  const lineCollection = createFeatureCollection(highlightedData.highlightedSegments.segments.map((segment) => ({
    type: 'Feature',
    geometry: {
      type: 'LineString',
      coordinates: segment.coordinates
    },
    properties: {
      speedBand: segment.speedBand,
      tripRaw: JSON.stringify(highlightedTrip || {})
    }
  })))

  return {
    ...highlightedData,
    lineCollection,
    fullLineCoordinates: highlightedData.lineCoordinates
  }
}

export const resolvePopupAnchorCoordinate = (lineCoordinates) => {
  if (!Array.isArray(lineCoordinates) || lineCoordinates.length === 0) {
    return null
  }

  return lineCoordinates[Math.floor(lineCoordinates.length / 2)] || lineCoordinates[0]
}
