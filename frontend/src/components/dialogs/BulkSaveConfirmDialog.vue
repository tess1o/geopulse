<template>
  <Dialog
    v-model:visible="internalVisible"
    modal
    header="Confirm Bulk Save"
    class="gp-dialog-sm"
    @hide="onDialogHide"
  >
    <div class="dialog-content">
      <div class="summary-section">
        <i class="pi pi-exclamation-triangle warning-icon" />
        <div class="summary-text">
          <p class="summary-title">You are about to save {{ totalCount }} favorite location{{ totalCount > 1 ? 's' : '' }}:</p>
          <ul class="summary-list">
            <li v-if="pointsCount > 0">{{ pointsCount }} point location{{ pointsCount > 1 ? 's' : '' }}</li>
            <li v-if="areasCount > 0">{{ areasCount }} area{{ areasCount > 1 ? 's' : '' }}</li>
          </ul>
        </div>
      </div>

      <Message severity="info" :closable="false">
        These favorite locations will be saved and after that a full timeline regeneration will be triggered.
      </Message>
    </div>

    <template #footer>
      <Button
        label="Cancel"
        icon="pi pi-times"
        severity="secondary"
        outlined
        :disabled="loading"
        @click="onCancel"
      />
      <Button
        label="Confirm"
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
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Message from 'primevue/message'

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
  color: var(--yellow-500);
  flex-shrink: 0;
}

.summary-text {
  flex: 1;
}

.summary-title {
  margin: 0 0 0.5rem 0;
  font-weight: 600;
  color: var(--text-color);
}

.summary-list {
  margin: 0;
  padding-left: 1.5rem;
  color: var(--text-color-secondary);
}

.summary-list li {
  margin-bottom: 0.25rem;
}
</style>
