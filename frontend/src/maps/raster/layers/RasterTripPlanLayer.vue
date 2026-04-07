<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import { computed, readonly, ref, watch } from 'vue'
import L from 'leaflet'
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'

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

const baseLayerRef = ref(null)
const planMarkers = ref([])

const hasPlannedItems = computed(() => Array.isArray(props.plannedItemsData) && props.plannedItemsData.length > 0)

const createPlanIcon = (item) => {
  const isMust = String(item?.priority || '').toUpperCase() === 'MUST'
  const pinColor = isMust ? 'var(--gp-danger-color, #dc2626)' : 'var(--gp-warning-color, #f59e0b)'
  const markerWidth = isMust ? 48 : 42
  const markerHeight = isMust ? 66 : 58
  const anchorX = Math.round(markerWidth / 2)
  const anchorY = markerHeight - 1

  return L.divIcon({
    className: 'gp-trip-plan-marker',
    html: `
      <svg width="${markerWidth}" height="${markerHeight}" viewBox="0 0 24 24" aria-hidden="true" style="filter: drop-shadow(0 2px 2px rgba(15,23,42,0.35));">
        <path fill="${pinColor}" d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" />
        <circle cx="12" cy="9" r="3.2" fill="var(--gp-surface-white, #ffffff)" />
        <circle cx="12" cy="9" r="${isMust ? '1.7' : '1.2'}" fill="${isMust ? 'var(--gp-danger-contrast, #7f1d1d)' : 'var(--gp-warning-contrast, #7c2d12)'}" />
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
      ...props.markerOptions
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
    planMarkers.value.push({ marker, item, index })
  })
}

const clearPlanMarkers = () => {
  planMarkers.value.forEach(({ marker }) => {
    baseLayerRef.value?.removeFromLayer(marker)
  })
  planMarkers.value = []
}

watch(() => props.plannedItemsData, () => {
  if (baseLayerRef.value?.isReady) {
    renderPlanMarkers()
  }
}, { deep: true })

defineExpose({
  baseLayerRef,
  planMarkers: readonly(planMarkers),
  clearPlanMarkers
})
</script>
