<template>
  <div class="detail-item">
    <span class="detail-label">{{ label }}</span>
    <span v-if="!$slots.value" class="detail-value">{{ value }}</span>
    <div v-else class="detail-value">
      <slot name="value" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    required: true
  },
  value: {
    type: [String, Number],
    default: null
  }
})
</script>

<style scoped>
.detail-item {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm) 0;
}

.detail-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  min-width: 120px;
  flex-shrink: 0;
  font-size: 0.9rem;
}

.detail-value {
  color: var(--gp-text-primary);
  flex: 1;
  font-size: 0.9rem;
  word-break: break-word;
}

/* Dark Mode */
.p-dark .detail-label {
  color: var(--gp-text-secondary);
}

.p-dark .detail-value {
  color: var(--gp-text-primary);
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .detail-item {
    flex-direction: column;
    gap: var(--gp-spacing-xs);
    align-items: flex-start;
  }

  .detail-label {
    min-width: auto;
    font-size: 0.875rem;
  }

  .detail-value {
    font-size: 0.875rem;
  }
}
</style>
