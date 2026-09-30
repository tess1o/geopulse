<template>
  <Card class="appearance-card profile-settings-card">
    <template #content>
      <form @submit.prevent="handleSubmit" class="appearance-form settings-tab">
        <div class="settings-tab-header">
          <div class="settings-tab-icon">
            <i class="pi pi-palette"></i>
          </div>
          <div class="settings-tab-info">
            <h3 class="settings-tab-title">{{ t('profile.appearance.title') }}</h3>
            <p class="settings-tab-description">{{ t('profile.appearance.description') }}</p>
          </div>
        </div>

        <section class="settings-group" aria-labelledby="appearance-color-vision-heading">
          <div class="settings-group-header">
            <h3 id="appearance-color-vision-heading">
              {{ t('profile.appearance.colorVision.heading') }}
              <Tag
                v-if="isCustomized"
                :value="t('profile.appearance.colorVision.customized')"
                severity="secondary"
                class="appearance-customized-tag"
              />
            </h3>
            <p>{{ t('profile.appearance.colorVision.description') }}</p>
          </div>

          <div
            class="color-scheme-options"
            role="radiogroup"
            aria-labelledby="appearance-color-vision-heading"
            data-setting-id="colorScheme"
            id="setting-colorScheme"
          >
            <button
              v-for="scheme in colorSchemeOptions"
              :key="scheme.value"
              type="button"
              role="radio"
              class="color-scheme-option"
              :class="{ selected: form.colorScheme === scheme.value }"
              :aria-checked="form.colorScheme === scheme.value"
              :disabled="readOnly"
              :data-testid="`color-scheme-${scheme.value}`"
              @click="selectColorScheme(scheme.value)"
            >
              <span class="color-scheme-swatches" aria-hidden="true">
                <span
                  v-for="(color, index) in scheme.swatches"
                  :key="index"
                  class="color-scheme-swatch"
                  :style="{ backgroundColor: color }"
                ></span>
              </span>
              <span class="color-scheme-name">
                <i v-if="form.colorScheme === scheme.value" class="pi pi-check-circle" aria-hidden="true"></i>
                {{ scheme.label }}
              </span>
              <span class="color-scheme-description">{{ scheme.description }}</span>
            </button>
          </div>
        </section>

        <section class="settings-group" aria-labelledby="appearance-preview-heading">
          <div class="settings-group-header">
            <h3 id="appearance-preview-heading">{{ t('profile.appearance.preview.heading') }}</h3>
            <p>{{ t('profile.appearance.preview.description') }}</p>
          </div>
          <MapAppearancePreview :appearance="resolvedAppearance" />
        </section>

        <section class="settings-group" aria-labelledby="appearance-paths-heading">
          <div class="settings-group-header">
            <h3 id="appearance-paths-heading">{{ t('profile.appearance.paths.heading') }}</h3>
            <p>{{ t('profile.appearance.paths.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              :title="t('profile.appearance.paths.defaultPathColor.title')"
              :description="t('profile.appearance.paths.defaultPathColor.description')"
              :details="t('profile.appearance.paths.defaultPathColor.details')"
              setting-id="defaultPathColor"
            >
              <template #control>
                <div class="field-control color-field-control">
                  <ColorPicker v-model="defaultPathColorPickerModel" format="hex" :disabled="readOnly" />
                  <span class="color-value-label">
                    {{ form.defaultPathColor || `${resolvedAppearance.defaultPathColor} · ${t('profile.appearance.paths.followScheme')}` }}
                  </span>
                  <Button
                    v-if="form.defaultPathColor"
                    :label="t('profile.appearance.paths.resetColor')"
                    icon="pi pi-refresh"
                    size="small"
                    text
                    :disabled="readOnly"
                    @click="form.defaultPathColor = ''"
                  />
                </div>
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.appearance.paths.activePathColor.title')"
              :description="t('profile.appearance.paths.activePathColor.description')"
              :details="t('profile.appearance.paths.activePathColor.details')"
              setting-id="activePathColor"
            >
              <template #control>
                <div class="field-control color-field-control">
                  <ColorPicker v-model="activePathColorPickerModel" format="hex" :disabled="readOnly" />
                  <span class="color-value-label">
                    {{ form.activePathColor || `${resolvedAppearance.activePathColor} · ${t('profile.appearance.paths.followScheme')}` }}
                  </span>
                  <Button
                    v-if="form.activePathColor"
                    :label="t('profile.appearance.paths.resetColor')"
                    icon="pi pi-refresh"
                    size="small"
                    text
                    :disabled="readOnly"
                    @click="form.activePathColor = ''"
                  />
                </div>
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.appearance.paths.pathWidth.title')"
              :description="t('profile.appearance.paths.pathWidth.description')"
              :details="t('profile.appearance.paths.pathWidth.details')"
              setting-id="pathWidth"
            >
              <template #control>
                <SliderControl
                  v-model="form.pathWidth"
                  :min="PATH_WIDTH.MIN"
                  :max="PATH_WIDTH.MAX"
                  :step="1"
                  :labels="pathWidthLabels"
                  suffix=" px"
                  :input-min="PATH_WIDTH.MIN"
                  :input-max="PATH_WIDTH.MAX"
                  :decimal-places="0"
                />
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.appearance.paths.outline.title')"
              :description="t('profile.appearance.paths.outline.description')"
              :details="t('profile.appearance.paths.outline.details')"
              setting-id="pathOutlineEnabled"
            >
              <template #control>
                <ToggleSwitch
                  v-model="form.pathOutlineEnabled"
                  class="toggle-control"
                  :aria-label="t('profile.appearance.paths.outline.title')"
                  :disabled="readOnly"
                />
              </template>
            </SettingCard>
          </div>
        </section>

        <section class="settings-group" aria-labelledby="appearance-speed-heading">
          <div class="settings-group-header">
            <h3 id="appearance-speed-heading">{{ t('profile.appearance.speedBands.heading') }}</h3>
            <p>{{ t('profile.appearance.speedBands.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              :title="t('profile.appearance.speedBands.palette.title')"
              :description="t('profile.appearance.speedBands.palette.description')"
              :details="speedBandPaletteDetails"
              setting-id="speedBandPalette"
            >
              <template #control>
                <Dropdown
                  id="speedBandPalette"
                  v-model="form.speedBandPalette"
                  :options="speedBandPaletteOptions"
                  optionLabel="label"
                  optionValue="value"
                  :placeholder="speedBandPaletteOptions[0].label"
                  class="w-full"
                  :disabled="readOnly"
                >
                  <template #option="{ option }">
                    <span class="palette-option">
                      <span class="palette-option-swatches" aria-hidden="true">
                        <span
                          v-for="(color, index) in option.swatches"
                          :key="index"
                          class="palette-option-swatch"
                          :style="{ backgroundColor: color }"
                        ></span>
                      </span>
                      <span>{{ option.label }}</span>
                    </span>
                  </template>
                </Dropdown>
              </template>
            </SettingCard>
          </div>
        </section>

        <section class="settings-group" aria-labelledby="appearance-heatmap-heading">
          <div class="settings-group-header">
            <h3 id="appearance-heatmap-heading">{{ t('profile.appearance.heatmap.heading') }}</h3>
            <p>{{ t('profile.appearance.heatmap.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              :title="t('profile.appearance.heatmap.gradient.title')"
              :description="t('profile.appearance.heatmap.gradient.description')"
              :details="t('profile.appearance.heatmap.gradient.details')"
              setting-id="heatmapGradient"
            >
              <template #control>
                <Dropdown
                  id="heatmapGradient"
                  v-model="form.heatmapGradient"
                  :options="heatmapGradientOptions"
                  optionLabel="label"
                  optionValue="value"
                  :placeholder="heatmapGradientOptions[0].label"
                  class="w-full"
                  :disabled="readOnly"
                >
                  <template #option="{ option }">
                    <span class="palette-option">
                      <span class="gradient-option-bar" :style="{ background: option.css }" aria-hidden="true"></span>
                      <span>{{ option.label }}</span>
                    </span>
                  </template>
                </Dropdown>
              </template>
            </SettingCard>
          </div>
        </section>

        <div class="settings-actions is-sticky">
          <Button
            type="button"
            :label="t('profile.appearance.resetToDefaults')"
            severity="secondary"
            outlined
            @click="handleReset"
            :disabled="loading || readOnly"
          />
          <Button
            type="submit"
            :label="t('profile.appearance.saveChanges')"
            :loading="loading"
            icon="pi pi-check"
            :disabled="readOnly"
          />
        </div>
      </form>
    </template>
  </Card>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { getSpeedBandThresholds } from '@/maps/shared/speedBandThresholds'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Dropdown from 'primevue/dropdown'
import ToggleSwitch from 'primevue/toggleswitch'
import ColorPicker from 'primevue/colorpicker'
import Tag from 'primevue/tag'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import SliderControl from '@/components/ui/forms/SliderControl.vue'
import MapAppearancePreview from '@/components/profile/MapAppearancePreview.vue'
import {
  APPEARANCE_PREFERENCE_DEFAULTS,
  COLOR_SCHEME_PRESETS,
  HEATMAP_GRADIENTS,
  HEATMAP_GRADIENT_KEYS,
  MAP_COLOR_SCHEMES,
  PATH_WIDTH,
  SPEED_BAND_PALETTES,
  SPEED_BAND_PALETTE_KEYS,
  heatmapGradientToCss,
  resolveMapAppearance
} from '@/maps/shared/mapAppearance'

const { t } = useI18n()
const { distanceUnit } = storeToRefs(useAuthStore())

const props = defineProps({
  readOnly: {
    type: Boolean,
    default: false
  },
  initialPreferences: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['save', 'dirty-change'])

const HEX_COLOR_PATTERN = /^#?[0-9a-f]{6}$/i

const normalizeHexColor = (color) => {
  const value = String(color || '').trim()
  return HEX_COLOR_PATTERN.test(value) ? `#${value.replace('#', '').toLowerCase()}` : ''
}

// Only this tab's fields; the Timeline & Map tab owns the rest of the same preferences document.
const normalizePreferences = (preferences = {}) => ({
  colorScheme: MAP_COLOR_SCHEMES.includes(preferences.colorScheme) ? preferences.colorScheme : APPEARANCE_PREFERENCE_DEFAULTS.colorScheme,
  defaultPathColor: normalizeHexColor(preferences.defaultPathColor),
  activePathColor: normalizeHexColor(preferences.activePathColor),
  speedBandPalette: SPEED_BAND_PALETTE_KEYS.includes(preferences.speedBandPalette) ? preferences.speedBandPalette : '',
  heatmapGradient: HEATMAP_GRADIENT_KEYS.includes(preferences.heatmapGradient) ? preferences.heatmapGradient : '',
  pathOutlineEnabled: preferences.pathOutlineEnabled === true,
  pathWidth: Number.isFinite(Number(preferences.pathWidth)) && preferences.pathWidth !== '' && preferences.pathWidth !== null
    ? Math.min(PATH_WIDTH.MAX, Math.max(PATH_WIDTH.MIN, Math.round(Number(preferences.pathWidth))))
    : APPEARANCE_PREFERENCE_DEFAULTS.pathWidth
})

const form = ref(normalizePreferences(APPEARANCE_PREFERENCE_DEFAULTS))
const loading = ref(false)

const resolvedAppearance = computed(() => resolveMapAppearance(form.value))
const isCustomized = computed(() => Object.values(resolvedAppearance.value.overrides).some(Boolean))

const schemeName = (scheme) => t(`profile.appearance.colorVision.schemes.${scheme}.name`)

const colorSchemeOptions = computed(() => MAP_COLOR_SCHEMES.map((scheme) => {
  const preset = COLOR_SCHEME_PRESETS[scheme]
  const bands = SPEED_BAND_PALETTES[preset.speedBandPalette]
  return {
    value: scheme,
    label: schemeName(scheme),
    description: t(`profile.appearance.colorVision.schemes.${scheme}.description`),
    swatches: [preset.defaultPathColor, preset.activePathColor, bands.slow, bands.medium, bands.fast]
  }
}))

const speedBandSwatches = (key) => {
  if (key === 'OFF') {
    return [resolvedAppearance.value.activePathColor]
  }
  const bands = SPEED_BAND_PALETTES[key]
  return [bands.slow, bands.medium, bands.fast]
}

const speedBandPaletteOptions = computed(() => {
  const schemePalette = COLOR_SCHEME_PRESETS[form.value.colorScheme].speedBandPalette
  return [
    {
      value: '',
      label: t('profile.appearance.speedBands.palette.followScheme', { name: schemeName(form.value.colorScheme) }),
      swatches: speedBandSwatches(schemePalette)
    },
    ...SPEED_BAND_PALETTE_KEYS.map((key) => ({
      value: key,
      label: t(`profile.appearance.speedBands.options.${key}`),
      swatches: speedBandSwatches(key)
    }))
  ]
})

const heatmapGradientOptions = computed(() => {
  const schemeGradient = COLOR_SCHEME_PRESETS[form.value.colorScheme].heatmapGradient
  return [
    {
      value: '',
      label: t('profile.appearance.heatmap.gradient.followScheme', { name: schemeName(form.value.colorScheme) }),
      css: heatmapGradientToCss(HEATMAP_GRADIENTS[schemeGradient])
    },
    ...HEATMAP_GRADIENT_KEYS.map((key) => ({
      value: key,
      label: t(`profile.appearance.heatmap.options.${key}`),
      css: heatmapGradientToCss(HEATMAP_GRADIENTS[key])
    }))
  ]
})

// Speed limits in the user's own unit (km/h or mph), matching the legend on the map.
const speedBandPaletteDetails = computed(() => {
  const { unit, slowBelow, mediumUpTo } = getSpeedBandThresholds(distanceUnit.value, {
    kmh: t('common.units.kmh'),
    mph: t('common.units.mph')
  })
  return t('profile.appearance.speedBands.palette.details', { slow: slowBelow, medium: mediumUpTo, unit })
})

const pathWidthLabels = computed(() => [
  t('profile.appearance.paths.widthLabels.thin'),
  t('profile.appearance.paths.widthLabels.default'),
  t('profile.appearance.paths.widthLabels.thick')
])

// PrimeVue's ColorPicker model is a hex string without a leading '#'. When unset, it shows the scheme color.
const defaultPathColorPickerModel = computed({
  get: () => resolvedAppearance.value.defaultPathColor.replace('#', ''),
  set: (value) => { form.value.defaultPathColor = normalizeHexColor(value) }
})
const activePathColorPickerModel = computed({
  get: () => resolvedAppearance.value.activePathColor.replace('#', ''),
  set: (value) => { form.value.activePathColor = normalizeHexColor(value) }
})

// A preset replaces individual color choices, otherwise picking it could look like it did nothing.
// Color vision presets also turn on the outline, which helps whatever the background.
const selectColorScheme = (scheme) => {
  if (props.readOnly) return
  form.value = {
    ...form.value,
    colorScheme: scheme,
    defaultPathColor: '',
    activePathColor: '',
    speedBandPalette: '',
    heatmapGradient: '',
    ...(scheme !== 'DEFAULT' ? { pathOutlineEnabled: true } : {}),
    ...(scheme === 'HIGH_CONTRAST' ? { pathWidth: Math.max(form.value.pathWidth, 6) } : {})
  }
}

const hasChanges = computed(() => (
  JSON.stringify(normalizePreferences(form.value)) !== JSON.stringify(normalizePreferences(props.initialPreferences))
))

// Re-initialize only when this tab's own saved values change, so saving the Timeline & Map tab
// (same preferences object) keeps unsaved edits here.
watch(
  () => (props.initialPreferences ? JSON.stringify(normalizePreferences(props.initialPreferences)) : null),
  (serialized) => {
    if (serialized) {
      form.value = normalizePreferences(props.initialPreferences)
    }
  },
  { immediate: true }
)

watch(hasChanges, (changed) => {
  emit('dirty-change', changed)
})

const handleSubmit = async () => {
  if (props.readOnly) {
    return
  }

  loading.value = true
  try {
    // Empty strings reset a value on the server, so it follows the color scheme again.
    emit('save', normalizePreferences(form.value))
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  if (props.readOnly) return
  form.value = normalizePreferences(APPEARANCE_PREFERENCE_DEFAULTS)
}
</script>

<style scoped>
.appearance-customized-tag {
  margin-left: 0.5rem;
  vertical-align: middle;
  font-size: 0.7rem;
}

.color-scheme-options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: var(--gp-spacing-md, 0.75rem);
  padding: var(--gp-spacing-md, 0.75rem) 0;
}

.color-scheme-option {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.4rem;
  padding: 0.75rem;
  border: 2px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium, 0.5rem);
  background: var(--gp-surface-white, transparent);
  color: var(--gp-text-primary);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.color-scheme-option:hover:not(:disabled) {
  border-color: var(--gp-border-medium);
}

.color-scheme-option:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

/* Selection is shown by border weight, a check icon and aria-checked, not by color alone. */
.color-scheme-option.selected {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 1px var(--gp-primary);
}

.color-scheme-option:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.color-scheme-swatches {
  display: flex;
  gap: 0.25rem;
}

.color-scheme-swatch {
  width: 1.25rem;
  height: 0.5rem;
  border-radius: 999px;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.15);
}

.color-scheme-name {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-weight: 600;
}

.color-scheme-name .pi {
  color: var(--gp-primary);
}

.color-scheme-description {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

.color-field-control {
  flex-direction: row !important;
  align-items: center;
  flex-wrap: wrap;
}

.color-value-label {
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
  font-family: var(--gp-font-mono, monospace);
}

.palette-option {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.palette-option-swatches {
  display: inline-flex;
  gap: 0.2rem;
}

.palette-option-swatch {
  width: 0.9rem;
  height: 0.9rem;
  border-radius: 0.2rem;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.15);
}

.gradient-option-bar {
  display: inline-block;
  width: 3.5rem;
  height: 0.6rem;
  border-radius: 999px;
}
</style>
