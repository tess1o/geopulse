<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import { computed, onBeforeUnmount, readonly, ref, watch } from 'vue'
import L from 'leaflet'
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { buildTripPlanItemPopupModel } from '@/maps/shared/popups/tripPlanPopupModel'
import { MAP_POPUP_COMPACT_MAX_WIDTH_PX, getMapPopupVariantClassName } from '@/maps/shared/popups/mapPopupOptions'
import { useTimezone } from '@/composables/useTimezone'

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
  },
  selectedPlanItemId: {
    type: [Number, String],
    default: null
  }
})

const emit = defineEmits(['plan-item-contextmenu', 'plan-item-click'])

const timezone = useTimezone()

const baseLayerRef = ref(null)
const planMarkers = ref([])

const hasPlannedItems = computed(() => Array.isArray(props.plannedItemsData) && props.plannedItemsData.length > 0)

const isSelected = (item) => (
  props.selectedPlanItemId !== null
  && props.selectedPlanItemId !== undefined
  && String(item?.planItemId) === String(props.selectedPlanItemId)
)

const createPlanIcon = (item) => {
  const isMust = String(item?.priority || '').toUpperCase() === 'MUST'
  // A manually rejected stop is not "visited" for display purposes either.
  const isVisited = Boolean(item?.isVisited) && item?.manualOverrideState !== 'REJECTED'
  const selected = isSelected(item)
  // Colour = visit state, then priority (the same encoding as the vector layer).
  // Fixed colours in both themes: the tiles don't switch theme.
  const pinColor = isVisited ? '#15803d' : (isMust ? 'var(--gp-danger)' : 'var(--gp-warning)')
  const scale = (isMust ? 1.1 : 1) * (selected ? 1.2 : 1)
  const markerWidth = Math.round(42 * scale)
  const markerHeight = Math.round(58 * scale)
  const anchorX = Math.round(markerWidth / 2)
  const anchorY = markerHeight - 1
  // The sequence number matches the stop's number in the rail, so the two can be read together.
  const label = item?.sequence ? String(item.sequence) : ''
  const fontSize = label.length > 2 ? 4.2 : 5.4

  return L.divIcon({
    className: 'gp-trip-plan-marker',
    html: `
      <svg width="${markerWidth}" height="${markerHeight}" viewBox="0 0 24 24" aria-hidden="true" style="filter: drop-shadow(0 2px 2px rgba(15,23,42,0.35));">
        <path fill="${pinColor}" stroke="${selected ? '#ffffff' : 'none'}" stroke-width="${selected ? 1.2 : 0}" d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" />
        <circle cx="12" cy="9" r="4.8" fill="#ffffff" />
        <text x="12" y="9" text-anchor="middle" dominant-baseline="central" font-family="Arial, sans-serif" font-weight="700" font-size="${fontSize}" fill="#0f172a">${label}</text>
      </svg>
    `,
    iconSize: [markerWidth, markerHeight],
    iconAnchor: [anchorX, anchorY]
  })
}

const handleLayerReady = () => {
  if (hasPlannedItems.value) {
    renderPlanMarkers()
  }
}

const renderPlanMarkers = () => {
  if (!baseLayerRef.value) return
  clearPlanMarkers()
  if (!hasPlannedItems.value) return

  props.plannedItemsData.forEach((item, index) => {
    const lat = item?.latitude
    const lon = item?.longitude
    if (typeof lat !== 'number' || typeof lon !== 'number') return

    const marker = L.marker([lat, lon], {
      icon: createPlanIcon(item),
      planItem: item,
      planIndex: index,
      // The selected stop is drawn above its neighbours.
      zIndexOffset: isSelected(item) ? 1000 : 0,
      ...props.markerOptions
    })

    // Click opens the stop's card (Leaflet opens a bound popup on click) and selects it in the
    // rail - the only way to inspect a stop on touch screens, which have no hover.
    const popupMount = mountMapPopup(MapInfoPopup, buildTripPlanItemPopupModel(item, { timezone }))
    marker.bindPopup(popupMount.element, {
      maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH_PX,
      className: getMapPopupVariantClassName('compact', 'gp-trip-plan-popup-container')
    })
    marker.on('click', () => {
      emit('plan-item-click', { item, index, type: 'trip-plan' })
    })

    marker.on('contextmenu', (e) => {
      if (e.originalEvent) {
        e.originalEvent.preventDefault()
        e.originalEvent.stopPropagation()
        e.originalEvent.stopImmediatePropagation()
      }
      L.DomEvent.stop(e)

      emit('plan-item-contextmenu', {
        item,
        index,
        event: e.originalEvent,
        latlng: e.latlng,
        type: 'trip-plan'
      })
    })

    marker.bindTooltip(item?.name || item?.title || 'Planned stop', {
      permanent: false,
      direction: 'top',
      offset: [0, -10]
    })

    baseLayerRef.value.addToLayer(marker)
    planMarkers.value.push({ marker, item, index, popupMount })
  })
}

const clearPlanMarkers = () => {
  planMarkers.value.forEach(({ marker, popupMount }) => {
    baseLayerRef.value?.removeFromLayer(marker)
    popupMount?.unmount()
  })
  planMarkers.value = []
}

watch(() => props.plannedItemsData, () => {
  if (baseLayerRef.value?.isReady) {
    renderPlanMarkers()
  }
}, { deep: true })

// Selection only restyles the markers in place: re-rendering them would close the popup that the
// selecting click has just opened.
watch(() => props.selectedPlanItemId, () => {
  planMarkers.value.forEach(({ marker, item }) => {
    marker.setIcon(createPlanIcon(item))
    marker.setZIndexOffset(isSelected(item) ? 1000 : 0)
  })
})

onBeforeUnmount(() => {
  clearPlanMarkers()
})

defineExpose({
  baseLayerRef,
  planMarkers: readonly(planMarkers),
  clearPlanMarkers
})
</script>
