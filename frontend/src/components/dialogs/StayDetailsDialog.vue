<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="dialogTitle"
    :modal="true"
    :style="{ width: '60vw', minWidth: '500px' }"
    @hide="$emit('close')"
  >
    <div class="stay-details-content">
      <!-- Map Section -->
      <div class="map-section">
        <h4 class="section-title">Location Map</h4>
        <MapContainer
          :map-id="`stay-details-map-${mapId}`"
          :center="mapCenter"
          :zoom="16"
          :show-controls="false"
          height="300px"
          width="100%"
          @map-ready="handleMapReady"
        />
      </div>

      <!-- Details Section -->
      <div class="details-section">
        <!-- Location Name Header -->
        <div class="location-header">
          <h3 class="location-name">{{ stay?.locationName || 'Unknown Location' }}</h3>
          <p v-if="stay?.address" class="location-address">{{ stay.address }}</p>
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
                {{ formatDuration(stay?.stayDuration) }}
              </span>
            </div>
          </div>

          <!-- Location Information -->
          <div class="detail-group">
            <h4 class="section-title">Coordinates</h4>
            <div class="detail-item">
              <span class="detail-label">Latitude:</span>
              <span class="detail-value coordinate">{{ stay?.latitude?.toFixed(6) || 'N/A' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">Longitude:</span>
              <span class="detail-value coordinate">{{ stay?.longitude?.toFixed(6) || 'N/A' }}</span>
            </div>
            <div v-if="stay?.latitude && stay?.longitude" class="detail-item">
              <span class="detail-label">Coordinates:</span>
              <span class="detail-value copyable" @click="copyToClipboard(`${stay.latitude}, ${stay.longitude}`)">
                {{ stay.latitude?.toFixed(6) }}, {{ stay.longitude?.toFixed(6) }}
                <i class="pi pi-copy copy-icon"></i>
              </span>
            </div>
          </div>

          <!-- Additional Information -->
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
import { formatDurationSmart } from '@/utils/calculationsHelpers'

const timezone = useTimezone()
const toast = useToast()

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  stay: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['close'])

// Internal visibility state
const internalVisible = ref(props.visible)

// Map data
const mapId = ref(Math.random().toString(36).substr(2, 9))
const mapInstance = ref(null)
const marker = ref(null)

// Computed
const dialogTitle = computed(() => {
  return `Stay Details - ${props.stay?.locationName || 'Unknown Location'}`
})

const mapCenter = computed(() => {
  return {
    lat: props.stay?.latitude || 0,
    lng: props.stay?.longitude || 0
  }
})

// Methods
const formatDate = (timestamp) => {
  return timezone.format(timestamp, 'YYYY-MM-DD')
}

const formatTime = (timestamp) => {
  return timezone.format(timestamp, 'HH:mm:ss')
}

const getStartDateTime = () => {
  if (!props.stay?.timestamp) return 'N/A'
  return timezone.format(props.stay.timestamp, 'YYYY-MM-DD HH:mm:ss')
}

const getEndDateTime = () => {
  if (!props.stay?.timestamp || !props.stay?.stayDuration) return 'N/A'
  
  // Create a moment object from the timestamp and add the duration in seconds
  const startTime = timezone.fromUtc(props.stay.timestamp)
  const endTime = startTime.clone().add(props.stay.stayDuration, 'seconds')
  
  return timezone.format(endTime.toISOString(), 'YYYY-MM-DD HH:mm:ss')
}

const formatDuration = (seconds) => {
  return formatDurationSmart(seconds || 0)
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
  } catch (error) {
    console.error('Failed to copy to clipboard:', error)
    toast.add({
      severity: 'error', 
      summary: 'Copy failed',
      detail: 'Unable to copy coordinates',
      life: 3000
    })
  }
}

const handleMapReady = (map) => {
  mapInstance.value = map
  updateMapMarker()
}

const updateMapMarker = async () => {
  if (!mapInstance.value || !props.stay?.latitude || !props.stay?.longitude) return

  await nextTick()

  // Remove existing marker
  if (marker.value) {
    mapInstance.value.removeLayer(marker.value)
  }

  // Add new marker
  const L = window.L
  if (L) {
    marker.value = L.marker([props.stay.latitude, props.stay.longitude])
      .addTo(mapInstance.value)
      .bindPopup(`
        <div class="marker-popup">
          <strong>${props.stay?.locationName || 'Unknown Location'}</strong><br/>
          ${props.stay?.address ? `<span>${props.stay.address}</span><br/>` : ''}
          <small>Duration: ${formatDuration(props.stay?.stayDuration)}</small>
        </div>
      `)
      .openPopup()
  }
}

// Watch for visibility changes
watch(() => props.visible, (newValue) => {
  internalVisible.value = newValue
  if (newValue) {
    nextTick(() => {
      updateMapMarker()
    })
  }
})

watch(() => props.stay, () => {
  if (internalVisible.value) {
    nextTick(() => {
      updateMapMarker()
    })
  }
}, { deep: true })

watch(internalVisible, (newValue) => {
  if (!newValue) {
    emit('close')
  }
})
</script>

<style scoped>
.stay-details-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xl);
}

.map-section {
  width: 100%;
}

.section-title {
  margin: 0 0 var(--gp-spacing-md) 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  border-bottom: 2px solid var(--gp-primary);
  padding-bottom: var(--gp-spacing-xs);
}

.details-section {
  width: 100%;
}

.location-header {
  text-align: center;
  margin-bottom: var(--gp-spacing-lg);
  padding: var(--gp-spacing-lg);
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
}

.location-name {
  margin: 0 0 var(--gp-spacing-xs) 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gp-primary);
}

.location-address {
  margin: 0;
  font-size: 1rem;
  color: var(--gp-text-secondary);
  line-height: 1.4;
}

.details-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: var(--gp-spacing-lg);
}

.detail-group {
  background: var(--gp-surface-light);
  padding: var(--gp-spacing-md);
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
}

.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--gp-spacing-xs) 0;
  border-bottom: 1px solid var(--gp-border-light);
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
  flex-shrink: 0;
  margin-right: var(--gp-spacing-md);
}

.detail-value {
  color: var(--gp-text-primary);
  font-weight: 500;
  text-align: right;
  word-break: break-word;
}

.detail-value.coordinate {
  font-family: monospace;
  font-size: 0.9rem;
}

.detail-value.copyable {
  cursor: pointer;
  color: var(--gp-primary);
  transition: color 0.2s ease;
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.detail-value.copyable:hover {
  color: var(--gp-primary-hover);
}

.copy-icon {
  font-size: 0.8rem;
  opacity: 0.7;
}

.duration-badge {
  background: var(--gp-primary-light);
  color: var(--gp-primary);
  padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
  border-radius: var(--gp-radius-small);
  font-size: 0.95rem;
  font-weight: 600;
}

.place-type-tag {
  font-size: 0.8rem;
}

/* Dark Mode */
.p-dark .location-header {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .detail-group {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .detail-item {
  border-bottom-color: var(--gp-border-dark);
}

.p-dark .duration-badge {
  background: rgba(30, 64, 175, 0.2);
  color: var(--gp-primary);
}

/* Responsive */
@media (max-width: 768px) {
  .details-grid {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-md);
  }
  
  .detail-item {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--gp-spacing-xs);
  }
  
  .detail-value {
    text-align: left;
  }
  
  .detail-value.copyable {
    justify-content: flex-start;
  }
}

/* Map popup styling */
:deep(.marker-popup) {
  font-size: 0.9rem;
  line-height: 1.4;
}

:deep(.marker-popup strong) {
  color: var(--gp-primary);
}
</style>