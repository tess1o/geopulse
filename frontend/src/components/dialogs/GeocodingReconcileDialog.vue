<template>
  <Dialog
    :visible="visible"
    :header="dialogTitle"
    :modal="true"
    :closable="true"
    @update:visible="$emit('close')"
    :style="{ width: '600px' }"
  >
    <div class="dialog-content">
      <!-- Provider Selection -->
      <div class="form-field">
        <label for="provider" class="field-label">
          Select Provider <span class="required">*</span>
        </label>
        <Select
          id="provider"
          v-model="selectedProvider"
          :options="providerOptions"
          optionLabel="displayName"
          optionValue="name"
          placeholder="Choose a geocoding provider"
          class="field-input"
        >
          <template #option="slotProps">
            <div class="provider-option">
              <span class="provider-name">{{ slotProps.option.displayName }}</span>
              <Tag
                v-if="slotProps.option.isPrimary"
                value="Primary"
                severity="success"
                size="small"
              />
              <Tag
                v-else-if="slotProps.option.isFallback"
                value="Fallback"
                severity="secondary"
                size="small"
              />
            </div>
          </template>
        </Select>
        <small class="field-hint">The provider will be used to fetch new geocoding data</small>
      </div>

      <!-- Reconciliation Info -->
      <div class="info-section">
        <div class="info-header">
          <i class="pi pi-info-circle info-icon"></i>
          <span class="info-title">Reconciliation Details</span>
        </div>
        <div class="info-row">
          <span class="info-label">Results to reconcile:</span>
          <span class="info-value">
            {{ reconcileCount }}
            <span v-if="reconcileMode === 'all'" class="scope-badge">
              {{ hasActiveFilters ? '(filtered)' : '(all)' }}
            </span>
          </span>
        </div>
        <div v-if="reconcileMode === 'all' && hasActiveFilters" class="info-row">
          <span class="info-label">Active filters:</span>
          <span class="info-value">
            <span v-if="currentFilters.provider">Provider: {{ currentFilters.provider }}</span>
            <span v-if="currentFilters.provider && currentFilters.searchText"> • </span>
            <span v-if="currentFilters.searchText">Search: "{{ currentFilters.searchText }}"</span>
          </span>
        </div>
        <div class="info-row">
          <span class="info-label">Action:</span>
          <span class="info-value">Fetch fresh data from selected provider</span>
        </div>
      </div>

      <!-- Warning Messages -->
      <Message severity="warn" :closable="false" class="warning-message">
        <template #messageicon>
          <i class="pi pi-exclamation-triangle"></i>
        </template>
        <div class="warning-content">
          <strong>Important:</strong>
          <ul>
            <li>This will update the display name, city, and country fields</li>
            <li>Changes will be synchronized across all timeline stays</li>
            <li>If reconciliation fails, original data will be kept</li>
            <li>This operation cannot be undone</li>
          </ul>
        </div>
      </Message>

      <!-- Progress (shown during reconciliation) -->
      <div v-if="reconciling" class="progress-section">
        <ProgressBar mode="indeterminate" class="progress-bar" />
        <p class="progress-text">Reconciling geocoding results, please wait...</p>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <Button
          label="Cancel"
          severity="secondary"
          @click="$emit('close')"
          :disabled="reconciling"
        />
        <Button
          label="Reconcile"
          severity="primary"
          icon="pi pi-refresh"
          @click="handleReconcile"
          :loading="reconciling"
          :disabled="!selectedProvider"
        />
      </div>
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import Dialog from 'primevue/dialog'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'

const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  },
  selectedResults: {
    type: Array,
    default: () => []
  },
  enabledProviders: {
    type: Array,
    default: () => []
  },
  reconcileMode: {
    type: String,
    default: 'selected',
    validator: (value) => ['selected', 'all'].includes(value)
  },
  totalRecords: {
    type: Number,
    default: 0
  },
  currentFilters: {
    type: Object,
    default: () => ({ provider: null, searchText: '' })
  }
})

const emit = defineEmits(['close', 'reconcile'])

const reconciling = ref(false)
const selectedProvider = ref(null)

const dialogTitle = computed(() => {
  if (props.reconcileMode === 'all') {
    const hasFilters = props.currentFilters.provider || props.currentFilters.searchText
    if (hasFilters) {
      return `Reconcile ${props.totalRecords} Filtered Results`
    }
    return `Reconcile All ${props.totalRecords} Results`
  } else {
    const count = props.selectedResults.length
    return count === 1
      ? 'Reconcile Geocoding Result'
      : `Reconcile ${count} Selected Results`
  }
})

const reconcileCount = computed(() => {
  return props.reconcileMode === 'all' ? props.totalRecords : props.selectedResults.length
})

const hasActiveFilters = computed(() => {
  return props.currentFilters.provider || (props.currentFilters.searchText && props.currentFilters.searchText.trim() !== '')
})

const providerOptions = computed(() => {
  return props.enabledProviders.filter(p => p.enabled)
})

const handleReconcile = async () => {
  if (!selectedProvider.value) return

  reconciling.value = true
  try {
    const request = {
      providerName: selectedProvider.value,
      reconcileAll: props.reconcileMode === 'all'
    }

    if (props.reconcileMode === 'all') {
      // Reconcile all mode
      request.geocodingIds = []

      // Add filter if active
      if (props.currentFilters.provider) {
        request.filterByProvider = props.currentFilters.provider
      }
    } else {
      // Reconcile selected mode
      request.geocodingIds = props.selectedResults.map(r => r.id)
    }

    emit('reconcile', request)
  } finally {
    reconciling.value = false
  }
}
</script>

<style scoped>
.dialog-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-lg);
  padding: var(--gp-spacing-md) 0;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.field-label {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--gp-text-primary);
}

.required {
  color: var(--p-red-500);
}

.field-input {
  width: 100%;
}

.field-hint {
  font-size: 0.85rem;
  color: var(--gp-text-muted);
  font-style: italic;
}

.provider-option {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.provider-name {
  flex: 1;
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-md);
  background-color: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  border-left: 3px solid var(--gp-primary);
}

.info-header {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  margin-bottom: var(--gp-spacing-xs);
}

.info-icon {
  color: var(--gp-primary);
  font-size: 1.1rem;
}

.info-title {
  font-weight: 600;
  font-size: 0.95rem;
  color: var(--gp-text-primary);
}

.info-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
}

.info-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  min-width: 150px;
}

.info-value {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.scope-badge {
  font-weight: 400;
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
  margin-left: var(--gp-spacing-xs);
}

.warning-message {
  margin-top: var(--gp-spacing-sm);
}

.warning-content {
  font-size: 0.9rem;
}

.warning-content ul {
  margin: var(--gp-spacing-xs) 0 0 var(--gp-spacing-md);
  padding-left: var(--gp-spacing-md);
}

.warning-content li {
  margin-bottom: var(--gp-spacing-xs);
}

.progress-section {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-md);
  background-color: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.progress-bar {
  height: 8px;
}

.progress-text {
  text-align: center;
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--gp-spacing-md);
}

/* Dark Mode */
.p-dark .info-section,
.p-dark .progress-section {
  background-color: var(--gp-surface-darker);
}
</style>
