<template></template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import maplibregl from 'maplibre-gl'
import { useAuthStore } from '@/stores/auth'
import { useTimezone } from '@/composables/useTimezone'
import '@/maps/shared/styles/mapPopupContent.css'
import '@/maps/shared/styles/weatherMapMarkers.css'
import { isMapLibreMap, toFiniteNumber } from '@/maps/vector/utils/maplibreLayerUtils'
import { groupItemsByProximity } from '@/maps/shared/nearbyPointGrouping'
import { groupByPixelDistance } from '@/maps/shared/crossTypeMarkerCollision'
import { buildStackRowHtml, buildTimelineStackItems } from '@/maps/shared/timelineStackContent'
import {
  buildWeatherPopupSection,
  createStayWeatherBadgeElement,
  getTimelineItemWeatherDisplay
} from '@/maps/shared/stayWeather'
import {
  createTimelineGroupMarkerElement,
  createTimelineMarkerElement,
  createTimelineStackMarkerElement
} from '@/maps/shared/timelineMarkerBuilder'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import { buildTimelineItemPopupModel } from '@/maps/shared/popups/timelinePopupModels'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH
} from '@/maps/shared/popups/mapPopupOptions'

const authStore = useAuthStore()
const { distanceUnit, temperatureUnit } = storeToRefs(authStore)
const timezone = useTimezone()
const { t } = useI18n()

const CLUSTER_RADIUS = 50
const CLUSTER_MAX_ZOOM = 16

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  timelineData: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  highlightedItem: {
    type: Object,
    default: null
  },
  markerOptions: {
    type: Object,
    default: () => ({})
  },
  // Map<timelineIndex, weather samples[]> for stays (partitionWeatherSamplesByStay);
  // null/empty when weather is hidden.
  itemWeather: {
    type: Map,
    default: null
  }
})

const emit = defineEmits(['marker-click', 'marker-hover', 'marker-contextmenu', 'groups-change'])

// Group indices (into state.groups) currently represented by a cross-type combo marker.
const excludedGroupIndices = ref(new Set())

const state = {
  timelineMarkers: [],
  clusterMarkers: [],
  styleLoadHandler: null,
  moveEndHandler: null,
  moveStartHandler: null,
  boundMap: null,
  stackPopup: null,
  highlightedStayPopup: null,
  highlightedStayPopupMount: null,
  highlightedStayPopupTimeoutId: null,
  lastHighlightedStayKey: '',
  groups: [],
  debounceHandle: null,
  activityToken: 0
}

const getTimelineKey = (item) => {
  if (item?.id) {
    return String(item.id)
  }

  return `${item?.timestamp || item?.startTime || 'unknown'}|${item?.latitude}|${item?.longitude}`
}

const isStayWithPlaceDetails = (item) => Boolean(
  item?.type === 'stay' &&
  (item.favoriteId || item.geocodingId)
)

const isSameTimelineItem = (left, right) => {
  if (!left || !right) {
    return false
  }

  if (left.id && right.id) {
    return left.id === right.id
  }

  return Boolean(
    left.timestamp
    && right.timestamp
    && left.timestamp === right.timestamp
    && left.latitude === right.latitude
    && left.longitude === right.longitude
  )
}

const groupTimelineByCoordinate = () => {
  const annotatedItems = props.timelineData
    .map((item, index) => ({
      ...item,
      __timelineIndex: index,
      __timelineKey: getTimelineKey(item)
    }))
    .filter((item) => toFiniteNumber(item.latitude) !== null && toFiniteNumber(item.longitude) !== null)

  return groupItemsByProximity(annotatedItems).map((group) => ({
    latitude: group.latitude,
    longitude: group.longitude,
    items: group.items
  }))
}

const formatDateTimeDisplay = (dateValue) =>
  `${timezone.formatDateDisplay(dateValue)} ${timezone.formatTime(dateValue, { withSeconds: true })}`

const getItemWeather = (item) => getTimelineItemWeatherDisplay(props.itemWeather, item, {
  temperatureUnit: temperatureUnit.value || 'CELSIUS',
  distanceUnit: distanceUnit.value || 'KILOMETERS'
})

