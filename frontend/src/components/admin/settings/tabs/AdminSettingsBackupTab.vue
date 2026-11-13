<template>
  <div class="settings-section backup-page">
    <div class="backup-tabs" role="tablist" :aria-label="t('adminSettings.backupTab.tabs.ariaLabel')">
      <button
        id="backup-tab-full"
        type="button"
        role="tab"
        :class="{ active: activeTab === 'full' }"
        :aria-selected="activeTab === 'full'"
        aria-controls="backup-panel-full"
        :tabindex="activeTab === 'full' ? 0 : -1"
        @click="selectTab('full')"
        @keydown.left.prevent="selectTab('settings')"
        @keydown.right.prevent="selectTab('settings')"
        @keydown.home.prevent="selectTab('full')"
        @keydown.end.prevent="selectTab('settings')"
      >
        <i class="pi pi-database" aria-hidden="true" />
        {{ t('adminSettings.backupTab.tabs.fullDatabaseBackup') }}
      </button>
      <button
        id="backup-tab-settings"
        type="button"
        role="tab"
        :class="{ active: activeTab === 'settings' }"
        :aria-selected="activeTab === 'settings'"
        aria-controls="backup-panel-settings"
        :tabindex="activeTab === 'settings' ? 0 : -1"
        @click="selectTab('settings')"
        @keydown.left.prevent="selectTab('full')"
        @keydown.right.prevent="selectTab('full')"
        @keydown.home.prevent="selectTab('full')"
        @keydown.end.prevent="selectTab('settings')"
      >
        <i class="pi pi-cog" aria-hidden="true" />
        {{ t('adminSettings.backupTab.tabs.adminSettings') }}
      </button>
    </div>

    <section
      id="backup-panel-full"
      role="tabpanel"
      aria-labelledby="backup-tab-full"
      v-show="activeTab === 'full'"
    >
      <AdminFullBackupSection :admin-read-only="adminReadOnly" />
    </section>

    <section
      id="backup-panel-settings"
      role="tabpanel"
      aria-labelledby="backup-tab-settings"
      v-show="activeTab === 'settings'"
    >
      <AdminSettingsExportSection :admin-read-only="adminReadOnly" />
    </section>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import AdminFullBackupSection from '@/components/admin/settings/sections/AdminFullBackupSection.vue'
import AdminSettingsExportSection from '@/components/admin/settings/sections/AdminSettingsExportSection.vue'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const authStore = useAuthStore()
const { adminReadOnly } = storeToRefs(authStore)
const activeTab = ref('full')

const selectTab = async (tab) => {
  activeTab.value = tab
  await nextTick()
  document.getElementById(`backup-tab-${tab}`)?.focus()
}
</script>

<style scoped>
@import '../admin-settings-common.css';

.backup-page {
  display: grid;
  gap: 1.25rem;
  padding: 0 1rem 1.5rem;
}

.backup-tabs {
  display: flex;
  gap: 0.35rem;
  width: fit-content;
  max-width: 100%;
  padding: 0.35rem;
  border: 1px solid var(--gp-border-medium);
  border-radius: 8px;
  background: var(--gp-surface-muted);
  overflow-x: auto;
}

.backup-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 1rem;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--gp-text-secondary);
  font: inherit;
  white-space: nowrap;
  cursor: pointer;
}

.backup-tabs button:hover {
  color: var(--gp-text-primary);
}

.backup-tabs button.active {
  background: var(--gp-surface-card);
  color: var(--gp-primary);
  font-weight: 600;
  box-shadow: var(--gp-shadow-card);
}

.backup-tabs button:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

@media (max-width: 768px) {
  .backup-page {
    padding: 0 0.5rem 1rem;
  }

  .backup-tabs {
    width: 100%;
  }

  .backup-tabs button {
    flex: 1 1 0;
    justify-content: center;
    padding: 0.6rem 0.5rem;
    font-size: 0.9rem;
    white-space: normal;
    text-align: left;
  }
}
</style>
