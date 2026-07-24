<template>
  <div v-if="summary" class="timeline-weather-summary">
    <button
      ref="summaryButton"
      type="button"
      class="weather-summary-button"
      :class="`weather-summary-button--${summary.severity}`"
      :title="summaryTitle"
      @click.stop="toggleDetails"
    >
      <span class="weather-summary-main">
        <i :class="summary.icon"></i>
        <strong>{{ temperatureText }}</strong>
      </span>
    </button>

    <Popover
      ref="detailsPopover"
      class="weather-details-popover"
      append-to="body"
      :base-z-index="1200"
      :breakpoints="popoverBreakpoints"
    >
      <div class="weather-details">
        <div class="weather-details-title">{{ summary.condition }}</div>
        <div class="weather-details-grid">
          <span>Temperature</span>
          <strong>{{ temperatureText }}</strong>
          <template v-if="temperatureRangeText">
            <span>Range</span>
            <strong>{{ temperatureRangeText }}</strong>
          </template>
          <template v-if="precipitationText">
            <span>Precipitation</span>
            <strong>{{ precipitationText }}</strong>
          </template>
          <span>Wind</span>
          <strong>{{ windText || 'n/a' }}</strong>
        </div>
      </div>
    </Popover>

    <Dialog
      v-model:visible="mobileDetailsVisible"
      header="Weather"
      modal
      append-to="body"
      class="weather-details-dialog"
      :base-z-index="2200"
      :draggable="false"
      :style="{ width: 'min(24rem, calc(100vw - 24px))' }"
      :content-style="{ maxHeight: '60dvh', overflowY: 'auto' }"
    >
      <div class="weather-details">
        <div class="weather-details-title">{{ summary.condition }}</div>
        <div class="weather-details-grid">
          <span>Temperature</span>
          <strong>{{ temperatureText }}</strong>
          <template v-if="temperatureRangeText">
            <span>Range</span>
            <strong>{{ temperatureRangeText }}</strong>
          </template>
          <template v-if="precipitationText">
            <span>Precipitation</span>
            <strong>{{ precipitationText }}</strong>
          </template>
          <span>Wind</span>
          <strong>{{ windText || 'n/a' }}</strong>
        </div>
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import Dialog from 'primevue/dialog'
import Popover from 'primevue/popover'
import { useAuthStore } from '@/stores/auth'
import {
  formatPrecipitation,
  formatTemperature,
  formatWindSpeed,
  summarizeWeatherSamples
} from '@/utils/weatherDisplay'

const props = defineProps({
  samples: {
    type: Array,
    default: () => []
  }
})

const authStore = useAuthStore()
const { measureUnit } = storeToRefs(authStore)
const detailsPopover = ref(null)
const mobileDetailsVisible = ref(false)
const isMobileViewport = ref(false)
const popoverBreakpoints = {
  '768px': 'calc(100vw - 24px)'
}

const summary = computed(() => summarizeWeatherSamples(props.samples))
const unit = computed(() => measureUnit.value || 'METRIC')

const temperatureText = computed(() => formatTemperature(summary.value?.avgTemperature, unit.value) || 'n/a')
const temperatureRangeText = computed(() => {
  if (!summary.value || summary.value.sampleCount <= 1) {
    return ''
  }
  const min = formatTemperature(summary.value.minTemperature, unit.value)
  const max = formatTemperature(summary.value.maxTemperature, unit.value)
  if (!min || !max || min === max) {
    return ''
  }
  return `${min}-${max}`
})
const precipitationText = computed(() => formatPrecipitation(summary.value?.precipitationTotal, unit.value))
const windText = computed(() => formatWindSpeed(summary.value?.maxWindSpeed, unit.value))
const summaryTitle = computed(() => [
  summary.value?.condition,
  temperatureText.value,
  temperatureRangeText.value ? `range ${temperatureRangeText.value}` : null,
  precipitationText.value ? `precipitation ${precipitationText.value}` : null,
  windText.value ? `wind ${windText.value}` : null
].filter(Boolean).join(' · '))

const updateMobileViewport = () => {
  isMobileViewport.value = typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(max-width: 768px)').matches

  if (isMobileViewport.value) {
    detailsPopover.value?.hide?.()
  } else {
    mobileDetailsVisible.value = false
  }
}

