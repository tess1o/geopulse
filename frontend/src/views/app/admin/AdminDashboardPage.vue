<template>
  <AppLayout>
    <div class="admin-dashboard">
      <div class="page-header">
        <h1>Admin Dashboard</h1>
        <p class="text-muted">System overview and quick actions</p>
      </div>

      <div class="stats-grid">
        <!-- Quick Stats -->
        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">Total Users</p>
                <h3 class="stat-value">{{ stats.totalUsers }}</h3>
              </div>
              <i class="pi pi-users stat-icon stat-icon-primary"></i>
            </div>
          </template>
        </Card>

        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">Admin Users</p>
                <h3 class="stat-value">{{ stats.adminUsers }}</h3>
              </div>
              <i class="pi pi-shield stat-icon stat-icon-orange"></i>
            </div>
          </template>
        </Card>

        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">Active Users</p>
                <h3 class="stat-value">{{ stats.activeUsers }}</h3>
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
                <h3 class="stat-value">{{ formatNumber(stats.totalGpsPoints) }}</h3>
              </div>
              <i class="pi pi-map-marker stat-icon stat-icon-blue"></i>
            </div>
          </template>
        </Card>
      </div>

    <!-- Quick Actions -->
    <Card class="actions-card">
      <template #content>
        <h3 class="actions-title">Quick Actions</h3>
        <div class="actions-buttons">
          <router-link to="/app/admin/settings" class="no-underline">
            <Button label="System Settings" icon="pi pi-cog" />
          </router-link>
          <router-link to="/app/admin/users" class="no-underline">
            <Button label="Manage Users" icon="pi pi-users" severity="secondary" />
          </router-link>
        </div>
      </template>
    </Card>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import apiService from '@/utils/apiService'

const stats = ref({
  totalUsers: 0,
  adminUsers: 0,
  activeUsers: 0,
  totalGpsPoints: 0
})

const formatNumber = (num) => {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M'
  } else if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toString()
}

onMounted(async () => {
  try {
    // For now, we'll get basic stats from the users endpoint
    const response = await apiService.get('/admin/users?size=1')
    stats.value.totalUsers = response.totalElements || 0

    // TODO: Add dedicated stats endpoint
  } catch (error) {
    console.error('Failed to load admin stats:', error)
  }
})
</script>

<style scoped>
.admin-dashboard {
  padding: 1.5rem;
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

/* Actions Card */
.actions-card {
  margin-top: 1.5rem;
}

.actions-card :deep(.p-card-body) {
  padding: 1.25rem;
}

.actions-card :deep(.p-card-content) {
  padding: 0;
}

.actions-title {
  margin: 0 0 1rem 0;
  font-size: 1.25rem;
  color: var(--text-color);
}

.actions-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.no-underline {
  text-decoration: none;
}
</style>
