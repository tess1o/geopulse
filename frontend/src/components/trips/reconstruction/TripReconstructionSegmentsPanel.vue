<template>
  <div class="segments-panel">
    <Message severity="info" :closable="false">
      {{ reconstructionHelpMessage }}
    </Message>

    <div class="segments-toolbar">
      <Button
        icon="pi pi-home"
        :label="t('trips.reconstruction.segmentsPanel.addStay')"
        outlined
        @click="emit('add-segment', 'STAY')"
      />
      <Button
        icon="pi pi-directions-alt"
        :label="t('trips.reconstruction.segmentsPanel.addTrip')"
        outlined
        @click="emit('add-segment', 'TRIP')"
      />
    </div>

    <div v-if="segments.length === 0" class="segments-empty">
      {{ t('trips.reconstruction.segmentsPanel.noSegments') }}
    </div>

    <div v-else class="segments-list">
      <div
        v-for="(segment, index) in segments"
        :key="segment.id"
        class="segment-card"
        :class="{ 'segment-card--active': activeSegmentId === segment.id }"
        @click="emit('set-active-segment', segment.id)"
      >
        <div class="segment-header">
          <div class="segment-title-row">
            <Tag
              :value="segment.segmentType"
              :severity="segment.segmentType === 'TRIP' ? 'info' : 'warn'"
            />
            <strong>{{ t('trips.reconstruction.segmentsPanel.segmentTitle', { number: index + 1 }) }}</strong>
          </div>
          <div class="segment-actions">
            <Button
              icon="pi pi-angle-up"
              text
              rounded
              size="small"
              :disabled="index === 0"
              @click.stop="emit('move-segment', index, -1)"
            />
            <Button
              icon="pi pi-angle-down"
              text
              rounded
              size="small"
              :disabled="index === segments.length - 1"
              @click.stop="emit('move-segment', index, 1)"
            />
            <Button
              icon="pi pi-trash"
              text
              rounded
              severity="danger"
              size="small"
              @click.stop="emit('remove-segment', index)"
            />
          </div>
        </div>

        <div class="segment-grid">
          <div class="field-row">
            <label>{{ t('trips.reconstruction.segmentsPanel.typeLabel') }}</label>
            <Select
              :model-value="segment.segmentType"
              :options="localizedSegmentTypeOptions"
              optionLabel="label"
              optionValue="value"
              class="w-full"
              @update:model-value="emit('update-segment-type', index, $event)"
            />
          </div>

          <div class="field-row">
            <label>{{ t('trips.reconstruction.segmentsPanel.startTimeLabel') }}</label>
            <DatePicker
              :model-value="segment.startTime"
              showTime
              hourFormat="24"
              :manualInput="false"
              :dateFormat="timezone.getPrimeVueDatePickerFormat()"
              class="w-full"
              @update:model-value="emit('update-segment-field', index, 'startTime', $event)"
            />
          </div>

          <div class="field-row">
            <label>{{ t('trips.reconstruction.segmentsPanel.endTimeLabel') }}</label>
            <DatePicker
              :model-value="segment.endTime"
              showTime
              hourFormat="24"
              :manualInput="false"
              :dateFormat="timezone.getPrimeVueDatePickerFormat()"
              class="w-full"
              @update:model-value="emit('update-segment-field', index, 'endTime', $event)"
            />
          </div>

          <template v-if="segment.segmentType === 'STAY'">
            <div class="field-row field-row--wide">
              <label>{{ t('trips.reconstruction.segmentsPanel.locationNameLabel') }}</label>
              <div class="resolved-location">
                <div class="resolved-location-main">
                  <InputText
                    :model-value="segment.locationName"
                    class="w-full"
                    :placeholder="t('trips.reconstruction.segmentsPanel.locationNamePlaceholder')"
                    @update:model-value="emit('update-segment-field', index, 'locationName', $event)"
                  />
                  <Tag
                    v-if="segment.locationSourceType"
                    :value="locationSourceLabel(segment)"
                    severity="secondary"
                  />
                </div>
                <small class="field-hint">{{ t('trips.reconstruction.segmentsPanel.locationNameHint') }}</small>
              </div>
            </div>

            <div class="field-row">
              <label>{{ t('trips.reconstruction.segmentsPanel.latitudeLabel') }}</label>
              <InputNumber
                :model-value="segment.latitude"
                class="w-full"
                :min="-90"
                :max="90"
                :minFractionDigits="6"
                :maxFractionDigits="6"
                @update:model-value="emit('update-segment-field', index, 'latitude', $event)"
              />
            </div>

            <div class="field-row">
              <label>{{ t('trips.reconstruction.segmentsPanel.longitudeLabel') }}</label>
              <InputNumber
                :model-value="segment.longitude"
                class="w-full"
                :min="-180"
                :max="180"
                :minFractionDigits="6"
                :maxFractionDigits="6"
                @update:model-value="emit('update-segment-field', index, 'longitude', $event)"
              />
            </div>
          </template>

          <template v-else>
            <div class="field-row">
              <label>{{ t('trips.reconstruction.segmentsPanel.movementTypeLabel') }}</label>
              <Select
                :model-value="segment.movementType"
                :options="localizedMovementTypeOptions"
                optionLabel="label"
                optionValue="value"
                class="w-full"
                @update:model-value="emit('update-segment-field', index, 'movementType', $event)"
              />
            </div>

            <div class="field-row field-row--wide">
              <label>{{ t('trips.reconstruction.segmentsPanel.waypointsLabel', { count: segment.waypoints.length }) }}</label>
              <small class="field-hint">{{ t('trips.reconstruction.segmentsPanel.waypointsHint') }}</small>
              <div class="waypoints-list">
                <div
                  v-for="(waypoint, waypointIndex) in segment.waypoints"
                  :key="waypoint.id"
                  class="waypoint-row"
                >
                  <Tag
                    :value="waypointLabel(waypointIndex, segment.waypoints.length)"
                    :severity="waypointTagSeverity(waypointIndex, segment.waypoints.length)"
                  />
                  <span class="waypoint-coords">
                    {{ waypoint.latitude.toFixed(5) }}, {{ waypoint.longitude.toFixed(5) }}
                  </span>
                  <div class="waypoint-actions">
                    <Button
                      icon="pi pi-angle-up"
                      text
                      rounded
                      size="small"
                      :disabled="waypointIndex === 0"
                      @click.stop="emit('move-waypoint', index, waypointIndex, -1)"
                    />
                    <Button
                      icon="pi pi-angle-down"
                      text
                      rounded
                      size="small"
                      :disabled="waypointIndex === segment.waypoints.length - 1"
                      @click.stop="emit('move-waypoint', index, waypointIndex, 1)"
                    />
                    <Button
                      icon="pi pi-trash"
                      text
                      rounded
                      severity="danger"
                      size="small"
                      @click.stop="emit('remove-waypoint', index, waypointIndex)"
                    />
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Message from 'primevue/message'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'

