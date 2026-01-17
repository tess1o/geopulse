<template>
  <Dialog
    :visible="visible"
    :header="'Edit Geocoding Result'"
    :modal="true"
    :closable="true"
    @update:visible="$emit('close')"
    class="gp-dialog-md"
  >
    <div class="dialog-content">
      <!-- Map Section -->
      <div class="map-section">
        <label class="field-label">Location Map</label>
        <MapContainer
          :map-id="`geocoding-edit-map-${mapId}`"
          :center="mapCenter"
          :zoom="16"
          :show-controls="false"
          height="250px"
          width="100%"
          @map-ready="handleMapReady"
        />
        <small class="field-hint">Read-only map showing the geocoding location</small>
      </div>

      <!-- Display Name -->
      <div class="form-field">
        <label for="displayName" class="field-label">
          Display Name <span class="required">*</span>
        </label>
        <InputText
          id="displayName"
          v-model="formData.displayName"
          placeholder="Enter location display name"
          class="field-input"
          :invalid="!formData.displayName || formData.displayName.trim() === ''"
        />
        <small class="field-hint">The main display name shown for this location</small>
      </div>

      <!-- City -->
      <div class="form-field">
        <label for="city" class="field-label">City</label>
        <InputText
          id="city"
          v-model="formData.city"
          placeholder="Enter city name"
          class="field-input"
        />
        <small class="field-hint">Optional city name</small>
      </div>

      <!-- Country -->
      <div class="form-field">
        <label for="country" class="field-label">Country</label>
        <InputText
          id="country"
          v-model="formData.country"
          placeholder="Enter country name"
          class="field-input"
        />
        <small class="field-hint">Optional country name</small>
      </div>

      <!-- Read-only Info -->
      <div class="info-section">
        <div class="info-row">
          <span class="info-label">Provider:</span>
          <Tag :value="geocodingResult?.providerName" severity="info" />
        </div>
        <div class="info-row">
          <span class="info-label">Coordinates:</span>
          <span class="info-value">
            {{ geocodingResult?.latitude?.toFixed(6) }}, {{ geocodingResult?.longitude?.toFixed(6) }}
          </span>
        </div>
      </div>

      <!-- Warning Message -->
      <Message severity="warn" :closable="false" class="sync-warning">
        <template #messageicon>
          <i class="pi pi-exclamation-triangle"></i>
        </template>
        <div class="warning-content">
          <strong>Note:</strong> Changes will be synchronized across all timeline stays using this geocoding result.
        </div>
      </Message>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <Button
          label="Cancel"
          severity="secondary"
          @click="$emit('close')"
          :disabled="saving"
        />
        <Button
          label="Save Changes"
          severity="primary"
          @click="handleSave"
          :loading="saving"
          :disabled="!isFormValid"
        />
      </div>
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import { MapContainer } from '@/components/maps'
import L from 'leaflet'

const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  },
  geocodingResult: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close', 'save'])

const saving = ref(false)
const mapId = ref(Math.random().toString(36).substring(2, 11))
const mapInstance = ref(null)
const currentMarker = ref(null)

const formData = ref({
  displayName: '',
  city: '',
  country: ''
})

const mapCenter = computed(() => {
  if (props.geocodingResult) {
    return [props.geocodingResult.latitude, props.geocodingResult.longitude]
  }
  return [0, 0]
})

const isFormValid = computed(() => {
  return formData.value.displayName && formData.value.displayName.trim() !== ''
})

// Watch for geocodingResult changes to populate form
watch(() => props.geocodingResult, (newValue) => {
  if (newValue) {
    formData.value = {
      displayName: newValue.displayName || '',
      city: newValue.city || '',
      country: newValue.country || ''
    }

    // Update map marker if map is ready
    if (mapInstance.value) {
      updateMapMarker()
    }
  }
}, { immediate: true })

const handleMapReady = (map) => {
  mapInstance.value = map
  updateMapMarker()
}

const updateMapMarker = () => {
  if (!mapInstance.value || !props.geocodingResult) return

  // Remove existing marker
  if (currentMarker.value) {
    currentMarker.value.remove()
  }

  const lat = props.geocodingResult.latitude
  const lng = props.geocodingResult.longitude

  // Create custom icon
  const customIcon = L.divIcon({
    className: 'custom-marker-icon',
    html: '<i class="pi pi-map-marker" style="font-size: 2rem; color: #3b82f6;"></i>',
    iconSize: [32, 32],
    iconAnchor: [16, 32]
  })

  // Add marker
  currentMarker.value = L.marker([lat, lng], { icon: customIcon })
    .addTo(mapInstance.value)

  // Center map on marker
  mapInstance.value.setView([lat, lng], 16)
}

const handleSave = async () => {
  if (!isFormValid.value) return

  saving.value = true
  try {
    emit('save', {
      displayName: formData.value.displayName.trim(),
      city: formData.value.city?.trim() || null,
      country: formData.value.country?.trim() || null
    })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.dialog-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-lg);
  padding: var(--gp-spacing-md) 0;
}

.map-section {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.field-label {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--gp-text-primary);
}

.required {
  color: var(--p-red-500);
}

.field-input {
  width: 100%;
}

.field-hint {
  font-size: 0.85rem;
  color: var(--gp-text-muted);
  font-style: italic;
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-md);
  background-color: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.info-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
}

.info-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  min-width: 100px;
}

.info-value {
  font-family: monospace;
  font-size: 0.9rem;
  color: var(--gp-text-primary);
}

.sync-warning {
  margin-top: var(--gp-spacing-sm);
}

.warning-content {
  font-size: 0.9rem;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--gp-spacing-md);
}

/* Dark Mode */
.p-dark .info-section {
  background-color: var(--gp-surface-darker);
}

/* Custom marker icon */
:deep(.custom-marker-icon) {
  background: transparent;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.custom-marker-icon i) {
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3));
}
</style>
