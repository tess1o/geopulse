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
      <template v-if="transitionDestinationName">
        🔄 {{ t('timeline.trip.transitionTo') }} <span class="transition-destination">{{ transitionDestinationName }}</span>
      </template>
      <template v-else>
        🔄 {{ t('timeline.trip.transitionToNewPlace') }}
      </template>
    </template>

    <template #content>
      <p class="trip-detail">
        ⏱️ {{ t('timeline.trip.durationLabel') }} <span class="font-bold">{{ formatDuration(item.tripDuration) }}</span>
      </p>
      <p class="trip-detail">
        📏 {{ t('timeline.trip.distanceLabel') }} <span class="font-bold">{{ formatDistance(item.distanceMeters) }}</span>
      </p>
      <p class="trip-detail">
        🚦 {{ t('timeline.trip.movementLabel') }} <span class="font-bold">{{ movementIcon }} {{ movementLabel }}</span>
      </p>
      <p v-if="hasEndLocation" class="trip-detail trip-detail--secondary">
        → {{ item.endLocationName }}
      </p>
    </template>
  </BaseFriendTimelineCard>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDuration, formatDistance } from '@/utils/calculationsHelpers'
import BaseFriendTimelineCard from './BaseFriendTimelineCard.vue'

const { t, te } = useI18n()

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  nextItem: {
    type: Object,
    default: null
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

const movementTypeIcons = {
  WALK: '🚶',
  BICYCLE: '🚴',
  RUNNING: '🏃',
  CAR: '🚗',
  MOTORCYCLE: '🏍️',
  PUBLIC_TRANSPORT: '🚌',
  TRAIN: '🚊',
  FLIGHT: '✈️',
  BOAT: '⛵',
  UNKNOWN: '❓'
}

const movementIcon = computed(() => {
  const type = props.item.movementType || 'UNKNOWN'
  return movementTypeIcons[type] || 'pi pi-map'
})

const movementLabel = computed(() => {
  const type = props.item.movementType || 'UNKNOWN'
  const key = `movementTypes.${type}`
  return te(key) ? t(key) : t('maps.popups.timeline.tripFallback')
})

const transitionDestinationName = computed(() => {
  if (props.nextItem?.type !== 'stay') {
    return ''
  }

  const locationName = typeof props.nextItem.locationName === 'string'
    ? props.nextItem.locationName.trim()
    : ''
  if (!locationName) {
    return ''
  }

  return locationName
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

.transition-destination {
  font-weight: 700;
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
