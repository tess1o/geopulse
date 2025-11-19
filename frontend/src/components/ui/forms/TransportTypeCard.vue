<template>
  <Card :class="['transport-type-card', `type-${type}`, { 'is-disabled': !isEnabled, 'has-warnings': hasWarnings }]">
    <template #content>
      <div class="transport-card-content">
        <!-- Header Section -->
        <div class="transport-header" @click="toggleCollapse">
          <div class="transport-info">
            <div class="transport-icon-wrapper">
              <i :class="icon" class="transport-icon"></i>
            </div>
            <div class="transport-title-section">
              <h3 class="transport-title">{{ title }}</h3>
              <p v-if="subtitle" class="transport-subtitle">{{ subtitle }}</p>
            </div>
          </div>

          <div class="transport-actions">
            <!-- Enable Toggle (only for optional types) -->
            <div v-if="!mandatory" class="enable-toggle-wrapper" @click.stop>
              <span class="enable-label">{{ isEnabled ? 'Enabled' : 'Disabled' }}</span>
              <ToggleSwitch
                :model-value="isEnabled"
                @update:model-value="handleEnableToggle"
                :aria-label="`Enable ${title} detection`"
              />
            </div>

            <!-- Mandatory Badge -->
            <div v-else class="mandatory-badge">
              <i class="pi pi-lock"></i>
              <span>Always Active</span>
            </div>

            <!-- Collapse Indicator (only for collapsible cards) -->
            <button
              v-if="collapsible && !mandatory"
              class="collapse-button"
              :aria-expanded="!isCollapsed"
              :aria-label="isCollapsed ? `Expand ${title}` : `Collapse ${title}`"
            >
              <i :class="isCollapsed ? 'pi pi-chevron-down' : 'pi pi-chevron-up'"></i>
            </button>
          </div>
        </div>

        <!-- Validation Warnings -->
        <Message
          v-if="hasWarnings && !isCollapsed"
          severity="warn"
          class="validation-warning"
          :closable="false"
        >
          <div class="warning-content">
            <div v-for="(warning, index) in validationMessages" :key="index" class="warning-item">
              <i class="pi pi-exclamation-triangle"></i>
              <span>{{ warning }}</span>
            </div>
          </div>
        </Message>

        <!-- Description (when enabled and not collapsed) -->
        <div v-if="isEnabled && !isCollapsed && description" class="transport-description">
          <i class="pi pi-info-circle"></i>
          <span>{{ description }}</span>
        </div>

        <!-- Parameters Section -->
        <Transition name="collapse">
          <div v-if="isEnabled && !isCollapsed" class="transport-parameters">
            <slot name="parameters" />
          </div>
        </Transition>

        <!-- Disabled State Message -->
        <div v-if="!isEnabled && !mandatory" class="disabled-message">
          <i class="pi pi-ban"></i>
          <span>{{ title }} detection is currently disabled. Enable to configure thresholds.</span>
        </div>
      </div>
    </template>
  </Card>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  type: {
    type: String,
    required: true,
    validator: (value) => ['walk', 'bicycle', 'car', 'train', 'flight'].includes(value)
  },
  title: {
    type: String,
    required: true
  },
  subtitle: {
    type: String,
    default: null
  },
  icon: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: null
  },
  enabled: {
    type: Boolean,
    default: true
  },
  mandatory: {
    type: Boolean,
    default: false
  },
  collapsible: {
    type: Boolean,
    default: false
  },
  validationMessages: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:enabled'])

// Local state
const isCollapsed = ref(props.collapsible && !props.enabled)

// Computed
const isEnabled = computed(() => props.mandatory || props.enabled)
const hasWarnings = computed(() => props.validationMessages.length > 0)

// Methods
const handleEnableToggle = (value) => {
  emit('update:enabled', value)

  // Auto-expand when enabled, auto-collapse when disabled
  if (props.collapsible) {
    isCollapsed.value = !value
  }
}

const toggleCollapse = () => {
  if (props.collapsible && !props.mandatory && isEnabled.value) {
    isCollapsed.value = !isCollapsed.value
  }
}

// Watch for external changes to enabled state
watch(() => props.enabled, (newVal) => {
  if (props.collapsible) {
    isCollapsed.value = !newVal
  }
})
</script>