const createPopupModel = (item) => {
  const model = buildTimelineItemPopupModel(item, {
    formatDateTimeDisplay,
    unit: distanceUnit.value
  })
  const weather = getItemWeather(item)
  return weather
    ? { ...model, sections: [buildWeatherPopupSection(weather), ...(model.sections || [])] }
    : model
}

const closeStackPopup = () => {
  if (state.stackPopup) {
    state.stackPopup.remove()
    state.stackPopup = null
  }
}

const closeHighlightedStayPopup = () => {
  if (state.highlightedStayPopupTimeoutId !== null) {
    clearTimeout(state.highlightedStayPopupTimeoutId)
    state.highlightedStayPopupTimeoutId = null
  }

  if (state.highlightedStayPopup) {
    state.highlightedStayPopup.remove()
    state.highlightedStayPopup = null
  }
  state.highlightedStayPopupMount?.unmount?.()
  state.highlightedStayPopupMount = null
}

const createStackPopupElement = (items, onSelect, onStayContextMenu) => {
  const popupRoot = document.createElement('div')
  popupRoot.className = 'timeline-stack-popup'

  const header = document.createElement('div')
  header.className = 'stack-popup-header'
  header.textContent = t('maps.popups.timeline.eventsAtLocation', { count: items.length })
  popupRoot.appendChild(header)

  const list = document.createElement('div')
  list.className = 'stack-popup-list'
  popupRoot.appendChild(list)

  const rows = buildTimelineStackItems(items, {
    formatDateDisplay: (value) => timezone.formatDateDisplay(value),
    formatTime: (value) => timezone.formatTime(value, { withSeconds: true }),
    unit: distanceUnit.value,
    getItemWeather
  })

  rows.forEach((row, stackIndex) => {
    const button = document.createElement('button')
    button.type = 'button'
    button.className = `timeline-stack-select ${row.typeClass}`
    button.dataset.stackItemIndex = String(stackIndex)

    button.innerHTML = buildStackRowHtml(row)

    button.addEventListener('click', (domEvent) => {
      domEvent.preventDefault()
      domEvent.stopPropagation()
      onSelect(row.item, domEvent)
    })

    button.addEventListener('contextmenu', (domEvent) => {
      domEvent.preventDefault()
      domEvent.stopPropagation()
      domEvent.stopImmediatePropagation?.()

      if (!isStayWithPlaceDetails(row.item)) {
        return
      }

      onStayContextMenu(row.item, domEvent)
    })

    list.appendChild(button)
  })

  popupRoot.addEventListener('click', (domEvent) => {
    domEvent.stopPropagation()
  })

  popupRoot.addEventListener('mousedown', (domEvent) => {
    domEvent.stopPropagation()
  })

  popupRoot.addEventListener('contextmenu', (domEvent) => {
    domEvent.stopPropagation()
  })

  return popupRoot
}

const openStackPopupAtCoordinates = (candidateLng, candidateLat, items) => {
  if (!isMapLibreMap(props.map) || !Array.isArray(items) || items.length <= 1) {
    return
  }

  if (candidateLng === null || candidateLat === null) {
    return
  }

  closeStackPopup()

  const popupElement = createStackPopupElement(
    items,
    (selectedItem, domEvent) => {
      const parsedIndex = Number.parseInt(selectedItem?.__timelineIndex, 10)
      const index = Number.isFinite(parsedIndex) ? parsedIndex : -1

      closeStackPopup()

      emit('marker-click', {
        timelineItem: selectedItem,
        stackItems: items,
        index,
        marker: null,
        event: {
          originalEvent: domEvent,
          target: props.map,
          lngLat: {
            lng: candidateLng,
            lat: candidateLat
          }
        }
      })
    },
    (selectedItem, domEvent) => {
      const parsedIndex = Number.parseInt(selectedItem?.__timelineIndex, 10)
      const index = Number.isFinite(parsedIndex) ? parsedIndex : -1

      closeStackPopup()

      emit('marker-contextmenu', {
        timelineItem: selectedItem,
        stackItems: items,
        index,
        marker: null,
        event: domEvent,
        latlng: {
          lat: candidateLat,
          lng: candidateLng
        },
        type: 'stay'
      })
    }
  )

  state.stackPopup = new maplibregl.Popup({
    closeButton: true,
    closeOnClick: true,
    closeOnMove: false,
    maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH,
    offset: 16,
    className: getMapPopupVariantClassName('compact', 'gp-timeline-stack-popup-container')
  })
    .setLngLat([candidateLng, candidateLat])
    .setDOMContent(popupElement)
    .addTo(props.map)
}

