<template>
  <section class="digest-hero" aria-labelledby="digest-hero-title">
    <template v-if="hasMetrics">
      <div class="digest-hero-copy">
        <p class="digest-eyebrow"><i class="pi pi-sparkles"></i> {{ title }}</p>
        <h2 id="digest-hero-title">{{ formatDistanceRounded(metrics.totalDistance) }} of movement.</h2>
        <p class="digest-summary">{{ metrics.tripCount }} trips across {{ metrics.citiesVisited }} {{ metrics.citiesVisited === 1 ? 'city' : 'cities' }}.</p>
        <span v-if="comparison" class="comparison-pill" :class="comparisonClass"><i :class="comparisonIcon"></i>{{ comparisonText }}</span>
      </div>

      <dl class="hero-stats">
        <div><dt>Trips</dt><dd>{{ metrics.tripCount }}</dd></div>
        <div><dt>Active days</dt><dd>{{ metrics.activeDays }}</dd></div>
        <div><dt>Moving time</dt><dd>{{ formatDuration(metrics.timeMoving || 0) }}</dd></div>
        <div><dt>Stays</dt><dd>{{ metrics.stayCount || 0 }}</dd></div>
      </dl>

      <div v-if="movementModes.length" class="movement-mix">
        <div class="movement-mix-heading"><span>How you moved</span><span v-if="highlights?.peakHours?.length" class="peak-hours">Most active: {{ highlights.peakHours.join(', ') }}</span></div>
        <div class="movement-bar" aria-label="Distance split by transport type"><span v-for="mode in movementModes" :key="mode.key" :style="{ width: `${mode.share}%`, background: mode.color }" :title="`${mode.label}: ${mode.share}%`"></span></div>
        <div class="movement-legend"><span v-for="mode in movementModes" :key="mode.key"><i :style="{ background: mode.color }"></i>{{ mode.label }} <b>{{ mode.share }}%</b></span></div>
      </div>
    </template>
    <div v-else class="no-metrics-placeholder"><i class="pi pi-compass"></i><p>No movement recorded for this period yet.</p></div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { formatDistanceRounded, formatDuration } from '@/utils/calculationsHelpers'

const props = defineProps({
  title: { type: String, default: 'Your rewind' },
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
  if (comparison.direction === 'increase') return `${change}% more than the previous period`
  if (comparison.direction === 'decrease') return `${change}% less than the previous period`
  return 'About the same as the previous period'
})
const movementModes = computed(() => {
  const definitions = [['carDistance', 'Car', '#3b82f6'], ['walkDistance', 'Walk', '#10b981'], ['bicycleDistance', 'Bicycle', '#f59e0b'], ['runningDistance', 'Running', '#8b5cf6'], ['motorcycleDistance', 'Motorcycle', '#06b6d4'], ['trainDistance', 'Train', '#64748b'], ['flightDistance', 'Flight', '#ef4444'], ['boatDistance', 'Boat', '#14b8a6'], ['unknownDistance', 'Other', '#94a3b8']]
  const total = Number(props.metrics?.totalDistance) || 0
  return definitions.map(([key, label, color]) => ({ key, label, color, distance: Number(props.metrics?.[key]) || 0 })).filter((mode) => mode.distance > 0).map((mode) => ({ ...mode, share: Math.max(1, Math.round((mode.distance / total) * 100)) }))
})
</script>

