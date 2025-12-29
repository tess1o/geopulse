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

      <!-- Desktop Table View -->
      <div class="card desktop-only">
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

      <!-- Mobile Card View -->
      <div class="mobile-only">
        <div v-if="loading" class="text-center p-4">
          <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
        </div>

        <div v-else-if="auditLogs.length === 0" class="text-center p-4 card">
          No audit logs found.
        </div>

        <div v-else class="audit-cards">
          <div v-for="log in auditLogs" :key="log.id" class="audit-card">
            <div class="audit-card-header">
              <div class="audit-info">
                <div class="audit-action">
                  <i :class="getActionIcon(log.actionType)" :style="{ color: getActionColor(log.actionType) }"></i>
                  <Tag :severity="getActionSeverity(log.actionType)" :value="formatActionType(log.actionType)" />
                </div>
                <div class="audit-timestamp">{{ formatDate(log.timestamp) }} {{ formatTime(log.timestamp) }}</div>
              </div>
            </div>

            <div class="audit-card-body">
              <div class="audit-stat">
                <span class="stat-label">Admin</span>
                <div class="stat-value">
                  <i class="pi pi-user"></i>
                  <span>{{ log.adminEmail }}</span>
                </div>
              </div>
              <div class="audit-stat">
                <span class="stat-label">Target Type</span>
                <Tag severity="secondary" :value="log.targetType" />
              </div>
            </div>

            <div class="audit-meta">
              <div class="meta-row" v-if="log.targetId">
                <span class="meta-label">Target ID:</span>
                <code class="meta-value">{{ log.targetId }}</code>
              </div>
              <div class="meta-row" v-if="log.ipAddress">
                <span class="meta-label">IP Address:</span>
                <code class="meta-value">{{ log.ipAddress }}</code>
              </div>
            </div>

            <div v-if="log.details && Object.keys(log.details).length > 0" class="audit-details">
              <div class="details-header" @click="toggleDetails(log.id)">
                <span class="details-label">Details</span>
                <i :class="expandedMobileLogs.includes(log.id) ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"></i>
              </div>
              <div v-if="expandedMobileLogs.includes(log.id)" class="details-content">
                <pre class="json-viewer">{{ JSON.stringify(log.details, null, 2) }}</pre>
              </div>
            </div>
          </div>
        </div>

        <!-- Mobile Pagination -->
        <div class="mobile-pagination" v-if="auditLogs.length > 0">
          <Button
            icon="pi pi-angle-double-left"
            text
            @click="goToFirstPage"
            :disabled="page === 0"
          />
          <Button
            icon="pi pi-angle-left"
            text
            @click="goToPrevPage"
            :disabled="page === 0"
          />
          <span class="pagination-info">
            Page {{ page + 1 }} of {{ Math.ceil(totalRecords / pageSize) }}
          </span>
          <Button
            icon="pi pi-angle-right"
            text
            @click="goToNextPage"
            :disabled="(page + 1) * pageSize >= totalRecords"
          />
          <Button
            icon="pi pi-angle-double-right"
            text
            @click="goToLastPage"
            :disabled="(page + 1) * pageSize >= totalRecords"
          />
        </div>
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
const expandedMobileLogs = ref([])

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

// Mobile-specific functions
const toggleDetails = (logId) => {
  const index = expandedMobileLogs.value.indexOf(logId)
  if (index > -1) {
    expandedMobileLogs.value.splice(index, 1)
  } else {
    expandedMobileLogs.value.push(logId)
  }
}

const goToFirstPage = () => {
  page.value = 0
  loadAuditLogs()
}

const goToPrevPage = () => {
  if (page.value > 0) {
    page.value--
    loadAuditLogs()
  }
}

const goToNextPage = () => {
  if ((page.value + 1) * pageSize.value < totalRecords.value) {
    page.value++
    loadAuditLogs()
  }
}

const goToLastPage = () => {
  page.value = Math.floor(totalRecords.value / pageSize.value)
  loadAuditLogs()
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

/* Desktop/Mobile Toggle */
.desktop-only {
  display: block;
}

.mobile-only {
  display: none;
}

/* Mobile Audit Cards */
.audit-cards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0.5rem;
  border-radius: 8px;
}

.audit-card {
  background: var(--gp-surface-white);
  border: 2px solid var(--surface-border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(255, 255, 255, 0.05);
  transition: all 0.2s ease;
}

/* Dark theme specific */
:global(.p-dark) .audit-card,
:global([data-theme="dark"]) .audit-card,
:global(html.dark) .audit-card {
  background: var(--gp-surface-dark);
}

.audit-card-header {
  margin-bottom: 0.75rem;
}

.audit-info {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.audit-action {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.1rem;
}

.audit-timestamp {
  font-size: 0.85rem;
  color: var(--text-color-secondary);
}

.audit-card-body {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--surface-border);
}

.audit-stat {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
}

.stat-label {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  text-transform: uppercase;
  font-weight: 600;
}

.stat-value {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
}

.audit-meta {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--surface-50);
  border-radius: 6px;
  margin-bottom: 0.75rem;
}

.meta-row {
  display: flex;
  gap: 0.5rem;
  font-size: 0.85rem;
}

.meta-label {
  font-weight: 600;
  color: var(--text-color-secondary);
}

.meta-value {
  font-family: 'Courier New', monospace;
  font-size: 0.8rem;
  word-break: break-all;
}

.audit-details {
  border-top: 1px solid var(--surface-border);
  padding-top: 0.75rem;
}

.details-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem;
  cursor: pointer;
  background: var(--surface-50);
  border-radius: 6px;
  transition: background 0.2s;
}

.details-header:active {
  background: var(--surface-100);
}

.details-label {
  font-weight: 600;
  font-size: 0.9rem;
}

.details-content {
  margin-top: 0.75rem;
}

/* Mobile Pagination */
.mobile-pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1rem;
  padding: 1rem;
  background: var(--surface-card);
  border-radius: 8px;
}

.pagination-info {
  font-size: 0.9rem;
  color: var(--text-color-secondary);
  padding: 0 0.5rem;
}

/* Mobile Responsive Styles */
@media (max-width: 768px) {
  .admin-audit-logs {
    padding: 0.75rem;
  }

  .admin-breadcrumb {
    margin-bottom: 0.75rem;
  }

  .page-header {
    margin-bottom: 1rem;
  }

  .page-header h1 {
    font-size: 1.5rem;
  }

  .filters-card {
    padding: 0.75rem;
    margin-bottom: 1rem;
  }

  .filters-grid {
    grid-template-columns: 1fr;
    gap: 0.75rem;
  }

  .filter-actions {
    padding-top: 0.75rem;
  }

  .desktop-only {
    display: none;
  }

  .mobile-only {
    display: block;
  }

  .card {
    padding: 0.75rem;
  }
}

/* Extra small screens */
@media (max-width: 480px) {
  .admin-audit-logs {
    padding: 0.5rem;
  }

  .page-header h1 {
    font-size: 1.25rem;
  }

  .audit-card {
    padding: 0.75rem;
  }

  .audit-card-body {
    gap: 1rem;
  }
}
</style>