const clearTimelineMarkers = () => {
  state.timelineMarkers.forEach((markerEntry) => {
    markerEntry.cleanup?.()
  })
  state.timelineMarkers = []

  state.clusterMarkers.forEach((markerEntry) => {
    markerEntry.cleanup?.()
  })
  state.clusterMarkers = []

  if (isMapLibreMap(props.map)) {
    props.map.getCanvas().style.cursor = ''
  }
}

const clearClusterState = () => {
  const targetMap = state.boundMap

  if (state.debounceHandle !== null) {
    clearTimeout(state.debounceHandle)
    state.debounceHandle = null
  }

  if (state.moveEndHandler && targetMap) {
    targetMap.off('moveend', state.moveEndHandler)
    targetMap.off('zoomend', state.moveEndHandler)
    state.moveEndHandler = null
  }

  if (state.moveStartHandler && targetMap) {
    targetMap.off('movestart', state.moveStartHandler)
    targetMap.off('zoomstart', state.moveStartHandler)
    state.moveStartHandler = null
  }

  state.groups = []
}

const findHighlightedGroupContext = () => {
  if (!props.highlightedItem) {
    return null
  }

  const targetKey = getTimelineKey(props.highlightedItem)
  if (!targetKey) {
    return null
  }

  const groups = groupTimelineByCoordinate()
  for (const group of groups) {
    const focusedItem = group.items.find((item) => (
      item.__timelineKey === targetKey || isSameTimelineItem(item, props.highlightedItem)
    ))

    if (focusedItem) {
      return {
        group,
        focusedItem,
        highlightedKey: `${targetKey}|${distanceUnit.value || 'KILOMETERS'}`
      }
    }
  }

  return null
}

const syncHighlightedStayFocus = () => {
  if (!isMapLibreMap(props.map) || !props.visible || !props.highlightedItem || props.highlightedItem.type === 'trip') {
    state.lastHighlightedStayKey = ''
    closeHighlightedStayPopup()
    closeStackPopup()
    return
  }

  const context = findHighlightedGroupContext()
  if (!context) {
    state.lastHighlightedStayKey = ''
    closeHighlightedStayPopup()
    closeStackPopup()
    return
  }

  if (state.lastHighlightedStayKey === context.highlightedKey) {
    return
  }

  state.lastHighlightedStayKey = context.highlightedKey

  const latitude = toFiniteNumber(context.group.latitude)
  const longitude = toFiniteNumber(context.group.longitude)
  if (latitude === null || longitude === null) {
    return
  }

  const currentZoom = props.map.getZoom()
  const defaultZoom = 16
  const targetZoom = currentZoom >= defaultZoom ? currentZoom : defaultZoom

  props.map.easeTo({
    center: [longitude, latitude],
    zoom: targetZoom,
    duration: 450
  })

  if (context.group.items.length > 1) {
    closeHighlightedStayPopup()
    setTimeout(() => {
      if (!isMapLibreMap(props.map) || state.lastHighlightedStayKey !== context.highlightedKey) {
        return
      }
      openStackPopupAtCoordinates(longitude, latitude, context.group.items)
    }, 220)
    return
  }

  closeStackPopup()
  closeHighlightedStayPopup()

  state.highlightedStayPopupTimeoutId = setTimeout(() => {
    state.highlightedStayPopupTimeoutId = null

    if (!isMapLibreMap(props.map) || state.lastHighlightedStayKey !== context.highlightedKey) {
      return
    }

    state.highlightedStayPopupMount = mountMapPopup(
      MapInfoPopup,
      createPopupModel(context.focusedItem)
    )
    state.highlightedStayPopup = new maplibregl.Popup({
      closeButton: true,
      closeOnClick: true,
      closeOnMove: false,
      className: getMapPopupVariantClassName('compact', 'gp-timeline-popup-container'),
      maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH,
      offset: 14
    })
      .setLngLat([longitude, latitude])
      .setDOMContent(state.highlightedStayPopupMount.element)
      .addTo(props.map)
  }, 220)
}

