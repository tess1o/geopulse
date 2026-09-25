<template></template>

<script setup>
import { readonly, ref, watch, onBeforeUnmount } from 'vue'
import {
  createFeatureCollection,
  ensureGeoJsonSource,
  ensureLayer,
  hasMapLibreLayer,
  isMapLibreMap,
  nextLayerToken,
  removeLayers,
  removeSources,
  setLayerVisibility,
  toFiniteNumber
} from '@/maps/vector/utils/maplibreLayerUtils'
import maplibregl from 'maplibre-gl'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { buildTripPlanItemPopupModel } from '@/maps/shared/popups/tripPlanPopupModel'
import { useTimezone } from '@/composables/useTimezone'
import {MAP_POPUP_MAX_WIDTH} from "../../shared/popups/mapPopupOptions";

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  plannedItemsData: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  markerOptions: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['plan-item-contextmenu'])

const state = {
  token: nextLayerToken('gp-trip-plan'),
  sourceId: '',
  layerId: '',
  labelLayerId: '',
  listeners: [],
  styleLoadHandler: null,
  boundMap: null
}

state.sourceId = `${state.token}-source`
state.layerId = `${state.token}-circle`
state.labelLayerId = `${state.token}-label`

const baseLayerRef = ref(null)
const planMarkers = ref([])

const buildCollection = () => {
  const features = props.plannedItemsData
    .map((item, index) => {
      const latitude = toFiniteNumber(item?.latitude)
      const longitude = toFiniteNumber(item?.longitude)

      if (latitude === null || longitude === null) {
        return null
      }

      const isMust = String(item?.priority || '').toUpperCase() === 'MUST'
      // A manually rejected stop is not "visited" for display purposes either.
      const isVisited = Boolean(item?.isVisited) && item?.manualOverrideState !== 'REJECTED'

      return {
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [longitude, latitude]
        },
        properties: {
          itemRaw: JSON.stringify(item || {}),
          itemIndex: index,
          isMust,
          isVisited,
          // Visited reads as a tick, pending as the priority letter.
          label: isVisited ? '✓' : (isMust ? 'M' : 'P')
        }
      }
    })
    .filter(Boolean)

  return createFeatureCollection(features)
}

const parseItem = (event) => {
  const raw = event?.features?.[0]?.properties?.itemRaw
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

const registerEvents = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const handleContextMenu = (event) => {
    const item = parseItem(event)
    const index = Number.parseInt(event?.features?.[0]?.properties?.itemIndex, 10)

    event?.originalEvent?.preventDefault?.()

    emit('plan-item-contextmenu', {
      item,
      index: Number.isFinite(index) ? index : -1,
      event: event?.originalEvent || event,
      latlng: event?.lngLat ? { lat: event.lngLat.lat, lng: event.lngLat.lng } : null,
      type: 'trip-plan'
    })
  }

  props.map.on('contextmenu', state.layerId, handleContextMenu)

  // Hover card: the marker alone only says "a stop is here", so name, day, priority and
  // status are surfaced on hover rather than requiring a right-click.
  const handleMouseEnter = (event) => {
    const item = parseItem(event)
    if (!item || !event?.lngLat) {
      return
    }

    props.map.getCanvas().style.cursor = 'pointer'
    showHoverPopup(event.lngLat, item)
  }

  const handleMouseLeave = () => {
    if (isMapLibreMap(props.map)) {
      props.map.getCanvas().style.cursor = ''
    }
    hideHoverPopup()
  }

  props.map.on('mouseenter', state.layerId, handleMouseEnter)
  props.map.on('mouseleave', state.layerId, handleMouseLeave)

  state.listeners = [
    { event: 'contextmenu', layerId: state.layerId, handler: handleContextMenu },
    { event: 'mouseenter', layerId: state.layerId, handler: handleMouseEnter },
    { event: 'mouseleave', layerId: state.layerId, handler: handleMouseLeave }
  ]
}

let hoverPopup = null
let hoverPopupMount = null

const showHoverPopup = (lngLat, item) => {
  hideHoverPopup()
  if (!isMapLibreMap(props.map)) {
    return
  }

  hoverPopupMount = mountMapPopup(
    MapInfoPopup,
    buildTripPlanItemPopupModel(item, { timezone: useTimezone() }),
    { className: 'gp-trip-plan-popup', stopEvents: [] }
  )

  // No `maxWidth`: MapLibre applies it to the popup content element, and capping it below
  // the card's own width (.gp-map-popup-card--compact is min(320px, …)) made the card
  // overflow its container - the right-aligned value ended up outside the card.
  hoverPopup = new maplibregl.Popup({
    closeButton: false,
    closeOnClick: false,
    offset: 14,
    maxWidth: MAP_POPUP_MAX_WIDTH,
  })
    .setLngLat(lngLat)
    .setDOMContent(hoverPopupMount.element)
    .addTo(props.map)
}

const hideHoverPopup = () => {
  if (hoverPopup) {
    hoverPopup.remove()
    hoverPopup = null
  }
  if (hoverPopupMount) {
    hoverPopupMount.unmount()
    hoverPopupMount = null
  }
}

