<template>
  <div class="slider-control">
    <Slider
      :modelValue="modelValue"
      @update:modelValue="updateValue"
      :min="min"
      :max="max"
      :step="step"
      class="slider"
    />
    
    <div v-if="labels?.length" class="slider-labels">
      <span v-for="(label, index) in labels" :key="index" class="label">
        {{ label }}
      </span>
    </div>
    
    <InputNumber
      :modelValue="modelValue"
      @update:modelValue="updateValue"
      :min="inputMin || min"
      :max="inputMax || max"
      :minFractionDigits="decimalPlaces"
      :maxFractionDigits="decimalPlaces"
      :suffix="suffix"
      class="number-input"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: {
    type: Number,
    required: true
  },
  min: {
    type: Number,
    required: true
  },
  max: {
    type: Number,
    required: true
  },
  step: {
    type: Number,
    default: 1
  },
  labels: {
    type: Array,
    default: () => []
  },
  suffix: {
    type: String,
    default: ''
  },
  inputMin: {
    type: Number,
    default: null
  },
  inputMax: {
    type: Number,
    default: null
  },
  decimalPlaces: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['update:modelValue'])

const updateValue = (value) => {
  emit('update:modelValue', value)
}
</script>

<style scoped>
.slider-control {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  width: 100%;
  max-width: 300px;
}

.slider {
  width: 100%;
}

.slider-labels {
  display: flex;
  justify-content: space-between;
  margin-top: -0.5rem;
}

.label {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  text-align: center;
  flex: 1;
}

.label:first-child {
  text-align: left;
}

.label:last-child {
  text-align: right;
}

.number-input {
  width: 100%;
}

/* Custom slider styling */
:deep(.p-slider) {
  background: var(--gp-border-medium);
  border-radius: var(--gp-radius-small);
  height: 6px;
}

:deep(.p-slider .p-slider-range) {
  background: var(--gp-primary);
  border-radius: var(--gp-radius-small);
}

:deep(.p-slider .p-slider-handle) {
  width: 20px;
  height: 20px;
  background: var(--gp-primary);
  border: 2px solid var(--gp-surface-white);
  border-radius: 50%;
  box-shadow: var(--gp-shadow-light);
  transition: all 0.2s ease;
}

:deep(.p-slider .p-slider-handle:hover) {
  background: var(--gp-primary-hover);
  transform: scale(1.1);
  box-shadow: var(--gp-shadow-medium);
}

/* Input number styling */
:deep(.p-inputnumber) {
  width: 100%;
}

:deep(.p-inputnumber-input) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.5rem 0.75rem;
  text-align: center;
  font-family: var(--font-mono, monospace);
  font-weight: 600;
}

:deep(.p-inputnumber-input:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

/* Responsive Design */
@media (max-width: 768px) {
  .slider-control {
    max-width: none;
    gap: 0.75rem;
  }
  
  .label {
    font-size: 0.7rem;
    line-height: 1.2;
  }
  
  :deep(.p-slider .p-slider-handle) {
    width: 24px;
    height: 24px;
  }
}

@media (max-width: 480px) {
  .slider-control {
    gap: 0.6rem;
  }
  
  .label {
    font-size: 0.65rem;
  }
  
  .slider-labels {
    margin-top: -0.3rem;
  }
  
  :deep(.p-slider) {
    height: 8px;
  }
  
  :deep(.p-slider .p-slider-handle) {
    width: 28px;
    height: 28px;
  }
  
  :deep(.p-inputnumber-input) {
    padding: 0.6rem 0.5rem;
    font-size: 0.9rem;
    min-height: 44px;
  }
}
</style>