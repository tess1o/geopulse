<template>
  <AppLayout>
    <div class="gp-admin-page">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="gp-admin-breadcrumb" />
      <div class="gp-page-header">
        <div class="gp-page-header-content">
          <div class="gp-page-header-text">
            <h1 class="gp-page-title">{{ t('admin.backupsPage.title') }}</h1>
            <p class="gp-page-subtitle">{{ t('admin.backupsPage.subtitle') }}</p>
            <a href="https://geopulse.cc/docs/system-administration/maintenance/backup-restore#encrypted-full-backups-from-the-admin-ui" target="_blank" rel="noopener noreferrer" class="documentation-link">{{ t('admin.backupsPage.learnMore') }} <i class="pi pi-external-link" /></a>
          </div>
        </div>
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
.documentation-link { display: inline-block; margin-top: .5rem; }

</style>
