<template>
  <!--
    Renders server-error toasts that carry a support reference (group "gp-error").

    The id is for self-hosted diagnosis: there is no support desk to quote it to, so the hint points
    at the backend logs, and the copy button saves hand-selecting a 36-character UUID.
  -->
  <Toast group="gp-error" position="top-right">
    <template #message="slotProps">
      <!-- The message and the reference hint are on separate lines of the detail (.p-toast keeps newlines). -->
      <ToastMessageContent :message="slotProps.message" />
      <Button
        v-if="slotProps.message.data?.errorId"
        icon="pi pi-copy"
        text
        size="small"
        class="gp-error-toast-copy"
        :aria-label="t('ui.errorReferenceToast.copyAriaLabel')"
        @click="copyReference(slotProps.message.data.errorId)"
      />
    </template>
  </Toast>
</template>

<script setup>
import Toast from 'primevue/toast'
import Button from 'primevue/button'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import { copyToClipboard } from '@/utils/clipboardUtils'
import ToastMessageContent from './ToastMessageContent.vue'

const { t } = useI18n()
const toast = useToast()

const copyReference = async (errorId) => {
  const copied = await copyToClipboard(errorId)

  toast.add({
    severity: copied ? 'success' : 'warn',
    summary: copied ? t('common.clipboard.copied') : t('common.clipboard.copyFailed'),
    detail: copied ? t('common.clipboard.referenceIdCopied') : t('common.clipboard.copyManually'),
    life: 2500
  })
}
</script>

<style scoped>
/* Sits between the text and the close button, in the toast's colour like the close button. */
.p-button.gp-error-toast-copy {
  flex: 0 0 auto;
  align-self: flex-end;
  color: inherit;
}

.p-button.gp-error-toast-copy:not(:disabled):hover {
  background: color-mix(in srgb, currentColor 12%, transparent);
}
</style>
