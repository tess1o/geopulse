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
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { getSpeedBandThresholds } from '@/maps/shared/speedBandThresholds'
import { getOutlineColor, heatmapGradientToCss } from '@/maps/shared/mapAppearance'

const props = defineProps({
  variant: {
    type: String,
    required: true,
    validator: (value) => ['speed', 'heatmap'].includes(value)
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
  outline: {
    type: Boolean,
    default: false
  }
})

const { t } = useI18n()
const { distanceUnit } = storeToRefs(useAuthStore())

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
