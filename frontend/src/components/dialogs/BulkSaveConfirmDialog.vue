<template>
  <Dialog
    v-model:visible="internalVisible"
    modal
    :header="t('miscDialogs.bulkSaveConfirm.header')"
    class="gp-dialog-sm"
    @hide="onDialogHide"
  >
    <div class="dialog-content">
      <div class="summary-section">
        <i class="pi pi-exclamation-triangle warning-icon" />
        <div class="summary-text">
          <p class="summary-title">{{ t('miscDialogs.bulkSaveConfirm.summaryTitle', { count: totalCount }, totalCount) }}</p>
          <ul class="summary-list">
            <li v-if="pointsCount > 0">{{ t('miscDialogs.bulkSaveConfirm.pointsItem', { count: pointsCount }, pointsCount) }}</li>
            <li v-if="areasCount > 0">{{ t('miscDialogs.bulkSaveConfirm.areasItem', { count: areasCount }, areasCount) }}</li>
          </ul>
        </div>
      </div>

      <Message severity="info" :closable="false">
        {{ t('miscDialogs.bulkSaveConfirm.infoMessage') }}
      </Message>
    </div>

    <template #footer>
      <Button
        :label="t('common.cancel')"
        icon="pi pi-times"
        severity="secondary"
        outlined
        :disabled="loading"
        @click="onCancel"
      />
      <Button
        :label="t('miscDialogs.bulkSaveConfirm.confirm')"
        icon="pi pi-check"
        severity="success"
        :loading="loading"
        @click="onConfirm"
      />
    </template>
  </Dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Message from 'primevue/message'

const { t } = useI18n()

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  pointsCount: {
    type: Number,
    default: 0
  },
  areasCount: {
    type: Number,
    default: 0
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'confirm'])

const internalVisible = ref(props.visible)

const totalCount = computed(() => props.pointsCount + props.areasCount)

watch(() => props.visible, (val) => {
  internalVisible.value = val
})

watch(internalVisible, (val) => {
  if (!val) {
    emit('close')
  }
})

const onDialogHide = () => {
  internalVisible.value = false
}

const onCancel = () => {
  internalVisible.value = false
}

const onConfirm = () => {
  emit('confirm')
}
</script>

<style scoped>
.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.summary-section {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.warning-icon {
  font-size: 2rem;
  color: var(--p-yellow-500);
  flex-shrink: 0;
}

.summary-text {
  flex: 1;
}

.summary-title {
  margin: 0 0 0.5rem 0;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.summary-list {
  margin: 0;
  padding-left: 1.5rem;
  color: var(--gp-text-secondary);
}

.summary-list li {
  margin-bottom: 0.25rem;
}
</style>
