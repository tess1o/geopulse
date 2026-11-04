<template>
  <div class="activity-date-time-field">
    <DatePicker :model-value="date" :date-format="timezone.getPrimeVueDatePickerFormat()"
      :aria-label="`${label} · ${t('missingActivity.date')}`" @update:model-value="setDate" @input="dateInput" />
    <DatePicker :model-value="time" time-only show-seconds :hour-format="timezone.getTimeFormat() === '12h' ? '12' : '24'"
      :aria-label="`${label} · ${t('missingActivity.time')}`" :placeholder="t('missingActivity.chooseTime')"
      @update:model-value="setTime" @input="timeInput" />
  </div>
</template>
<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import customParseFormat from 'dayjs/plugin/customParseFormat'
import DatePicker from 'primevue/datepicker'
import { useTimezone } from '@/composables/useTimezone'
import { timelineUtcToPicker } from '@/utils/timelineEditTime'
dayjs.extend(customParseFormat)
const props = defineProps({ modelValue: Date, contextDate: String, label: String })
const emit = defineEmits(['update:modelValue'])
const timezone = useTimezone(), { t } = useI18n()
const date = ref(null), time = ref(null)
watch(() => props.modelValue, value => {
  if (value instanceof Date && Number.isFinite(value.getTime())) { date.value = value; time.value = value }
}, { immediate: true })
watch(() => props.contextDate, value => {
  if (!props.modelValue) date.value = timelineUtcToPicker(value || new Date().toISOString(), timezone)
}, { immediate: true })
function publish() {
  if (!date.value || !time.value) { emit('update:modelValue', null); return }
  emit('update:modelValue', new Date(date.value.getFullYear(), date.value.getMonth(), date.value.getDate(),
    time.value.getHours(), time.value.getMinutes(), time.value.getSeconds(), time.value.getMilliseconds()))
}
function setDate(value) { date.value = value; publish() }
function setTime(value) { time.value = value; publish() }
function dateInput(event) {
  const pattern = timezone.getPrimeVueDatePickerFormat().replace('mm', 'MM').replace('dd', 'DD').replace('yy', 'YYYY')
  const parsed = dayjs(event.target.value, pattern, true)
  setDate(parsed.isValid() ? parsed.toDate() : null)
}
function timeInput(event) {
  const pattern = timezone.getTimeFormatPattern(true)
  const parsed = dayjs(event.target.value, [pattern, pattern.replace(/^h:/, 'hh:')], true)
  setTime(parsed.isValid() ? parsed.toDate() : null)
}
</script>
<style scoped>
.activity-date-time-field { display: grid; gap: .4rem; min-width: 0; }
.activity-date-time-field :deep(.p-datepicker), .activity-date-time-field :deep(input) { width: 100%; min-width: 0; }
</style>
