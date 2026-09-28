<template>
  <BaseFriendTimelineCard
    :item="item"
    :user-name="userName"
    :user-avatar="userAvatar"
    :user-color="userColor"
    variant="stay"
    @click="$emit('click', $event)"
  >
    <template #subtitle>
      🏠 {{ t('timeline.stay.stayedAt') }}
      <span class="location-name">{{ item.locationName || t('data.tables.unknownLocation') }}</span>
    </template>

    <template #content>
      <p class="stay-detail">
        {{ t('timeline.stay.forDuration') }} <span class="font-bold">{{ formatDuration(item.stayDuration) }}</span>
      </p>
    </template>
  </BaseFriendTimelineCard>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { formatDuration } from '@/utils/calculationsHelpers'
import BaseFriendTimelineCard from './BaseFriendTimelineCard.vue'

const { t } = useI18n()

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
</script>

<style scoped>
.location-name {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.stay-detail {
  font-size: 0.875rem;
  color: var(--gp-text-primary);
  margin: 0;
  line-height: 1.4;
}

.font-bold {
  font-weight: 600;
}

.p-dark .location-name,
.p-dark .stay-detail {
  color: var(--gp-text-primary);
}

@media (max-width: 768px) {
  .stay-detail {
    font-size: 0.8rem;
  }
}
</style>
