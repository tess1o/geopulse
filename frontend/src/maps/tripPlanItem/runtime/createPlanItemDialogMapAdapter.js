import L from 'leaflet'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'

const PLAN_ITEM_DIALOG_MARKER_HTML = `
  <span class="plan-item-dialog-marker-pin" aria-hidden="true">
    <svg viewBox="0 0 30 40" xmlns="http://www.w3.org/2000/svg">
      <path d="M15 1.5C9.2 1.5 4.5 6.2 4.5 12c0 8.1 10.5 19.6 10.5 19.6S25.5 20.1 25.5 12c0-5.8-4.7-10.5-10.5-10.5z" fill="#f43f5e" stroke="#0f172a" stroke-width="1.4" />
      <circle cx="15" cy="12" r="4.2" fill="#fef08a" stroke="#0f172a" stroke-width="1.2" />
    </svg>
  </span>
`

const createRasterPlanItemDialogMapAdapter = (map) => {
  let marker = null

  const clear = () => {
    if (marker) {
      map.removeLayer(marker)
      marker = null
    }
  }

  const render = (coords) => {
    clear()
    if (!coords) return

    const markerIcon = L.divIcon({
      className: 'plan-item-dialog-marker-icon',
      html: PLAN_ITEM_DIALOG_MARKER_HTML,
      iconSize: [30, 40],
      iconAnchor: [15, 38]
    })

    marker = L.marker([coords.lat, coords.lon], { icon: markerIcon }).addTo(map)
  }

  return {
    render,
    cleanup: clear
  }
}

const createVectorPlanItemDialogMarkerElement = () => {
  const markerElement = document.createElement('div')
  markerElement.className = 'plan-item-dialog-marker-icon plan-item-dialog-marker-icon--vector'
  markerElement.innerHTML = PLAN_ITEM_DIALOG_MARKER_HTML
  return markerElement
}

const createVectorPlanItemDialogMapAdapter = (map) => {
  const { maplibregl } = getVectorEngine()
  let marker = null

  const clear = () => {
    if (marker) {
      marker.remove()
      marker = null
    }
  }

  const render = (coords) => {
    clear()
    if (!coords) return

    marker = new maplibregl.Marker({
      element: createVectorPlanItemDialogMarkerElement(),
      anchor: 'bottom'
    })
      .setLngLat([coords.lon, coords.lat])
      .addTo(map)
  }

  return {
    render,
    cleanup: clear
  }
}

/**
 * Single marker on the plan item dialog's map, for either map engine: render({ lat, lon }) moves it, render(null)
 * and cleanup() remove it.
 */
export const createPlanItemDialogMapAdapter = (map) => {
  const mapMode = resolveMapEngineModeFromInstance(map, MAP_RENDER_MODES.RASTER)
  if (mapMode === MAP_RENDER_MODES.VECTOR) {
    return createVectorPlanItemDialogMapAdapter(map)
  }
  return createRasterPlanItemDialogMapAdapter(map)
}
