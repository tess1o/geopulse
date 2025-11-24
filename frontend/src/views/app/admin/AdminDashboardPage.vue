<template>
  <AppLayout>
    <div class="admin-dashboard">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <h1>Admin Dashboard</h1>
        <p class="text-muted">System overview and quick actions</p>
      </div>

      <div class="stats-header">
        <h2 class="stats-title">System Overview</h2>
        <Button
          icon="pi pi-refresh"
          label="Refresh"
          size="small"
          text
          @click="loadStats"
          :loading="loading"
        />
      </div>

      <div class="stats-grid">
        <!-- Quick Stats -->
        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">Total Users</p>
                <h3 class="stat-value">
                  <Skeleton v-if="loading" width="3rem" height="2rem" />
                  <template v-else>{{ stats.totalUsers }}</template>
                </h3>
              </div>
              <i class="pi pi-users stat-icon stat-icon-primary"></i>
            </div>
          </template>
        </Card>

        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">Active Users (24h)</p>
                <h3 class="stat-value">
                  <Skeleton v-if="loading" width="3rem" height="2rem" />
                  <template v-else>{{ stats.activeUsers24h }}</template>
                </h3>
              </div>
              <i class="pi pi-check-circle stat-icon stat-icon-green"></i>
            </div>
          </template>
        </Card>

        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">Total GPS Points</p>
                <h3 class="stat-value">
                  <Skeleton v-if="loading" width="4rem" height="2rem" />
                  <template v-else>{{ formatNumber(stats.totalGpsPoints) }}</template>
                </h3>
              </div>
              <i class="pi pi-map-marker stat-icon stat-icon-blue"></i>
            </div>
          </template>
        </Card>

        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">Memory Usage</p>
                <h3 class="stat-value">
                  <Skeleton v-if="loading" width="4rem" height="2rem" />
                  <template v-else>{{ stats.memoryUsageMB }} MB</template>
                </h3>
              </div>
              <i class="pi pi-server stat-icon stat-icon-orange"></i>
            </div>
          </template>
        </Card>
      </div>

    <!-- Quick Actions -->
    <div class="quick-actions-grid">
      <!-- User Management -->
      <Card class="actions-card">
        <template #content>
          <h3 class="actions-title">
            <i class="pi pi-users actions-icon"></i>
            User Management
          </h3>
          <div class="actions-buttons">
            <router-link to="/app/admin/users" class="no-underline">
              <Button label="Manage Users" icon="pi pi-users" class="action-button" />
            </router-link>
            <router-link to="/app/admin/invitations" class="no-underline">
              <Button label="User Invitations" icon="pi pi-send" severity="secondary" class="action-button" />
            </router-link>
            <router-link to="/app/admin/oidc-providers" class="no-underline">
              <Button label="OIDC Providers" icon="pi pi-key" severity="secondary" class="action-button" />
            </router-link>
          </div>
        </template>
      </Card>

      <!-- System Configuration -->
      <Card class="actions-card">
        <template #content>
          <h3 class="actions-title">
            <i class="pi pi-cog actions-icon"></i>
            System Configuration
          </h3>
          <div class="actions-buttons">
            <router-link to="/app/admin/settings" class="no-underline">
              <Button label="System Settings" icon="pi pi-cog" class="action-button" />
            </router-link>
          </div>
        </template>
      </Card>
    </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Skeleton from 'primevue/skeleton'
import Breadcrumb from 'primevue/breadcrumb'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import adminService from '@/utils/adminService'

const router = useRouter()

const breadcrumbHome = ref({
  icon: 'pi pi-home',
  command: () => router.push('/')
})
const breadcrumbItems = ref([
  {
    label: 'Administration',
    command: () => router.push('/app/admin')
  }
])

const stats = ref({
  totalUsers: 0,
  activeUsers24h: 0,
  totalGpsPoints: 0,
  memoryUsageMB: 0
})

const loading = ref(false)

const formatNumber = (num) => {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M'
  } else if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toString()
}

const loadStats = async () => {
  loading.value = true
  try {
    const dashboardStats = await adminService.getDashboardStats()
    stats.value = dashboardStats
  } catch (error) {
    console.error('Failed to load admin stats:', error)
    // Keep existing values on error
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.admin-dashboard {
  padding: 1.5rem;
}

.admin-breadcrumb {
  margin-bottom: 1.5rem;
}

.page-header {
  margin-bottom: 1.5rem;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
  color: var(--text-color);
}

.text-muted {
  color: var(--text-color-secondary);
  margin: 0;
}

/* Stats Header */
.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.stats-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-color);
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}

/* Stat Cards */
.stat-card {
  height: 100%;
}

.stat-card :deep(.p-card-body) {
  padding: 1.25rem;
}

.stat-card :deep(.p-card-content) {
  padding: 0;
}

.stat-card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-label {
  margin: 0 0 0.5rem 0;
  font-size: 0.875rem;
  color: var(--text-color-secondary);
}

.stat-value {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-color);
}

.stat-icon {
  font-size: 2.5rem;
  opacity: 0.8;
}

.stat-icon-primary {
  color: var(--primary-color);
}

.stat-icon-orange {
  color: var(--orange-500);
}

.stat-icon-green {
  color: var(--green-500);
}

.stat-icon-blue {
  color: var(--blue-500);
}

/* Quick Actions Grid */
.quick-actions-grid {
  margin-top: 1.5rem;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

@media (max-width: 768px) {
  .quick-actions-grid {
    grid-template-columns: 1fr;
  }
}

/* Actions Card */
.actions-card {
  height: 100%;
}

.actions-card :deep(.p-card-body) {
  padding: 1.25rem;
}

.actions-card :deep(.p-card-content) {
  padding: 0;
}

.actions-title {
  margin: 0 0 1rem 0;
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-color);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.actions-icon {
  color: var(--primary-color);
  font-size: 1.25rem;
}

.actions-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.action-button {
  width: 100%;
  justify-content: flex-start;
}

.no-underline {
  text-decoration: none;
}
</style>
