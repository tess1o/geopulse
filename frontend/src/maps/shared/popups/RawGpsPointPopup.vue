<template>
  <MapPopupCard
    class="raw-gps-popup"
    :title="title"
    :subtitle="subtitle"
    variant="wide"
  >
    <div class="raw-gps-popup-grid">
      <div class="gp-map-popup-label raw-gps-popup-label">Coordinates</div>
      <div class="gp-map-popup-value">
        <span class="gp-map-popup-value-line">{{ formatCoordinate(selectedPoint?.latitude) }}</span>
        <span class="gp-map-popup-value-line">{{ formatCoordinate(selectedPoint?.longitude) }}</span>
      </div>

      <div class="gp-map-popup-label raw-gps-popup-label">Accuracy</div>
      <div class="gp-map-popup-value">{{ formatMeters(selectedPoint?.accuracy) }}</div>

      <div class="gp-map-popup-label raw-gps-popup-label">Battery</div>
      <div class="gp-map-popup-value">{{ formatBattery(selectedPoint?.battery) }}</div>

      <div class="gp-map-popup-label raw-gps-popup-label">Speed</div>
      <div class="gp-map-popup-value">{{ formatSpeed(selectedPoint?.velocity) }}</div>

      <div class="gp-map-popup-label raw-gps-popup-label">Altitude</div>
      <div class="gp-map-popup-value">{{ formatMeters(selectedPoint?.altitude) }}</div>
    </div>

    <div class="raw-gps-popup-location">
      <template v-if="locationStatus === 'resolved' && locationName">
        <div class="raw-gps-popup-location-name">{{ locationName }}</div>
        <div class="raw-gps-popup-location-source">{{ locationSource }}</div>
      </template>
      <div v-else-if="locationStatus === 'error'" class="raw-gps-popup-location-muted">
        Location unavailable
      </div>
      <div v-else class="raw-gps-popup-location-muted">Finding location...</div>
    </div>

    <div v-if="isStack" class="raw-gps-stack-list">
      <button
        v-for="(point, pointIndex) in visiblePoints"
        :key="point.id ?? pointIndex"
        type="button"
        :class="['raw-gps-stack-row', pointIndex === selectedPointIndex ? 'is-selected' : '']"
        @click.stop.prevent="selectPoint(pointIndex)"
      >
        <span class="raw-gps-stack-time">{{ formatRawGpsDateTime(timezone, point.timestamp) }}</span>
        <span class="raw-gps-stack-telemetry">
          <span class="raw-gps-stack-telemetry-item">
            <span class="raw-gps-stack-telemetry-label">Speed</span>
            <span>{{ formatSpeed(point.velocity) }}</span>
          </span>
          <span class="raw-gps-stack-telemetry-item">
            <span class="raw-gps-stack-telemetry-label">Battery</span>
            <span>{{ formatBattery(point.battery) }}</span>
          </span>
        </span>
      </button>
      <div v-if="overflowCount > 0" class="raw-gps-stack-overflow">
        Showing first {{ visiblePoints.length }} of {{ group.count }} points
      </div>
    </div>
  </MapPopupCard>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { formatSpeed } from '@/utils/calculationsHelpers'
import MapPopupCard from './MapPopupCard.vue'

const props = defineProps({
  group: {
    type: Object,
    required: true
  },
  timezone: {
    type: Object,
    required: true
  },
  resolveLocation: {
    type: Function,
    default: null
  },
  onRender: {
    type: Function,
    default: null
  }
})

const selectedPointIndex = ref(0)
const locationStatus = ref('loading')
const resolvedLocation = ref(null)

const points = computed(() => Array.isArray(props.group?.points) ? props.group.points : [])
const isStack = computed(() => Number(props.group?.count || 0) > 1)
const selectedPoint = computed(() => (
  points.value[selectedPointIndex.value] || props.group?.representative || points.value[0] || null
))
const visiblePoints = computed(() => points.value.slice(0, 80))
const overflowCount = computed(() => Math.max(0, points.value.length - visiblePoints.value.length))
const title = computed(() => (
  isStack.value
    ? `${props.group.count} GPS points here`
    : formatRawGpsDateTime(props.timezone, selectedPoint.value?.timestamp)
))
const subtitle = computed(() => (
  isStack.value
    ? `${formatRawGpsDateTime(props.timezone, props.group.firstTimestamp)} - ${formatRawGpsDateTime(props.timezone, props.group.lastTimestamp)}`
    : selectedPoint.value?.sourceType || 'Raw GPS point'
))
const locationName = computed(() => resolvedLocation.value?.locationName || '')
const locationSource = computed(() => (
  resolvedLocation.value?.sourceType === 'favorite' ? 'Favorite' : 'Geocoding'
))

const notifyRender = () => {
  if (typeof props.onRender !== 'function') return
  nextTick(() => props.onRender())
}

const selectPoint = (pointIndex) => {
  selectedPointIndex.value = pointIndex
  notifyRender()
}

const loadLocation = async () => {
  if (typeof props.resolveLocation !== 'function') {
    locationStatus.value = 'error'
    notifyRender()
    return
  }

  try {
    resolvedLocation.value = await Promise.resolve(props.resolveLocation(props.group.representative || selectedPoint.value))
    locationStatus.value = 'resolved'
  } catch {
    locationStatus.value = 'error'
  } finally {
    notifyRender()
  }
}

onMounted(() => {
  notifyRender()
  loadLocation()
})

const formatRawGpsDateTime = (timezone, value) => {
  if (!value) return 'Unknown time'
  try {
    return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value, { withSeconds: true })}`
  } catch {
    return 'Unknown time'
  }
}

const formatCoordinate = (value) => {
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(6) : 'N/A'
}

const formatMeters = (value) => {
  const number = Number(value)
  return Number.isFinite(number) ? `${Math.round(number)}m` : 'N/A'
}

const formatBattery = (value) => {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? `${Math.round(number)}%` : 'N/A'
}
</script>

<style src="../styles/rawGpsPointPopup.css"></style>
