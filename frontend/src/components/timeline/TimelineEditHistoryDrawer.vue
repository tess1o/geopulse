<template>
  <Drawer :visible="visible" position="right" class="timeline-edit-history" :header="t('timeline.edit.history')" @show="rememberFocus" @after-hide="restoreFocus" @update:visible="value => !value && $emit('close')">
    <TimelineChangesList :visible="visible" :item="item" :edits="edits" :reload="reload"
      @changed="$emit('changed', $event)" @replace="$emit('replace', $event)" />
  </Drawer>
</template>
<script setup>
import { useI18n } from 'vue-i18n'
import Drawer from 'primevue/drawer'
import TimelineChangesList from './TimelineChangesList.vue'
defineProps({ visible: Boolean, item: { type: Object, default: null }, edits: { type: Array, default: null }, reload: Function })
defineEmits(['close', 'changed', 'replace'])
const { t } = useI18n()
let opener = null
function rememberFocus() { opener = document.activeElement }
function restoreFocus() {
  if (document.activeElement === document.body && opener?.isConnected) opener.focus()
}
</script>
<style>
.timeline-edit-history {
  width: min(29rem, 100vw) !important;
  background: var(--gp-surface-card);
  box-shadow: var(--gp-shadow-dialog);
}

.timeline-edit-history .p-drawer-header {
  padding: 1.15rem 1.25rem .85rem;
  border-bottom: 1px solid var(--gp-border);
}

.timeline-edit-history .p-drawer-title {
  color: var(--gp-text-primary);
  font-size: 1.08rem;
  font-weight: 700;
}

.timeline-edit-history .p-drawer-content {
  padding: 1rem 1.25rem 1.5rem;
}

@media (max-width: 640px) {
  .timeline-edit-history {
    width: 100vw !important;
    max-width: none !important;
    height: 100dvh !important;
    border-radius: 0 !important;
  }

  .timeline-edit-history .p-drawer-header {
    padding: .9rem 1rem .75rem;
  }

  .timeline-edit-history .p-drawer-content {
    padding: .85rem 1rem calc(1rem + env(safe-area-inset-bottom));
  }
}
</style>
