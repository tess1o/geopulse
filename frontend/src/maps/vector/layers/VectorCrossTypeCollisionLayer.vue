<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import maplibregl from 'maplibre-gl'
import { useAuthStore } from '@/stores/auth'
import { useTimezone } from '@/composables/useTimezone'
import '@/maps/shared/styles/mapPopupContent.css'
import { isMapLibreMap } from '@/maps/vector/utils/maplibreLayerUtils'
import { buildCrossTypeStackItems, buildStackRowHtml } from '@/maps/shared/timelineStackContent'
import {
  computeCrossTypeCollisions,
  countMembersByType,
  CROSS_TYPE_ORDER
} from '@/maps/shared/crossTypeMarkerCollision'
import { buildPhotoMarkerClickPayload } from '@/maps/shared/photoMarkerGroups'
import { NOTE_MARKER_COLOR } from '@/maps/shared/noteMapMarkers'
import { getTimelineItemWeatherDisplay } from '@/maps/shared/stayWeather'
import { placeWeatherMarkers } from '@/maps/shared/weatherMarkerPlacement'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH
} from '@/maps/shared/popups/mapPopupOptions'

const SETTLE_MS = 50

// Mirrors each type's own marker so the chip reads without a legend.
const TYPE_STYLES = {
  timeline: { color: '#0f766e', icon: 'pi pi-map-marker' },
  notes: { color: NOTE_MARKER_COLOR, icon: 'pi pi-file-edit' },
  photos: { color: '#2563eb', icon: 'pi pi-camera' }
}

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  // () => ({ timeline, notes, photos, weather, getFocusObstacles }) - the exposed APIs of
  // the sibling layers, plus the highlighted trip's endpoint markers.
  getSources: {
    type: Function,
    required: true
  },
  // Map<timelineIndex, weather samples[]> for stays, shown on their popup rows.
  itemWeather: {
    type: Map,
    default: null
  }
})

const emit = defineEmits(['select-timeline', 'select-notes', 'select-photos'])

const authStore = useAuthStore()
const { distanceUnit, temperatureUnit } = storeToRefs(authStore)
const timezone = useTimezone()
const { t } = useI18n()

const state = {
  boundMap: null,
  markers: [],
  popup: null,
  debounceHandle: null,
  activityToken: 0,
  moveHandler: null,
  styleLoadHandler: null
}

const closePopup = () => {
  state.popup?.remove()
  state.popup = null
}

const clearMarkers = () => {
  state.markers.forEach((entry) => entry.cleanup())
  state.markers = []
}

const ROW_KIND_TO_TYPE = { timeline: 'timeline', note: 'notes', photo: 'photos' }

const createTypeIcon = (type) => {
  const icon = document.createElement('span')
  icon.className = 'gp-cross-type-marker-icon'
  icon.style.background = TYPE_STYLES[type].color
  icon.innerHTML = `<i class="${TYPE_STYLES[type].icon}"></i>`
  return icon
}

const createRowButton = (row, onSelect) => {
  const button = document.createElement('button')
  button.type = 'button'
  button.className = `timeline-stack-select ${row.typeClass}`
  button.innerHTML = buildStackRowHtml(row)

  button.addEventListener('click', (domEvent) => {
    domEvent.preventDefault()
    domEvent.stopPropagation()
    onSelect(row, domEvent)
  })
  return button
}

// Header total and section counts use the same item counts as the chip, so
// "2 + 9" on the map reads as "11 items" here - not the number of rows.
const createPopupElement = (collision, onSelect) => {
  const root = document.createElement('div')
  root.className = 'timeline-stack-popup gp-cross-type-popup'

  const rows = buildCrossTypeStackItems(collision.members, {
    formatDateDisplay: (value) => timezone.formatDateDisplay(value),
    formatTime: (value) => timezone.formatTime(value, { withSeconds: true }),
    unit: distanceUnit.value,
    getItemWeather: (item) => getTimelineItemWeatherDisplay(props.itemWeather, item, {
      temperatureUnit: temperatureUnit.value || 'CELSIUS',
      distanceUnit: distanceUnit.value || 'KILOMETERS'
    })
  })
  const countsByType = countMembersByType(collision.members)
  const total = Object.values(countsByType).reduce((sum, count) => sum + count, 0)

  const header = document.createElement('div')
  header.className = 'stack-popup-header'
  header.textContent = t('maps.popups.timeline.itemsAtLocation', { count: total }, total)
  root.appendChild(header)

  const list = document.createElement('div')
  list.className = 'stack-popup-list'
  root.appendChild(list)

  CROSS_TYPE_ORDER.filter((type) => countsByType[type]).forEach((type) => {
    const section = document.createElement('section')
    section.className = 'gp-cross-type-section'

    const sectionHeader = document.createElement('div')
    sectionHeader.className = 'gp-cross-type-section-header'
    sectionHeader.appendChild(createTypeIcon(type))

    const label = document.createElement('span')
    label.className = 'gp-cross-type-section-label'
    label.textContent = t(`maps.popups.timeline.crossType.sections.${type}`)
    sectionHeader.appendChild(label)

    const count = document.createElement('span')
    count.className = 'gp-cross-type-section-count'
    count.textContent = String(countsByType[type])
    sectionHeader.appendChild(count)

    section.appendChild(sectionHeader)
    rows
      .filter((row) => ROW_KIND_TO_TYPE[row.kind] === type)
      .forEach((row) => section.appendChild(createRowButton(row, onSelect)))
    list.appendChild(section)
  })

  ;['click', 'mousedown', 'contextmenu'].forEach((eventName) => {
    root.addEventListener(eventName, (domEvent) => domEvent.stopPropagation())
  })

  return root
}

