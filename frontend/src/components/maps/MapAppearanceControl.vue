<template>
  <div ref="rootRef" class="map-appearance-control">
    <!-- Same button as the theme switcher it sits next to in the shared page header. -->
    <Button
      icon="pi pi-palette"
      severity="secondary"
      outlined
      size="small"
      :title="t('maps.appearanceMenu.buttonLabel')"
      :aria-label="t('maps.appearanceMenu.buttonLabel')"
      aria-haspopup="true"
      :aria-expanded="open"
      data-testid="map-appearance-button"
      @click="open = !open"
    />

    <div
      v-if="open"
      class="map-appearance-panel"
      role="radiogroup"
      :aria-label="t('maps.appearanceMenu.title')"
      data-testid="map-appearance-panel"
    >
      <div class="map-appearance-title">{{ t('maps.appearanceMenu.title') }}</div>
      <template v-for="(item, index) in items" :key="item.value">
        <div v-if="item.dividerBefore && index > 0" class="map-appearance-divider"></div>
        <button
          type="button"
          role="radio"
          class="map-appearance-option"
          :class="{ selected: item.value === choice }"
          :aria-checked="item.value === choice"
          :data-testid="`map-appearance-option-${item.value}`"
          @click="select(item.value)"
        >
          <i class="pi map-appearance-check" :class="item.value === choice ? 'pi-check-circle' : 'pi-circle'" aria-hidden="true"></i>
          <span class="map-appearance-label">{{ item.label }}</span>
          <span class="map-appearance-swatches" aria-hidden="true">
            <span
              v-for="(color, swatchIndex) in item.swatches"
              :key="swatchIndex"
              class="map-appearance-swatch"
              :style="{ backgroundColor: color }"
            ></span>
          </span>
        </button>
      </template>
      <p class="map-appearance-hint">{{ t('maps.appearanceMenu.remembered') }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import { useSharedMapAppearance } from '@/composables/useMapAppearance'
import { SHARED_APPEARANCE_CHOICES } from '@/maps/shared/mapAppearance'

/**
 * Shared-link pages only: lets the viewer see the map as the owner shared it, with their own profile
 * settings (when signed in), or with one of the color vision presets. Lives in the page header beside
 * the theme switcher, since both are the viewer's own display preferences.
 */
const { t } = useI18n()
const { choice, options, ownerName, appearanceFor, setChoice } = useSharedMapAppearance()

const open = ref(false)
const rootRef = ref(null)

const labelFor = (option) => {
  if (option === SHARED_APPEARANCE_CHOICES.OWNER) {
    return ownerName.value
      ? t('maps.appearanceMenu.asSharedBy', { name: ownerName.value })
      : t('maps.appearanceMenu.asShared')
  }
  if (option === SHARED_APPEARANCE_CHOICES.MINE) {
    return t('maps.appearanceMenu.mine')
  }
  return t(`profile.appearance.colorVision.schemes.${option}.name`)
}

const isPersonal = (option) => Object.values(SHARED_APPEARANCE_CHOICES).includes(option)

const items = computed(() => options.value.map((option, index, all) => {
  const appearance = appearanceFor(option)
  const bands = appearance.speedBandColors
  return {
    value: option,
    label: labelFor(option),
    // Presets follow the owner's / viewer's own rows, set apart by a divider.
    dividerBefore: !isPersonal(option) && isPersonal(all[index - 1]),
    swatches: [
      appearance.defaultPathColor,
      appearance.activePathColor,
      ...(bands ? [bands.slow, bands.medium, bands.fast] : [])
    ]
  }
}))

const select = (value) => {
  setChoice(value)
  open.value = false
}

const handleDocumentPointerDown = (event) => {
  if (open.value && rootRef.value && !rootRef.value.contains(event.target)) {
    open.value = false
  }
}

const handleKeydown = (event) => {
  if (event.key === 'Escape') {
    open.value = false
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.map-appearance-control {
  position: relative;
  display: inline-flex;
}

.map-appearance-panel {
  position: absolute;
  top: calc(100% + 0.4rem);
  right: 0;
  z-index: 1100;
  width: 17rem;
  max-width: calc(100vw - 2rem);
  padding: 0.5rem;
  border: 1px solid var(--gp-border-medium);
  border-radius: 8px;
  background: var(--gp-surface-card);
  color: var(--gp-text-primary);
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.22);
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.map-appearance-title {
  padding: 0.2rem 0.4rem 0.35rem;
  font-size: 0.8rem;
  font-weight: 700;
}

.map-appearance-divider {
  height: 1px;
  margin: 0.25rem 0;
  background: var(--gp-border-medium);
}

.map-appearance-option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.45rem 0.4rem;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font-size: 0.85rem;
  text-align: left;
  cursor: pointer;
}

.map-appearance-option:hover {
  background: var(--gp-surface-muted);
}

.map-appearance-option:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 1px;
}

/* The selected row is marked by the check icon and bold text, not by color alone. */
.map-appearance-option.selected {
  font-weight: 700;
}

.map-appearance-check {
  flex: 0 0 auto;
  font-size: 0.9rem;
}

.map-appearance-label {
  flex: 1;
  min-width: 0;
}

.map-appearance-swatches {
  display: inline-flex;
  gap: 0.15rem;
  flex: 0 0 auto;
}

.map-appearance-swatch {
  width: 0.7rem;
  height: 0.7rem;
  border-radius: 0.15rem;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.18);
}

.map-appearance-hint {
  margin: 0.35rem 0.4rem 0.1rem;
  font-size: 0.72rem;
  opacity: 0.75;
}
</style>