const { t } = useI18n()

const props = defineProps({
  reconstructionHelpMessage: {
    type: String,
    required: true
  },
  segments: {
    type: Array,
    default: () => []
  },
  activeSegmentId: {
    type: Number,
    default: null
  },
  segmentTypeOptions: {
    type: Array,
    required: true
  },
  movementTypeOptions: {
    type: Array,
    required: true
  },
  timezone: {
    type: Object,
    required: true
  },
  locationSourceLabel: {
    type: Function,
    required: true
  },
  waypointLabel: {
    type: Function,
    required: true
  },
  waypointTagSeverity: {
    type: Function,
    required: true
  }
})

/**
 * The options arrive keyed (`labelKey`) from `movementTypeOptions`/`segmentTypeOptions`; PrimeVue's
 * `optionLabel` reads a field and cannot call `t()`, so labels are resolved here. Accepts a plain
 * `label` too, so any caller still passing a pre-resolved list keeps working.
 */
const localizedMovementTypeOptions = computed(() => (props.movementTypeOptions || []).map(option => ({
  label: option.labelKey ? t(option.labelKey) : option.label,
  value: option.value
})))

const localizedSegmentTypeOptions = computed(() => (props.segmentTypeOptions || []).map(option => ({
  label: option.labelKey ? t(option.labelKey) : option.label,
  value: option.value
})))

const emit = defineEmits([
  'set-active-segment',
  'add-segment',
  'move-segment',
  'remove-segment',
  'update-segment-type',
  'update-segment-field',
  'move-waypoint',
  'remove-waypoint'
])
</script>

<style scoped>
.segments-panel {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  min-height: 0;
}

.segments-toolbar {
  display: flex;
  gap: var(--gp-spacing-xs);
  flex-wrap: wrap;
}

.segments-empty {
  border: 1px dashed var(--gp-border);
  border-radius: var(--gp-radius-small);
  color: var(--gp-text-secondary);
  padding: var(--gp-spacing-md);
  text-align: center;
}

.segments-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  max-height: 510px;
  overflow-y: auto;
  padding-right: 0.15rem;
}

.segment-card {
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
  padding: var(--gp-spacing-sm);
  cursor: pointer;
  background: var(--gp-surface-muted);
}

.segment-card--active {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--gp-primary) 40%, transparent);
}

.segment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--gp-spacing-xs);
  margin-bottom: var(--gp-spacing-sm);
}

.segment-title-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.segment-actions {
  display: flex;
  align-items: center;
  gap: 0.05rem;
}

.segment-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--gp-spacing-sm);
}

.field-row {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.field-row label {
  font-size: 0.82rem;
  color: var(--gp-text-secondary);
}

.field-row--wide {
  grid-column: 1 / -1;
}

.field-hint {
  color: var(--gp-text-secondary);
}

.resolved-location {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-card);
  padding: 0.5rem;
}

.resolved-location-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-xs);
  flex-wrap: wrap;
}

.resolved-location-main :deep(.p-inputtext) {
  flex: 1;
  min-width: 0;
}

.waypoints-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  max-height: 170px;
  overflow-y: auto;
  padding-right: 0.1rem;
}

.waypoint-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--gp-spacing-xs);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-card);
  padding: 0.2rem 0.3rem;
}

.waypoint-coords {
  font-size: 0.8rem;
  color: var(--gp-text-primary);
}

.waypoint-actions {
  display: flex;
  align-items: center;
  gap: 0;
}

@media (max-width: 1080px) {
  .segments-list {
    max-height: 360px;
  }
}

@media (max-width: 720px) {
  .segment-grid {
    grid-template-columns: 1fr;
  }
}
</style>
