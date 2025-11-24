<template>
  <AppLayout>
    <div class="admin-audit-logs">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <div>
          <h1>Audit Logs</h1>
          <p class="text-muted">Review administrative actions and system changes</p>
        </div>
      </div>

      <!-- Filters Card -->
      <div class="card filters-card">
        <div class="filters-grid">
          <div class="filter-field">
            <label for="dateRange">Date Range</label>
            <DatePicker
              id="dateRange"
              v-model="dateRange"
              selectionMode="range"
              :maxDate="new Date()"
              placeholder="Select date range"
              showButtonBar
              @update:modelValue="onFilterChange"
              class="w-full"
            />
          </div>

          <div class="filter-field">
            <label for="actionType">Action Type</label>
            <Select
              id="actionType"
              v-model="actionTypeFilter"
              :options="actionTypeOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="All Actions"
              @change="onFilterChange"
              class="w-full"
              showClear
            />
          </div>

          <div class="filter-field">
            <label for="targetType">Target Type</label>
            <Select
              id="targetType"
              v-model="targetTypeFilter"
              :options="targetTypeOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="All Targets"
              @change="onFilterChange"
              class="w-full"
              showClear
            />
          </div>

          <div class="filter-field">
            <label for="adminEmail">Admin Email</label>
            <InputText
              id="adminEmail"
              v-model="adminEmailFilter"
              placeholder="Filter by admin email..."
              @input="onAdminEmailSearch"
              class="w-full"
            />
          </div>
        </div>

        <div class="filter-actions">
          <Button
            label="Clear Filters"
            icon="pi pi-filter-slash"
            severity="secondary"
            outlined
            @click="clearFilters"
            size="small"
          />
        </div>
      </div>

      <!-- Audit Logs Table -->
      <div class="card">
        <DataTable
          :value="auditLogs"
          :loading="loading"
          :paginator="true"
          :rows="pageSize"
          :totalRecords="totalRecords"
          :lazy="true"
          @page="onPage"
          dataKey="id"
          responsiveLayout="scroll"
          :rowsPerPageOptions="[20, 50, 100]"
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
          currentPageReportTemplate="Showing {first} to {last} of {totalRecords} audit logs"
          v-model:expandedRows="expandedRows"
        >
          <Column :expander="true" style="width: 3rem" />

          <Column field="timestamp" header="Timestamp" sortable style="min-width: 180px">
            <template #body="{ data }">
              <div class="timestamp-cell">
                <div>{{ formatDate(data.timestamp) }}</div>
                <div class="text-muted small">{{ formatTime(data.timestamp) }}</div>
              </div>
            </template>
          </Column>

          <Column field="adminEmail" header="Admin" style="min-width: 200px">
            <template #body="{ data }">
              <div class="flex align-items-center gap-2">
                <i class="pi pi-user"></i>
                <span>{{ data.adminEmail }}</span>
              </div>
            </template>
          </Column>

          <Column field="actionType" header="Action" style="min-width: 200px">
            <template #body="{ data }">
              <div class="flex align-items-center gap-2">
                <i :class="getActionIcon(data.actionType)" :style="{ color: getActionColor(data.actionType) }"></i>
                <Tag :severity="getActionSeverity(data.actionType)" :value="formatActionType(data.actionType)" />
              </div>
            </template>
          </Column>

          <Column field="targetType" header="Target Type" style="min-width: 150px">
            <template #body="{ data }">
              <Tag severity="secondary" :value="data.targetType" />
            </template>
          </Column>

          <Column field="targetId" header="Target ID" style="min-width: 200px">
            <template #body="{ data }">
              <span class="font-mono text-sm">{{ data.targetId || '-' }}</span>
            </template>
          </Column>

          <Column field="ipAddress" header="IP Address" style="min-width: 150px">
            <template #body="{ data }">
              <span class="font-mono text-sm">{{ data.ipAddress || '-' }}</span>
            </template>
          </Column>

          <template #expansion="{ data }">
            <div class="expansion-panel">
              <h3>Details</h3>
              <div class="details-content" v-if="data.details && Object.keys(data.details).length > 0">
                <pre class="json-viewer">{{ JSON.stringify(data.details, null, 2) }}</pre>
              </div>
              <div v-else class="text-muted">
                No additional details available
              </div>

              <div class="expansion-meta">
                <div class="meta-item">
                  <span class="meta-label">Admin User ID:</span>
                  <span class="font-mono">{{ data.adminUserId }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">Timestamp:</span>
                  <span>{{ formatFullTimestamp(data.timestamp) }}</span>
                </div>
              </div>
            </div>
          </template>

          <template #empty>
            <div class="text-center p-4">
              No audit logs found.
            </div>
          </template>
        </DataTable>
      </div>

      <Toast />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import Toast from 'primevue/toast'
import Breadcrumb from 'primevue/breadcrumb'
import { useToast } from 'primevue/usetoast'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import adminService from '@/utils/adminService'

const router = useRouter()
const toast = useToast()

const breadcrumbHome = ref({
  icon: 'pi pi-home',
  command: () => router.push('/')
})
const breadcrumbItems = ref([
  {
    label: 'Administration',
    command: () => router.push('/app/admin')
  },
  { label: 'Audit Logs' }
])

const auditLogs = ref([])
const loading = ref(false)
const totalRecords = ref(0)
const page = ref(0)
const pageSize = ref(20)
const expandedRows = ref([])

// Filters
const dateRange = ref(null)
const actionTypeFilter = ref(null)
const targetTypeFilter = ref(null)
const adminEmailFilter = ref('')
const searchTimeout = ref(null)

// Filter Options
const actionTypeOptions = ref([
  { label: 'Setting Changed', value: 'SETTING_CHANGED' },
  { label: 'Setting Reset', value: 'SETTING_RESET' },
  { label: 'User Enabled', value: 'USER_ENABLED' },
  { label: 'User Disabled', value: 'USER_DISABLED' },
  { label: 'User Deleted', value: 'USER_DELETED' },
  { label: 'User Role Changed', value: 'USER_ROLE_CHANGED' },
  { label: 'User Password Reset', value: 'USER_PASSWORD_RESET' },
  { label: 'OIDC Provider Created', value: 'OIDC_PROVIDER_CREATED' },
  { label: 'OIDC Provider Updated', value: 'OIDC_PROVIDER_UPDATED' },
  { label: 'OIDC Provider Deleted', value: 'OIDC_PROVIDER_DELETED' },
  { label: 'OIDC Provider Reset', value: 'OIDC_PROVIDER_RESET' },
  { label: 'Invitation Created', value: 'INVITATION_CREATED' },
  { label: 'Invitation Revoked', value: 'INVITATION_REVOKED' },
  { label: 'Admin Login', value: 'ADMIN_LOGIN' }
])

const targetTypeOptions = ref([
  { label: 'Setting', value: 'SETTING' },
  { label: 'User', value: 'USER' },
  { label: 'OIDC Provider', value: 'OIDC_PROVIDER' },
  { label: 'Invitation', value: 'INVITATION' }
])

const loadAuditLogs = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: pageSize.value
    }

    if (actionTypeFilter.value) {
      params.actionType = actionTypeFilter.value
    }

    if (targetTypeFilter.value) {
      params.targetType = targetTypeFilter.value
    }

    if (dateRange.value && dateRange.value[0]) {
      params.from = dateRange.value[0].getTime()
      if (dateRange.value[1]) {
        // Set to end of day
        const endDate = new Date(dateRange.value[1])
        endDate.setHours(23, 59, 59, 999)
        params.to = endDate.getTime()
      }
    }

    const response = await adminService.getAuditLogs(params)

    // Filter by admin email on frontend if needed
    let logs = response.content
    if (adminEmailFilter.value) {
      logs = logs.filter(log =>
        log.adminEmail.toLowerCase().includes(adminEmailFilter.value.toLowerCase())
      )
    }

    auditLogs.value = logs
    totalRecords.value = response.totalElements
  } catch (error) {
    console.error('Failed to load audit logs:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load audit logs',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const onPage = (event) => {
  page.value = event.page
  pageSize.value = event.rows
  loadAuditLogs()
}

const onFilterChange = () => {
  page.value = 0
  loadAuditLogs()
}

const onAdminEmailSearch = () => {
  // Debounce search
  if (searchTimeout.value) {
    clearTimeout(searchTimeout.value)
  }
  searchTimeout.value = setTimeout(() => {
    page.value = 0
    loadAuditLogs()
  }, 300)
}

const clearFilters = () => {
  dateRange.value = null
  actionTypeFilter.value = null
  targetTypeFilter.value = null
  adminEmailFilter.value = ''
  onFilterChange()
}

const formatDate = (timestamp) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleDateString()
}

const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleTimeString()
}

