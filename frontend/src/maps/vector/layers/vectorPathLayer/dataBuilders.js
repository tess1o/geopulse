import { createFeatureCollection, toFiniteNumber } from '@/maps/vector/utils/maplibreLayerUtils'
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

export const buildPathCollection = (pathData) => {
  const features = []

  ;(Array.isArray(pathData) ? pathData : []).forEach((pathGroup, pathIndex) => {
    const coordinates = normalizePathCoordinates(pathGroup)
    if (coordinates.length < 2) {
      return
    }

    features.push({
      type: 'Feature',
      geometry: {
        type: 'LineString',
        coordinates
      },
      properties: {
        pathIndex,
        pathRaw: JSON.stringify(pathGroup || [])
      }
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
