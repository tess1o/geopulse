<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="t(`timeline.explanationDialog.header.${kind}`)"
    :modal="true"
    class="gp-dialog-lg"
    @hide="$emit('close')"
  >
    <div v-if="loading" class="explanation-state">
      <ProgressSpinner />
      <p>{{ t('timeline.explanationDialog.loading') }}</p>
    </div>

    <div v-else-if="error" class="explanation-state">
      <Message severity="error" :closable="false">{{ error }}</Message>
    </div>

    <div v-else-if="details" class="explanation-content">
      <p class="explanation-period">{{ periodText }}</p>

      <!-- Unusual decisions and things to know first -->
      <div v-if="callouts.length" class="explanation-callouts">
        <Message
          v-for="callout in callouts"
          :key="callout.key"
          :severity="callout.severity"
          :closable="false"
          class="explanation-callout"
          :class="{ 'explanation-callout--linked': callout.stepKey }"
          @click="callout.stepKey && selectStep(callout.stepKey)"
        >
          {{ calloutText(callout) }}
        </Message>
      </div>

      <!-- Map -->
      <section class="explanation-section">
        <h3 class="explanation-section-title">{{ t('timeline.explanationDialog.sections.map') }}</h3>
        <div v-if="mapModel.points.length || mapModel.lines.length" class="explanation-map-shell">
          <MapContainer
            :map-id="`timeline-explanation-map-${mapId}`"
            :center="mapCenter"
            :zoom="16"
            :show-controls="false"
            height="340px"
            width="100%"
            @map-ready="handleMapReady"
          />
        </div>
        <p v-else class="explanation-muted">{{ t('timeline.explanationDialog.noPoints') }}</p>
        <ul class="explanation-legend">
          <li v-for="entry in legend" :key="entry.key">
            <span class="explanation-legend-swatch" :class="`explanation-legend-swatch--${entry.shape}`" :style="entry.style"></span>
            {{ t(`timeline.explanationDialog.legend.${entry.key}`) }}
          </li>
        </ul>
        <p class="explanation-muted">{{ t('timeline.explanationDialog.mapHint') }}</p>
        <p v-if="details.truncated" class="explanation-muted">
          {{ t('timeline.explanationDialog.truncated', { total: details.totalEventPoints, shown: shownEventPoints }) }}
        </p>
      </section>

      <!-- Decisions, start to end -->
      <section class="explanation-section">
        <h3 class="explanation-section-title">{{ t('timeline.explanationDialog.sections.steps') }}</h3>
        <ol class="explanation-steps">
          <li
            v-for="step in visibleSteps"
            :key="step.key"
            class="explanation-step"
            :class="{
              'explanation-step--notable': step.notable,
              'explanation-step--selected': selectedStepKey === step.key,
              'explanation-step--mappable': step.pointIndexes.length > 0
            }"
          >
            <button
              type="button"
              class="explanation-step-button"
              :disabled="step.pointIndexes.length === 0"
              :aria-pressed="selectedStepKey === step.key"
              @click="selectStep(step.key)"
            >
              <span class="explanation-step-marker" aria-hidden="true">
                <i :class="stepIcon(step)"></i>
              </span>
              <span class="explanation-step-text">{{ step.text }}</span>
            </button>
            <div v-if="step.settings.length" class="explanation-step-settings">
              <a
                v-for="setting in step.settings"
                :key="setting.key"
                href="#"
                class="explanation-setting-link"
                @click.prevent="openSetting(setting)"
              >
                <i class="pi pi-sliders-h" aria-hidden="true"></i>
                {{ t(setting.labelKey) }}<template v-if="setting.value !== null">: {{ formatValue(setting.key, setting.value) }}</template>
              </a>
            </div>
          </li>
        </ol>
      </section>

      <!-- Points the timeline left out for their accuracy -->
      <section class="explanation-section">
        <h3 class="explanation-section-title">{{ t('timeline.explanationDialog.sections.filtered') }}</h3>
        <p v-for="(line, index) in filteredLines" :key="index" class="explanation-text">{{ line }}</p>
        <a href="#" class="explanation-setting-link" @click.prevent="openSetting(accuracySetting)">
          <i class="pi pi-sliders-h" aria-hidden="true"></i>
          {{ t(accuracySetting.labelKey) }}<template v-if="accuracySetting.value !== null">: {{ formatValue(accuracySetting.key, accuracySetting.value) }}</template>
        </a>
      </section>

      <!-- Points the GPS sources dropped before storing them -->
      <section v-if="ingestionLines.length" class="explanation-section">
        <h3 class="explanation-section-title">{{ t('timeline.explanationDialog.sections.ingestion') }}</h3>
        <p class="explanation-text">{{ t('timeline.explanationDialog.ingestion.intro') }}</p>
        <ul class="explanation-list">
          <li v-for="(line, index) in ingestionLines" :key="index">{{ line }}</li>
        </ul>
        <a href="#" class="explanation-setting-link" @click.prevent="navigate({ path: '/app/location-sources' })">
          <i class="pi pi-wifi" aria-hidden="true"></i>
          {{ t('timeline.explanationDialog.ingestion.link') }}
        </a>
      </section>
    </div>

    <template #footer>
      <Button
        v-if="kind === 'trip'"
        :label="t('timeline.explanationDialog.whyClassification')"
        icon="pi pi-question-circle"
        text
        class="explanation-classification-button"
        @click="$emit('show-classification', item)"
      />
      <Button
        :label="t('timeline.explanationDialog.changeSettings')"
        icon="pi pi-sliders-h"
        text
        @click="navigate({ path: '/app/timeline/preferences' })"
      />
      <Button :label="t('timeline.explanationDialog.close')" outlined @click="internalVisible = false" />
    </template>
  </Dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import MapContainer from '@/components/maps/MapContainer.vue'
