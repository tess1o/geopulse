<template>
  <div class="user-deletion-status">
    <div class="user-deletion-status-row">
      <Tag :severity="severity" :icon="icon" :value="label" />
      <Button
        v-if="deletion.status === 'FAILED' && canRetry"
        icon="pi pi-refresh"
        size="small"
        text
        rounded
        severity="danger"
        :loading="retrying"
        :aria-label="t('admin.usersPage.deletion.retry')"
        v-tooltip.top="t('admin.usersPage.deletion.retry')"
        @click.stop="emit('retry')"
      />
    </div>
    <small class="user-deletion-status-detail" :title="deletion.status === 'FAILED' ? deletion.lastError : null">
      {{ detail }}
    </small>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Tag from 'primevue/tag'
import Button from 'primevue/button'

const props = defineProps({
  deletion: { type: Object, required: true },
  canRetry: { type: Boolean, default: false },
  retrying: { type: Boolean, default: false }
})
const emit = defineEmits(['retry'])

const { t } = useI18n()

const severity = computed(() => (props.deletion.status === 'FAILED' ? 'danger' : 'warn'))

const icon = computed(() => {
  if (props.deletion.status === 'FAILED') return 'pi pi-exclamation-triangle'
  if (props.deletion.status === 'RUNNING') return 'pi pi-spin pi-spinner'
  return 'pi pi-clock'
})

const label = computed(() => {
  switch (props.deletion.status) {
    case 'FAILED': return t('admin.usersPage.deletion.failed')
    case 'RUNNING': return t('admin.usersPage.deletion.running')
    default: return t('admin.usersPage.deletion.queued')
  }
})

const phaseLabels = computed(() => ({
  WAITING: t('admin.usersPage.deletion.phases.WAITING'),
  TIMELINE: t('admin.usersPage.deletion.phases.TIMELINE'),
  LOCATION_DATA: t('admin.usersPage.deletion.phases.LOCATION_DATA'),
  GPS_POINTS: t('admin.usersPage.deletion.phases.GPS_POINTS'),
  FINALIZING: t('admin.usersPage.deletion.phases.FINALIZING')
}))

const detail = computed(() => {
  const parts = [phaseLabels.value[props.deletion.phase] || props.deletion.phase]
  if (props.deletion.deletedGpsPoints > 0) {
    parts.push(t('admin.usersPage.deletion.deletedPoints', {
      count: Number(props.deletion.deletedGpsPoints).toLocaleString()
    }))
  }
  return parts.join(' · ')
})
</script>

<style scoped>
.user-deletion-status {
  display: inline-flex;
  flex-direction: column;
  gap: 0.25rem;
}

.user-deletion-status-row {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.user-deletion-status-detail {
  color: var(--p-text-muted-color);
}
</style>
