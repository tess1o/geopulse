<template>
  <span style="display: none" aria-hidden="true"></span>
</template>

<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { createTripReconstructionMapAdapter } from '@/maps/tripReconstruction/runtime/createTripReconstructionMapAdapter'
import { hasValidCoordinates } from '@/maps/tripReconstruction/shared/tripReconstructionMapData'

const props = defineProps({
  map: {
    type: Object,
    default: null
  },
  activeSegment: {
    type: Object,
    default: null
  },
  activeSegmentIndex: {
    type: Number,
    default: -1
  },
  contextPoints: {
    type: Array,
    default: () => []
  },
  editableContext: { type: Boolean, default: false },
  contextTripLines: {
    type: Array,
    default: () => []
  },
  idPrefix: {
    type: String,
    default: null
  }
})

const emit = defineEmits([
  'context-point-dragged',
  'stay-dragged',
  'waypoint-dragged',
  'waypoint-removed'
])

const mapAdapter = ref(null)

const renderOverlay = () => {
  mapAdapter.value?.render?.({
    activeSegment: props.activeSegment,
    activeSegmentIndex: props.activeSegmentIndex,
    contextPoints: props.contextPoints.map(point => ({ ...point, draggable: props.editableContext && point.draggable !== false && !['•', '◇'].includes(point.label) })),
    contextTripLines: props.contextTripLines
  })
}

const destroyAdapter = () => {
  mapAdapter.value?.destroy?.()
  mapAdapter.value = null
}

watch(() => props.map, (nextMap) => {
  destroyAdapter()

  if (!nextMap) {
    return
  }

  mapAdapter.value = createTripReconstructionMapAdapter(nextMap, {
    idPrefix: props.idPrefix,
    hasValidCoordinates,
    onContextPointDragged: (point, latitude, longitude) => {
      emit('context-point-dragged', { label: point.label, latitude, longitude })
    },
    onStayDragged: (segmentIndex, latitude, longitude) => {
      emit('stay-dragged', { segmentIndex, latitude, longitude })
    },
    onWaypointDragged: (segmentIndex, waypointIndex, latitude, longitude) => {
      emit('waypoint-dragged', { segmentIndex, waypointIndex, latitude, longitude })
    },
    onWaypointRemoved: (segmentIndex, waypointIndex) => {
      emit('waypoint-removed', { segmentIndex, waypointIndex })
    }
  })

  mapAdapter.value.initialize?.(nextMap)
  renderOverlay()
}, { immediate: true })

watch(
  () => [props.activeSegment, props.activeSegmentIndex, props.contextPoints, props.contextTripLines, props.editableContext],
  () => {
    renderOverlay()
  },
  { deep: true }
)

onUnmounted(() => {
  destroyAdapter()
})
</script>
