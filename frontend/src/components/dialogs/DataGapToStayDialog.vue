<template>
  <Dialog
    v-model:visible="internalVisible"
    header="Convert Data Gap to Stay"
    :modal="true"
    class="gp-dialog-md"
    @hide="$emit('close')"
  >
    <div v-if="dataGap" class="conversion-content">
      <div class="gap-meta">
        <Tag value="Data Gap" severity="warn" />
        <span class="gap-time">{{ formatGapRange(dataGap) }}</span>
      </div>

      <div class="strategy-controls">
        <label for="location-strategy" class="control-label">Stay location source</label>
        <Select
          id="location-strategy"
          v-model="strategy"
          :options="strategyOptions"
          optionLabel="label"
          optionValue="value"
          class="strategy-select"
          :disabled="saving"
        />
      </div>

      <div v-if="strategy === 'LATEST_POINT'" class="latest-point-block">
        <Message v-if="previewError" severity="error" :closable="false">
          {{ previewError }}
        </Message>
        <Message v-else-if="previewLoading" severity="info" :closable="false">
          Resolving latest known point location...
        </Message>
        <Message v-else-if="preview" severity="success" :closable="false">
          <strong>Default location:</strong> {{ preview.locationName || 'Unknown location' }}
        </Message>
      </div>

      <div v-else class="selected-location-block">
        <div class="selected-mode-controls">
          <label for="selected-source" class="control-label">Selected location type</label>
          <Select
            id="selected-source"
            v-model="selectedSourceType"
            :options="selectedSourceOptions"
            optionLabel="label"
            optionValue="value"
            class="strategy-select"
            :disabled="saving"
          />
        </div>

        <template v-if="selectedSourceType === 'place'">
          <div class="selected-mode-controls">
            <label for="place-autocomplete" class="control-label">Search Favorite or Geocoding place</label>
            <AutoComplete
              id="place-autocomplete"
              v-model="selectedPlace"
              :suggestions="placeSuggestions"
              optionLabel="displayName"
              placeholder="Type at least 2 characters..."
              forceSelection
              :minLength="2"
              :delay="250"
              :loading="searchLoading"
              :disabled="saving"
              class="strategy-select"
              @complete="searchPlaces"
            >
              <template #option="{ option }">
                <div class="place-suggestion">
                  <span class="place-name">{{ option.displayName }}</span>
                  <small class="place-category">{{ option.category }}</small>
                </div>
              </template>
            </AutoComplete>
          </div>

          <Message v-if="searchError" severity="error" :closable="false">{{ searchError }}</Message>
        </template>

        <template v-else>
          <div class="custom-coordinates-row">
            <InputText
              v-model.trim="customLatitude"
              placeholder="Latitude"
              :disabled="saving"
            />
            <InputText
              v-model.trim="customLongitude"
              placeholder="Longitude"
              :disabled="saving"
            />
          </div>
          <InputText
            v-model.trim="customLocationName"
            placeholder="Optional custom location name"
            :disabled="saving"
          />
        </template>
      </div>
    </div>

    <template #footer>
      <Button label="Cancel" severity="secondary" outlined :disabled="saving" @click="internalVisible = false" />
      <Button
        label="Convert to Stay"
        icon="pi pi-check"
        :loading="saving"
        :disabled="!canConvert"
        @click="convert"
      />
    </template>
  </Dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import InputText from 'primevue/inputtext'
import AutoComplete from 'primevue/autocomplete'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import apiService from '@/utils/apiService'
import { useTimelineStore } from '@/stores/timeline'
import { useTimezone } from '@/composables/useTimezone'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  dataGap: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close', 'converted'])

const toast = useToast()
const timelineStore = useTimelineStore()
const timezone = useTimezone()

const strategy = ref('LATEST_POINT')
const selectedSourceType = ref('place')
const selectedPlace = ref(null)
const placeSuggestions = ref([])
const customLatitude = ref('')
const customLongitude = ref('')
const customLocationName = ref('')

const preview = ref(null)
const previewLoading = ref(false)
const previewError = ref('')
const searchLoading = ref(false)
const searchError = ref('')
const saving = ref(false)

const strategyOptions = [
  { label: 'Latest known point (default)', value: 'LATEST_POINT' },
  { label: 'Selected location', value: 'SELECTED_LOCATION' }
]

const selectedSourceOptions = [
  { label: 'Favorite or geocoding place', value: 'place' },
  { label: 'Custom coordinates', value: 'custom' }
]