import { useTimezone } from '@/composables/useTimezone'
import { useTimelineStore } from '@/stores/timeline'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { formatDistance, formatDuration, formatDurationSmart, formatSpeed } from '@/utils/calculationsHelpers'
import {
  buildExplanationCallouts,
  buildExplanationSteps,
  describePointStatus,
  explanationStepText,
  formatExplanationValue,
  settingLink,
  summarizePoints
} from '@/utils/timelineExplanationStory'
import {
  boundsForIndexes,
  buildExplanationMapModel,
  EXPLANATION_MAP_COLORS
} from '@/maps/explanation/shared/explanationMapModel'
import { createExplanationMapAdapter } from '@/maps/explanation/runtime/createExplanationMapAdapter'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  // 'stay', 'trip' or 'dataGap'
  kind: {
    type: String,
    default: 'stay',
    validator: value => ['stay', 'trip', 'dataGap'].includes(value)
  },
  // The timeline item the dialog explains, as the timeline API returns it
  item: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close', 'show-classification'])

const { t, te } = useI18n()
const router = useRouter()
const timezone = useTimezone()
const timelineStore = useTimelineStore()

const loading = ref(false)
const error = ref(null)
const details = ref(null)
const selectedStepKey = ref(null)
const mapAdapter = ref(null)
const mapId = Math.random().toString(36).slice(2, 10)

const internalVisible = computed({
  get: () => props.visible,
  set: (value) => {
    if (!value) emit('close')
  }
})

const locationTimezone = computed(() => (
  props.item?.locationTimezone || props.item?.startLocationTimezone || null
))

const formatters = computed(() => ({
  t,
  te,
  formatDistance,
  formatDuration,
  formatSpeed,
  formatTime: value => timezone.formatTimeAt(value, locationTimezone.value)
}))

const formatValue = (name, value) => formatExplanationValue(name, value, formatters.value)

const steps = computed(() => buildExplanationSteps(details.value).map(step => ({
  ...step,
  text: explanationStepText(step, props.kind, formatters.value)
})))

const visibleSteps = computed(() => steps.value.filter(step => step.text))