const handleRowSelect = (collision, row, domEvent) => {
  closePopup()

  if (row.kind === 'note') {
    emit('select-notes', row.notes)
    return
  }

  if (row.kind === 'photo') {
    emit('select-photos', buildPhotoMarkerClickPayload(row.group))
    return
  }

  const timelineGroup = collision.members.find((member) => (
    member.type === 'timeline' && member.group.items.includes(row.item)
  ))?.group
  const parsedIndex = Number.parseInt(row.item?.__timelineIndex, 10)

  emit('select-timeline', {
    timelineItem: row.item,
    stackItems: timelineGroup?.items || [row.item],
    index: Number.isFinite(parsedIndex) ? parsedIndex : -1,
    marker: null,
    event: {
      originalEvent: domEvent,
      target: props.map,
      lngLat: { lng: collision.longitude, lat: collision.latitude }
    }
  })
}

const openPopup = (collision) => {
  closePopup()

  state.popup = new maplibregl.Popup({
    closeButton: true,
    closeOnClick: true,
    closeOnMove: false,
    maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH,
    // A chip moved beside a focus marker opens its popup above where it is drawn.
    offset: collision.offset ? [collision.offset[0], collision.offset[1] - 18] : 18,
    className: getMapPopupVariantClassName('compact', 'gp-timeline-stack-popup-container')
  })
    .setLngLat([collision.longitude, collision.latitude])
    .setDOMContent(createPopupElement(collision, (row, domEvent) => handleRowSelect(collision, row, domEvent)))
    .addTo(props.map)
}

const createComboElement = (collision, focusActive) => {
  const element = document.createElement('div')
  element.className = 'gp-cross-type-marker'

  const countsByType = countMembersByType(collision.members)

  CROSS_TYPE_ORDER.filter((type) => countsByType[type]).forEach((type) => {
    const segment = document.createElement('span')
    // While a stay/trip is highlighted, other stays/trips dim - in chips too.
    // Photos and notes never dim, matching their own layers.
    segment.className = focusActive && type === 'timeline'
      ? 'gp-cross-type-marker-segment gp-cross-type-marker-segment--dimmed'
      : 'gp-cross-type-marker-segment'

    segment.appendChild(createTypeIcon(type))

    const count = document.createElement('span')
    count.className = 'gp-cross-type-marker-count'
    count.textContent = String(countsByType[type])
    segment.appendChild(count)

    element.appendChild(segment)
  })

  return element
}

const renderCollisionMarker = (collision, focusActive) => {
  const element = createComboElement(collision, focusActive)
  // Below the highlighted timeline marker (340) and trip endpoints (410+) while focusing.
  element.style.zIndex = focusActive ? '330' : '345'

  const marker = new maplibregl.Marker({ element, anchor: 'center', offset: collision.offset || [0, 0] })
    .setLngLat([collision.longitude, collision.latitude])
    .addTo(props.map)

  const handleClick = (domEvent) => {
    domEvent.preventDefault()
    domEvent.stopPropagation()
    openPopup(collision)
  }
  const handleEnter = () => { props.map.getCanvas().style.cursor = 'pointer' }
  const handleLeave = () => { props.map.getCanvas().style.cursor = '' }

  element.addEventListener('click', handleClick)
  element.addEventListener('mouseenter', handleEnter)
  element.addEventListener('mouseleave', handleLeave)

  state.markers.push({
    cleanup: () => {
      element.removeEventListener('click', handleClick)
      element.removeEventListener('mouseenter', handleEnter)
      element.removeEventListener('mouseleave', handleLeave)
      marker.remove()
    }
  })
}

const applyExclusions = (sources, excluded) => {
  sources.timeline?.setExcludedGroupIndices?.([...excluded.timeline])
  sources.notes?.setExcludedGroupIndices?.([...excluded.notes])
  sources.photos?.setExcludedGroupIndices?.([...excluded.photos])
}

