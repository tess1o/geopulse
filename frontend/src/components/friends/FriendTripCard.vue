<template>
  <BaseFriendTimelineCard
    :item="item"
    :user-name="userName"
    :user-avatar="userAvatar"
    :user-color="userColor"
    variant="trip"
    @click="$emit('click', $event)"
  >
    <template #subtitle>
      🔄 Transition to new place
    </template>

    <template #content>
      <p class="trip-detail">
        ⏱️ Duration: <span class="font-bold">{{ formatDuration(item.tripDuration) }}</span>
      </p>
      <p class="trip-detail">
        📏 Distance: <span class="font-bold">{{ formatDistance(item.distanceMeters) }}</span>
      </p>
      <p class="trip-detail">
        🚦 Movement: <span class="font-bold">{{ movementIcon }} {{ movementLabel }}</span>
      </p>
      <p v-if="hasEndLocation" class="trip-detail trip-detail--secondary">
        → {{ item.endLocationName }}
      </p>
    </template>
  </BaseFriendTimelineCard>
</template>

<script setup>
import { computed } from 'vue'
import { formatDuration, formatDistance } from '@/utils/calculationsHelpers'
import BaseFriendTimelineCard from './BaseFriendTimelineCard.vue'

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  userName: {
    type: String,
    required: true
  },
  userAvatar: {
    type: String,
    default: null
  },
  userColor: {
    type: String,
    required: true
  }
})

defineEmits(['click'])

const movementTypeMap = {
  WALK: { label: 'Walk', icon: '🚶' },
  BICYCLE: { label: 'Bicycle', icon: '🚴' },
  RUNNING: { label: 'Running', icon: '🏃' },
  CAR: { label: 'Car', icon: '🚗' },
  TRAIN: { label: 'Train', icon: '🚊' },
  FLIGHT: { label: 'Flight', icon: '✈️' },
  UNKNOWN: { label: 'Unknown', icon: '❓' }
}

const movementIcon = computed(() => {
  const type = props.item.movementType || 'UNKNOWN'
  return movementTypeMap[type]?.icon || 'pi pi-map'
})

const movementLabel = computed(() => {
  const type = props.item.movementType || 'UNKNOWN'
  return movementTypeMap[type]?.label || 'Trip'
})

const hasEndLocation = computed(() => {
  return props.item.endLocationName && props.item.endLocationName.trim().length > 0
})
</script>

<style scoped>
.trip-detail {
  font-size: 0.875rem;
  color: var(--gp-text-primary);
  margin: 0;
  line-height: 1.4;
}

.trip-detail--secondary {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
}

.font-bold {
  font-weight: 600;
}

.p-dark .trip-detail {
  color: var(--gp-text-primary);
}

@media (max-width: 768px) {
  .trip-detail {
    font-size: 0.8rem;
  }
}
</style>