const internalVisible = computed({
  get: () => props.visible,
  set: (value) => {
    if (!value) emit('close')
  }
})

const canConvert = computed(() => {
  if (!props.dataGap?.id || saving.value) {
    return false
  }

  if (strategy.value === 'LATEST_POINT') {
    return !previewLoading.value && !previewError.value
  }

  if (selectedSourceType.value === 'place') {
    return Boolean(
      selectedPlace.value &&
      typeof selectedPlace.value === 'object' &&
      selectedPlace.value.id &&
      (selectedPlace.value.category === 'favorite' || selectedPlace.value.category === 'geocoding')
    )
  }

  const lat = Number(customLatitude.value)
  const lon = Number(customLongitude.value)
  return Number.isFinite(lat) && Number.isFinite(lon)
})

watch(
  () => [props.visible, props.dataGap?.id],
  async ([visible]) => {
    if (!visible || !props.dataGap?.id) return

    resetForm()
    await loadPreview()
  },
  { immediate: true }
)

const resetForm = () => {
  strategy.value = 'LATEST_POINT'
  selectedSourceType.value = 'place'
  selectedPlace.value = null
  placeSuggestions.value = []
  customLatitude.value = ''
  customLongitude.value = ''
  customLocationName.value = ''
  searchError.value = ''
}

const loadPreview = async () => {
  preview.value = null
  previewError.value = ''
  previewLoading.value = true
  try {
    preview.value = await timelineStore.getDataGapStayConversionPreview(props.dataGap.id)
  } catch (error) {
    previewError.value = error.response?.data?.message || error.message || 'Failed to resolve latest point preview'
  } finally {
    previewLoading.value = false
  }
}

const searchPlaces = async (event) => {
  const query = event?.query?.trim() || ''
  searchError.value = ''
  placeSuggestions.value = []

  if (query.length < 2) {
    return
  }

  searchLoading.value = true
  try {
    const response = await apiService.get('/location-analytics/search', {
      q: query,
      type: 'place'
    })

    const results = Array.isArray(response?.data) ? response.data : []
    placeSuggestions.value = results
      .filter((result) => result?.category === 'favorite' || result?.category === 'geocoding')
      .map((result) => ({
        id: result.id,
        category: result.category,
        displayName: result.displayName || result.name
      }))
  } catch (error) {
    searchError.value = error.response?.data?.message || error.message || 'Failed to search places'
  } finally {
    searchLoading.value = false
  }
}

const convert = async () => {
  if (!props.dataGap?.id) return

  const payload = {
    locationStrategy: strategy.value
  }

  if (strategy.value === 'SELECTED_LOCATION') {
    if (selectedSourceType.value === 'place') {
      if (selectedPlace.value?.category === 'favorite') {
        payload.favoriteId = selectedPlace.value.id
      } else if (selectedPlace.value?.category === 'geocoding') {
        payload.geocodingId = selectedPlace.value.id
      }
    } else {
      payload.latitude = Number(customLatitude.value)
      payload.longitude = Number(customLongitude.value)
      if (customLocationName.value) {
        payload.locationName = customLocationName.value
      }
    }
  }

  saving.value = true
  try {
    const result = await timelineStore.convertDataGapToStay(props.dataGap.id, payload)
    toast.add({
      severity: 'success',
      summary: 'Converted',
      detail: 'Data gap was converted to stay.',
      life: 3000
    })
    emit('converted', result)
    internalVisible.value = false
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Conversion Failed',
      detail: error.response?.data?.message || error.message || 'Could not convert data gap',
      life: 5000
    })
  } finally {
    saving.value = false
  }
}

const formatGapRange = (gap) => {
  if (!gap?.startTime || !gap?.endTime) {
    return 'Unknown time range'
  }
  return `${timezone.formatDateDisplay(gap.startTime)} ${timezone.formatTime(gap.startTime)} - ${timezone.formatDateDisplay(gap.endTime)} ${timezone.formatTime(gap.endTime)}`
}
</script>

<style scoped>
.conversion-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.gap-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.gap-time {
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
}

.strategy-controls,
.selected-mode-controls {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.control-label {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.strategy-select {
  width: 100%;
}

.custom-coordinates-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--gp-spacing-sm);
}

.selected-location-block {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.place-suggestion {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.place-name {
  color: var(--gp-text-primary);
}

.place-category {
  text-transform: capitalize;
  color: var(--gp-text-secondary);
}

@media (max-width: 768px) {
  .custom-coordinates-row {
    grid-template-columns: 1fr;
  }
}
</style>
