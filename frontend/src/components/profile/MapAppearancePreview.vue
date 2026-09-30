<template>
  <div class="appearance-preview">
    <div class="appearance-preview-toolbar">
      <span class="appearance-preview-toolbar-label" :id="`${uid}-simulate-label`">
        {{ t('profile.appearance.preview.simulate.label') }}
      </span>
      <SelectButton
        v-model="simulation"
        :options="simulationOptions"
        optionLabel="label"
        optionValue="value"
        :allowEmpty="false"
        size="small"
        :aria-labelledby="`${uid}-simulate-label`"
        class="appearance-preview-simulate"
      />
    </div>

    <div class="appearance-preview-panels" role="img" :aria-label="t('profile.appearance.preview.ariaLabel')">
      <!-- Color vision simulation (Machado et al. 2009, full severity); applied in linearRGB, the SVG default. -->
      <svg width="0" height="0" class="appearance-preview-defs" aria-hidden="true" focusable="false">
        <defs>
          <filter v-for="(matrix, key) in SIMULATION_MATRICES" :id="`${uid}-${key}`" :key="key">
            <feColorMatrix type="matrix" :values="matrix" />
          </filter>
        </defs>
      </svg>

      <figure v-for="panel in panels" :key="panel.key" class="appearance-preview-panel">
        <svg
          viewBox="0 0 200 120"
          class="appearance-preview-svg"
          preserveAspectRatio="xMidYMid slice"
          aria-hidden="true"
          focusable="false"
        >
          <g :filter="simulation !== 'none' ? `url(#${uid}-${simulation})` : undefined">
            <rect width="200" height="120" :fill="panel.base" />
            <path v-for="(shape, index) in panel.shapes" :key="`s${index}`" :d="shape.d" :fill="shape.fill" />
            <path
              v-for="(road, index) in panel.roads"
              :key="`r${index}`"
              :d="road"
              fill="none"
              :stroke="panel.road"
              stroke-width="3"
              stroke-linecap="round"
            />

            <!-- Unselected trip -->
            <path
              v-if="appearance.outlineEnabled"
              :d="PATHS.normal"
              fill="none"
              :stroke="getOutlineColor(appearance.defaultPathColor)"
              :stroke-width="appearance.pathWidth + PATH_OUTLINE_EXTRA_WIDTH"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-opacity="0.8"
            />
            <path
              :d="PATHS.normal"
              fill="none"
              :stroke="appearance.defaultPathColor"
              :stroke-width="appearance.pathWidth"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-opacity="0.8"
            />

            <!-- Selected walking trip (dashed) and selected driving trip (speed colors) -->
            <template v-for="line in highlightedLines" :key="line.key">
              <path
                v-if="appearance.outlineEnabled"
                :d="line.d"
                fill="none"
                :stroke="getOutlineColor(line.color)"
                :stroke-width="appearance.highlightedPathWidth + PATH_OUTLINE_EXTRA_WIDTH"
                :stroke-dasharray="line.dashArray"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </template>
            <path
              v-for="line in highlightedLines"
              :key="`${line.key}-line`"
              :d="line.d"
              fill="none"
              :stroke="line.color"
              :stroke-width="appearance.highlightedPathWidth"
              :stroke-dasharray="line.dashArray"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </g>
        </svg>
        <figcaption>{{ panel.label }}</figcaption>
      </figure>
    </div>

    <MapColorLegend
      v-if="appearance.speedBandsEnabled"
      variant="speed"
      :speed-colors="appearance.speedBandColors"
      :outline="appearance.outlineEnabled"
      class="appearance-preview-legend"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectButton from 'primevue/selectbutton'
import MapColorLegend from '@/components/maps/MapColorLegend.vue'
import { PATH_OUTLINE_EXTRA_WIDTH, getOutlineColor } from '@/maps/shared/mapAppearance'

const props = defineProps({
  // Output of resolveMapAppearance() for the values being edited.
  appearance: {
    type: Object,
    required: true
  }
})

const { t } = useI18n()

const uid = `gp-appearance-${Math.random().toString(36).slice(2, 9)}`

const SIMULATION_MATRICES = {
  deuteranopia: '0.367322 0.860646 -0.227968 0 0  0.280085 0.672501 0.047413 0 0  -0.011820 0.042940 0.968881 0 0  0 0 0 1 0',
  protanopia: '0.152286 1.052583 -0.204868 0 0  0.114503 0.786281 0.099216 0 0  -0.003882 -0.048116 1.051998 0 0  0 0 0 1 0',
  tritanopia: '1.255528 -0.076749 -0.178779 0 0  -0.078411 0.930809 0.147602 0 0  0.004733 0.691367 0.303900 0 0  0 0 0 1 0'
}

const PATHS = {
  normal: 'M8 102 C 50 72, 90 116, 130 90 S 178 70, 192 80',
  walk: 'M8 22 C 26 40, 46 12, 66 26',
  slow: 'M80 30 Q 92 42 106 46',
  medium: 'M106 46 Q 122 50 138 42',
  fast: 'M138 42 Q 162 28 192 32'
}

