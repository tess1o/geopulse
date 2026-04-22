import L from 'leaflet'

const createRasterWaypointIcon = (index, total) => {
  const markerVariant = index === 0 ? 'start' : (index === total - 1 ? 'end' : 'mid')
  return L.divIcon({
    className: 'trip-reconstruction-waypoint-icon-wrapper',
    html: `<div class="trip-reconstruction-waypoint-icon trip-reconstruction-waypoint-icon--${markerVariant}">${index + 1}</div>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14]
  })
}

const createRasterStayIcon = () => {
  return L.divIcon({
    className: 'trip-reconstruction-waypoint-icon-wrapper',
    html: '<div class="trip-reconstruction-waypoint-icon trip-reconstruction-waypoint-icon--stay"><i class="pi pi-home"></i></div>',
    iconSize: [30, 30],
    iconAnchor: [15, 15]
  })
}

const createRasterContextIcon = (point) => {
  return L.divIcon({
    className: 'trip-reconstruction-waypoint-icon-wrapper',
    html: `<div class="trip-reconstruction-context-icon">${point.label}</div>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11]
  })
}

export const createRasterTripReconstructionMapAdapter = (callbacks = {}) => {
  let map = null
  let waypointMarkers = []
  let stayMarker = null
  let polyline = null
  let contextMarkers = []
  let contextTripPolylines = []

  const clear = () => {
    if (!map) {
      return
    }

    waypointMarkers.forEach((marker) => map.removeLayer(marker))
    waypointMarkers = []

    if (stayMarker) {
      map.removeLayer(stayMarker)
      stayMarker = null
    }

    if (polyline) {
      map.removeLayer(polyline)
      polyline = null
    }

    contextTripPolylines.forEach((line) => map.removeLayer(line))
    contextTripPolylines = []

    contextMarkers.forEach((marker) => map.removeLayer(marker))
    contextMarkers = []
  }

  const renderContext = (contextPoints = []) => {
    contextMarkers = contextPoints.map((point) => {
      return L.marker([point.latitude, point.longitude], {
        icon: createRasterContextIcon(point),
        interactive: false,
        keyboard: false
      }).addTo(map)
    })
  }

  const renderContextTripLines = (contextTripLines = []) => {
    contextTripPolylines = contextTripLines.map((linePoints) => {
      return L.polyline(linePoints, {
        color: '#0ea5e9',
        weight: 3,
        opacity: 0.8,
        dashArray: '3 3',
        interactive: false
      }).addTo(map)
    })
  }

  const renderStay = (segment, segmentIndex) => {
    if (!callbacks.hasValidCoordinates?.(segment?.latitude, segment?.longitude)) {
      return
    }

    stayMarker = L.marker([segment.latitude, segment.longitude], {
      icon: createRasterStayIcon(),
      draggable: true
    }).addTo(map)

    stayMarker.on('dragend', (event) => {
      const latLng = event.target.getLatLng()
      callbacks.onStayDragged?.(segmentIndex, latLng.lat, latLng.lng)
    })
  }

  const renderTrip = (segment, segmentIndex) => {
    const points = (segment?.waypoints || [])
      .filter((waypoint) => callbacks.hasValidCoordinates?.(waypoint.latitude, waypoint.longitude))
      .map((waypoint) => [waypoint.latitude, waypoint.longitude])

    if (points.length >= 2) {
      polyline = L.polyline(points, {
        color: '#1d4ed8',
        weight: 4,
        opacity: 0.85
      }).addTo(map)
    }

    waypointMarkers = (segment?.waypoints || []).map((waypoint, index) => {
      const marker = L.marker([waypoint.latitude, waypoint.longitude], {
        icon: createRasterWaypointIcon(index, segment.waypoints.length),
        draggable: true
      }).addTo(map)

      marker.on('dragend', (event) => {
        const latLng = event.target.getLatLng()
        callbacks.onWaypointDragged?.(segmentIndex, index, latLng.lat, latLng.lng)
      })

      marker.on('contextmenu', () => {
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
      if (!map) {
        return
      }

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