// --- Zoom-aware clustering, computed synchronously from screen-pixel
// distance via map.project() - deliberately NOT routed through a real
// MapLibre GeoJSON source/queryRenderedFeatures. That approach (tried
// earlier) depends on the source's tiles being loaded, which is
// asynchronous and can lag behind a camera coming to rest, returning
// incomplete results for a moment and causing markers to visibly reshuffle
// after the fact. Projecting coordinates and grouping by pixel distance has
// no loading state at all - it's a pure function of the current view. ---

const computeClusters = (groups, mapInstance) => {
  if (mapInstance.getZoom() >= CLUSTER_MAX_ZOOM) {
    return { standalone: groups, clusters: [] }
  }

  const standalone = []
  const clusters = []

  groupByPixelDistance(
    groups,
    (group) => mapInstance.project([group.longitude, group.latitude]),
    CLUSTER_RADIUS
  ).forEach((members) => {
    if (members.length === 1) {
      standalone.push(members[0])
      return
    }

    const totalCount = members.reduce((sum, member) => sum + member.items.length, 0)
    const latitude = members.reduce((sum, member) => sum + (member.latitude * member.items.length), 0) / totalCount
    const longitude = members.reduce((sum, member) => sum + (member.longitude * member.items.length), 0) / totalCount

    clusters.push({ latitude, longitude, totalCount, members })
  })

  return { standalone, clusters }
}

const REQUERY_SETTLE_MS = 50

// Wait for the camera to be fully stationary before recomputing clusters.
// An automatic "fit to data" camera move on load (or any other external
// easeTo/flyTo) can fire several intermediate zoomend/moveend events in a
// row while still animating toward its final zoom - recomputing clusters at
// each of those intermediate zoom levels reshuffles the cluster/standalone
// split mid-animation, which looks like markers jumping around.
//
// A plain "wait N ms, then check isMoving()" debounce isn't quite enough:
// if two chained camera animations (e.g. a multi-leg fit-to-bounds) have a
// gap between them longer than the debounce window, isMoving() can
// genuinely read false for a moment in between and a premature render slips
// through before the next leg starts. `activityToken` closes that gap: it's
// bumped on every movement-related event (movestart/moveend/zoomstart/
// zoomend/renderLayer), and a scheduled render only proceeds if the token is
// still the same one it captured when scheduled - i.e. nothing happened
// during the whole wait, not just "nothing is happening right this instant".
const requestRequery = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  state.activityToken += 1
  const myToken = state.activityToken

  if (state.debounceHandle !== null) {
    clearTimeout(state.debounceHandle)
  }

  const attemptRender = () => {
    if (props.map.isMoving?.() || state.activityToken !== myToken) {
      state.debounceHandle = setTimeout(attemptRender, REQUERY_SETTLE_MS)
      return
    }

    state.debounceHandle = null
    renderVisibleMarkers()
  }

  state.debounceHandle = setTimeout(attemptRender, REQUERY_SETTLE_MS)
}