const simulation = ref('none')
const simulationOptions = computed(() => [
  { label: t('profile.appearance.preview.simulate.none'), value: 'none' },
  { label: t('profile.appearance.preview.simulate.deuteranopia'), value: 'deuteranopia' },
  { label: t('profile.appearance.preview.simulate.protanopia'), value: 'protanopia' },
  { label: t('profile.appearance.preview.simulate.tritanopia'), value: 'tritanopia' }
])

const ROADS = ['M0 60 H200', 'M70 0 V120', 'M150 0 Q 140 60 170 120']

const panels = computed(() => [
  {
    key: 'light',
    label: t('profile.appearance.preview.backgrounds.light'),
    base: '#f2efe9',
    road: '#ffffff',
    roads: ROADS,
    shapes: [
      { d: 'M110 70 h40 v40 h-40 z', fill: '#cdebb0' },
      { d: 'M0 0 h40 q 10 20 -5 40 h-35 z', fill: '#aad3df' }
    ]
  },
  {
    key: 'dark',
    label: t('profile.appearance.preview.backgrounds.dark'),
    base: '#1f2430',
    road: '#353c4a',
    roads: ROADS,
    shapes: [
      { d: 'M110 70 h40 v40 h-40 z', fill: '#233129' },
      { d: 'M0 0 h40 q 10 20 -5 40 h-35 z', fill: '#16283a' }
    ]
  },
  {
    key: 'satellite',
    label: t('profile.appearance.preview.backgrounds.satellite'),
    base: '#3f5a2a',
    road: '#8a8578',
    roads: ROADS.slice(0, 2),
    shapes: [
      { d: 'M0 0 h70 v60 h-70 z', fill: '#2f4a22' },
      { d: 'M70 60 h80 v60 h-80 z', fill: '#556b2f' },
      { d: 'M150 0 h50 v60 h-50 z', fill: '#6b5b3a' },
      { d: 'M20 70 q 30 -10 40 20 q -20 25 -45 10 z', fill: '#4a6b3a' },
      { d: 'M160 80 q 20 -10 35 10 v30 h-40 z', fill: '#2c4020' }
    ]
  }
])

const highlightedLines = computed(() => {
  const { activePathColor, highlightedPathWidth, speedBandColors } = props.appearance
  const walkDash = `1 ${Math.max(8, highlightedPathWidth * 1.5)}`
  const walk = { key: 'walk', d: PATHS.walk, color: activePathColor, dashArray: walkDash }

  if (!speedBandColors) {
    return [walk, { key: 'drive', d: `${PATHS.slow} ${PATHS.medium.replace('M', 'L')} ${PATHS.fast.replace('M', 'L')}`, color: activePathColor, dashArray: null }]
  }

  return [
    walk,
    { key: 'slow', d: PATHS.slow, color: speedBandColors.slow, dashArray: null },
    { key: 'medium', d: PATHS.medium, color: speedBandColors.medium, dashArray: null },
    { key: 'fast', d: PATHS.fast, color: speedBandColors.fast, dashArray: null }
  ]
})
</script>

<style scoped>
.appearance-preview {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md, 0.75rem);
  padding: var(--gp-spacing-md, 0.75rem) 0;
}

.appearance-preview-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.appearance-preview-toolbar-label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
}

/* PrimeVue's toggle buttons stay light in the app's dark theme; tie their tokens to the app's own
   surface and text variables so the segmented control follows the theme. */
.appearance-preview-simulate {
  flex-wrap: wrap;
  --p-togglebutton-background: var(--gp-surface-light);
  --p-togglebutton-border-color: var(--gp-surface-light);
  --p-togglebutton-color: var(--gp-text-secondary);
  --p-togglebutton-hover-background: var(--gp-surface-light);
  --p-togglebutton-hover-color: var(--gp-text-primary);
  --p-togglebutton-checked-background: var(--gp-surface-light);
  --p-togglebutton-checked-border-color: var(--gp-surface-light);
  --p-togglebutton-checked-color: var(--gp-text-primary);
  --p-togglebutton-content-checked-background: var(--gp-surface-white);
}

/* The selected option is also bold, so it does not depend on the background shade alone. */
.appearance-preview-simulate :deep(.p-togglebutton-checked) {
  font-weight: 700;
}

.appearance-preview-defs {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}

.appearance-preview-panels {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--gp-spacing-md, 0.75rem);
}

.appearance-preview-panel {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.appearance-preview-svg {
  width: 100%;
  aspect-ratio: 5 / 3;
  border-radius: var(--gp-radius-medium, 0.5rem);
  border: 1px solid var(--gp-border-light);
  display: block;
}

.appearance-preview-panel figcaption {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  text-align: center;
}

.appearance-preview-legend {
  color: var(--gp-text-primary);
}

@media (max-width: 640px) {
  .appearance-preview-panels {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