const formatFullTimestamp = (timestamp) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString()
}

const formatActionType = (actionType) => {
  if (!actionType) return '-'
  return actionType.replace(/_/g, ' ')
}

const getActionIcon = (actionType) => {
  const iconMap = {
    'SETTING_CHANGED': 'pi pi-cog',
    'SETTING_RESET': 'pi pi-refresh',
    'USER_ENABLED': 'pi pi-check-circle',
    'USER_DISABLED': 'pi pi-ban',
    'USER_DELETED': 'pi pi-trash',
    'USER_ROLE_CHANGED': 'pi pi-shield',
    'USER_PASSWORD_RESET': 'pi pi-key',
    'OIDC_PROVIDER_CREATED': 'pi pi-plus-circle',
    'OIDC_PROVIDER_UPDATED': 'pi pi-pencil',
    'OIDC_PROVIDER_DELETED': 'pi pi-times-circle',
    'OIDC_PROVIDER_RESET': 'pi pi-replay',
    'INVITATION_CREATED': 'pi pi-send',
    'INVITATION_REVOKED': 'pi pi-ban',
    'ADMIN_LOGIN': 'pi pi-sign-in'
  }
  return iconMap[actionType] || 'pi pi-circle'
}

const getActionColor = (actionType) => {
  if (actionType?.includes('DELETE') || actionType?.includes('REVOKED') || actionType?.includes('DISABLED')) {
    return '#ef4444'
  }
  if (actionType?.includes('CREATE') || actionType?.includes('ENABLED')) {
    return '#22c55e'
  }
  if (actionType?.includes('UPDATE') || actionType?.includes('CHANGED')) {
    return '#3b82f6'
  }
  return '#6b7280'
}

