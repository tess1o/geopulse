<template>
  <section class="digest-hero" aria-labelledby="digest-hero-title">
    <template v-if="hasMetrics">
      <div class="digest-hero-copy">
        <p class="digest-eyebrow"><i class="pi pi-sparkles"></i> {{ title }}</p>
        <h2 id="digest-hero-title">{{ t('analytics.digest.hero.distanceOfMovement', { distance: formatDistanceRounded(metrics.totalDistance) }) }}</h2>
        <p class="digest-summary">{{ metrics.citiesVisited === 1 ? t('analytics.digest.hero.tripsAcrossCity', { count: metrics.tripCount, cities: metrics.citiesVisited }) : t('analytics.digest.hero.tripsAcrossCities', { count: metrics.tripCount, cities: metrics.citiesVisited }) }}</p>
        <span v-if="comparison" class="comparison-pill" :class="comparisonClass"><i :class="comparisonIcon"></i>{{ comparisonText }}</span>
      </div>

      <dl class="hero-stats">
        <div><dt>{{ t('analytics.digest.hero.trips') }}</dt><dd>{{ metrics.tripCount }}</dd></div>
        <div><dt>{{ t('analytics.digest.hero.activeDays') }}</dt><dd>{{ metrics.activeDays }}</dd></div>
        <div><dt>{{ t('analytics.digest.hero.movingTime') }}</dt><dd>{{ formatDuration(metrics.timeMoving || 0) }}</dd></div>
        <div><dt>{{ t('analytics.digest.hero.stays') }}</dt><dd>{{ metrics.stayCount || 0 }}</dd></div>
      </dl>

      <div v-if="movementModes.length" class="movement-mix">
        <div class="movement-mix-heading"><span>{{ t('analytics.digest.hero.howYouMoved') }}</span><span v-if="highlights?.peakHours?.length" class="peak-hours">{{ t('analytics.digest.hero.mostActive', { hours: highlights.peakHours.join(', ') }) }}</span></div>
        <div class="movement-bar" :aria-label="t('analytics.digest.hero.distanceSplitAriaLabel')"><span v-for="mode in movementModes" :key="mode.key" :style="{ width: `${mode.share}%`, background: mode.color }" :title="t('analytics.digest.hero.modeSharePercent', { label: mode.label, share: mode.share })"></span></div>
        <div class="movement-legend"><span v-for="mode in movementModes" :key="mode.key"><i :style="{ background: mode.color }"></i><span class="movement-legend-emoji" aria-hidden="true">{{ mode.emoji }}</span>{{ mode.label }} <b>{{ mode.share }}%</b></span></div>
      </div>
    </template>
    <div v-else class="no-metrics-placeholder"><i class="pi pi-compass"></i><p>{{ t('analytics.digest.hero.noMetrics') }}</p></div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { t as translate } from '@/locales'
import { formatDistanceRounded, formatDuration } from '@/utils/calculationsHelpers'
import { useMapAppearance } from '@/composables/useMapAppearance'
import { getMovementTypeEmoji } from '@/constants/movementTypes'

const { t } = useI18n()
const mapAppearance = useMapAppearance()

const props = defineProps({
  title: { type: String, default: () => translate('analytics.digest.hero.title') },
  metrics: { type: Object, default: () => ({}) },
  comparison: { type: Object, default: null },
  highlights: { type: Object, default: () => ({}) }
})

const hasMetrics = computed(() => Number(props.metrics?.tripCount) > 0)
const comparisonClass = computed(() => props.comparison?.direction || 'same')
const comparisonIcon = computed(() => ({ increase: 'pi pi-arrow-up-right', decrease: 'pi pi-arrow-down-right', same: 'pi pi-minus' })[comparisonClass.value])
const comparisonText = computed(() => {
  const comparison = props.comparison
  if (!comparison) return ''
  const change = Math.abs(Number(comparison.percentChange) || 0)
  if (comparison.direction === 'increase') return t('analytics.digest.hero.increaseComparison', { change })
  if (comparison.direction === 'decrease') return t('analytics.digest.hero.decreaseComparison', { change })
  return t('analytics.digest.hero.sameComparison')
})
const movementModes = computed(() => {
  const definitions = [
    ['carDistance', 'CAR', t('movementTypes.CAR')],
    ['walkDistance', 'WALK', t('movementTypes.WALK')],
    ['bicycleDistance', 'BICYCLE', t('movementTypes.BICYCLE')],
    ['runningDistance', 'RUNNING', t('movementTypes.RUNNING')],
    ['motorcycleDistance', 'MOTORCYCLE', t('movementTypes.MOTORCYCLE')],
    ['publicTransportDistance', 'PUBLIC_TRANSPORT', t('movementTypes.PUBLIC_TRANSPORT')],
    ['trainDistance', 'TRAIN', t('movementTypes.TRAIN')],
    ['flightDistance', 'FLIGHT', t('movementTypes.FLIGHT')],
    ['boatDistance', 'BOAT', t('movementTypes.BOAT')],
    ['unknownDistance', 'UNKNOWN', t('analytics.digest.hero.otherMode')]
  ]
  const colors = mapAppearance.value.movementTypeColors
  const total = Number(props.metrics?.totalDistance) || 0
  return definitions.map(([key, type, label]) => ({ key, label, color: colors[type], emoji: getMovementTypeEmoji(type), distance: Number(props.metrics?.[key]) || 0 })).filter((mode) => mode.distance > 0).map((mode) => ({ ...mode, share: Math.max(1, Math.round((mode.distance / total) * 100)) }))
})
</script>

