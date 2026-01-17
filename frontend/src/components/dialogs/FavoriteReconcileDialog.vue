<template>
  <Dialog
    :visible="visible"
    :header="dialogTitle"
    :modal="true"
    :closable="true"
    @update:visible="$emit('close')"
    class="gp-dialog-md"
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
          <span class="info-label">Favorites to reconcile:</span>
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
            <span v-if="currentFilters.type">Type: {{ currentFilters.type }}</span>
            <span v-if="currentFilters.type && currentFilters.searchText"> • </span>
            <span v-if="currentFilters.searchText">Search: "{{ currentFilters.searchText }}"</span>
          </span>
        </div>
        <div class="info-row">
          <span class="info-label">Action:</span>
          <span class="info-value">Update city and country from provider</span>
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
            <li>This will update the city and country fields only</li>
            <li>Name and location coordinates will remain unchanged</li>
            <li>For areas, the center point will be used for geocoding</li>
            <li>If reconciliation fails, original data will be kept</li>
            <li>This operation cannot be undone</li>
          </ul>
        </div>
      </Message>

      <!-- Progress (shown during reconciliation) -->
      <div v-if="showProgress" class="progress-section">
        <div class="progress-header">
          <span class="progress-label">Reconciliation Progress</span>
          <span class="progress-percentage">{{ jobProgress.progressPercentage }}%</span>
        </div>

        <ProgressBar
          :value="jobProgress.progressPercentage"
          :showValue="false"
          class="progress-bar"
          :class="{ 'progress-complete': isComplete }"
        />

        <div class="progress-details">
          <div class="detail-row">
            <span class="detail-label">Processed:</span>
            <span class="detail-value">{{ jobProgress.processedItems }} / {{ jobProgress.totalItems }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">Successful:</span>
            <span class="detail-value success-count">{{ jobProgress.successCount }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">Errors:</span>
            <span class="detail-value" :class="{ 'failed-count': jobProgress.failedCount > 0 }">
              {{ jobProgress.failedCount }}
            </span>
          </div>
        </div>

        <div v-if="isComplete && jobProgress.failedCount === 0" class="completion-message">
          <i class="pi pi-check-circle"></i>
          <span>Reconciliation completed successfully!</span>
        </div>

        <div v-if="isComplete && jobProgress.failedCount > 0" class="warning-message-box">
          <i class="pi pi-exclamation-triangle"></i>
          <span>
            Completed with {{ jobProgress.failedCount }} error{{ jobProgress.failedCount !== 1 ? 's' : '' }}.
            {{ jobProgress.successCount }} item{{ jobProgress.successCount !== 1 ? 's' : '' }} reconciled successfully.
          </span>
        </div>

        <div v-if="jobProgress.status === 'FAILED'" class="error-message">
          <i class="pi pi-times-circle"></i>
          <div>
            <strong>Reconciliation job failed</strong>
            <div class="error-details">{{ jobProgress.errorMessage || 'An unexpected error occurred' }}</div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <Button
          label="Cancel"
          severity="secondary"
          @click="handleClose"
          :disabled="showProgress && !isComplete"
        />
        <Button
          v-if="!showProgress"
          label="Reconcile"
          severity="primary"
          icon="pi pi-refresh"
          @click="handleReconcile"
          :disabled="!selectedProvider"
        />
      </div>
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
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
    default: () => ({ type: null, searchText: '' })
  },
  jobProgress: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close', 'reconcile', 'reconcile-complete'])

const selectedProvider = ref(null)

const dialogTitle = computed(() => {
  if (props.reconcileMode === 'all') {
    const hasFilters = props.currentFilters.type || props.currentFilters.searchText
    if (hasFilters) {
      return `Reconcile ${props.totalRecords} Filtered Favorites`
    }
    return `Reconcile All ${props.totalRecords} Favorites`
  } else {
    const count = props.selectedResults.length
    return count === 1
      ? 'Reconcile Favorite Location'
      : `Reconcile ${count} Selected Favorites`
  }
})

const reconcileCount = computed(() => {
  return props.reconcileMode === 'all' ? props.totalRecords : props.selectedResults.length
})

const hasActiveFilters = computed(() => {
  return props.currentFilters.type || (props.currentFilters.searchText && props.currentFilters.searchText.trim() !== '')
})

const providerOptions = computed(() => {
  return props.enabledProviders.filter(p => p.enabled)
})

const showProgress = computed(() => {
  return props.jobProgress !== null
})

const isComplete = computed(() => {
  return props.jobProgress?.status === 'COMPLETED'
})

const handleReconcile = async () => {
  if (!selectedProvider.value) return

  const request = {
    providerName: selectedProvider.value,
    reconcileAll: props.reconcileMode === 'all'
  }

  if (props.reconcileMode === 'all') {
    // Reconcile all mode
    request.favoriteIds = []

    // Add filters if active
    if (props.currentFilters.type) {
      request.filterByType = props.currentFilters.type
    }
    if (props.currentFilters.searchText) {
      request.filterBySearchText = props.currentFilters.searchText
    }
  } else {
    // Reconcile selected mode
    request.favoriteIds = props.selectedResults.map(r => r.id)
  }

  emit('reconcile', request)
}

const handleClose = () => {
  if (isComplete.value) {
    emit('reconcile-complete')
  }
  emit('close')
}

// Watch for job completion to auto-close
watch(() => props.jobProgress?.status, (status) => {
  if (status === 'COMPLETED') {
    setTimeout(() => {
      handleClose()
    }, 2000) // Auto-close 2 seconds after completion
  }
})
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
  min-width: 180px;
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
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-md);
  background-color: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.progress-label {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.progress-percentage {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--gp-primary);
}

.progress-bar {
  height: 8px;
}

.progress-bar.progress-complete :deep(.p-progressbar-value) {
  background: var(--green-500);
}

.progress-details {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.9rem;
}

.detail-label {
  color: var(--gp-text-secondary);
}

.detail-value {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.success-count {
  color: var(--green-600);
}

.failed-count {
  color: var(--red-600);
}

.completion-message {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm);
  background: var(--green-50);
  border: 1px solid var(--green-200);
  border-radius: var(--gp-radius-small);
  color: var(--green-700);
  font-weight: 600;
}

.completion-message i {
  font-size: 1.2rem;
  color: var(--green-600);
}

.warning-message-box {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm);
  background: var(--yellow-50);
  border: 1px solid var(--yellow-300);
  border-radius: var(--gp-radius-small);
  color: var(--yellow-900);
  font-weight: 600;
}

.warning-message-box i {
  font-size: 1.2rem;
  color: var(--yellow-600);
}

.error-message {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm);
  background: var(--red-50);
  border: 1px solid var(--red-200);
  border-radius: var(--gp-radius-small);
  color: var(--red-700);
  font-weight: 600;
}

.error-message i {
  font-size: 1.2rem;
  color: var(--red-600);
  margin-top: 2px;
}

.error-details {
  font-size: 0.85rem;
  font-weight: 400;
  margin-top: var(--gp-spacing-xs);
  color: var(--red-600);
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
