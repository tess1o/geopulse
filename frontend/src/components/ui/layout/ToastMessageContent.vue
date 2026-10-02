<template>
  <!--
    PrimeVue's default toast content (severity icon, summary, detail), for toasts that use the #message slot.
    The slot replaces the icon and the text column, so custom toasts render this to look like every other toast:
    Aura styles these class names and colours the detail per severity. Extra lines go in the default slot, under
    the detail. Mirrors primevue/toast/ToastMessage.vue (4.3).
  -->
  <component :is="icon" class="p-toast-message-icon" />
  <div class="p-toast-message-text">
    <span class="p-toast-summary">{{ message.summary }}</span>
    <div v-if="message.detail" class="p-toast-detail">{{ message.detail }}</div>
    <slot />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import CheckIcon from '@primevue/icons/check'
import ExclamationTriangleIcon from '@primevue/icons/exclamationtriangle'
import InfoCircleIcon from '@primevue/icons/infocircle'
import TimesCircleIcon from '@primevue/icons/timescircle'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const SEVERITY_ICONS = {
  info: InfoCircleIcon,
  success: CheckIcon,
  warn: ExclamationTriangleIcon,
  error: TimesCircleIcon
}

// Like PrimeVue, other severities (secondary, contrast, none) get an empty icon slot.
const icon = computed(() => SEVERITY_ICONS[props.message.severity] || 'span')
</script>
