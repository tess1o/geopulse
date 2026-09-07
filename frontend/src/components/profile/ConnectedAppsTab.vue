<template>
  <div class="connected-apps">
    <div class="apps-list" aria-label="Connected apps">
      <button v-for="app in apps" :key="app.key" type="button" class="app-row" :class="{ active: activeApp === app.key }" @click="selectApp(app.key)">
        <span><i :class="app.icon" aria-hidden="true" />{{ app.label }}</span>
        <small :class="app.enabled ? 'is-ready' : ''">{{ app.status }}</small>
      </button>
    </div>
    <AIAssistantTab v-if="activeApp === 'ai'" :read-only="readOnly" :initial-settings="aiSettings" @save="$emit('ai-save', $event)" @dirty-change="$emit('dirty-change', { key: 'ai', dirty: $event })" />
    <ImmichTab v-else-if="activeApp === 'immich'" :read-only="readOnly" :config="immichConfig" :loading="immichLoading" @save="$emit('immich-save', $event)" @dirty-change="$emit('dirty-change', { key: 'immich', dirty: $event })" />
    <MemosTab v-else :read-only="readOnly" :config="memosConfig" :loading="memosLoading" @save="$emit('memos-save', $event)" @dirty-change="$emit('dirty-change', { key: 'memos', dirty: $event })" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import AIAssistantTab from './AIAssistantTab.vue'
import ImmichTab from './ImmichTab.vue'
import MemosTab from './MemosTab.vue'

const props = defineProps({
  readOnly: Boolean,
  activeApp: { type: String, default: 'ai' },
  aiSettings: { type: Object, required: true },
  immichConfig: { type: Object, default: null }, immichLoading: Boolean,
  memosConfig: { type: Object, default: null }, memosLoading: Boolean
})
const emit = defineEmits(['ai-save', 'immich-save', 'memos-save', 'dirty-change', 'select-app'])
const activeApp = ref(props.activeApp)
watch(() => props.activeApp, (app) => { activeApp.value = ['ai', 'immich', 'memos'].includes(app) ? app : 'ai' })
const selectApp = (app) => { activeApp.value = app; emit('select-app', app) }
const apps = computed(() => [
  { key: 'ai', label: 'AI Assistant', icon: 'pi pi-sparkles', enabled: props.aiSettings.enabled, status: props.aiSettings.enabled ? 'Enabled' : 'Not enabled' },
  { key: 'immich', label: 'Immich', icon: 'pi pi-images', enabled: props.immichConfig?.enabled, status: props.immichConfig?.enabled ? 'Connected' : 'Not configured' },
  { key: 'memos', label: 'Memos', icon: 'pi pi-file-edit', enabled: props.memosConfig?.enabled, status: props.memosConfig?.enabled ? 'Connected' : 'Not configured' }
])
</script>

<style scoped>
.connected-apps { display: grid; gap: 1rem; }
.apps-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .75rem; }
.app-row { display: flex; justify-content: space-between; align-items: center; gap: .5rem; padding: .9rem 1rem; border: 1px solid var(--gp-border-light); border-radius: var(--gp-radius-medium); background: var(--gp-surface-white); color: var(--gp-text-primary); cursor: pointer; text-align: left; }
.app-row span { display: flex; gap: .5rem; align-items: center; font-weight: 600; }.app-row small { color: var(--gp-text-secondary); }.app-row .is-ready { color: var(--gp-success); }.app-row.active { border-color: var(--gp-primary); box-shadow: 0 0 0 1px var(--gp-primary); }
@media (max-width: 768px) { .apps-list { grid-template-columns: 1fr; } }
</style>