const renderStandaloneMarker = (group, hasActiveHighlight) => {
  const markerItems = group.items
  const primaryItem = markerItems[0]
  const primaryIndex = Number.isFinite(primaryItem?.__timelineIndex) ? primaryItem.__timelineIndex : -1
  const isStack = markerItems.length > 1
  const isHighlighted = Boolean(
    props.highlightedItem
    && markerItems.some((item) => isSameTimelineItem(item, props.highlightedItem))
  )
  const isDimmed = hasActiveHighlight && !isHighlighted

  const markerSpec = isStack
    ? createTimelineGroupMarkerElement({ items: markerItems, highlighted: isHighlighted, dimmed: isDimmed })
    : createTimelineMarkerElement({ item: primaryItem, highlighted: isHighlighted, dimmed: isDimmed })

  markerSpec.element.style.zIndex = isHighlighted ? '340' : (isDimmed ? '300' : '320')

  const weather = isStack ? null : getItemWeather(primaryItem)
  if (weather) {
    // The inner circle carries the dimmed/highlighted styles, so the badge follows them.
    const markerCircle = markerSpec.element.firstElementChild || markerSpec.element
    markerCircle.appendChild(createStayWeatherBadgeElement(weather))
  }

  const marker = new maplibregl.Marker({
    element: markerSpec.element,
    anchor: 'center',
    offset: markerSpec.offset || [0, 0]
  })
    .setLngLat([group.longitude, group.latitude])
    .addTo(props.map)

  let markerPopup = null
  let markerPopupMount = null
  if (!isStack && (primaryItem?.address || primaryItem?.timestamp)) {
    markerPopupMount = mountMapPopup(
      MapInfoPopup,
      createPopupModel(primaryItem)
    )
    markerPopup = new maplibregl.Popup({
      closeButton: true,
      closeOnClick: true,
      closeOnMove: false,
      className: getMapPopupVariantClassName('compact', 'gp-timeline-popup-container'),
      maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH,
      offset: 14
    }).setDOMContent(markerPopupMount.element)
  }

  const openMarkerPopup = () => {
    if (!markerPopup || !isMapLibreMap(props.map)) {
      return
    }

    markerPopup
      .setLngLat([group.longitude, group.latitude])
      .addTo(props.map)
  }

  const closeMarkerPopup = () => {
    markerPopup?.remove()
  }

  const handleClick = (domEvent) => {
    domEvent.preventDefault()
    domEvent.stopPropagation()

    if (isStack) {
      openStackPopupAtCoordinates(group.longitude, group.latitude, group.items)
      return
    }

    emit('marker-click', {
      timelineItem: primaryItem,
      stackItems: group.items,
      index: primaryIndex,
      marker: null,
      event: {
        target: marker,
        originalEvent: domEvent,
        lngLat: {
          lng: group.longitude,
          lat: group.latitude
        }
      }
    })

    openMarkerPopup()
  }

  const handleMouseEnter = (domEvent) => {
    props.map.getCanvas().style.cursor = 'pointer'

    emit('marker-hover', {
      timelineItem: primaryItem,
      index: primaryIndex,
      marker: null,
      event: {
        target: marker,
        originalEvent: domEvent,
        lngLat: {
          lng: group.longitude,
          lat: group.latitude
        }
      }
    })
  }

  const handleMouseLeave = () => {
    props.map.getCanvas().style.cursor = ''
  }

  const handleContextMenu = (domEvent) => {
    if (isStack || !isStayWithPlaceDetails(primaryItem)) {
      return
    }

    domEvent.preventDefault()
    domEvent.stopPropagation()
    domEvent.stopImmediatePropagation?.()

    emit('marker-contextmenu', {
      timelineItem: primaryItem,
      stackItems: group.items,
      index: primaryIndex,
      marker: null,
      event: domEvent,
      latlng: {
        lat: group.latitude,
        lng: group.longitude
      },
      type: 'stay'
    })
  }

  markerSpec.element.addEventListener('click', handleClick)
  markerSpec.element.addEventListener('mouseenter', handleMouseEnter)
  markerSpec.element.addEventListener('mouseleave', handleMouseLeave)
  markerSpec.element.addEventListener('contextmenu', handleContextMenu)

  state.timelineMarkers.push({
    marker,
    cleanup: () => {
      markerSpec.element.removeEventListener('click', handleClick)
      markerSpec.element.removeEventListener('mouseenter', handleMouseEnter)
      markerSpec.element.removeEventListener('mouseleave', handleMouseLeave)
      markerSpec.element.removeEventListener('contextmenu', handleContextMenu)
      closeMarkerPopup()
      markerPopupMount?.unmount?.()
      marker.remove()
    }
  })
}

