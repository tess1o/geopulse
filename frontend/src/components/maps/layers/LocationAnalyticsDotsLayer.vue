<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import L from 'leaflet'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'
import BaseLayer from './BaseLayer.vue'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  places: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  selectedPlaceKey: {
    type: String,
    default: null
  },
  hoveredPlaceKey: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['marker-click', 'open-place-details'])

const baseLayerRef = ref(null)
const markerClusterGroup = ref(null)
const selectedMarker = ref(null)
const hoveredMarker = ref(null)
const markersByKey = new Map()
const openPopupPlaceKey = ref(null)
const CLUSTER_LABEL_MIN_ZOOM = 8
const CLUSTER_LABEL_MAX_COUNT = 49
const POPUP_RESTORE_MIN_ZOOM = 12
let hoveredMarkerKey = null
let suppressPopupCloseTracking = false

const getPlaceKey = (place) => `${place.type}-${place.id}`

const escapeHtml = (value) => {
  if (value == null) return ''
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

const formatDate = (timestamp) => {
  if (!timestamp) return 'Unknown'
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Unknown'
  return date.toLocaleString()
}

const createDotIcon = ({ hovered = false } = {}) => {
  const size = 12
  const innerSize = 6

  return L.divIcon({
    className: hovered ? 'location-analytics-dot-icon is-hovered' : 'location-analytics-dot-icon',
    html: `
      <div
        class="location-analytics-dot-shell"
        style="
          width:${size}px;
          height:${size}px;
          border-radius:50%;
          background:#ff5e00;
          border:2px solid #ffe5d0;
          box-shadow:0 0 0 1px rgba(0, 0, 0, 0.12);
          display:flex;
          align-items:center;
          justify-content:center;
        "
      >
        <div
          class="location-analytics-dot-inner"
          style="
            width:${innerSize}px;
            height:${innerSize}px;
            border-radius:50%;
            background:#ff8a3d;
          "
        ></div>
      </div>
    `,
    iconSize: [size, size],
    iconAnchor: [Math.floor(size / 2), Math.floor(size / 2)]
  })
}

const createSelectedPinIcon = () => {
  return L.divIcon({
    className: 'location-analytics-selected-pin-icon',
    html: `
      <div class="location-analytics-selected-pin">
        <div class="place-like-pin">
          <i class="pi pi-map-marker"></i>
        </div>
      </div>
    `,
    iconSize: [40, 40],
    iconAnchor: [20, 40]
  })
}

const buildPopup = (place) => {
  const cityCountry = [place.city, place.country].filter(Boolean).join(', ')
  return `
    <div class="location-analytics-popup">
      <div class="popup-name">${escapeHtml(place.locationName || 'Unknown location')}</div>
      ${cityCountry ? `<div class="popup-sub">${escapeHtml(cityCountry)}</div>` : ''}
      <div class="popup-meta">Visits: ${place.visitCount}</div>
      <div class="popup-meta">Last visit: ${escapeHtml(formatDate(place.lastVisit))}</div>
      <button type="button" class="popup-open-btn">Open place details</button>
    </div>
  `.trim()
}

const buildTooltip = (place) => {
  const name = escapeHtml(place.locationName || 'Unknown location')
  const cityCountry = [place.city, place.country].filter(Boolean).join(', ')
  return cityCountry ? `${name}<br><span class="dot-tooltip-sub">${escapeHtml(cityCountry)}</span>` : name
}

const clearMarkers = () => {
  suppressPopupCloseTracking = true
  try {
    if (markerClusterGroup.value) {
      markerClusterGroup.value.clearLayers()
    } else if (baseLayerRef.value) {
      for (const marker of markersByKey.values()) {
        baseLayerRef.value.removeFromLayer(marker)
      }
    }
  } finally {
    suppressPopupCloseTracking = false
  }
  markersByKey.clear()
  hoveredMarkerKey = null
}

const removeSelectedMarker = () => {
  if (!selectedMarker.value || !props.map) return
  props.map.removeLayer(selectedMarker.value)
  selectedMarker.value = null
}

const removeHoveredMarker = () => {
  if (!hoveredMarker.value || !props.map) return
  props.map.removeLayer(hoveredMarker.value)
  hoveredMarker.value = null
}

const renderSelectedMarker = (selectedKey) => {
  removeSelectedMarker()

  if (!selectedKey || !props.map) return
  if (props.hoveredPlaceKey && props.hoveredPlaceKey !== selectedKey) return
  const selectedPlace = props.places.find((place) => getPlaceKey(place) === selectedKey)
  if (!selectedPlace) return
  if (typeof selectedPlace.latitude !== 'number' || typeof selectedPlace.longitude !== 'number') return

  selectedMarker.value = L.marker([selectedPlace.latitude, selectedPlace.longitude], {
    icon: createSelectedPinIcon(),
    keyboard: false,
    interactive: false,
    zIndexOffset: 2200
  })

  props.map.addLayer(selectedMarker.value)
}

const renderHoveredMarker = (hoveredKey) => {
  removeHoveredMarker()

  if (!hoveredKey || !props.map) return
  if (hoveredKey === props.selectedPlaceKey) return

  const hoveredPlace = props.places.find((place) => getPlaceKey(place) === hoveredKey)
  if (!hoveredPlace) return
  if (typeof hoveredPlace.latitude !== 'number' || typeof hoveredPlace.longitude !== 'number') return

  hoveredMarker.value = L.marker([hoveredPlace.latitude, hoveredPlace.longitude], {
    icon: createSelectedPinIcon(),
    keyboard: false,
    interactive: false,
    zIndexOffset: 1600
  })

  props.map.addLayer(hoveredMarker.value)
}

const updateSelectedMarkerIcon = (_previousKey, nextKey) => {
  renderSelectedMarker(nextKey)
}

const bindPlacePopupActions = (marker, place) => {
  const placeKey = getPlaceKey(place)
  marker.on('popupopen', () => {
    openPopupPlaceKey.value = placeKey
    const popupElement = marker.getPopup()?.getElement()
    if (!popupElement) return
    const actionButton = popupElement.querySelector('.popup-open-btn')
    if (!actionButton) return
    actionButton.addEventListener('click', () => {
      emit('open-place-details', place)
    }, { once: true })
  })

  marker.on('popupclose', () => {
    if (suppressPopupCloseTracking) return
    if (openPopupPlaceKey.value === placeKey) {
      openPopupPlaceKey.value = null
    }
  })
}

const setMarkerHovered = (key, hovered) => {
  const marker = markersByKey.get(key)
  if (!marker) return

  const iconElement = marker.getElement?.()
  if (iconElement) {
    iconElement.classList.toggle('is-hovered', hovered)
  }
  if (marker.setZIndexOffset) {
    marker.setZIndexOffset(hovered ? 1200 : 0)
  }
}

const syncHoveredMarker = (nextKey) => {
  if (hoveredMarkerKey && hoveredMarkerKey !== nextKey) {
    setMarkerHovered(hoveredMarkerKey, false)
  }
  hoveredMarkerKey = nextKey || null
  if (hoveredMarkerKey) {
    setMarkerHovered(hoveredMarkerKey, true)
  }
}

const restoreOpenPopup = () => {
  const popupKey = openPopupPlaceKey.value
  if (!popupKey) return

  const marker = markersByKey.get(popupKey)
  if (!marker) return
  if (!marker.getElement?.()) return

  marker.openPopup()
}

const createPlaceMarker = (place) => {
  const marker = L.marker([place.latitude, place.longitude], {
    icon: createDotIcon(),
    keyboard: false,
    zIndexOffset: 0
  })

  marker.bindPopup(buildPopup(place))
  marker.bindTooltip(buildTooltip(place), {
    direction: 'top',
    offset: [0, -8],
    opacity: 0.96
  })
  marker.on('click', () => {
    emit('marker-click', place)
  })
  marker.on('dblclick', () => {
    emit('open-place-details', place)
  })
  bindPlacePopupActions(marker, place)

  return marker
}

const addMarkerToLayer = (marker) => {
  if (markerClusterGroup.value) {
    markerClusterGroup.value.addLayer(marker)
  } else if (baseLayerRef.value) {
    baseLayerRef.value.addToLayer(marker)
  }
}

const renderMarkers = () => {
  if (!baseLayerRef.value) return
  const popupKeyToRestore = openPopupPlaceKey.value
  clearMarkers()

  for (const place of props.places) {
    if (typeof place.latitude !== 'number' || typeof place.longitude !== 'number') continue

    const key = getPlaceKey(place)
    const marker = createPlaceMarker(place)
    addMarkerToLayer(marker)
    markersByKey.set(key, marker)
  }

  openPopupPlaceKey.value = popupKeyToRestore
  syncHoveredMarker(props.hoveredPlaceKey)
  renderHoveredMarker(props.hoveredPlaceKey)
  renderSelectedMarker(props.selectedPlaceKey)
  const currentZoom = props.map?.getZoom?.() ?? 0
  if (currentZoom >= POPUP_RESTORE_MIN_ZOOM) {
    restoreOpenPopup()
  }
}

const handleLayerReady = () => {
  markerClusterGroup.value = L.markerClusterGroup({
    maxClusterRadius: (zoom) => {
      if (zoom <= 4) return 12
      if (zoom <= 6) return 16
      if (zoom <= 8) return 20
      if (zoom <= 10) return 24
      return 28
    },
    disableClusteringAtZoom: 12,
    spiderfyOnMaxZoom: true,
    showCoverageOnHover: false,
    zoomToBoundsOnClick: true,
    chunkedLoading: true,
    chunkInterval: 200,
    chunkDelay: 50,
    animate: false,
    removeOutsideVisibleBounds: true,
    iconCreateFunction: (cluster) => {
      const count = cluster.getChildCount()
      const zoom = props.map?.getZoom?.() ?? 0
      const showLabel = zoom >= CLUSTER_LABEL_MIN_ZOOM && count <= CLUSTER_LABEL_MAX_COUNT
      const size =
        zoom <= 5 ? 14
          : zoom <= 7 ? 16
            : zoom <= 9 ? 18
              : 20

      return L.divIcon({
        html: `
          <div
            class="location-analytics-cluster ${showLabel ? 'is-labeled' : 'is-compact'}"
            style="--cluster-size:${size}px"
            title="${count} places"
            aria-label="${count} places"
          >
            ${showLabel ? `<span>${count}</span>` : ''}
          </div>
        `,
        className: 'location-analytics-cluster-icon',
        iconSize: L.point(size, size)
      })
    }
  })

  if (props.map) {
    props.map.addLayer(markerClusterGroup.value)
  }

  renderMarkers()
}

watch(() => props.places, () => {
  renderMarkers()
})

watch(() => props.selectedPlaceKey, (next, previous) => {
  updateSelectedMarkerIcon(previous, next)
  renderHoveredMarker(props.hoveredPlaceKey)
})

watch(() => props.hoveredPlaceKey, (next) => {
  syncHoveredMarker(next)
  renderHoveredMarker(next)
  renderSelectedMarker(props.selectedPlaceKey)
})

onBeforeUnmount(() => {
  clearMarkers()
  removeSelectedMarker()
  removeHoveredMarker()
  if (markerClusterGroup.value && props.map) {
    props.map.removeLayer(markerClusterGroup.value)
  }
  markerClusterGroup.value = null
})
</script>

<style scoped>
:global(.location-analytics-dot-icon) {
  background: transparent;
  border: none;
}

:global(.location-analytics-dot-icon .location-analytics-dot-shell) {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #ff5e00;
  border: 2px solid #ffe5d0;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  transform: scale(1);
  transition: transform 0.16s ease, box-shadow 0.16s ease, border-color 0.16s ease;
}

:global(.location-analytics-dot-icon .location-analytics-dot-inner) {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff8a3d;
}

:global(.location-analytics-dot-icon.is-hovered .location-analytics-dot-shell) {
  transform: scale(1.55);
  border-color: #fff;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.22), 0 2px 8px rgba(0, 0, 0, 0.18);
}

:global(.location-analytics-cluster-icon) {
  background: transparent;
  border: none;
}

:global(.location-analytics-cluster) {
  width: var(--cluster-size, 18px);
  height: var(--cluster-size, 18px);
  border-radius: 50%;
  background: rgba(255, 94, 0, 0.86);
  border: 2px solid #fff7f0;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.16);
}

:global(.location-analytics-cluster.is-compact) {
  border-width: 1.5px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
}

:global(.location-analytics-cluster span) {
  color: #fff;
  font-weight: 700;
  font-size: 0.62rem;
  line-height: 1;
}

:global(.location-analytics-popup .popup-name) {
  font-weight: 700;
  margin-bottom: 0.2rem;
}

:global(.location-analytics-popup .popup-sub) {
  color: var(--gp-text-secondary, #64748b);
  margin-bottom: 0.2rem;
  font-size: 0.82rem;
}

:global(.location-analytics-popup .popup-meta) {
  font-size: 0.8rem;
}

:global(.location-analytics-popup .popup-open-btn) {
  margin-top: 0.45rem;
  border: 1px solid var(--gp-primary, #1a56db);
  background: var(--gp-primary, #1a56db);
  color: #fff;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 600;
  padding: 0.32rem 0.55rem;
  cursor: pointer;
}

:global(.location-analytics-popup .popup-open-btn:hover) {
  filter: brightness(0.95);
}

:global(.leaflet-tooltip .dot-tooltip-sub) {
  color: #64748b;
  font-size: 0.76rem;
}

:global(.location-analytics-selected-pin-icon) {
  background: transparent;
  border: none;
}

:global(.location-analytics-selected-pin) {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

:global(.location-analytics-selected-pin .place-like-pin) {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: #f97316;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  border: 2px solid #ffffff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

:global(.location-analytics-selected-pin .place-like-pin i) {
  transform: rotate(45deg);
  font-size: 1.5rem;
}
</style>