const unregisterEvents = () => {
  // The hover card outlives the listeners if it is not torn down explicitly.
  hideHoverPopup()

  if (!isMapLibreMap(props.map)) {
    state.listeners = []
    return
  }

  state.listeners.forEach(({ event, layerId, handler }) => {
    if (hasMapLibreLayer(props.map, layerId)) {
      props.map.off(event, layerId, handler)
    }
  })

  state.listeners = []
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const collection = buildCollection()

  ensureGeoJsonSource(props.map, state.sourceId, collection)

  ensureLayer(props.map, {
    id: state.layerId,
    type: 'circle',
    source: state.sourceId,
    paint: {
      'circle-radius': ['case', ['get', 'isMust'], 17, 15],
      // Two things are encoded, so they need two channels:
      //   colour = whether the stop has been visited, so a glance at the map shows progress
      //   glyph  = the same, redundantly, plus the priority letter while still pending
      // Previously neither colour nor glyph varied with the visit state, so a completed
      // stop looked identical to one still to come.
      'circle-color': [
        'case',
        ['get', 'isVisited'],
        '#15803d',
        ['case', ['get', 'isMust'], '#b91c1c', '#b45309']
      ],
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 2.5,
      'circle-stroke-opacity': 1
    }
  })

  ensureLayer(props.map, {
    id: state.labelLayerId,
    type: 'symbol',
    source: state.sourceId,
    layout: {
      'text-field': ['get', 'label'],
      'text-size': 16,
      'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold'],
      // Keep the glyph centred and on top of its circle.
      'text-allow-overlap': true,
      'text-ignore-placement': true
    },
    paint: {
      'text-color': '#ffffff'
    }
  })

  setLayerVisibility(props.map, [state.layerId, state.labelLayerId], props.visible)

  unregisterEvents()
  registerEvents()
}

const clearLayer = () => {
  unregisterEvents()

  if (state.boundMap && state.styleLoadHandler) {
    state.boundMap.off('style.load', state.styleLoadHandler)
    state.styleLoadHandler = null
  }

  const targetMap = state.boundMap || props.map
  if (!isMapLibreMap(targetMap)) {
    state.boundMap = null
    return
  }

  removeLayers(targetMap, [state.labelLayerId, state.layerId])
  removeSources(targetMap, [state.sourceId])
  state.boundMap = null
}

watch(
  () => [props.map, props.plannedItemsData, props.visible],
  () => {
    if (!isMapLibreMap(props.map)) {
      clearLayer()
      return
    }

    if (state.boundMap && state.boundMap !== props.map) {
      clearLayer()
    }

    state.boundMap = props.map

    if (!state.styleLoadHandler) {
      state.styleLoadHandler = () => renderLayer()
      props.map.on('style.load', state.styleLoadHandler)
    }

    renderLayer()
  },
  { immediate: true, deep: true }
)

onBeforeUnmount(() => {
  clearLayer()
})

defineExpose({
  baseLayerRef: readonly(baseLayerRef),
  planMarkers: readonly(planMarkers),
  clearPlanMarkers: clearLayer
})
</script>

<!--
  Global, not scoped: the hover card is mounted imperatively into MapLibre's popup
  container, so scoped attribute selectors would never match it.

  MapInfoPopup renders a light card, but its text picks up the app's text tokens, which
  flip to light values in dark mode - leaving pale text on a white card. These rules make
  the container follow the active theme so the two always agree.
-->
<style>
.maplibregl-popup-content:has(.gp-trip-plan-popup) {
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  background: var(--gp-surface-white);
  border-radius: var(--gp-radius-medium);
  box-shadow: var(--gp-shadow-dialog);
}

.gp-trip-plan-popup {
  color: var(--gp-text-primary);
  /* The card sets its own width (`.gp-map-popup-card--compact` is min(320px, …)) and its
     grid already wraps values. Constraining this wrapper narrower than the card made the
     card overflow its own parent, so the wrapper only needs to not fight it. */
  width: 100%;
}

/* MapLibre's default arrow is a white triangle drawn with borders. */
.maplibregl-popup-anchor-top .maplibregl-popup-tip,
.maplibregl-popup-anchor-bottom .maplibregl-popup-tip,
.maplibregl-popup-anchor-left .maplibregl-popup-tip,
.maplibregl-popup-anchor-right .maplibregl-popup-tip {
  border-top-color: var(--gp-surface-white);
  border-bottom-color: var(--gp-surface-white);
  border-left-color: var(--gp-surface-white);
  border-right-color: var(--gp-surface-white);
}

.p-dark .maplibregl-popup-content:has(.gp-trip-plan-popup) {
  background: var(--gp-surface-dark);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.45);
}

.p-dark .maplibregl-popup-anchor-top .maplibregl-popup-tip,
.p-dark .maplibregl-popup-anchor-bottom .maplibregl-popup-tip,
.p-dark .maplibregl-popup-anchor-left .maplibregl-popup-tip,
.p-dark .maplibregl-popup-anchor-right .maplibregl-popup-tip {
  border-top-color: var(--gp-surface-dark);
  border-bottom-color: var(--gp-surface-dark);
  border-left-color: var(--gp-surface-dark);
  border-right-color: var(--gp-surface-dark);
}
</style>
