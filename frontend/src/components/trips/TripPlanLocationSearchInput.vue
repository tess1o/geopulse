<template>
  <div class="trip-plan-location-search-control">
    <AutoComplete
      :id="inputId"
      v-model="localValue"
      :suggestions="suggestions"
      optionLabel="displayName"
      :placeholder="placeholder"
      :minLength="minLength"
      :delay="delay"
      :loading="loading && !showLoadingText"
      :showEmptyMessage="!loading"
      class="trip-plan-location-search"
      :class="inputClass"
      :panelClass="panelClass"
      @complete="(event) => emit('complete', event)"
      @item-select="(event) => emit('select', event?.value || null)"
    >
      <template #option="{ option }">
        <div class="trip-plan-location-option">
          <div class="trip-plan-location-option-title">{{ option.displayName }}</div>
          <div class="trip-plan-location-option-meta">
            <span
              v-if="option.groupLabel"
              class="trip-plan-location-source-chip"
              :class="option.groupLabel === 'Saved place' || option.groupLabel === 'Cached place'
                ? 'trip-plan-location-source-chip--saved'
                : 'trip-plan-location-source-chip--provider'"
            >
              {{ option.groupLabel }}
            </span>
            <span v-if="option.metaLine" class="trip-plan-location-option-subtitle">{{ option.metaLine }}</span>
          </div>
        </div>
      </template>
    </AutoComplete>

    <div v-if="loading && showLoadingText" class="trip-plan-location-search-loading" aria-live="polite">
      <i class="pi pi-spin pi-spinner trip-plan-location-search-loading-icon" />
      <span>Searching places...</span>
    </div>

    <small v-if="error" class="trip-plan-location-search-error">{{ error }}</small>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AutoComplete from 'primevue/autocomplete'

const props = defineProps({
  modelValue: {
    type: [String, Object],
    default: ''
  },
  suggestions: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: 'Search saved places or providers...'
  },
  inputId: {
    type: String,
    default: null
  },
  inputClass: {
    type: [String, Object, Array],
    default: null
  },
  panelClass: {
    type: String,
    default: 'trip-plan-location-search-panel'
  },
  minLength: {
    type: Number,
    default: 2
  },
  delay: {
    type: Number,
    default: 300
  },
  showLoadingText: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:modelValue', 'complete', 'select'])

const localValue = computed({
  get: () => props.modelValue,
  set: (value) => {
    emit('update:modelValue', value || '')
  }
})
</script>

<style scoped>
.trip-plan-location-search-control {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  width: 100%;
}

.trip-plan-location-search {
  width: 100%;
}

.trip-plan-location-search :deep(.p-autocomplete-loader) {
  display: none !important;
}

.trip-plan-location-search :deep(.p-autocomplete-input),
.trip-plan-location-search :deep(input) {
  width: 100%;
}

.trip-plan-location-search-loading {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-secondary);
  font-size: 0.78rem;
}

.trip-plan-location-search-loading-icon {
  font-size: 0.95rem;
}

.trip-plan-location-search-error {
  color: var(--gp-danger, var(--p-red-500));
  font-size: 0.8rem;
}
</style>

<style>
.trip-plan-location-search-panel .trip-plan-location-option {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.trip-plan-location-search-panel .trip-plan-location-option-title {
  font-size: 0.88rem;
  color: var(--gp-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trip-plan-location-search-panel .trip-plan-location-option-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.2rem;
  min-width: 0;
}

.trip-plan-location-search-panel .trip-plan-location-source-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid transparent;
  padding: 0.08rem 0.45rem;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.trip-plan-location-search-panel .trip-plan-location-source-chip--saved {
  color: #166534;
  background: #dcfce7;
  border-color: #86efac;
}

.trip-plan-location-search-panel .trip-plan-location-source-chip--provider {
  color: #1e3a8a;
  background: #dbeafe;
  border-color: #93c5fd;
}

.trip-plan-location-search-panel .trip-plan-location-option-subtitle {
  max-width: min(420px, 80vw);
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