const expandCluster = (cluster) => {
  const bounds = cluster.members.reduce((box, member) => ([
    [Math.min(box[0][0], member.longitude), Math.min(box[0][1], member.latitude)],
    [Math.max(box[1][0], member.longitude), Math.max(box[1][1], member.latitude)]
  ]), [[cluster.longitude, cluster.latitude], [cluster.longitude, cluster.latitude]])

  props.map.fitBounds(bounds, {
    padding: 60,
    maxZoom: CLUSTER_MAX_ZOOM + 2,
    duration: 280
  })
}

const renderClusterMarker = (cluster) => {
  const hasActiveHighlight = Boolean(props.highlightedItem)

  const markerSpec = createTimelineStackMarkerElement({
    count: cluster.totalCount,
    highlighted: false,
    dimmed: hasActiveHighlight
  })
  markerSpec.element.style.zIndex = '320'

  const marker = new maplibregl.Marker({
    element: markerSpec.element,
    anchor: 'center',
    offset: markerSpec.offset || [0, 0]
  })
    .setLngLat([cluster.longitude, cluster.latitude])
    .addTo(props.map)

  const handleClick = (domEvent) => {
    domEvent.preventDefault()
    domEvent.stopPropagation()
    expandCluster(cluster)
  }

  const handleMouseEnter = () => {
    props.map.getCanvas().style.cursor = 'pointer'
  }

  const handleMouseLeave = () => {
    props.map.getCanvas().style.cursor = ''
  }

  markerSpec.element.addEventListener('click', handleClick)
  markerSpec.element.addEventListener('mouseenter', handleMouseEnter)
  markerSpec.element.addEventListener('mouseleave', handleMouseLeave)

  state.clusterMarkers.push({
    marker,
    cleanup: () => {
      markerSpec.element.removeEventListener('click', handleClick)
      markerSpec.element.removeEventListener('mouseenter', handleMouseEnter)
      markerSpec.element.removeEventListener('mouseleave', handleMouseLeave)
      marker.remove()
    }
  })
}

const renderVisibleMarkers = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  clearTimelineMarkers()

  if (!props.visible || state.groups.length === 0) {
    return
  }

  const hasActiveHighlight = Boolean(props.highlightedItem)
  const { standalone, clusters } = computeClusters(state.groups, props.map)

  const isExcluded = (group) => excludedGroupIndices.value.has(group.groupIndex)

  standalone
    .filter((group) => !isExcluded(group))
    .forEach((group) => renderStandaloneMarker(group, hasActiveHighlight))
  clusters
    .filter((cluster) => !cluster.members.some(isExcluded))
    .forEach((cluster) => renderClusterMarker(cluster))
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  if (!props.visible || !Array.isArray(props.timelineData) || props.timelineData.length === 0) {
    clearTimelineMarkers()
    state.groups = []
    excludedGroupIndices.value = new Set()
    emit('groups-change')
    syncHighlightedStayFocus()
    return
  }

  // Exclusions are intentionally kept until the coordinator recomputes them
  // after 'groups-change', so unrelated re-renders (e.g. highlight changes)
  // don't flash the overlapped markers back.
  state.groups = groupTimelineByCoordinate().map((group, groupIndex) => ({ ...group, groupIndex }))
  emit('groups-change')
  requestRequery()

  // Runs synchronously (not deferred with the cluster requery above) so the
  // initial pan/zoom to a highlighted item happens once, immediately -
  // deferring it caused a visible "jump" after the map's first paint.
  syncHighlightedStayFocus()
}

// Synchronous access to the pre-cluster groups (for the cross-type overlap pass).
const getCurrentGroups = () => state.groups

