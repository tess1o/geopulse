<template>
  <header class="location-details-header">
    <div class="identity-row">
      <Button
        :label="backLabel"
        icon="pi pi-arrow-left"
        text
        class="back-button"
        :aria-label="backLabel"
        @click="$emit('back')"
      />
      <div class="identity-icon" aria-hidden="true">
        <slot name="icon"><i :class="icon" /></slot>
      </div>
      <div class="identity-copy">
        <h1>{{ title }}</h1>
        <p v-if="subtitle">{{ subtitle }}</p>
        <div v-if="$slots.metadata" class="identity-metadata">
          <slot name="metadata" />
        </div>
      </div>
      <div v-if="$slots.actions" class="identity-actions">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<script setup>
import Button from 'primevue/button'

defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  icon: { type: String, default: 'pi pi-map-marker' },
  backLabel: { type: String, default: 'Back to Location Analytics' }
})

defineEmits(['back'])
</script>

<style scoped>
.location-details-header {
  margin-bottom: var(--gp-spacing-lg);
  padding: var(--gp-spacing-md);
  border: 1px solid color-mix(in srgb, var(--gp-primary) 24%, var(--gp-border-light));
  border-radius: var(--gp-radius-large);
  background: linear-gradient(125deg, color-mix(in srgb, var(--gp-primary) 10%, var(--gp-surface-white)), var(--gp-surface-white) 62%);
  box-shadow: var(--gp-shadow-card);
}

.back-button {
  white-space: nowrap;
}

.identity-row {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--gp-spacing-md);
  min-width: 0;
}

.identity-icon {
  display: grid;
  place-items: center;
  width: 2.75rem;
  height: 2.75rem;
  border-radius: var(--gp-radius-medium);
  background: var(--gp-primary);
  color: white;
  font-size: 1.25rem;
}

.identity-copy {
  flex: 1;
  min-width: 0;
}

.identity-copy h1 {
  margin: 0;
  color: var(--gp-text-primary);
  font-size: clamp(1.35rem, 2vw, 1.75rem);
  line-height: 1.12;
  overflow-wrap: anywhere;
}

.identity-copy p {
  margin: .15rem 0 0;
  color: var(--gp-text-secondary);
  font-size: .875rem;
}

.identity-metadata {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--gp-spacing-xs) var(--gp-spacing-sm);
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-secondary);
  font-size: .8rem;
}

.identity-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--gp-spacing-sm);
}

.p-dark .location-details-header {
  background: linear-gradient(125deg, color-mix(in srgb, var(--gp-primary) 18%, var(--gp-surface-dark)), var(--gp-surface-dark) 66%);
  border-color: color-mix(in srgb, var(--gp-primary) 36%, var(--gp-border-dark));
}

@media (max-width: 640px) {
  .location-details-header {
    padding: var(--gp-spacing-sm);
  }

  .identity-row {
    grid-template-columns: auto auto minmax(0, 1fr);
    gap: var(--gp-spacing-sm);
  }

  .identity-copy h1 {
    font-size: 1.45rem;
  }

  .identity-actions {
    grid-column: 2 / -1;
    justify-content: flex-start;
  }
}

@media (prefers-reduced-motion: reduce) {
  * { transition: none !important; }
}
</style>
