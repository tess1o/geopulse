<template>
  <div class="friends-timeline-map">
    <MapContainer
      ref="mapContainerRef"
      :map-id="mapId"
      :center="mapCenter"
      :zoom="13"
      height="100%"
      width="100%"
      :show-controls="false"
      @map-ready="handleMapReady"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { MapContainer } from '@/components/maps'
import { createFriendsTimelineMapAdapter } from '@/maps/friendsTimeline/runtime/createFriendsTimelineMapAdapter'
import { toLeafletLatLngTuple } from '@/maps/shared/coordinateUtils'

const props = defineProps({
  multiUserTimeline: {
    type: Object,
    default: null
  },
  selectedUserIds: {
    type: Set,
    default: () => new Set()
  },
  userColorMap: {
    type: Map,
    default: () => new Map()
  },
  selectedItem: {
    type: Object,
    default: null
  }
})

const mapId = ref(`friends-timeline-map-${Date.now()}`)
const mapContainerRef = ref(null)
const map = ref(null)
const mapAdapter = ref(null)

const visibleTimelines = computed(() => {
  if (!props.multiUserTimeline || !props.multiUserTimeline.timelines) {
    return []
  }

  return props.multiUserTimeline.timelines.filter((timeline) => props.selectedUserIds.has(timeline.userId))
})

const mapCenter = computed(() => {
  if (visibleTimelines.value.length > 0) {
    const firstTimeline = visibleTimelines.value[0].timeline
    if (firstTimeline && firstTimeline.stays && firstTimeline.stays.length > 0) {
      const firstStayCoords = toLeafletLatLngTuple(firstTimeline.stays[0])
      if (firstStayCoords) {
        return firstStayCoords
      }
    }
  }

  return [51.505, -0.09]
})

function renderVisibleTimelines() {
  if (!mapAdapter.value) {
    return
  }

  mapAdapter.value.render({
    visibleTimelines: visibleTimelines.value
  })

  if (props.selectedItem) {
    mapAdapter.value.focusOnItem(props.selectedItem)
  }
}

function handleMapReady(mapInstance) {
  map.value = mapInstance

  mapAdapter.value?.destroy?.()

  const adapter = createFriendsTimelineMapAdapter(mapInstance)
  adapter.initialize?.(mapInstance)
  mapAdapter.value = adapter

  renderVisibleTimelines()
}

watch([() => props.selectedUserIds, () => props.multiUserTimeline], () => {
  if (!map.value) {
    return
  }

  renderVisibleTimelines()
}, { deep: true })

watch(() => props.selectedItem, (newItem) => {
  if (!mapAdapter.value) {
    return
  }

  mapAdapter.value.focusOnItem(newItem)
}, { deep: true })

onBeforeUnmount(() => {
  mapAdapter.value?.destroy?.()
  mapAdapter.value = null
})
</script>

<style scoped>
.friends-timeline-map {
  width: 100%;
  height: 100%;
  position: relative;
}
</style>
