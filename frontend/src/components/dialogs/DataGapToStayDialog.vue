<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="t('tripDialogs.dataGapToStay.header')"
    :modal="true"
    class="gp-dialog-md"
    @hide="$emit('close')"
  >
    <div v-if="dataGap" class="conversion-content">
      <div class="gap-meta">
        <Tag :value="t('tripDialogs.dataGapToStay.gapTag')" severity="warn" />
        <span class="gap-time">{{ formatGapRange(dataGap) }}</span>
      </div>

      <div class="strategy-controls">
        <label for="location-strategy" class="control-label">{{ t('tripDialogs.dataGapToStay.locationSourceLabel') }}</label>
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
          {{ t('tripDialogs.dataGapToStay.resolvingLocation') }}
        </Message>
        <Message v-else-if="preview" severity="success" :closable="false">
          <strong>{{ t('tripDialogs.dataGapToStay.defaultLocationLabel') }}</strong> {{ preview.locationName || t('tripDialogs.dataGapToStay.unknownLocation') }}
        </Message>
      </div>

      <div v-else class="selected-location-block">
        <div class="selected-mode-controls">
          <label for="selected-source" class="control-label">{{ t('tripDialogs.dataGapToStay.selectedLocationTypeLabel') }}</label>
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
            <label for="place-autocomplete" class="control-label">{{ t('tripDialogs.dataGapToStay.searchPlaceLabel') }}</label>
            <AutoComplete
              id="place-autocomplete"
              v-model="selectedPlace"
              :suggestions="placeSuggestions"
              optionLabel="displayName"
              :placeholder="t('tripDialogs.dataGapToStay.searchPlaceholder')"
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
              :placeholder="t('tripDialogs.dataGapToStay.latitudePlaceholder')"
              :disabled="saving"
            />
            <InputText
              v-model.trim="customLongitude"
              :placeholder="t('tripDialogs.dataGapToStay.longitudePlaceholder')"
              :disabled="saving"
            />
          </div>
          <InputText
            v-model.trim="customLocationName"
            :placeholder="t('tripDialogs.dataGapToStay.customNamePlaceholder')"
            :disabled="saving"
          />
        </template>
      </div>
    </div>

    <template #footer>
      <Button :label="t('common.cancel')" severity="secondary" outlined :disabled="saving" @click="internalVisible = false" />
      <Button
        :label="t('tripDialogs.dataGapToStay.convert')"
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
import { useI18n } from 'vue-i18n'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import InputText from 'primevue/inputtext'
import AutoComplete from 'primevue/autocomplete'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import { useTimelineStore } from '@/stores/timeline'
import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'
import { useTimezone } from '@/composables/useTimezone'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

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

const { t } = useI18n()
const toast = useToast()
const timelineStore = useTimelineStore()
const locationAnalyticsStore = useLocationAnalyticsStore()
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

const strategyOptions = computed(() => ([
  { label: t('tripDialogs.dataGapToStay.strategyOptions.latestPoint'), value: 'LATEST_POINT' },
  { label: t('tripDialogs.dataGapToStay.strategyOptions.selectedLocation'), value: 'SELECTED_LOCATION' }
]))

const selectedSourceOptions = computed(() => ([
  { label: t('tripDialogs.dataGapToStay.selectedSourceOptions.place'), value: 'place' },
  { label: t('tripDialogs.dataGapToStay.selectedSourceOptions.custom'), value: 'custom' }
]))

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
    previewError.value = formatApiErrorDetail(error, t('tripDialogs.dataGapToStay.errors.previewFailed'))
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
    const results = await locationAnalyticsStore.searchLocations(query, 'place')
    placeSuggestions.value = results
      .filter((result) => result?.category === 'favorite' || result?.category === 'geocoding')
      .map((result) => ({
        id: result.id,
        category: result.category,
        displayName: result.displayName || result.name
      }))
  } catch (error) {
    searchError.value = formatApiErrorDetail(error, t('tripDialogs.dataGapToStay.errors.searchFailed'))
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
      summary: t('tripDialogs.dataGapToStay.toasts.convertedSummary'),
      detail: t('tripDialogs.dataGapToStay.toasts.convertedDetail'),
      life: 3000
    })
    emit('converted', result)
    internalVisible.value = false
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('tripDialogs.dataGapToStay.toasts.failedSummary'),
      detail: formatApiErrorDetail(error, t('tripDialogs.dataGapToStay.toasts.failedFallback')),
      life: 5000
    })
  } finally {
    saving.value = false
  }
}

const formatGapRange = (gap) => {
  if (!gap?.startTime || !gap?.endTime) {
    return t('tripDialogs.dataGapToStay.unknownTimeRange')
  }
  return `${timezone.formatDateTimeDisplayAt(gap.startTime, gap.startLocationTimezone)} - ${timezone.formatDateTimeDisplayAt(gap.endTime, gap.endLocationTimezone)}`
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
