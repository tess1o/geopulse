<template>
  <div
    class="setting-card"
    :id="settingId ? `setting-${settingId}` : undefined"
    :data-setting-id="settingId || undefined"
  >
    <div class="setting-layout">
      <div class="setting-info">
        <div class="setting-title-row">
          <h3 class="setting-title">{{ title }}</h3>
          <button
            v-if="detailsText"
            type="button"
            class="setting-help"
            :aria-label="`${title}: ${detailsText}`"
            v-tooltip.top="{ value: detailsText, class: 'setting-help-tooltip', fitContent: false }"
          >
            <i class="pi pi-info-circle" aria-hidden="true" />
          </button>
        </div>
        <p class="setting-description">{{ description }}</p>
      </div>

      <div class="setting-control">
        <slot name="control" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    required: true
  },
  details: {
    type: [String, Object],
    default: null
  },
  settingId: {
    type: String,
    default: ''
  }
})

const detailsText = computed(() => {
  if (!props.details) return ''
  if (typeof props.details === 'string') return props.details

  return Object.entries(props.details)
    .map(([label, value]) => `${label}: ${value}`)
    .join('\n')
})
</script>

<style scoped>
.setting-card {
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--gp-border-light);
  width: 100%;
  box-sizing: border-box;
}

.setting-card:last-child {
  border-bottom: 0;
}

.setting-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(16rem, 28rem);
  gap: var(--gp-spacing-lg);
  align-items: center;
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
}

.setting-info {
  min-width: 0;
}

.setting-title-row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.setting-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
  line-height: 1.35;
}

.setting-description {
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
  margin: 0.3rem 0 0;
  line-height: 1.4;
}

.setting-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 1.75rem;
  height: 1.75rem;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--gp-text-secondary);
  cursor: help;
}

.setting-help:hover,
.setting-help:focus-visible {
  color: var(--gp-primary);
  background: var(--gp-surface-light);
  outline: none;
}

.setting-help i {
  font-size: 0.85rem;
}

.setting-control {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  width: min(100%, 28rem);
  min-width: 0;
  justify-self: end;
  box-sizing: border-box;
}

.setting-control > * {
  min-width: 0;
  max-width: 100%;
}

:global(.setting-help-tooltip .p-tooltip-text) {
  width: max-content;
  max-width: min(22rem, calc(100vw - 2rem));
  white-space: pre-line;
  line-height: 1.4;
}

@media (max-width: 768px) {
  .setting-layout {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-md);
    padding: var(--gp-spacing-md);
  }

  .setting-control {
    width: 100%;
    justify-content: flex-end;
    justify-self: auto;
  }
}

@media (max-width: 480px) {
  .setting-title {
    font-size: 0.95rem;
  }

  .setting-description {
    font-size: 0.8rem;
    overflow-wrap: break-word;
    line-height: 1.3;
  }
}
</style>
