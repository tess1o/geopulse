<template>
  <div class="date-separator-nav">
    <button
      v-if="showNav"
      type="button"
      class="date-nav-button"
      @click.stop="navigateDay(-1)"
      aria-label="Previous day"
      title="Previous day"
    >
      <i class="pi pi-chevron-left"></i>
    </button>
    <div
      class="date-separator-text"
      :class="{ 'date-separator-text--swipeable': showNav }"
      @touchstart.passive="onTouchStart"
      @touchend.passive="onTouchEnd"
    >{{ label }}</div>
    <button
      v-if="showNav"
      type="button"
      class="date-nav-button"
      @click.stop="navigateDay(1)"
      aria-label="Next day"
      title="Next day"
    >
      <i class="pi pi-chevron-right"></i>
    </button>
  </div>
</template>

<script setup>
import { useTimezone } from '@/composables/useTimezone'

const props = defineProps({
  date: {
    type: String,
    required: true
  },
  label: {
    type: String,
    default: ''
  },
  showNav: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['navigate-date'])

const timezone = useTimezone()

// Navigate to the previous (offset = -1) or next (offset = 1) day
const navigateDay = (offset) => {
  const targetDate = timezone.add(timezone.create(props.date), offset, 'day').format('YYYY-MM-DD')
  emit('navigate-date', targetDate)
}

// Swipe handling: swipe left -> previous day, swipe right -> next day
const SWIPE_THRESHOLD = 40;
const touchStart = { x: 0, y: 0 };

const onTouchStart = (event) => {
  const touch = event.changedTouches[0];
  touchStart.x = touch.clientX;
  touchStart.y = touch.clientY;
}

const onTouchEnd = (event) => {
  if (!props.showNav)
   return;

  const touch = event.changedTouches[0];
  const deltaX = touch.clientX - touchStart.x;
  const deltaY = touch.clientY - touchStart.y;

  // Require a mostly-horizontal swipe past the threshold
  if (Math.abs(deltaX) < SWIPE_THRESHOLD || Math.abs(deltaX) <= Math.abs(deltaY)) {
    return;
  }

  navigateDay(deltaX < 0 ? -1 : 1); 
}
</script>

<style scoped>
.date-separator-nav {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.date-separator-text {
  color: var(--gp-text-secondary);
  font-weight: 600;
  font-size: 0.9rem;
  padding: 0 var(--gp-spacing-sm);
  background: var(--gp-surface-white);
  white-space: nowrap;
}

.date-separator-text--swipeable {
  touch-action: pan-y;
  user-select: none;
}

.date-nav-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.5rem;
  height: 1.5rem;
  padding: 0;
  border: 1px solid var(--gp-border-medium);
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-white);
  color: var(--gp-text-secondary);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.date-nav-button:hover {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  color: #fff;
}

.date-nav-button:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

.date-nav-button i {
  font-size: 0.7rem;
}

.p-dark .date-nav-button {
  border-color: var(--gp-border-dark);
  background: var(--gp-surface-white);
}

/* Mobile optimizations */
@media (max-width: 768px) {
  .date-separator-text {
    font-size: 0.8rem;
    padding: 0 var(--gp-spacing-xs);
  }
}
</style>
