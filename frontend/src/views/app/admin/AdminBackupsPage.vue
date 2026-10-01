<template>
  <AppLayout>
    <div class="admin-backups">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />
      <div class="page-header">
        <h1>{{ t('admin.backupsPage.title') }}</h1>
        <p class="text-muted">{{ t('admin.backupsPage.subtitle') }}</p>
        <a href="https://geopulse.cc/docs/system-administration/maintenance/backup-restore#encrypted-full-backups-from-the-admin-ui" target="_blank" rel="noopener noreferrer" class="documentation-link">{{ t('admin.backupsPage.learnMore') }} <i class="pi pi-external-link" /></a>
      </div>
      <DemoReadOnlyBanner />
      <AdminSettingsBackupTab />
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import Breadcrumb from 'primevue/breadcrumb'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import DemoReadOnlyBanner from '@/components/admin/DemoReadOnlyBanner.vue'
import AdminSettingsBackupTab from '@/components/admin/settings/tabs/AdminSettingsBackupTab.vue'

const { t } = useI18n()
const router = useRouter()
const breadcrumbHome = ref({ icon: 'pi pi-home', command: () => router.push('/') })
const breadcrumbItems = computed(() => [
  { label: t('admin.breadcrumb.administration'), command: () => router.push('/app/admin') },
  { label: t('admin.backupsPage.breadcrumbTitle') }
])
</script>

<style scoped>
.admin-backups { padding: 1.5rem; }
.admin-breadcrumb { margin-bottom: 1.5rem; }
.page-header { margin-bottom: 1.5rem; }
.page-header h1 { margin: 0; font-size: 1.75rem; }
.text-muted { color: var(--gp-text-secondary); }
.documentation-link { display: inline-block; margin-top: .5rem; }
@media (max-width: 768px) { .admin-backups { padding: .75rem; } }
</style>