const compute = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const sources = props.getSources() || {}
  const { collisions, excluded, focusActive, occupied } = computeCrossTypeCollisions({
    sources: Object.fromEntries(CROSS_TYPE_ORDER.map((type) => [type, {
      groups: sources[type]?.getCurrentGroups?.() ?? [],
      entities: sources[type]?.getRenderedEntities?.() ?? []
    }])),
    obstacles: sources.getFocusObstacles?.() ?? [],
    mapInstance: props.map
  })

  clearMarkers()
  applyExclusions(sources, excluded)
  collisions.forEach((collision) => renderCollisionMarker(collision, focusActive))

  // Weather goes last: it only fills space nothing else uses.
  sources.weather?.setPlacedGroups?.(placeWeatherMarkers({
    ...(sources.weather.getPlacementInput?.() ?? {}),
    occupied,
    mapInstance: props.map
  }))
}

// Same settle discipline as VectorTimelineLayer.requestRequery: only compute
// once the camera has been idle for a full window, and let every movement
// event (start or end) restart that window. No source/tile queries involved.
const requestCompute = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  state.activityToken += 1
  const myToken = state.activityToken

  if (state.debounceHandle !== null) {
    clearTimeout(state.debounceHandle)
  }

  const attempt = () => {
    if (props.map.isMoving?.() || state.activityToken !== myToken) {
      state.debounceHandle = setTimeout(attempt, SETTLE_MS)
      return
    }

    state.debounceHandle = null
    compute()
  }

  state.debounceHandle = setTimeout(attempt, SETTLE_MS)
}

const unbind = () => {
  if (state.debounceHandle !== null) {
    clearTimeout(state.debounceHandle)
    state.debounceHandle = null
  }

  if (state.boundMap && state.moveHandler) {
    ;['movestart', 'moveend', 'zoomstart', 'zoomend'].forEach((eventName) => {
      state.boundMap.off(eventName, state.moveHandler)
    })
  }
  if (state.boundMap && state.styleLoadHandler) {
    state.boundMap.off('style.load', state.styleLoadHandler)
  }

  state.moveHandler = null
  state.styleLoadHandler = null
  state.boundMap = null
}

const clearAll = () => {
  unbind()
  clearMarkers()
  closePopup()

  try {
    applyExclusions(props.getSources() || {}, { timeline: new Set(), notes: new Set(), photos: new Set() })
  } catch {
    // Sibling layers may already be unmounting.
  }
}

watch(
  () => props.map,
  (mapInstance) => {
    if (state.boundMap && state.boundMap !== mapInstance) {
      unbind()
      clearMarkers()
    }

    if (!isMapLibreMap(mapInstance) || state.boundMap === mapInstance) {
      return
    }

    state.boundMap = mapInstance
    state.moveHandler = () => requestCompute()
    state.styleLoadHandler = () => requestCompute()
    ;['movestart', 'moveend', 'zoomstart', 'zoomend'].forEach((eventName) => {
      mapInstance.on(eventName, state.moveHandler)
    })
    mapInstance.on('style.load', state.styleLoadHandler)
    requestCompute()
  },
  { immediate: true }
)

onBeforeUnmount(clearAll)

defineExpose({ requestCompute })
</script>

<style>
.gp-cross-type-marker {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px 3px 3px;
  border-radius: 999px;
  background: #ffffff;
  color: #111827;
  font-size: 0.75rem;
  font-weight: 700;
  line-height: 1;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.32);
  white-space: nowrap;
  cursor: pointer;
}

/* No transform here: MapLibre positions the marker element via its own transform. */
.gp-cross-type-marker:hover {
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.45), 0 2px 10px rgba(0, 0, 0, 0.32);
}

.gp-cross-type-marker-segment {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.gp-cross-type-marker-segment--dimmed {
  opacity: 0.35;
  filter: grayscale(0.35);
}

.gp-cross-type-marker-icon {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 0.7rem;
}

.gp-cross-type-popup .stack-popup-list {
  gap: 0.75rem;
  max-height: 320px;
}

.gp-cross-type-section {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.gp-cross-type-section-header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.15rem 0;
  background: #ffffff;
  color: var(--gp-text-primary, #1e293b);
  font-size: 0.78rem;
  font-weight: 600;
}

.gp-cross-type-section-header .gp-cross-type-marker-icon {
  width: 20px;
  height: 20px;
  font-size: 0.65rem;
}

.gp-cross-type-section-label {
  flex: 1;
}

.gp-cross-type-section-count {
  min-width: 22px;
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  background: #f1f5f9;
  color: var(--gp-text-secondary, #475569);
  font-size: 0.72rem;
  text-align: center;
}

.p-dark .gp-cross-type-section-header {
  /* Opaque match for the dark popup gradient so rows don't show through. */
  background: #172033;
  color: #f1f5f9;
}

.p-dark .gp-cross-type-section-count {
  background: rgba(148, 163, 184, 0.2);
  color: #cbd5e1;
}

.p-dark .gp-cross-type-marker {
  background: #1f2937;
  color: #f9fafb;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.55);
}
</style>
