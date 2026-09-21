<template>
  <Select
    :options="options"
    optionLabel="label"
    optionValue="value"
    :placeholder="placeholder"
    v-model="proxyValue"
    @change="$emit('change', $event)"
    class="preset-select"
    :overlayStyle="presetSelectOverlayStyle"
    v-bind="groupProps"
  >
    <template #value="slotProps">
      <span v-if="!selectedPresetOption" class="preset-selected-placeholder">
        {{ slotProps.placeholder || placeholder }}
      </span>
      <span
        v-else
        class="preset-selected-value"
        :class="{ 'preset-selected-value--timeline-label': isPeriodPresetOption(selectedPresetOption) }"
        :style="getPresetOptionStyle(selectedPresetOption)"
      >
        <span class="preset-selected-value-label">{{ selectedPresetOption.label }}</span>
      </span>
    </template>

    <template #optiongroup="slotProps">
      <div class="preset-option-group-header">
        <span class="preset-option-group-label">{{ slotProps.option.label }}</span>
        <span class="preset-option-group-divider" aria-hidden="true"></span>
      </div>
    </template>

    <template #option="slotProps">
      <div
        class="preset-option-row"
        :class="{ 'preset-option-row--timeline-label': isPeriodPresetOption(slotProps.option) }"
        :style="getPresetOptionStyle(slotProps.option)"
      >
        <div class="preset-option-main">
          <span class="preset-option-label">{{ slotProps.option.nameLabel || slotProps.option.label }}</span>
          <span
            v-if="slotProps.option.kind === 'timeline-label' && slotProps.option.dateLabel"
            class="preset-option-meta"
          >
            {{ slotProps.option.dateLabel }}
          </span>
        </div>
      </div>
    </template>
  </Select>
</template>

<script setup>
import { computed } from 'vue'
import Select from 'primevue/select'

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: null
  },
  options: {
    type: Array,
    default: () => []
  },
  placeholder: {
    type: String,
    default: 'Select Preset'
  },
  groupProps: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const timelineLabelTintStrength = 12
const presetSelectOverlayStyle = {
  width: 'min(16rem, calc(100vw - 1rem))',
  maxWidth: 'calc(100vw - 1rem)'
}

const proxyValue = computed({
  get: () => props.modelValue,
  set: (value) => {
    // Emit through v-model so parent keeps current selection state.
    emit('update:modelValue', value)
  }
})

function flattenOptions(options, groupProps) {
  if (!Array.isArray(options)) return []

  const childrenKey = groupProps?.optionGroupChildren
  if (!childrenKey) return options

  return options.flatMap((group) => {
    const items = group?.[childrenKey]
    return Array.isArray(items) ? items : []
  })
}

const selectedPresetOption = computed(() => {
  return flattenOptions(props.options, props.groupProps)
    .find((option) => option?.value === props.modelValue) || null
})

function isPeriodPresetOption(option) {
  return option?.kind === 'timeline-label'
}

function getPresetOptionStyle(option) {
  if (!isPeriodPresetOption(option)) return null

  const color = option?.color || '#3b82f6'
  return {
    '--timeline-label-preset-color': color,
    '--timeline-label-preset-tint-strength': `${timelineLabelTintStrength}%`
  }
}
</script>

<style scoped>
.preset-select {
  width: 100%;
}

.preset-selected-placeholder {
  color: var(--gp-text-secondary);
}

.preset-selected-value {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  border-radius: 8px;
  padding: 0.25rem 0.5rem;
}

.preset-selected-value--timeline-label {
  border-left: 3px solid var(--timeline-label-preset-color);
  background: color-mix(in srgb, var(--timeline-label-preset-color) var(--timeline-label-preset-tint-strength), white);
  padding-left: calc(0.5rem - 3px);
}

.preset-selected-value-label {
  display: block;
  min-width: 0;
  width: 100%;
  color: var(--gp-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.preset-option-group-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.25rem 0.15rem;
}

.preset-option-group-label {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.preset-option-group-divider {
  flex: 1;
  height: 1px;
  background: var(--gp-border-light);
}

.preset-option-row {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 15.5rem;
  border-radius: 8px;
  padding: 0.35rem 0.5rem;
}

.preset-option-row--timeline-label {
  border-left: 3px solid var(--timeline-label-preset-color);
  background: color-mix(in srgb, var(--timeline-label-preset-color) var(--timeline-label-preset-tint-strength), white);
  padding-left: calc(0.5rem - 3px);
}

.preset-option-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  width: 100%;
}

.preset-option-label {
  color: var(--gp-text-primary);
  font-size: 0.9rem;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.preset-option-meta {
  color: var(--gp-text-secondary);
  font-size: 0.75rem;
  line-height: 1.15;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.p-dark .preset-option-group-label {
  color: var(--gp-text-muted);
}

.p-dark .preset-option-group-divider {
  background: var(--gp-border-dark);
}

.p-dark .preset-option-row--timeline-label {
  background: color-mix(in srgb, var(--timeline-label-preset-color) 16%, var(--gp-surface-dark));
}

.p-dark .preset-selected-value--timeline-label {
  background: color-mix(in srgb, var(--timeline-label-preset-color) 16%, var(--gp-surface-dark));
}
</style>
