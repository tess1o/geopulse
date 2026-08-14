import maplibregl from 'maplibre-gl'
import {
  ensureGeoJsonSource,
  ensureLayer,
  removeLayers,
  removeSources
} from '@/maps/vector/utils/maplibreLayerUtils'

const createVectorWaypointElement = (index, total) => {
  const markerVariant = index === 0 ? 'start' : (index === total - 1 ? 'end' : 'mid')
  const root = document.createElement('div')
  root.className = `trip-reconstruction-waypoint-marker trip-reconstruction-waypoint-marker--${markerVariant}`
  root.textContent = String(index + 1)
  return root
}

const createVectorStayElement = () => {
  const root = document.createElement('div')
  root.className = 'trip-reconstruction-waypoint-marker trip-reconstruction-waypoint-marker--stay'
  root.textContent = 'S'
  return root
}

const createVectorContextElement = (point) => {
  const root = document.createElement('div')
  root.className = 'trip-reconstruction-context-marker'
  root.textContent = point.label
  return root
}

export const createVectorTripReconstructionMapAdapter = (callbacks = {}) => {
  const token = callbacks.idPrefix || `trip-reconstruction-${Math.random().toString(36).slice(2, 10)}`
  const sourceId = `${token}-source`
  const layerId = `${token}-line`
  const contextSourceId = `${token}-context-source`
  const contextLayerId = `${token}-context-line`

  let map = null
  let waypointMarkers = []
  let stayMarker = null
  let contextMarkers = []

  const isMapUsable = () => {
    if (!map || map._removed === true) {
      return false
    }

    if (typeof map.getStyle !== 'function') {
      return false
    }

    try {
      return Boolean(map.getStyle())
    } catch {
      return false
    }
  }

  const clearPolyline = () => {
    if (!isMapUsable()) {
      return
    }

    removeLayers(map, [contextLayerId, layerId])
    removeSources(map, [contextSourceId, sourceId])
  }

  const clear = () => {
    waypointMarkers.forEach((marker) => {
      try {
        marker.remove()
      } catch {
        // Ignore marker cleanup errors during teardown.
      }
    })
    waypointMarkers = []

    if (stayMarker) {
      try {
        stayMarker.remove()
      } catch {
        // Ignore marker cleanup errors during teardown.
      }
      stayMarker = null
    }

    contextMarkers.forEach((marker) => {
      try {
        marker.remove()
      } catch {
        // Ignore marker cleanup errors during teardown.
      }
    })
    contextMarkers = []

    clearPolyline()
  }

  const renderContext = (contextPoints = []) => {
    if (!isMapUsable()) {
      return
    }

    contextMarkers = contextPoints.map((point) => {
      return new maplibregl.Marker({
        element: createVectorContextElement(point),
        anchor: 'center'
      })
        .setLngLat([point.longitude, point.latitude])
        .addTo(map)
    })
  }

  const renderPolyline = (segment) => {
    if (!isMapUsable()) {
      return
    }

    const coordinates = (segment?.waypoints || [])
      .filter((waypoint) => callbacks.hasValidCoordinates?.(waypoint.latitude, waypoint.longitude))
      .map((waypoint) => [waypoint.longitude, waypoint.latitude])

    if (coordinates.length < 2) {
      return
    }

    ensureGeoJsonSource(map, sourceId, {
      type: 'Feature',
      geometry: {
        type: 'LineString',
        coordinates
      },
      properties: {}
    })

    ensureLayer(map, {
      id: layerId,
      type: 'line',
      source: sourceId,
      paint: {
        'line-color': '#1d4ed8',
        'line-width': 4,
        'line-opacity': 0.85
      }
    })
  }

  const renderContextTripLines = (contextTripLines = []) => {
    if (!isMapUsable()) {
      return
    }

    const features = contextTripLines
      .map((linePoints) => ({
        type: 'Feature',
        geometry: {
          type: 'LineString',
          coordinates: linePoints.map(([latitude, longitude]) => [longitude, latitude])
        },
        properties: {}
      }))
      .filter((feature) => feature.geometry.coordinates.length >= 2)

    if (features.length === 0) {
      return
    }

    ensureGeoJsonSource(map, contextSourceId, {
      type: 'FeatureCollection',
      features
    })

    ensureLayer(map, {
      id: contextLayerId,
      type: 'line',
      source: contextSourceId,
      paint: {
        'line-color': '#0ea5e9',
        'line-width': 3,
        'line-opacity': 0.8,
        'line-dasharray': [1, 1.4]
      }
    })
  }

  const renderStay = (segment, segmentIndex) => {
    if (!isMapUsable() || !callbacks.hasValidCoordinates?.(segment?.latitude, segment?.longitude)) {
      return
    }

    stayMarker = new maplibregl.Marker({
      element: createVectorStayElement(),
      anchor: 'center',
      draggable: true
    })
      .setLngLat([segment.longitude, segment.latitude])
      .addTo(map)

    stayMarker.on('dragend', () => {
      const lngLat = stayMarker.getLngLat()
      callbacks.onStayDragged?.(segmentIndex, lngLat.lat, lngLat.lng)
    })
  }

  const renderTrip = (segment, segmentIndex) => {
    if (!isMapUsable()) {
      return
    }

    renderPolyline(segment)

    waypointMarkers = (segment?.waypoints || []).map((waypoint, index) => {
      const marker = new maplibregl.Marker({
        element: createVectorWaypointElement(index, segment.waypoints.length),
        anchor: 'center',
        draggable: true
      })
        .setLngLat([waypoint.longitude, waypoint.latitude])
        .addTo(map)

      marker.on('dragend', () => {
        const lngLat = marker.getLngLat()
        callbacks.onWaypointDragged?.(segmentIndex, index, lngLat.lat, lngLat.lng)
      })

      marker.getElement().addEventListener('contextmenu', (event) => {
        event.preventDefault()
        callbacks.onWaypointRemoved?.(segmentIndex, index)
      })

      return marker
    })
  }

  return {
    initialize(mapInstance) {
      map = mapInstance
    },

    render({ activeSegment, activeSegmentIndex, contextPoints, contextTripLines }) {
      clear()
      if (!activeSegment) {
        return
      }

      renderContext(contextPoints)
      renderContextTripLines(contextTripLines)

      if (activeSegment.segmentType === 'STAY') {
        renderStay(activeSegment, activeSegmentIndex)
      } else {
        renderTrip(activeSegment, activeSegmentIndex)
      }
    },

    cleanup: clear,

    destroy() {
      clear()
      map = null
    }
  }
}
