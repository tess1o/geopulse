<template>
  <div class="success-state">
    <div class="success-icon">
      <i class="pi pi-check-circle"></i>
    </div>
    <h3>{{ t('sharing.linkSuccess.title') }}</h3>
    <p>{{ t('sharing.linkSuccess.subtitle') }}</p>

    <div class="created-link-options">
      <div
        v-for="linkOption in linkOptions"
        :key="linkOption.key"
        class="created-link-row"
      >
        <label class="created-link-label">{{ linkOption.label }}</label>
        <div class="link-display">
          <InputText :value="linkOption.url" readonly class="share-url-input" />
          <Button
            icon="pi pi-copy"
            @click="copyLinkToClipboard(linkOption)"
            v-tooltip="t('sharing.linkSuccess.copyTooltip', { label: linkOption.label.toLowerCase() })"
            class="copy-btn"
          />
        </div>
      </div>
    </div>

    <div class="success-actions">
      <Button :label="t('sharing.linkSuccess.createAnother')" icon="pi pi-plus" @click="$emit('create-another')" outlined />
      <Button :label="t('sharing.linkSuccess.done')" icon="pi pi-check" @click="$emit('done')" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import { copyToClipboard } from '@/utils/clipboardUtils'
import { buildShareLinkOptions } from '@/utils/shareLinkUrls'

const { t } = useI18n()

const props = defineProps({
  share: {
    type: Object,
    required: true
  },
  baseUrl: {
    type: String,
    default: ''
  }
})

defineEmits(['create-another', 'done'])

const toast = useToast()

const linkOptions = computed(() => buildShareLinkOptions(props.share, props.baseUrl))

async function copyLinkToClipboard(linkOption) {
  const success = await copyToClipboard(linkOption.url)

  if (success) {
    toast.add({
      severity: 'success',
      summary: t('sharing.linkSuccess.copiedSummary'),
      detail: linkOption.toastDetail,
      life: 2000
    })
  } else {
    toast.add({
      severity: 'error',
      summary: t('sharing.linkSuccess.copyFailedSummary'),
      detail: t('sharing.linkSuccess.copyFailedDetail'),
      life: 3000
    })
  }
}
</script>

<style scoped>
.success-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 2rem 1rem;
  text-align: center;
}

.success-icon {
  font-size: 4rem;
  color: var(--p-green-500);
}

.success-state h3 {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
  color: var(--gp-text-primary);
}

.success-state p {
  color: var(--gp-text-secondary);
  margin: 0;
  max-width: 400px;
}

.created-link-options {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  width: 100%;
  margin-top: 0.5rem;
}

.created-link-row {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  width: 100%;
  text-align: left;
}

.created-link-label {
  color: var(--gp-text-primary);
  font-size: 0.88rem;
  font-weight: 600;
}

.link-display {
  display: flex;
  gap: 0.5rem;
  width: 100%;
}

.share-url-input {
  flex: 1;
  font-family: var(--gp-font-mono);
  font-size: 0.9rem;
}

.copy-btn {
  flex-shrink: 0;
}

.success-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
  margin-top: 1rem;
  width: 100%;
}

.success-actions button {
  flex: 1;
  max-width: 200px;
}

@media (max-width: 640px) {
  .link-display {
    gap: 0.4rem;
  }

  .share-url-input {
    min-width: 0;
    font-size: 0.78rem;
  }
}
</style>