<style scoped>
.transport-type-card {
  background: var(--gp-surface-white);
  border: 2px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  transition: all 0.3s ease;
  width: 100%;
  box-sizing: border-box;
}

.transport-type-card:hover {
  box-shadow: var(--gp-shadow-medium);
}

.transport-type-card.has-warnings {
  border-color: #f59e0b;
}

.transport-type-card.is-disabled {
  opacity: 0.6;
  background: var(--gp-surface-light);
}

/* Header Section */
.transport-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding-bottom: 0.5rem;
  cursor: pointer;
  user-select: none;
}

.transport-info {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex: 1;
}

.transport-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  background: var(--gp-primary-light);
  flex-shrink: 0;
}

.transport-icon {
  font-size: 1.5rem;
  color: var(--gp-primary);
}

/* Type-specific icon colors */
.type-walk .transport-icon-wrapper {
  background: #dbeafe;
}
.type-walk .transport-icon {
  color: #3b82f6;
}

.type-bicycle .transport-icon-wrapper {
  background: #d1fae5;
}
.type-bicycle .transport-icon {
  color: #10b981;
}

.type-car .transport-icon-wrapper {
  background: #fee2e2;
}
.type-car .transport-icon {
  color: #ef4444;
}

.type-train .transport-icon-wrapper {
  background: #e0e7ff;
}
.type-train .transport-icon {
  color: #6366f1;
}

.type-flight .transport-icon-wrapper {
  background: #fef3c7;
}
.type-flight .transport-icon {
  color: #f59e0b;
}

.transport-title-section {
  flex: 1;
}

.transport-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

.transport-subtitle {
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
  margin: 0.25rem 0 0 0;
}

/* Actions Section */
.transport-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-shrink: 0;
}

.enable-toggle-wrapper {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.enable-label {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--gp-text-secondary);
}

.mandatory-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-medium);
  border-radius: var(--gp-radius-medium);
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--gp-text-secondary);
}

.mandatory-badge i {
  font-size: 0.75rem;
}

.collapse-button {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.5rem;
  color: var(--gp-text-secondary);
  transition: color 0.2s ease;
}

.collapse-button:hover {
  color: var(--gp-text-primary);
}

.collapse-button i {
  font-size: 1rem;
}

/* Description */
.transport-description {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-left: 3px solid var(--gp-primary);
  border-radius: var(--gp-radius-small);
  margin-top: 1rem;
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  line-height: 1.5;
}

.transport-description i {
  color: var(--gp-primary);
  flex-shrink: 0;
  margin-top: 0.2rem;
}

/* Validation Warnings */
.validation-warning {
  margin-top: 1rem;
}

.warning-content {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.warning-item {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  font-size: 0.9rem;
}

.warning-item i {
  flex-shrink: 0;
  margin-top: 0.2rem;
}

/* Parameters Section */
.transport-parameters {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--gp-border-light);
}

/* Disabled Message */
.disabled-message {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-small);
  margin-top: 1rem;
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  font-style: italic;
}

.disabled-message i {
  color: var(--gp-text-tertiary);
  flex-shrink: 0;
}

/* Collapse Animation */
.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}

.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  max-height: 0;
  margin-top: 0;
  padding-top: 0;
}

.collapse-enter-to,
.collapse-leave-from {
  opacity: 1;
  max-height: 1000px;
}

/* Responsive Design */
@media (max-width: 768px) {
  .transport-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
  }

  .transport-actions {
    width: 100%;
    justify-content: space-between;
  }

  .transport-icon-wrapper {
    width: 2.5rem;
    height: 2.5rem;
  }

  .transport-icon {
    font-size: 1.25rem;
  }

  .transport-title {
    font-size: 1.1rem;
  }
}

@media (max-width: 480px) {
  .transport-info {
    gap: 0.75rem;
  }

  .transport-icon-wrapper {
    width: 2rem;
    height: 2rem;
  }

  .transport-icon {
    font-size: 1rem;
  }

  .transport-title {
    font-size: 1rem;
  }

  .enable-toggle-wrapper {
    flex-direction: column;
    align-items: flex-end;
    gap: 0.5rem;
  }

  .mandatory-badge {
    font-size: 0.75rem;
    padding: 0.4rem 0.8rem;
  }
}
</style>