const toggleDetails = (event) => {
  if (isMobileViewport.value) {
    detailsPopover.value?.hide?.()
    mobileDetailsVisible.value = true
    return
  }

  detailsPopover.value?.toggle(event)
}

onMounted(() => {
  updateMobileViewport()
  window.addEventListener('resize', updateMobileViewport)
  window.visualViewport?.addEventListener?.('resize', updateMobileViewport)
})

onBeforeUnmount(() => {
  if (typeof window === 'undefined') return
  window.removeEventListener('resize', updateMobileViewport)
  window.visualViewport?.removeEventListener?.('resize', updateMobileViewport)
})
</script>

<style scoped>
.timeline-weather-summary {
  display: inline-flex;
}

.weather-summary-button {
  width: auto;
  max-width: 100%;
  min-height: 0;
  display: inline-flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid var(--gp-border-medium);
  border-radius: 999px;
  background: var(--gp-surface-white);
  color: var(--gp-text-primary);
  padding: 2px 8px;
  font-size: 0.75rem;
  font-weight: 700;
  line-height: 1.2;
  cursor: pointer;
  text-align: left;
}

.weather-summary-button:hover {
  background: var(--gp-surface-light);
}

.weather-summary-button:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 3px;
}

.weather-summary-main > i {
  color: #0f766e;
}

.weather-summary-button--rain .weather-summary-main > i,
.weather-summary-button--storm .weather-summary-main > i {
  color: #2563eb;
}

.weather-summary-button--snow .weather-summary-main > i {
  color: #0284c7;
}

.weather-summary-button--clear .weather-summary-main > i {
  color: #ca8a04;
}

.weather-summary-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.weather-details {
  min-width: 220px;
  padding: 2px;
  color: var(--gp-text-primary);
}

.weather-details-title {
  font-weight: 800;
  margin-bottom: 8px;
}

.weather-details-grid {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 6px 14px;
  font-size: 0.82rem;
}

.weather-details-grid span {
  color: var(--gp-text-secondary);
}

@media (max-width: 768px) {
  .weather-summary-button {
    font-size: 0.76rem;
  }
}

:global(.weather-details-popover.p-popover) {
  --weather-details-popover-surface: var(--gp-surface-white);
  --weather-details-popover-border: rgba(148, 163, 184, 0.24);

  background: var(--weather-details-popover-surface);
  color: var(--gp-text-primary);
  border: 1px solid var(--weather-details-popover-border);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.16);
}

:global(.weather-details-popover .p-popover-content) {
  background: var(--weather-details-popover-surface);
  color: var(--gp-text-primary);
}

:global(.weather-details-popover.p-popover::after) {
  border-bottom-color: var(--weather-details-popover-surface);
}

:global(.weather-details-popover.p-popover::before) {
  display: none;
}

:global(.weather-details-popover.p-popover.p-popover-flipped::after) {
  border-top-color: var(--weather-details-popover-surface);
}

:global(.p-dark .weather-details-popover.p-popover) {
  --weather-details-popover-border: rgba(148, 163, 184, 0.16);

  box-shadow: 0 18px 40px rgba(2, 6, 23, 0.45);
}

:global(.weather-details-dialog.p-dialog) {
  color: var(--gp-text-primary);
}

:global(.weather-details-dialog .p-dialog-header),
:global(.weather-details-dialog .p-dialog-content) {
  background: var(--gp-surface-white);
  color: var(--gp-text-primary);
}

@media (max-width: 768px) {
  :global(.weather-details-popover.p-popover) {
    position: fixed !important;
    top: 50% !important;
    left: 12px !important;
    right: 12px !important;
    bottom: auto !important;
    transform: translateY(-50%) !important;
    z-index: 2200 !important;
    width: auto !important;
    max-width: calc(100vw - 24px);
  }

  :global(.weather-details-popover .p-popover-content) {
    max-height: min(60dvh, 360px);
    overflow-y: auto;
  }

  :global(.weather-details-popover.p-popover::before),
  :global(.weather-details-popover.p-popover::after) {
    display: none !important;
  }

  .weather-details {
    min-width: 0;
    width: 100%;
  }
}
</style>
