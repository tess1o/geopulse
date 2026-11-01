<template>
  <span v-if="!readOnly" class="timeline-edit-trigger">
    <button v-if="showEdit" class="timeline-edit-button" type="button" :aria-label="label" :title="label" @click.stop="$emit('edit')">
      <i class="pi pi-pencil" />
    </button>
    <button
      v-if="count > 0"
      class="timeline-edit-count"
      type="button"
      :aria-label="$t('timeline.edit.editedCount', { count })"
      :title="$t('timeline.edit.editedCount', { count })"
      @click.stop="$emit('history')"
    >{{ $t('timeline.edit.changeCount', { count }, count) }}</button>
  </span>
</template>

<script setup>
defineProps({
  showEdit: { type: Boolean, default: true },
  label: { type: String, required: true },
  count: { type: Number, default: 0 },
  readOnly: { type: Boolean, default: false }
})

defineEmits(['edit', 'history'])
</script>

<style scoped>
.timeline-edit-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  margin-left: .15rem;
}
.timeline-edit-button, .timeline-edit-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--gp-radius-pill);
  cursor: pointer;
}
.timeline-edit-button {
  width: 2rem;
  height: 2rem;
  padding: 0;
  border: 1px solid transparent;
  background: transparent;
  color: var(--gp-text-secondary);
  transition: background-color .15s ease, border-color .15s ease, color .15s ease;
}
.timeline-edit-count {
  min-height: 1.5rem;
  padding: .15rem .25rem;
  border: 0;
  background: transparent;
  color: var(--gp-primary-text);
  font-size: .75rem;
  margin-left: .25rem;
}
.timeline-edit-button:hover {
  border-color: var(--gp-border);
  background: var(--gp-surface-hover);
  color: var(--gp-primary-text);
}
.timeline-edit-button:focus-visible,
.timeline-edit-count:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}
.timeline-edit-count:hover { background: var(--gp-surface-hover); text-decoration: underline; }
</style>
