<template>
  <Drawer :visible="visible" position="right" class="timeline-edit-history" :header="t('timeline.edit.history')" @show="rememberFocus" @after-hide="restoreFocus" @update:visible="value => !value && $emit('close')">
    <TimelineChangesList :visible="visible" :item="item" @changed="$emit('changed', $event)" />
  </Drawer>
</template>
<script setup>
import { useI18n } from 'vue-i18n'
import Drawer from 'primevue/drawer'
import TimelineChangesList from './TimelineChangesList.vue'
defineProps({ visible: Boolean, item: { type: Object, default: null } })
defineEmits(['close', 'changed'])
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

.history-entry {
  margin-bottom: .75rem;
  padding: .9rem;
  border: 1px solid var(--gp-border);
  border-left: 3px solid var(--gp-primary);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-card);
  box-shadow: var(--gp-shadow-subtle);
}

.history-entry--reverted,
.history-entry--superseded {
  border-left-color: var(--gp-text-muted);
  background: color-mix(in srgb, var(--gp-surface-muted) 65%, var(--gp-surface-card));
}

.history-entry-heading {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr) auto;
  align-items: center;
  gap: .65rem;
}

.history-entry-heading > div {
  min-width: 0;
  display: grid;
  gap: .2rem;
}

.history-entry-heading strong {
  color: var(--gp-text-primary);
  font-size: .9rem;
}

.history-entry-heading small {
  overflow: hidden;
  color: var(--gp-text-secondary);
  font-size: .72rem;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-kind-icon {
  width: 2rem;
  height: 2rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--gp-radius-medium);
  background: color-mix(in srgb, var(--gp-primary) 10%, var(--gp-surface-card));
  color: var(--gp-primary-text);
  font-size: .8rem;
}

.edit-status {
  padding: .2rem .45rem;
  border-radius: var(--gp-radius-pill);
  background: var(--gp-primary-soft);
  color: var(--gp-primary-text);
  font-size: .65rem;
  font-weight: 750;
  letter-spacing: .03em;
  white-space: nowrap;
}

.history-entry--reverted .edit-status,
.history-entry--superseded .edit-status {
  background: var(--gp-surface-emphasis);
  color: var(--gp-text-secondary);
}

.unapplied {
  display: flex;
  align-items: flex-start;
  gap: .4rem;
  margin: .7rem 0 0;
  padding: .55rem .6rem;
  border-radius: var(--gp-radius-small);
  background: var(--gp-warning-soft);
  color: var(--gp-warning-text);
  font-size: .72rem;
  line-height: 1.35;
}

.history-entry-actions {
  display: flex;
  justify-content: flex-end;
  margin: .45rem -.35rem -.35rem 0;
}

.history-empty {
  min-height: 12rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: .7rem;
  color: var(--gp-text-secondary);
  text-align: center;
}

.history-empty i {
  color: var(--gp-text-muted);
  font-size: 1.5rem;
}

.history-error {
  display: flex;
  align-items: flex-start;
  gap: .5rem;
  margin: 0 0 .75rem;
  padding: .75rem;
  border: 1px solid var(--gp-danger-border);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-danger-soft);
  color: var(--gp-danger-text);
  font-size: .82rem;
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
