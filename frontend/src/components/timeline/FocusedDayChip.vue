<template>
  <!-- Visible way back from a focused day: on the map (desktop) and on the timeline sheet handle (mobile). -->
  <button
    v-if="focusedDay"
    type="button"
    class="focused-day-chip"
    :title="t('timeline.focusedDay.showAll')"
    :aria-label="`${t('timeline.focusedDay.showAll')} (${label})`"
    @click.stop="highlightStore.clearFocusedDay()"
  >
    <span
      v-if="dayColors?.[focusedDay]"
      class="focused-day-chip-color"
      :style="{ backgroundColor: dayColors[focusedDay] }"
      aria-hidden="true"
    ></span>
    <span class="focused-day-chip-label">{{ label }}</span>
    <i class="pi pi-times focused-day-chip-icon" aria-hidden="true"></i>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useHighlightStore } from '@/stores/highlight'
import { useTimezone } from '@/composables/useTimezone'

defineProps({
  // { 'YYYY-MM-DD': color } while the path is colored by day.
  dayColors: {
    type: Object,
    default: null
  }
})

const { t } = useI18n()
const highlightStore = useHighlightStore()
const timezone = useTimezone()

const focusedDay = computed(() => highlightStore.focusedDay)
// Day keys are calendar dates in the user's timezone, labeled like the map legend.
const label = computed(() => (
  focusedDay.value ? t('timeline.focusedDay.showing', { day: timezone.create(focusedDay.value).format('ddd D') }) : ''
))
</script>

<style scoped>
.focused-day-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 2rem;
  padding: 0.25rem 0.6rem;
  border: 1px solid var(--gp-primary);
  border-radius: 999px;
  background: var(--gp-surface-card);
  color: var(--gp-text-primary);
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}

.focused-day-chip:hover {
  background: var(--gp-surface-muted);
}

.focused-day-chip:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

.focused-day-chip-color {
  display: inline-block;
  width: 1.1rem;
  height: 0.35rem;
  border-radius: 999px;
  flex-shrink: 0;
}

.focused-day-chip-icon {
  font-size: 0.7rem;
  color: var(--gp-text-secondary);
}
</style>
