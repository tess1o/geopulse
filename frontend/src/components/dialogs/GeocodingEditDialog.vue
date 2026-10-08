<template>
  <Dialog
    :visible="visible"
    :header="t('favoritesGeocodingDialogs.geocodingEdit.header')"
    :modal="true"
    :closable="true"
    @update:visible="$emit('close')"
    class="gp-dialog-md"
  >
    <div class="dialog-content">
      <!-- Map Section -->
      <div class="map-section">
        <label class="field-label">{{ t('favoritesGeocodingDialogs.geocodingEdit.mapLabel') }}</label>
        <MapContainer
          :map-id="`geocoding-edit-map-${mapId}`"
          :center="mapCenter"
          :zoom="16"
          :show-controls="false"
          :enable-fullscreen="false"
          height="250px"
          width="100%"
          @map-ready="handleMapReady"
        />
        <small class="field-hint">{{ t('favoritesGeocodingDialogs.geocodingEdit.mapHint') }}</small>
      </div>

      <!-- Display Name -->
      <div class="form-field">
        <label for="displayName" class="field-label">
          {{ t('favoritesGeocodingDialogs.geocodingEdit.displayNameLabel') }} <span class="required">*</span>
        </label>
        <InputText
          id="displayName"
          v-model="formData.displayName"
          :placeholder="t('favoritesGeocodingDialogs.geocodingEdit.displayNamePlaceholder')"
          class="field-input"
          :invalid="!formData.displayName || formData.displayName.trim() === ''"
        />
        <small class="field-hint">{{ t('favoritesGeocodingDialogs.geocodingEdit.displayNameHint') }}</small>
      </div>

      <!-- City -->
      <div class="form-field">
        <label for="city" class="field-label">{{ t('favoritesGeocodingDialogs.geocodingEdit.cityLabel') }}</label>
        <InputText
          id="city"
          v-model="formData.city"
          :placeholder="t('favoritesGeocodingDialogs.geocodingEdit.cityPlaceholder')"
          class="field-input"
        />
        <small class="field-hint">{{ t('favoritesGeocodingDialogs.geocodingEdit.cityHint') }}</small>
      </div>

      <!-- Country -->
      <div class="form-field">
        <label for="country" class="field-label">{{ t('favoritesGeocodingDialogs.geocodingEdit.countryLabel') }}</label>
        <InputText
          id="country"
          v-model="formData.country"
          :placeholder="t('favoritesGeocodingDialogs.geocodingEdit.countryPlaceholder')"
          class="field-input"
        />
        <small class="field-hint">{{ t('favoritesGeocodingDialogs.geocodingEdit.countryHint') }}</small>
      </div>

      <!-- Read-only Info -->
      <div class="info-section">
        <div class="info-row">
          <span class="info-label">{{ t('favoritesGeocodingDialogs.geocodingEdit.providerLabel') }}</span>
          <Tag :value="geocodingResult?.providerName" severity="info" />
        </div>
        <div class="info-row">
          <span class="info-label">{{ t('favoritesGeocodingDialogs.geocodingEdit.coordinatesLabel') }}</span>
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
          <strong>{{ t('favoritesGeocodingDialogs.geocodingEdit.noteLabel') }}</strong> {{ t('favoritesGeocodingDialogs.geocodingEdit.syncNoteMessage') }}
        </div>
      </Message>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          @click="$emit('close')"
          :disabled="saving"
        />
        <Button
          :label="t('favoritesGeocodingDialogs.geocodingEdit.saveChanges')"
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
import { ref, computed, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import { MapContainer } from '@/components/maps'
import L from 'leaflet'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const { t } = useI18n()

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
const mapAdapter = ref(null)

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

const resolveCoordinates = () => {
  const lat = Number(props.geocodingResult?.latitude)
  const lng = Number(props.geocodingResult?.longitude)
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return null
  }

  return { lat, lng }
}

const createRasterGeocodingMapAdapter = (map) => {
  let marker = null

  const clear = () => {
    if (marker) {
      map.removeLayer(marker)
      marker = null
    }
  }

  const render = (coords) => {
    clear()
    if (!coords) {
      return
    }

    const customIcon = L.divIcon({
      className: 'custom-marker-icon',
      html: '<i class="pi pi-map-marker" style="font-size: 2rem; color: #3b82f6;"></i>',
      iconSize: [32, 32],
      iconAnchor: [16, 32]
    })

    marker = L.marker([coords.lat, coords.lng], { icon: customIcon })
      .addTo(map)
  }

  return {
    render,
    cleanup: clear
  }
}

const createVectorMarkerElement = () => {
  const root = document.createElement('div')
  root.className = 'custom-marker-icon custom-marker-icon--vector'
  root.innerHTML = '<i class="pi pi-map-marker"></i>'
  return root
}

const createVectorGeocodingMapAdapter = (map) => {
  const { maplibregl } = getVectorEngine()
  let marker = null

  const clear = () => {
    if (marker) {
      marker.remove()
      marker = null
    }
  }

  const render = (coords) => {
    clear()
    if (!coords) {
      return
    }

    marker = new maplibregl.Marker({
      element: createVectorMarkerElement(),
      anchor: 'bottom'
    })
      .setLngLat([coords.lng, coords.lat])
      .addTo(map)
  }

  return {
    render,
    cleanup: clear
  }
}

const createGeocodingMapAdapter = (map) => {
  const mode = resolveMapEngineModeFromInstance(map, MAP_RENDER_MODES.RASTER)
  if (mode === MAP_RENDER_MODES.VECTOR) {
    return createVectorGeocodingMapAdapter(map)
  }

  return createRasterGeocodingMapAdapter(map)
}

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
  mapAdapter.value?.cleanup?.()
  mapAdapter.value = createGeocodingMapAdapter(mapInstance.value)
  updateMapMarker()
}

const updateMapMarker = () => {
  if (!mapInstance.value || !mapAdapter.value) {
    return
  }

  const coordinates = resolveCoordinates()
  mapAdapter.value.render(coordinates)

  if (coordinates) {
    mapInstance.value.setView([coordinates.lat, coordinates.lng], 16)
  }
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

onUnmounted(() => {
  mapAdapter.value?.cleanup?.()
  mapAdapter.value = null
})
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
  background-color: var(--gp-surface-ground);
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
  font-family: var(--gp-font-mono);
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

<style>
.custom-marker-icon--vector i {
  font-size: 2rem;
  color: #3b82f6;
}
</style>
