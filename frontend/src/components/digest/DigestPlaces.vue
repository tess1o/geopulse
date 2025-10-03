<template>
  <div class="digest-places">
    <h3 class="places-title">
      <i class="pi pi-map-marker"></i>
      Top Places
    </h3>

    <div class="places-list" v-if="places && places.length > 0">
      <div
        v-for="(place, index) in displayedPlaces"
        :key="index"
        class="place-item"
      >
        <div class="place-rank">{{ index + 1 }}</div>
        <div class="place-info">
          <div class="place-name">{{ place.name }}</div>
          <div class="place-stats">{{ place.visits }} visits</div>
        </div>
      </div>
    </div>

    <div class="no-places-placeholder" v-else>
      <i class="pi pi-compass"></i>
      <p>No places visited during this period.</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  places: {
    type: Array,
    default: () => []
  },
  limit: {
    type: Number,
    default: 5
  }
});

const displayedPlaces = computed(() => {
  return props.places.slice(0, props.limit)
})
</script>

<style scoped>
.digest-places {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xl);
  margin-bottom: var(--gp-spacing-xl);
  min-height: 00px;
}

.places-title {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-lg);
}

.places-title i {
  color: var(--gp-error);
}

.places-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.place-item {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-md);
  transition: all 0.2s ease;
}

.place-item:hover {
  background: var(--gp-timeline-blue);
  border-color: var(--gp-primary);
}

.place-rank {
  width: 32px;
  height: 32px;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.875rem;
  flex-shrink: 0;
}

.place-info {
  flex: 1;
  word-break: break-all;
}

.place-name {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
  white-space: break-spaces;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 250px;
}

.place-stats {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
}

.no-places-placeholder {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: var(--gp-spacing-xl);
  color: var(--gp-text-muted);
  font-style: italic;
}

.no-places-placeholder i {
  font-size: 2rem;
  opacity: 0.5;
  margin-bottom: var(--gp-spacing-md);
}

/* Dark Mode */
.p-dark .digest-places {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .place-item {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .place-item:hover {
  background: rgba(30, 64, 175, 0.2);
}

.p-dark .places-title {
  color: var(--gp-text-primary);
}

/* Responsive */
@media (max-width: 768px) {
  .place-item {
    padding: var(--gp-spacing-sm);
  }

  .place-rank {
    width: 28px;
    height: 28px;
    font-size: 0.75rem;
  }

  .place-name {
    font-size: 0.875rem;
  }
}
</style>
