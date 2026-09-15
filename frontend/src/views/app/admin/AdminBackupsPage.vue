<template>
  <AppLayout>
    <div class="admin-backups">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />
      <div class="page-header">
        <h1>Backups & Restore</h1>
        <p class="text-muted">Protect the instance and recover it when needed.</p>
        <a href="https://geopulse.cc/docs/system-administration/maintenance/backup-restore#encrypted-full-backups-from-the-admin-ui" target="_blank" rel="noopener noreferrer" class="documentation-link">Learn more in the documentation <i class="pi pi-external-link" /></a>
      </div>
      <DemoReadOnlyBanner />
      <AdminSettingsBackupTab />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Breadcrumb from 'primevue/breadcrumb'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import DemoReadOnlyBanner from '@/components/admin/DemoReadOnlyBanner.vue'
import AdminSettingsBackupTab from '@/components/admin/settings/tabs/AdminSettingsBackupTab.vue'

const router = useRouter()
const breadcrumbHome = ref({ icon: 'pi pi-home', command: () => router.push('/') })
const breadcrumbItems = ref([
  { label: 'Administration', command: () => router.push('/app/admin') },
  { label: 'Backups & Restore' }
])
</script>

<style scoped>
.admin-backups { padding: 1.5rem; }
.admin-breadcrumb { margin-bottom: 1.5rem; }
.page-header { margin-bottom: 1.5rem; }
.page-header h1 { margin: 0; font-size: 1.75rem; }
.text-muted { color: var(--text-color-secondary); }
.documentation-link { display: inline-block; margin-top: .5rem; }
@media (max-width: 768px) { .admin-backups { padding: .75rem; } }
</style>
