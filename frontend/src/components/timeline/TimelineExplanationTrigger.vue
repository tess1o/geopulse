<template>
  <button
    type="button"
    class="explanation-trigger"
    :aria-label="label"
    v-tooltip.top="label"
    @click.stop="emit('show-explanation')"
  >
    <i class="pi pi-info-circle" aria-hidden="true" />
  </button>
</template>

<script setup>
// Opens the explanation dialog of a stay, trip or data gap: its GPS points on a map and how the timeline decided it.
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  kind: {
    type: String,
    required: true,
    validator: value => ['stay', 'trip', 'dataGap'].includes(value)
  }
})

const emit = defineEmits(['show-explanation'])

const { t } = useI18n()

const label = computed(() => t(`timeline.explanation.trigger.${props.kind}`))
</script>

<style scoped>
.explanation-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  /* Takes 16px in the layout, under the timestamp's line height, so the icon doesn't make the title row taller */
  margin: -4px 0;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: none;
  color: var(--gp-text-secondary);
  cursor: pointer;
}

.explanation-trigger:hover,
.explanation-trigger:focus-visible {
  background: var(--gp-surface-muted);
  color: var(--gp-primary);
}

.explanation-trigger i {
  font-size: 0.9rem;
}
</style>