// Synchronous "what is rendered right now" answer for the cross-type pass:
// standalone groups and clusters, as indices into getCurrentGroups().
const getRenderedEntities = () => {
  if (!isMapLibreMap(props.map) || !props.visible || state.groups.length === 0) {
    return []
  }

  // The highlighted item's marker is the focus: the cross-type pass keeps it
  // out of combo chips and keeps everything else from hiding under it.
  const isFocused = (groups) => Boolean(props.highlightedItem) && groups.some((group) => (
    group.items.some((item) => isSameTimelineItem(item, props.highlightedItem))
  ))
  const { standalone, clusters } = computeClusters(state.groups, props.map)
  return [
    ...standalone.map((group) => ({
      indices: [group.groupIndex],
      latitude: group.latitude,
      longitude: group.longitude,
      isCluster: false,
      isFocused: isFocused([group])
    })),
    ...clusters.map((cluster) => ({
      indices: cluster.members.map((member) => member.groupIndex),
      latitude: cluster.latitude,
      longitude: cluster.longitude,
      isCluster: true,
      isFocused: isFocused(cluster.members)
    }))
  ]
}

const setExcludedGroupIndices = (indices) => {
  const next = new Set(indices || [])
  const current = excludedGroupIndices.value
  if (next.size === current.size && [...next].every((index) => current.has(index))) {
    return
  }

  excludedGroupIndices.value = next
  renderVisibleMarkers()
}

const clearLayer = () => {
  clearTimelineMarkers()
  clearClusterState()
  state.lastHighlightedStayKey = ''
  closeHighlightedStayPopup()
  closeStackPopup()

  if (state.boundMap && state.styleLoadHandler) {
    state.boundMap.off('style.load', state.styleLoadHandler)
    state.styleLoadHandler = null
  }

  state.boundMap = null
}

watch(
  () => [props.map, props.timelineData, props.highlightedItem, props.visible, props.itemWeather, distanceUnit.value, temperatureUnit.value],
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

    if (!state.moveEndHandler) {
      state.moveEndHandler = () => requestRequery()
      props.map.on('moveend', state.moveEndHandler)
      props.map.on('zoomend', state.moveEndHandler)
    }

    if (!state.moveStartHandler) {
      // Any new leg of a camera animation (even mid-debounce) must reset
      // the settle window - see the comment on requestRequery.
      state.moveStartHandler = () => requestRequery()
      props.map.on('movestart', state.moveStartHandler)
      props.map.on('zoomstart', state.moveStartHandler)
    }

    renderLayer()
  },
  { immediate: true, deep: true }
)

onBeforeUnmount(() => {
  clearLayer()
})

defineExpose({
  getCurrentGroups,
  getRenderedEntities,
  setExcludedGroupIndices
})
</script>

<style>
/* Zoom cluster (click zooms in). White ring + shadow like every other map marker. */
.timeline-stack-marker {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f766e 0%, #0ea5a4 100%);
  border: 2px solid #ffffff;
  color: #ffffff;
  font-size: 0.78rem;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.32);
  box-sizing: border-box;
}

.timeline-stack-marker-highlighted {
  width: 34px;
  height: 34px;
  background: linear-gradient(135deg, #ea580c 0%, #f97316 100%);
}

.timeline-stack-marker-dimmed {
  opacity: 0.28;
  filter: grayscale(0.35) saturate(0.7);
}

.p-dark .timeline-stack-marker {
  background: linear-gradient(135deg, #14b8a6 0%, #0d9488 100%);
}

.p-dark .timeline-stack-marker-highlighted {
  background: linear-gradient(135deg, #fb923c 0%, #f97316 100%);
}

/* Same-location group (click lists the items): count on the dominant item's marker. */
.timeline-marker-count-badge {
  position: absolute;
  top: -7px;
  right: -7px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #0f172a;
  border: 1.5px solid #ffffff;
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  box-sizing: border-box;
  pointer-events: none;
}

.p-dark .timeline-marker-count-badge {
  background: #f8fafc;
  border-color: #0f172a;
  color: #0f172a;
}
</style>
