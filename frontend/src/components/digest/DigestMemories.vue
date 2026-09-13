<template>
  <ImmichLatestPhotosSection
    class="digest-memories"
    title="Memories along the way"
    presentation="rewind"
    :search-params="periodParams"
    :show-on-map-enabled="false"
    :load-map-markers="false"
    @latest-photos-change="emit('availability', $event.length > 0)"
  />
</template>

<script setup>
import { computed } from 'vue'
import ImmichLatestPhotosSection from '@/components/location-analytics/ImmichLatestPhotosSection.vue'
import { useTimezone } from '@/composables/useTimezone'

const props = defineProps({
  viewMode: { type: String, required: true },
  year: { type: Number, required: true },
  month: { type: Number, default: null }
})

const emit = defineEmits(['availability'])
const timezone = useTimezone()
const periodParams = computed(() => {
  const start = props.viewMode === 'monthly'
    ? timezone.create(`${props.year}-${String(props.month).padStart(2, '0')}-01`)
    : timezone.create(`${props.year}-01-01`)
  const end = props.viewMode === 'monthly' ? start.endOf('month') : timezone.create(`${props.year}-12-31`)
  return { startDate: timezone.startOfDayUtc(start), endDate: timezone.endOfDayUtc(end) }
})
</script>
