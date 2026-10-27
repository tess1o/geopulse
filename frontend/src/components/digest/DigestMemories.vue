<template>
  <ImmichLatestPhotosSection
    :title="t('analytics.digest.memories.title')"
    presentation="rewind"
    :search-params="periodParams"
    :show-on-map-enabled="false"
    :load-map-markers="false"
    @latest-photos-change="handlePhotosChange"
    @latest-photos-settled="handlePhotosSettled"
  />
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ImmichLatestPhotosSection from '@/components/location-analytics/ImmichLatestPhotosSection.vue'
import { useTimezone } from '@/composables/useTimezone'

const { t } = useI18n()

const props = defineProps({
  viewMode: { type: String, required: true },
  year: { type: Number, required: true },
  month: { type: Number, default: null }
})

const emit = defineEmits(['availability', 'photos-change'])
const timezone = useTimezone()
const handlePhotosChange = (photos) => {
  emit('availability', photos.length > 0)
  emit('photos-change', { photos, settled: false })
}
const handlePhotosSettled = (photos) => {
  emit('availability', photos.length > 0)
  emit('photos-change', { photos, settled: true })
}
const periodParams = computed(() => {
  const start = props.viewMode === 'monthly'
    ? timezone.create(`${props.year}-${String(props.month).padStart(2, '0')}-01`)
    : timezone.create(`${props.year}-01-01`)
  const end = props.viewMode === 'monthly' ? start.endOf('month') : timezone.create(`${props.year}-12-31`)
  return { startDate: timezone.startOfDayUtc(start), endDate: timezone.endOfDayUtc(end) }
})
</script>
