<template>
  <div
    v-if="variant === 'speed' && speedColors"
    class="map-color-legend map-color-legend--speed"
    role="img"
    :aria-label="speedAriaLabel"
  >
    <span class="map-color-legend-title">{{ t('maps.legend.speed.title') }}</span>
    <span v-for="item in speedItems" :key="item.key" class="map-color-legend-item">
      <span
        class="map-color-legend-swatch"
        :class="{ 'map-color-legend-swatch--outlined': outline }"
        :style="{ backgroundColor: item.color, '--legend-outline': outlineColorFor(item.color) }"
      ></span>
      <span>{{ item.label }}</span>
    </span>
  </div>

  <div
    v-else-if="variant === 'heatmap' && gradient"
    class="map-color-legend map-color-legend--heatmap"
    role="img"
    :aria-label="t('maps.legend.heatmap.ariaLabel')"
  >
    <span>{{ t('maps.legend.heatmap.low') }}</span>
    <span class="map-color-legend-gradient" :style="{ background: heatmapGradientToCss(gradient) }"></span>
    <span>{{ t('maps.legend.heatmap.high') }}</span>
  </div>

  <div
    v-else-if="variant === 'days' && dayItems.length"
    class="map-color-legend map-color-legend--days"
    role="img"
    :aria-label="daysAriaLabel"
  >
    <template v-if="daysMode === DAY_COLOR_MODES.DISTINCT">
      <!-- With daysInteractive, each day toggles it on its own on the map. -->
      <component
        :is="daysInteractive ? 'button' : 'span'"
        v-for="item in dayItems"
        :key="item.dayKey"
        class="map-color-legend-item"
        :class="{
          'map-color-legend-day-button': daysInteractive,
          'map-color-legend-day--focused': focusedDay === item.dayKey,
          'map-color-legend-day--muted': focusedDay && focusedDay !== item.dayKey
        }"
        v-bind="daysInteractive ? {
          type: 'button',
          'aria-pressed': focusedDay === item.dayKey,
          title: focusedDay === item.dayKey ? t('timeline.container.showAllDaysOnMap') : t('timeline.container.showOnlyDayOnMap')
        } : {}"
        @click="daysInteractive && emit('day-click', item.dayKey)"
      >
        <span
          class="map-color-legend-swatch"
          :class="{ 'map-color-legend-swatch--outlined': outline }"
          :style="{ backgroundColor: item.color, '--legend-outline': outlineColorFor(item.color) }"
        ></span>
        <span>{{ item.label }}</span>
      </component>
    </template>
    <template v-else>
      <span>{{ dayItems[0].label }}</span>
      <span class="map-color-legend-gradient" :style="{ background: dayGradientCss }"></span>
      <span>{{ dayItems[dayItems.length - 1].label }}</span>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { getSpeedBandThresholds } from '@/maps/shared/speedBandThresholds'
import { useTimezone } from '@/composables/useTimezone'
import { getOutlineColor, heatmapGradientToCss } from '@/maps/shared/mapAppearance'
import { DAY_COLOR_MODES } from '@/maps/shared/pathDayColoring'

const props = defineProps({
  variant: {
    type: String,
    required: true,
    validator: (value) => ['speed', 'heatmap', 'days'].includes(value)
  },
  // { slow, medium, fast, unknown } for the speed variant.
  speedColors: {
    type: Object,
    default: null
  },
  // { stop: color } for the heatmap variant.
  gradient: {
    type: Object,
    default: null
  },
  // [{ dayKey: 'YYYY-MM-DD', color }] in range order, for the days variant.
  days: {
    type: Array,
    default: () => []
  },
  // DAY_COLOR_MODES: one swatch per day, or a gradient from the first to the last day.
  daysMode: {
    type: String,
    default: DAY_COLOR_MODES.DISTINCT
  },
  // Days become buttons that emit 'day-click' (distinct mode only).
  daysInteractive: {
    type: Boolean,
    default: false
  },
  focusedDay: {
    type: String,
    default: null
  },
  outline: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['day-click'])

const { t } = useI18n()
const { distanceUnit } = storeToRefs(useAuthStore())
const timezone = useTimezone()

const outlineColorFor = (color) => getOutlineColor(color)

const speedItems = computed(() => {
  const { unit, slowBelow, mediumUpTo } = getSpeedBandThresholds(distanceUnit.value, {
    kmh: t('common.units.kmh'),
    mph: t('common.units.mph')
  })
  return [
    { key: 'slow', color: props.speedColors?.slow, label: t('maps.legend.speed.slow', { speed: `${slowBelow} ${unit}` }) },
    { key: 'medium', color: props.speedColors?.medium, label: t('maps.legend.speed.medium', { from: slowBelow, to: `${mediumUpTo} ${unit}` }) },
    { key: 'fast', color: props.speedColors?.fast, label: t('maps.legend.speed.fast', { speed: `${mediumUpTo} ${unit}` }) }
  ]
})

const speedAriaLabel = computed(() => (
  `${t('maps.legend.speed.ariaLabel')}: ${speedItems.value.map(item => item.label).join(', ')}`
))

// Day keys are calendar dates in the user's timezone.
const dayItems = computed(() => props.days.map(({ dayKey, color }) => ({
  dayKey,
  color,
  label: timezone.create(dayKey).format('ddd D')
})))

const dayGradientCss = computed(() => {
  const last = Math.max(1, dayItems.value.length - 1)
  const stops = dayItems.value.map((item, index) => `${item.color} ${Math.round((index / last) * 100)}%`)
  return `linear-gradient(to right, ${stops.join(', ')})`
})

const daysAriaLabel = computed(() => {
  const items = dayItems.value
  if (props.daysMode === DAY_COLOR_MODES.DISTINCT) {
    return `${t('maps.legend.days.ariaLabel')}: ${items.map(item => item.label).join(', ')}`
  }
  return `${t('maps.legend.days.ariaLabel')}: ${t('maps.legend.days.gradientRange', {
    from: items[0]?.label,
    to: items[items.length - 1]?.label
  })}`
})
</script>

<style scoped>
.map-color-legend {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.35rem 0.6rem;
  font-size: 0.75rem;
  line-height: 1.2;
  color: inherit;
}

.map-color-legend-title {
  font-weight: 700;
}

.map-color-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  white-space: nowrap;
}

.map-color-legend-swatch {
  display: inline-block;
  width: 1.1rem;
  height: 0.35rem;
  border-radius: 999px;
}

.map-color-legend-day-button {
  padding: 0.15rem 0.35rem;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
  pointer-events: auto;
}

.map-color-legend-day-button:hover {
  border-color: var(--gp-border-medium);
}

.map-color-legend-day-button:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 1px;
}

/* The focused day is outlined and bold, not only brighter, so the state does not rely on color alone. */
.map-color-legend-day--focused {
  border-color: var(--gp-primary);
  font-weight: 700;
}

.map-color-legend-day--muted {
  opacity: 0.45;
}

.map-color-legend-swatch--outlined {
  box-shadow: 0 0 0 1.5px var(--legend-outline);
}

.map-color-legend-gradient {
  display: inline-block;
  width: 6rem;
  height: 0.5rem;
  border-radius: 999px;
}
</style>