<style scoped>
.digest-hero { position:relative; overflow:hidden; display:grid; grid-template-columns:minmax(0,1.25fr) minmax(340px,.75fr); gap:var(--gp-spacing-xl); padding:clamp(1.5rem,4vw,3rem); margin-bottom:var(--gp-spacing-xl); border:1px solid color-mix(in srgb,var(--gp-primary) 28%,var(--gp-border-light)); border-radius:20px; background:linear-gradient(128deg,color-mix(in srgb,var(--gp-primary) 16%,var(--gp-surface-white)),var(--gp-surface-white) 58%); box-shadow:var(--gp-shadow-card) }.digest-hero::after { content:''; position:absolute; width:18rem; height:18rem; right:-7rem; top:-11rem; border-radius:50%; background:color-mix(in srgb,var(--gp-secondary) 22%,transparent); pointer-events:none }.digest-hero-copy,.hero-stats,.movement-mix { position:relative; z-index:1 }.digest-eyebrow { display:flex; align-items:center; gap:.45rem; margin:0 0 .7rem; font-size:.78rem; font-weight:800; letter-spacing:.09em; text-transform:uppercase; color:var(--gp-primary) }.digest-hero h2 { margin:0; font-size:clamp(2rem,4.6vw,4rem); line-height:.98; letter-spacing:-.055em; color:var(--gp-text-primary) }.digest-summary { margin:.9rem 0 1rem; font-size:1.05rem; color:var(--gp-text-secondary) }.comparison-pill { display:inline-flex; align-items:center; gap:.4rem; padding:.42rem .7rem; border-radius:999px; font-size:.82rem; font-weight:700; background:var(--gp-surface-light); color:var(--gp-text-secondary) }.comparison-pill.increase { color:var(--gp-success-dark); background:var(--gp-success-light) }.comparison-pill.decrease { color:var(--gp-error); background:var(--gp-danger-light) }.hero-stats { display:grid; grid-template-columns:repeat(2,1fr); gap:.65rem; margin:0; align-content:center }.hero-stats div { padding:1rem; border-radius:14px; background:color-mix(in srgb,var(--gp-surface-light) 88%,transparent); border:1px solid var(--gp-border-light) }.hero-stats dt { font-size:.74rem; font-weight:700; color:var(--gp-text-secondary); text-transform:uppercase; letter-spacing:.06em }.hero-stats dd { margin:.28rem 0 0; font-size:1.2rem; font-weight:800; color:var(--gp-text-primary) }.movement-mix { grid-column:1 / -1; padding-top:.25rem }.movement-mix-heading { display:flex; justify-content:space-between; gap:1rem; margin-bottom:.55rem; color:var(--gp-text-secondary); font-size:.84rem; font-weight:700 }.peak-hours { font-weight:500 }.movement-bar { display:flex; overflow:hidden; height:.6rem; border-radius:999px; background:var(--gp-border-subtle) }.movement-bar span { min-width:2px }.movement-legend { display:flex; flex-wrap:wrap; gap:.6rem 1rem; margin-top:.65rem; font-size:.78rem; color:var(--gp-text-secondary) }.movement-legend span { display:inline-flex; align-items:center; gap:.32rem }.movement-legend i { width:.55rem; height:.55rem; border-radius:50% }.movement-legend b { color:var(--gp-text-primary) }.no-metrics-placeholder { display:grid; place-items:center; min-height:14rem; color:var(--gp-text-muted); text-align:center }.no-metrics-placeholder i { font-size:2.5rem }.p-dark .digest-hero { background:linear-gradient(128deg,color-mix(in srgb,var(--gp-primary) 25%,var(--gp-surface-dark)),var(--gp-surface-dark) 64%) }.p-dark .comparison-pill.increase { color:var(--gp-secondary-light); background:color-mix(in srgb,var(--gp-success) 18%,var(--gp-surface-dark)) }.p-dark .comparison-pill.decrease { background:color-mix(in srgb,var(--gp-error) 18%,var(--gp-surface-dark)) }@media (max-width:720px) { .digest-hero { grid-template-columns:1fr; padding:1.35rem }.hero-stats { order:2 }.movement-mix { grid-column:auto; order:3 }.digest-hero h2 { font-size:2.55rem }.movement-mix-heading { flex-direction:column; gap:.25rem } }@media (prefers-reduced-motion:reduce) { * { transition:none!important } }
</style>
