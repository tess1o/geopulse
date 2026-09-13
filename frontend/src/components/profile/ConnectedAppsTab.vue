<template>
  <Card class="profile-settings-card">
    <template #content>
      <div class="connected-apps settings-tab">
        <div class="settings-tab-header">
          <div class="settings-tab-icon"><i class="pi pi-box"></i></div>
          <div class="settings-tab-info">
            <h3 class="settings-tab-title">Connected apps</h3>
            <p class="settings-tab-description">Configure services that add AI, photos, and notes to GeoPulse.</p>
          </div>
        </div>

        <div class="apps-list" role="tablist" aria-label="Connected apps">
          <button
            v-for="(app, index) in apps"
            :id="`connected-app-tab-${app.key}`"
            :key="app.key"
            ref="appButtons"
            type="button"
            role="tab"
            class="app-tab"
            :class="{ active: activeApp === app.key }"
            :aria-selected="activeApp === app.key"
            :aria-controls="`connected-app-panel-${app.key}`"
            :tabindex="activeApp === app.key ? 0 : -1"
            @click="selectApp(app.key)"
            @keydown.left.prevent="selectAppAt(index - 1)"
            @keydown.right.prevent="selectAppAt(index + 1)"
            @keydown.home.prevent="selectAppAt(0)"
            @keydown.end.prevent="selectAppAt(apps.length - 1)"
          >
            <span class="app-tab-label"><i :class="app.icon" aria-hidden="true" /><span class="app-tab-label-text">{{ app.label }}</span></span>
            <small :class="{ 'is-ready': app.enabled }"><span aria-hidden="true"></span>{{ app.status }}</small>
          </button>
        </div>

        <div
          :id="`connected-app-panel-${activeApp}`"
          class="connected-app-panel"
          role="tabpanel"
          :aria-labelledby="`connected-app-tab-${activeApp}`"
        >
          <AIAssistantTab v-if="activeApp === 'ai'" :read-only="readOnly" :initial-settings="aiSettings" @save="$emit('ai-save', $event)" @dirty-change="$emit('dirty-change', { key: 'ai', dirty: $event })" />
          <ImmichTab v-else-if="activeApp === 'immich'" :read-only="readOnly" :config="immichConfig" :loading="immichLoading" @save="$emit('immich-save', $event)" @dirty-change="$emit('dirty-change', { key: 'immich', dirty: $event })" />
          <MemosTab v-else :read-only="readOnly" :config="memosConfig" :loading="memosLoading" @save="$emit('memos-save', $event)" @dirty-change="$emit('dirty-change', { key: 'memos', dirty: $event })" />
        </div>
      </div>
    </template>
  </Card>
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
const appButtons = ref([])
const activeApp = ref(['ai', 'immich', 'memos'].includes(props.activeApp) ? props.activeApp : 'ai')
watch(() => props.activeApp, (app) => { activeApp.value = ['ai', 'immich', 'memos'].includes(app) ? app : 'ai' })
const selectApp = (app) => { activeApp.value = app; emit('select-app', app) }
const apps = computed(() => [
  { key: 'ai', label: 'AI Assistant', icon: 'pi pi-sparkles', enabled: props.aiSettings.enabled, status: props.aiSettings.enabled ? 'Enabled' : 'Not enabled' },
  { key: 'immich', label: 'Immich', icon: 'pi pi-images', enabled: props.immichConfig?.enabled, status: props.immichConfig?.enabled ? 'Connected' : 'Not configured' },
  { key: 'memos', label: 'Memos', icon: 'pi pi-file-edit', enabled: props.memosConfig?.enabled, status: props.memosConfig?.enabled ? 'Connected' : 'Not configured' }
])
const selectAppAt = (index) => {
  const normalizedIndex = (index + apps.value.length) % apps.value.length
  selectApp(apps.value[normalizedIndex].key)
  appButtons.value[normalizedIndex]?.focus()
}
</script>

<style scoped>
.apps-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--gp-spacing-xs);
  padding: var(--gp-spacing-xs);
  overflow-x: auto;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
}

.app-tab {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-md);
  min-width: 0;
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  border: 1px solid transparent;
  border-radius: var(--gp-radius-small);
  background: transparent;
  color: var(--gp-text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.app-tab:hover {
  background: var(--gp-surface-white);
}

.app-tab:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 1px;
}

.app-tab.active {
  background: var(--gp-surface-white);
  border-color: var(--gp-primary);
  box-shadow: var(--gp-shadow-subtle);
}

.app-tab-label {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  min-width: 0;
  font-weight: 600;
  white-space: nowrap;
}

.app-tab small {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  color: var(--gp-text-secondary);
  font-size: 0.75rem;
  white-space: nowrap;
}

.app-tab small span {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
  background: var(--gp-text-muted);
}

.app-tab small.is-ready {
  color: var(--gp-success);
}

.app-tab small.is-ready span {
  background: var(--gp-success);
}

.connected-app-panel {
  min-width: 0;
}

@media (max-width: 768px) {
  .apps-list {
    grid-template-columns: repeat(3, minmax(11.5rem, 1fr));
  }

  .app-tab {
    align-items: flex-start;
    flex-direction: column;
    justify-content: center;
    gap: var(--gp-spacing-xs);
  }

  .app-tab-label,
  .app-tab small {
    max-width: 100%;
  }

  .app-tab-label-text {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .app-tab small {
    white-space: normal;
  }
}
</style>
