<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="dialogTitle"
    :modal="true"
    :style="{ width: '70vw', minWidth: '600px' }"
    @hide="$emit('close')"
  >
    <div class="trip-details-content">
      <!-- Map Section -->
      <div class="map-section">
        <h4 class="section-title">Trip Route</h4>
        <MapContainer
          :map-id="`trip-details-map-${mapId}`"
          :center="mapCenter"
          :zoom="14"
          :show-controls="false"
          height="350px"
          width="100%"
          @map-ready="handleMapReady"
        />
      </div>

      <!-- Details Section -->
      <div class="details-section">
        <!-- Trip Header -->
        <div class="trip-header">
          <h3 class="trip-title">{{ getTripTitle() }}</h3>
          <Tag 
            v-if="trip?.movementType"
            :value="trip.movementType"
            :severity="getTransportSeverity(trip.movementType)"
            :icon="getTransportIcon(trip.movementType)"
            class="transport-tag"
          />
        </div>
        
        <div class="details-grid">
          <!-- Timing Information -->
          <div class="detail-group">
            <h4 class="section-title">Timing</h4>
            <div class="detail-item">
              <span class="detail-label">Start:</span>
              <span class="detail-value">{{ getStartDateTime() }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">End:</span>
              <span class="detail-value">{{ getEndDateTime() }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">Duration:</span>
              <span class="detail-value duration-badge">
                {{ formatDuration(trip?.tripDuration) }}
              </span>
            </div>
          </div>

          <!-- Trip Information -->
          <div class="detail-group">
            <h4 class="section-title">Trip Details</h4>
            <div class="detail-item">
              <span class="detail-label">Distance:</span>
              <span class="detail-value">{{ formatDistance(trip?.distanceMeters) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">Route Points:</span>
              <span class="detail-value">{{ trip?.path?.length || 0 }} points</span>
            </div>
          </div>

          <!-- Route Information -->
          <div class="detail-group">
            <h4 class="section-title">Route</h4>
            <div class="detail-item">
              <span class="detail-label">Origin:</span>
              <span class="detail-value">{{ trip?.origin?.locationName || 'Unknown' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">Destination:</span>
              <span class="detail-value">{{ trip?.destination?.locationName || 'Unknown' }}</span>
            </div>
          </div>

          <!-- Coordinates -->
          <div class="detail-group">
            <h4 class="section-title">Coordinates</h4>
            <div class="detail-item">
              <span class="detail-label">Start:</span>
              <span class="detail-value coordinate copyable" @click="copyToClipboard(`${trip?.latitude}, ${trip?.longitude}`)">
                {{ trip?.latitude?.toFixed(6) }}, {{ trip?.longitude?.toFixed(6) }}
                <i class="pi pi-copy copy-icon"></i>
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">End:</span>
              <span class="detail-value coordinate copyable" @click="copyToClipboard(`${trip?.endLatitude}, ${trip?.endLongitude}`)">
                {{ trip?.endLatitude?.toFixed(6) }}, {{ trip?.endLongitude?.toFixed(6) }}
                <i class="pi pi-copy copy-icon"></i>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Dialog Footer -->
    <template #footer>
      <Button label="Close" outlined @click="internalVisible = false" />
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import MapContainer from '@/components/maps/MapContainer.vue'
import { useTimezone } from '@/composables/useTimezone'
import { formatDurationSmart, formatDistance } from '@/utils/calculationsHelpers'

const timezone = useTimezone()
const toast = useToast()

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  trip: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close'])

const internalVisible = computed({
  get: () => props.visible,
  set: (value) => {
    if (!value) {
      emit('close')
    }
  }
})

const mapId = ref(Date.now())
const mapInstance = ref(null)

const dialogTitle = computed(() => {
  if (!props.trip) return 'Trip Details'
  return `Trip Details - ${props.trip.movementType || 'Unknown'}`
})

const mapCenter = computed(() => {
  if (!props.trip?.latitude || !props.trip?.longitude) {
    return [49.5472, 25.5951] // Default center
  }
  return [props.trip.latitude, props.trip.longitude]
})

// Methods
const getTripTitle = () => {
  const origin = props.trip?.origin?.locationName || 'Unknown Origin'
  const destination = props.trip?.destination?.locationName || 'Unknown Destination'
  return `${origin} → ${destination}`
}

const getStartDateTime = () => {
  if (!props.trip?.timestamp) return 'N/A'
  return timezone.format(props.trip.timestamp, 'YYYY-MM-DD HH:mm:ss')
}

const getEndDateTime = () => {
  if (!props.trip?.timestamp || !props.trip?.tripDuration) return 'N/A'
  
  const startTime = timezone.fromUtc(props.trip.timestamp)
  const endTime = startTime.clone().add(props.trip.tripDuration, 'seconds')
  
  return timezone.format(endTime.toISOString(), 'YYYY-MM-DD HH:mm:ss')
}

const formatDuration = (seconds) => {
  return formatDurationSmart(seconds || 0)
}

const getTransportSeverity = (transportMode) => {
  const severityMap = {
    'CAR': 'info',
    'WALK': 'success', 
    'UNKNOWN': 'secondary'
  }
  return severityMap[transportMode?.toUpperCase()] || 'secondary'
}

const getTransportIcon = (transportMode) => {
  const iconMap = {
    'CAR': 'pi pi-car',
    'WALK': 'fas fa-walking',
    'UNKNOWN': 'pi pi-question-circle'
  }
  return iconMap[transportMode?.toUpperCase()] || 'pi pi-question-circle'
}

const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    toast.add({
      severity: 'success',
      summary: 'Copied!',
      detail: 'Coordinates copied to clipboard',
      life: 2000
    })
  } catch (err) {
    console.error('Failed to copy:', err)
    toast.add({
      severity: 'error',
      summary: 'Copy Failed',
      detail: 'Could not copy coordinates',
      life: 3000
    })
  }
}

const handleMapReady = (map) => {
  mapInstance.value = map
  
  // Create PathLayer instance and add it to the map when trip data is available
  nextTick(() => {
    if (props.trip?.path && props.trip.path.length > 0) {
      addPathToMap()
    }
  })
}

const addPathToMap = () => {
  if (!mapInstance.value || !props.trip?.path) return

  try {
    // Create PathLayer with trip data
    const pathData = {
      path: props.trip.path,
      color: getPathColor(props.trip.movementType),
      weight: 4,
      opacity: 0.8
    }

    // Add path to map
    const pathLayer = new window.L.polyline(
      props.trip.path.map(point => [point.latitude, point.longitude]),
      pathData
    ).addTo(mapInstance.value)

    // Add start and end markers
    if (props.trip.latitude && props.trip.longitude) {
      new window.L.marker([props.trip.latitude, props.trip.longitude], {
        icon: new window.L.divIcon({
          className: 'start-marker',
          html: '<div class="marker-pin start-pin"><i class="pi pi-play"></i></div>',
          iconSize: [30, 30],
          iconAnchor: [15, 15]
        })
      }).addTo(mapInstance.value)
    }

    if (props.trip.endLatitude && props.trip.endLongitude) {
      new window.L.marker([props.trip.endLatitude, props.trip.endLongitude], {
        icon: new window.L.divIcon({
          className: 'end-marker', 
          html: '<div class="marker-pin end-pin"><i class="pi pi-stop"></i></div>',
          iconSize: [30, 30],
          iconAnchor: [15, 15]
        })
      }).addTo(mapInstance.value)
    }

    // Fit map to show entire path
    const bounds = pathLayer.getBounds()
    mapInstance.value.fitBounds(bounds, { padding: [20, 20] })
    
  } catch (error) {
    console.error('Error adding path to map:', error)
  }
}

const getPathColor = (movementType) => {
  const colorMap = {
    'CAR': '#ef4444',
    'WALK': '#22c55e', 
    'BIKE': '#3b82f6',
    'PUBLIC_TRANSPORT': '#f59e0b',
    'TRAIN': '#8b5cf6',
    'PLANE': '#ef4444',
    'UNKNOWN': '#6b7280'
  }
  return colorMap[movementType?.toUpperCase()] || '#6b7280'
}

// Watch for trip changes to update map
watch(() => props.trip, (newTrip) => {
  if (newTrip && mapInstance.value) {
    nextTick(() => {
      addPathToMap()
    })
  }
}, { deep: true })

// Reset map ID when dialog opens/closes to ensure fresh map
watch(() => props.visible, (isVisible) => {
  if (isVisible) {
    mapId.value = Date.now()
  }
})
</script>

<style scoped>
.trip-details-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-lg);
  max-height: 80vh;
  overflow-y: auto;
}

.map-section {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.section-title {
  margin: 0 0 var(--gp-spacing-sm) 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.details-section {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-lg);
}

.trip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-md);
  padding-bottom: var(--gp-spacing-md);
  border-bottom: 1px solid var(--gp-border-light);
}

.trip-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  flex: 1;
}