const callouts = computed(() => buildExplanationCallouts(details.value, steps.value)
  .filter(callout => callout.type !== 'step' || steps.value.find(step => step.key === callout.key)?.text))

const mapModel = computed(() => buildExplanationMapModel(details.value))

const mapCenter = computed(() => {
  const bounds = mapModel.value.bounds
  if (!bounds) return [0, 0]
  return [(bounds[0][0] + bounds[1][0]) / 2, (bounds[0][1] + bounds[1][1]) / 2]
})

const shownEventPoints = computed(() => (details.value?.points || []).filter(point => point.part === 'EVENT').length)

const periodText = computed(() => {
  if (!details.value) return ''
  const start = details.value.startTime
  const end = details.value.endTime
  const seconds = Math.max(0, (new Date(end).getTime() - new Date(start).getTime()) / 1000)
  return t('timeline.explanationDialog.period', {
    start: timezone.formatDateTimeDisplayAt(start, locationTimezone.value),
    end: timezone.formatDateTimeDisplayAt(end, locationTimezone.value),
    duration: formatDurationSmart(seconds)
  })
})

const legend = computed(() => {
  const colors = EXPLANATION_MAP_COLORS
  const entries = [
    { key: 'used', shape: 'dot', style: { background: colors.used } },
    { key: 'filtered', shape: 'ring', style: { borderColor: colors.filtered } },
    { key: 'context', shape: 'dot', style: { background: colors.context } },
    { key: 'deciding', shape: 'ring', style: { borderColor: colors.deciding, borderWidth: '3px' } }
  ]
  if (mapModel.value.circle) {
    entries.push({ key: 'radius', shape: 'circle', style: { borderColor: colors.radius } })
  }
  if (mapModel.value.anchor) {
    entries.push({ key: 'anchor', shape: 'ring', style: { borderColor: colors.deciding } })
  }
  return entries
})

const accuracySetting = computed(() => settingLink('staypointMaxAccuracyThreshold', details.value?.settings))

const filteredLines = computed(() => {
  if (!details.value) return []
  const summary = summarizePoints(details.value)
  const setting = t(accuracySetting.value.labelKey)
  if (summary.thresholdMeters === null) {
    return [t('timeline.explanationDialog.filtered.off', { setting: t('timelinePreferences.page.settings.useVelocityAccuracy') })]
  }
  const lines = []
  if (summary.eventFiltered > 0) {
    lines.push(t('timeline.explanationDialog.filtered.some', {
      filtered: summary.eventFiltered,
      total: summary.eventTotal,
      thresholdMeters: formatDistance(summary.thresholdMeters),
      setting
    }))
    if (summary.worstAccuracyMeters) {
      lines.push(t('timeline.explanationDialog.filtered.worst', { accuracyMeters: formatDistance(summary.worstAccuracyMeters) }))
    }
  } else if (summary.eventTotal > 0) {
    lines.push(t('timeline.explanationDialog.filtered.none'))
  }
  if (summary.contextFiltered > 0) {
    lines.push(t('timeline.explanationDialog.filtered.context', { count: summary.contextFiltered }))
  }
  return lines
})

const ingestionLines = computed(() => (details.value?.ingestionFilters || []).map((filter) => {
  const rules = []
  if (filter.maxAccuracyMeters) rules.push(t('timeline.explanationDialog.ingestion.accuracy', { value: formatDistance(filter.maxAccuracyMeters) }))
  if (filter.maxSpeedKmh) rules.push(t('timeline.explanationDialog.ingestion.speed', { value: formatSpeed(filter.maxSpeedKmh) }))
  if (filter.duplicateThresholdMinutes) {
    rules.push(t('timeline.explanationDialog.ingestion.duplicates', {
      value: t('timeline.explanationDialog.minutes', { value: filter.duplicateThresholdMinutes })
    }))
  }
  const source = [filter.sourceType, filter.name].filter(Boolean).join(' · ')
  return t('timeline.explanationDialog.ingestion.source', { source, rules: rules.join(', ') })
}))

