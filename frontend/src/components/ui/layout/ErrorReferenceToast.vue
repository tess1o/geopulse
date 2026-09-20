<template>
  <!--
    Renders server-error toasts that carry a support reference (group "gp-error").

    The id is for self-hosted diagnosis: there is no support desk to quote it to, so the hint points
    at the backend logs, and the copy button saves hand-selecting a 36-character UUID.
  -->
  <Toast group="gp-error" position="top-right">
    <template #message="slotProps">
      <div class="gp-error-toast">
        <div class="gp-error-toast-body">
          <!-- The house .p-toast-summary / .p-toast-detail rules (style.css) own the typography, so
               custom markup has to carry those class names to match every other toast. -->
          <div class="p-toast-summary">{{ slotProps.message.summary }}</div>
          <div v-if="slotProps.message.detail" class="p-toast-detail gp-error-toast-detail">
            {{ slotProps.message.detail }}
          </div>
        </div>
        <Button
          v-if="slotProps.message.data?.errorId"
          icon="pi pi-copy"
          text
          size="small"
          class="gp-error-toast-copy"
          aria-label="Copy error reference id"
          @click="copyReference(slotProps.message.data.errorId)"
        />
      </div>
    </template>
  </Toast>
</template>

<script setup>
import Toast from 'primevue/toast'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import { copyToClipboard } from '@/utils/clipboardUtils'

const toast = useToast()

const copyReference = async (errorId) => {
  const copied = await copyToClipboard(errorId)

  toast.add({
    severity: copied ? 'success' : 'warn',
    summary: copied ? 'Copied' : 'Copy failed',
    detail: copied ? 'Reference id copied to clipboard' : 'Select the id and copy it manually',
    life: 2500
  })
}
</script>

<style scoped>
.gp-error-toast {
  display: flex;
  align-items: flex-end;
  gap: 0.5rem;
  min-width: 0;
}

.gp-error-toast-body {
  flex: 1 1 auto;
  min-width: 0;
}

/* Layout only -- font size, weight and colour come from the global .p-toast-detail rule.
   The message and the reference hint sit on separate lines. */
.gp-error-toast-detail {
  white-space: pre-line;
  word-break: break-all;
}

.gp-error-toast-copy {
  flex: 0 0 auto;
}
</style>