<style scoped>
.digest-hero { position:relative; overflow:hidden; display:grid; grid-template-columns:minmax(0,1.25fr) minmax(340px,.75fr); gap:var(--gp-spacing-xl); padding:clamp(1.5rem,4vw,3rem); margin-bottom:var(--gp-spacing-xl); border:1px solid color-mix(in srgb,var(--gp-primary) 28%,var(--gp-border)); border-radius:20px; background:linear-gradient(128deg,color-mix(in srgb,var(--gp-primary) 16%,var(--gp-surface-card)),var(--gp-surface-card) 58%); box-shadow:var(--gp-shadow-card) }.digest-hero::after { content:''; position:absolute; width:18rem; height:18rem; right:-7rem; top:-11rem; border-radius:50%; background:color-mix(in srgb,var(--gp-secondary) 22%,transparent); pointer-events:none }.digest-hero-copy,.hero-stats,.movement-mix { position:relative; z-index:1 }.digest-eyebrow { display:flex; align-items:center; gap:.45rem; margin:0 0 .7rem; font-size:.78rem; font-weight:800; letter-spacing:.09em; text-transform:uppercase; color:var(--gp-primary) }.digest-hero h2 { margin:0; font-size:clamp(2rem,4.6vw,4rem); line-height:.98; letter-spacing:-.055em; color:var(--gp-text-primary) }.digest-summary { margin:.9rem 0 1rem; font-size:1.05rem; color:var(--gp-text-secondary) }.comparison-pill { display:inline-flex; align-items:center; gap:.4rem; padding:.42rem .7rem; border-radius:999px; font-size:.82rem; font-weight:700; background:var(--gp-surface-muted); color:var(--gp-text-secondary) }.comparison-pill.increase { color:var(--gp-success-text); background:var(--gp-success-soft) }.comparison-pill.decrease { color:var(--gp-danger); background:var(--gp-danger-soft) }.hero-stats { display:grid; grid-template-columns:repeat(2,1fr); gap:.65rem; margin:0; align-content:center }.hero-stats div { padding:1rem; border-radius:14px; background:color-mix(in srgb,var(--gp-surface-muted) 88%,transparent); border:1px solid var(--gp-border) }.hero-stats dt { font-size:.74rem; font-weight:700; color:var(--gp-text-secondary); text-transform:uppercase; letter-spacing:.06em }.hero-stats dd { margin:.28rem 0 0; font-size:1.2rem; font-weight:800; color:var(--gp-text-primary) }.movement-mix { grid-column:1 / -1; padding-top:.25rem }.movement-mix-heading { display:flex; justify-content:space-between; gap:1rem; margin-bottom:.55rem; color:var(--gp-text-secondary); font-size:.84rem; font-weight:700 }.peak-hours { font-weight:500 }.movement-bar { display:flex; overflow:hidden; height:.6rem; border-radius:999px; background:var(--gp-border-subtle) }.movement-bar span { min-width:2px }.movement-bar span + span { box-shadow:inset 2px 0 0 var(--gp-surface-card) }.movement-legend { display:flex; flex-wrap:wrap; gap:.6rem 1rem; margin-top:.65rem; font-size:.78rem; color:var(--gp-text-secondary) }.movement-legend span { display:inline-flex; align-items:center; gap:.32rem }.movement-legend i { width:.55rem; height:.55rem; border-radius:50% }.movement-legend .movement-legend-emoji { display:inline; font-size:.9rem; line-height:1 }.movement-legend b { color:var(--gp-text-primary) }.no-metrics-placeholder { display:grid; place-items:center; min-height:14rem; color:var(--gp-text-muted); text-align:center }.no-metrics-placeholder i { font-size:2.5rem }@media (max-width:720px) { .digest-hero { grid-template-columns:1fr; padding:1.35rem }.hero-stats { order:2 }.movement-mix { grid-column:auto; order:3 }.digest-hero h2 { font-size:2.55rem }.movement-mix-heading { flex-direction:column; gap:.25rem } }@media (prefers-reduced-motion:reduce) { * { transition:none!important } }
</style>
