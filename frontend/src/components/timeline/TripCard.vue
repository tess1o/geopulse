<template>
  <Card
    class="timeline-card timeline-card--trip"
    @click="handleClick"
    @contextmenu="showContextMenu"
  >
    <template #title>
      <p class="timeline-timestamp">
        🕐 {{ formattedTimestamp }}
      </p>
    </template>

    <template #subtitle>
      <div class="timeline-subtitle">
        <p class="transition-title">
          🔄 Transition to new place
        </p>
      </div>
    </template>

    <template #content>
      <div class="trip-content">
        <p class="trip-detail">
          ⏱️ Duration:
          <span class="font-bold">{{ formatDuration(tripItem.tripDuration) }}</span>
        </p>
        <p class="trip-detail">
          📏 Distance:
          <span class="font-bold">{{ formatDistance(tripItem.distanceMeters) }}</span>
        </p>
        <p class="trip-detail">
          🚦 Movement:
          <span class="font-bold">
            {{ formatMovementType(tripItem.movementType).icon }}
            {{ formatMovementType(tripItem.movementType).label }}
          </span>
        </p>
      </div>
    </template>
  </Card>

  <ContextMenu ref="contextMenu" :model="contextMenuItems" />
</template>

<script setup>
import { ref, computed } from 'vue'
import { formatDistance, formatDuration } from '@/utils/calculationsHelpers'
import { useTimezone } from '@/composables/useTimezone'

const props = defineProps({
  tripItem: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['click', 'export-gpx'])

const contextMenu = ref(null)
const contextMenuItems = ref([
  {
    label: 'Export as GPX',
    icon: 'pi pi-download',
    command: () => {
      emit('export-gpx', props.tripItem)
    }
  }
])

const timezone = useTimezone()

// Movement type mapping
const movementTypeMap = {
  WALK: { label: 'Walk', icon: '🚶' },
  BICYCLE: { label: 'Bicycle', icon: '🚴' },
  RUNNING: { label: 'Running', icon: '🏃' },
  CAR: { label: 'Car', icon: '🚗' },
  TRAIN: { label: 'Train', icon: '🚊' },
  FLIGHT: { label: 'Flight', icon: '✈️' },
  UNKNOWN: { label: 'Unknown', icon: '❓' }
}

const formatMovementType = (type) => {
  return movementTypeMap[type] || { label: type, icon: '' }
}

const handleClick = () => {
  emit('click', props.tripItem)
}

const showContextMenu = (event) => {
  event.preventDefault()
  contextMenu.value.show(event)
}

const formattedTimestamp = computed(() => {
  if (!props.tripItem.timestamp) return '';
  return timezone.format(props.tripItem.timestamp);
});
</script>

<style scoped>
.timeline-card {
  margin-top: var(--gp-spacing-md);
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
  overflow: hidden;
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
}

/* Mobile optimizations */
@media (max-width: 768px) {
  .timeline-card {
    margin-top: var(--gp-spacing-sm);
    padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
  }
  
  .timeline-timestamp {
    font-size: 0.875rem;
  }
  
  .timeline-subtitle {
    margin: var(--gp-spacing-xs) 0 0 0;
  }
  
  .transition-title {
    font-size: 0.875rem;
  }
  
  .trip-content {
    margin-top: var(--gp-spacing-xs);
  }
  
  .trip-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }
}

.timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.timeline-card--trip {
  background-color: var(--gp-timeline-green-light);
  border-left: 4px solid var(--gp-success);
}

.timeline-timestamp {
  color: var(--gp-primary);
  font-weight: 600;
  font-size: 0.95rem;
  margin: 0;
  line-height: 1.2;
}

.timeline-subtitle {
  margin: var(--gp-spacing-xs) 0 0 0;
  color: var(--gp-text-primary);
}

.transition-title {
  color: var(--gp-primary);
  font-weight: 700;
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.3;
}

.trip-content {
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-primary);
}

.trip-detail {
  margin: var(--gp-spacing-xs) 0;
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.3;
}

.trip-detail .font-bold {
  font-weight: 700;
  color: var(--gp-primary);
}

/* Dark mode adjustments */
.p-dark .timeline-card {
  border-color: var(--gp-border-medium);
}

.p-dark .timeline-card--trip {
  background-color: var(--gp-timeline-green);
  border-left: 4px solid var(--gp-success);
}

.p-dark .timeline-timestamp,
.p-dark .transition-title {
  color: var(--gp-primary);
}

.p-dark .trip-detail .font-bold {
  color: var(--gp-primary);
}

.p-dark .timeline-subtitle,
.p-dark .trip-content,
.p-dark .trip-detail {
  color: var(--gp-text-primary);
}

.p-dark .timeline-card:hover {
  box-shadow: var(--gp-shadow-medium);
}
</style>