.transport-tag {
  font-size: 0.8rem;
  flex-shrink: 0;
}

.details-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--gp-spacing-lg);
}

.detail-group {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.detail-item {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm) 0;
  border-bottom: 1px solid var(--gp-border-subtle);
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  min-width: 80px;
  flex-shrink: 0;
}

.detail-value {
  color: var(--gp-text-primary);
  flex: 1;
  word-break: break-word;
}

.coordinate {
  font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, 'Courier New', monospace;
  font-size: 0.875rem;
}

.copyable {
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
  position: relative;
}

.copyable:hover {
  background: var(--gp-surface-light);
  color: var(--gp-primary);
}

.copy-icon {
  margin-left: var(--gp-spacing-xs);
  opacity: 0.6;
  font-size: 0.75rem;
}

.copyable:hover .copy-icon {
  opacity: 1;
}

.duration-badge {
  background: var(--gp-success-50);
  color: var(--gp-success-700);
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 0.9rem;
  font-weight: 600;
  display: inline-block;
}

/* Map markers */
:deep(.marker-pin) {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: white;
  border: 2px solid white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

:deep(.start-pin) {
  background: var(--gp-success);
}

:deep(.end-pin) {
  background: var(--gp-danger);
}

/* Dark Mode */
.p-dark .trip-header {
  border-color: var(--gp-border-dark);
}

.p-dark .detail-item {
  border-color: var(--gp-border-subtle);
}

.p-dark .copyable:hover {
  background: var(--gp-surface-darker);
}

.p-dark .duration-badge {
  background: var(--gp-success-900);
  color: var(--gp-success-300);
}

/* Responsive */
@media (max-width: 768px) {
  .trip-details-content {
    max-height: 85vh;
  }
  
  .trip-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--gp-spacing-sm);
  }
  
  .details-grid {
    grid-template-columns: 1fr;
  }
  
  .detail-item {
    flex-direction: column;
    gap: var(--gp-spacing-xs);
    align-items: flex-start;
  }
  
  .detail-label {
    min-width: auto;
    font-size: 0.875rem;
  }
}
</style>