const getActionSeverity = (actionType) => {
  if (actionType?.includes('DELETE') || actionType?.includes('REVOKED') || actionType?.includes('DISABLED')) {
    return 'danger'
  }
  if (actionType?.includes('CREATE') || actionType?.includes('ENABLED')) {
    return 'success'
  }
  if (actionType?.includes('UPDATE') || actionType?.includes('CHANGED')) {
    return 'info'
  }
  return 'secondary'
}

onMounted(() => {
  loadAuditLogs()
})
</script>

<style scoped>
.admin-audit-logs {
  padding: 1.5rem;
}

.admin-breadcrumb {
  margin-bottom: 1.5rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
}

.card {
  background: var(--surface-card);
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 1rem;
}

.filters-card {
  margin-bottom: 1.5rem;
}

.filters-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1rem;
  margin-bottom: 1rem;
}

.filter-field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: var(--text-color);
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 0.5rem;
  border-top: 1px solid var(--surface-border);
}

.text-muted {
  color: var(--text-color-secondary);
}

.small {
  font-size: 0.875rem;
}

.text-sm {
  font-size: 0.875rem;
}

.font-mono {
  font-family: 'Courier New', Courier, monospace;
}

.timestamp-cell {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.expansion-panel {
  padding: 1rem 2rem;
  background: var(--surface-50);
}

.expansion-panel h3 {
  margin-top: 0;
  margin-bottom: 1rem;
  font-size: 1.1rem;
  color: var(--text-color);
}

.details-content {
  margin-bottom: 1.5rem;
}

.json-viewer {
  background: var(--surface-900);
  color: var(--surface-0);
  padding: 1rem;
  border-radius: 4px;
  overflow-x: auto;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.875rem;
  line-height: 1.5;
}

.expansion-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--surface-border);
}

.meta-item {
  display: flex;
  gap: 0.5rem;
}

.meta-label {
  font-weight: 600;
  color: var(--text-color-secondary);
}

.no-underline {
  text-decoration: none;
}
</style>