const calloutText = (callout) => {
  if (callout.type === 'step') {
    return steps.value.find(step => step.key === callout.key)?.text || ''
  }
  if (callout.type === 'gapFiltered') {
    return t('timeline.explanationDialog.callouts.gapFiltered', {
      count: callout.params.count,
      thresholdMeters: formatDistance(callout.params.thresholdMeters),
      setting: t(accuracySetting.value.labelKey)
    })
  }
  if (callout.type === 'settingChanged') {
    const name = callout.params.setting
    return t('timeline.explanationDialog.callouts.settingChanged', {
      setting: te(callout.setting.labelKey) ? t(callout.setting.labelKey) : name,
      builtWith: formatValue(name, callout.params.builtWith),
      current: formatValue(name, callout.params.current)
    })
  }
  return ''
}

const stepIcon = (step) => {
  if (step.notable) return 'pi pi-exclamation-circle'
  if (step.section === 'start') return 'pi pi-play'
  if (step.section === 'end') return 'pi pi-stop'
  return 'pi pi-check-circle'
}

const describePoint = (point) => {
  const lines = describePointStatus(point, details.value).map(({ key, params }) => t(key, {
    ...params,
    accuracyMeters: params.accuracyMeters !== undefined ? formatDistance(params.accuracyMeters) : undefined,
    thresholdMeters: params.thresholdMeters !== undefined && params.thresholdMeters !== null
      ? formatDistance(params.thresholdMeters) : undefined,
    distanceMeters: params.distanceMeters !== undefined ? formatDistance(params.distanceMeters) : undefined,
    radiusMeters: params.radiusMeters !== undefined ? formatDistance(params.radiusMeters) : undefined
  }))
  lines.push(Number.isFinite(point.accuracyMeters)
    ? t('timeline.explanationDialog.point.accuracy', { value: formatDistance(point.accuracyMeters) })
    : t('timeline.explanationDialog.point.noAccuracy'))
  if (Number.isFinite(point.speedKmh)) {
    lines.push(t('timeline.explanationDialog.point.speed', { value: formatSpeed(point.speedKmh) }))
  }
  return {
    title: timezone.formatTimeAt(point.timestamp, locationTimezone.value, { withSeconds: true }),
    lines
  }
}

const selectStep = (key) => {
  const step = steps.value.find(candidate => candidate.key === key)
  if (!step || step.pointIndexes.length === 0) return
  if (selectedStepKey.value === key) {
    selectedStepKey.value = null
    mapAdapter.value?.highlight(null)
    mapAdapter.value?.fitTo(mapModel.value.bounds)
    return
  }
  selectedStepKey.value = key
  mapAdapter.value?.highlight(step.pointIndexes)
  mapAdapter.value?.fitTo(boundsForIndexes(mapModel.value, step.pointIndexes))
}

const navigate = (route) => {
  emit('close')
  router?.push(route)
}

const openSetting = (setting) => navigate(setting.route)

const renderMap = () => {
  if (!mapAdapter.value || !details.value) return
  selectedStepKey.value = null
  mapAdapter.value.render(mapModel.value)
}

const handleMapReady = (map) => {
  mapAdapter.value?.destroy?.()
  mapAdapter.value = createExplanationMapAdapter(map, { describePoint })
  mapAdapter.value.initialize(map)
  renderMap()
}

const load = async () => {
  if (!props.item?.id) return
  loading.value = true
  error.value = null
  try {
    details.value = await timelineStore.fetchTimelineEventExplanation(props.kind, props.item.id)
  } catch (err) {
    error.value = formatApiErrorDetail(err, t('timeline.explanationDialog.loadFailed'))
  } finally {
    loading.value = false
  }
}

const reset = () => {
  mapAdapter.value?.destroy?.()
  mapAdapter.value = null
  details.value = null
  error.value = null
  selectedStepKey.value = null
}

watch(() => [props.visible, props.item?.id, props.kind], ([visible]) => {
  reset()
  if (visible) load()
}, { immediate: true })

onBeforeUnmount(() => {
  mapAdapter.value?.destroy?.()
})

defineExpose({ selectStep })
</script>

<style scoped>
.explanation-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--gp-spacing-xxl);
  gap: var(--gp-spacing-md);
  color: var(--gp-text-secondary);
}

.explanation-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-lg);
  max-height: 70vh;
  overflow-y: auto;
  padding: var(--gp-spacing-xs);
}

.explanation-period {
  margin: 0;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.explanation-callouts {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.explanation-callout {
  margin: 0;
}

.explanation-callout--linked {
  cursor: pointer;
}

.explanation-section {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.explanation-section-title {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--gp-primary);
  margin: 0;
  padding-bottom: var(--gp-spacing-xs);
  border-bottom: 2px solid var(--gp-primary);
}

.explanation-map-shell {
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
  border: 1px solid var(--gp-border-subtle);
}

.explanation-legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-sm) var(--gp-spacing-md);
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

.explanation-legend li {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.explanation-legend-swatch {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 999px;
  box-sizing: border-box;
}

.explanation-legend-swatch--ring {
  border: 2px solid;
  background: var(--gp-surface-card);
}

.explanation-legend-swatch--circle {
  width: 16px;
  height: 16px;
  border: 2px dashed;
}

.explanation-muted {
  margin: 0;
  font-size: 0.8rem;
  color: var(--gp-text-muted);
}

.explanation-text {
  margin: 0;
  font-size: 0.9rem;
  color: var(--gp-text-primary);
  line-height: 1.45;
}

.explanation-steps {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
  margin: 0;
  padding: 0;
  list-style: none;
}

.explanation-step {
  border: 1px solid var(--gp-border-subtle);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-sm);
  background: var(--gp-surface-card);
}

.explanation-step--notable {
  border-left: 4px solid var(--gp-warning);
}

.explanation-step--selected {
  border-color: var(--gp-primary);
  background: var(--gp-surface-ground);
}

.explanation-step-button {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
  width: 100%;
  padding: 0;
  border: none;
  background: none;
  color: var(--gp-text-primary);
  font: inherit;
  font-size: 0.9rem;
  line-height: 1.45;
  text-align: left;
  cursor: default;
}

.explanation-step--mappable .explanation-step-button {
  cursor: pointer;
}

.explanation-step-button:disabled {
  color: var(--gp-text-primary);
}

.explanation-step-marker {
  flex-shrink: 0;
  color: var(--gp-primary);
  padding-top: 2px;
}

.explanation-step--notable .explanation-step-marker {
  color: var(--gp-warning);
}

.explanation-step-settings {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-xs) var(--gp-spacing-md);
  margin-top: var(--gp-spacing-xs);
  padding-left: 1.6rem;
}

.explanation-setting-link {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.8rem;
  color: var(--gp-primary);
  text-decoration: none;
}

.explanation-setting-link:hover,
.explanation-setting-link:focus-visible {
  text-decoration: underline;
}

.explanation-setting-link i {
  font-size: 0.75rem;
}

.explanation-list {
  margin: 0;
  padding-left: 1.2rem;
  font-size: 0.9rem;
  color: var(--gp-text-primary);
}

.explanation-classification-button {
  margin-right: auto;
}

@media (max-width: 768px) {
  .explanation-content {
    max-height: 62vh;
  }

  .explanation-step-settings {
    padding-left: 0;
  }
}
</style>

<style>
/* Popups are mounted by the map engine outside this component. The container is the shared map popup card
   (gp-map-popup-container), so the --gp-map-popup-* tokens give the right colours in light and dark mode. */
.gp-explanation-point-popup {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0.7rem 2rem 0.7rem 0.85rem;
  font-family: var(--gp-font-family);
  font-size: 0.8rem;
  line-height: 1.35;
  color: var(--gp-map-popup-text-secondary);
}

.gp-explanation-point-popup__title {
  color: var(--gp-map-popup-text);
  font-weight: 700;
}
</style>
