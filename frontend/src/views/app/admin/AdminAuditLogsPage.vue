<template>
  <AppLayout>
    <div class="admin-audit-logs">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <div>
          <h1>{{ t('admin.dashboardPage.auditLogs') }}</h1>
          <p class="text-muted">{{ t('adminAuditInvitations.auditLogsPage.subtitle') }}</p>
        </div>
      </div>

      <!-- Filters Card -->
      <div class="card filters-card">
        <div class="filters-grid">
          <div class="filter-field">
            <label for="dateRange">{{ t('adminAuditInvitations.auditLogsPage.filters.dateRangeLabel') }}</label>
            <DatePicker
              id="dateRange"
              v-model="dateRange"
              selectionMode="range"
              :maxDate="new Date()"
              :dateFormat="timezone.getPrimeVueDatePickerFormat()"
              :placeholder="t('adminAuditInvitations.auditLogsPage.filters.dateRangePlaceholder')"
              showButtonBar
              @update:modelValue="onFilterChange"
              class="w-full"
            />
          </div>

          <div class="filter-field">
            <label for="actionType">{{ t('adminAuditInvitations.auditLogsPage.filters.actionTypeLabel') }}</label>
            <Select
              id="actionType"
              v-model="actionTypeFilter"
              :options="actionTypeOptions"
              optionLabel="label"
              optionValue="value"
              :placeholder="t('adminAuditInvitations.auditLogsPage.filters.actionTypePlaceholder')"
              @change="onFilterChange"
              class="w-full"
              showClear
            />
          </div>

          <div class="filter-field">
            <label for="targetType">{{ t('adminAuditInvitations.auditLogsPage.filters.targetTypeLabel') }}</label>
            <Select
              id="targetType"
              v-model="targetTypeFilter"
              :options="targetTypeOptions"
              optionLabel="label"
              optionValue="value"
              :placeholder="t('adminAuditInvitations.auditLogsPage.filters.targetTypePlaceholder')"
              @change="onFilterChange"
              class="w-full"
              showClear
            />
          </div>

          <div class="filter-field">
            <label for="adminEmail">{{ t('adminAuditInvitations.auditLogsPage.filters.adminEmailLabel') }}</label>
            <InputText
              id="adminEmail"
              v-model="adminEmailFilter"
              :placeholder="t('adminAuditInvitations.auditLogsPage.filters.adminEmailPlaceholder')"
              @input="onAdminEmailSearch"
              class="w-full"
            />
          </div>
        </div>

        <div class="filter-actions">
          <Button
            :label="t('adminAuditInvitations.auditLogsPage.filters.clearFilters')"
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
          :currentPageReportTemplate="t('adminAuditInvitations.auditLogsPage.table.currentPageReport')"
          v-model:expandedRows="expandedRows"
        >
          <Column :expander="true" style="width: 3rem" />

          <Column field="timestamp" :header="t('adminAuditInvitations.auditLogsPage.table.columns.timestamp')" sortable style="min-width: 180px">
            <template #body="{ data }">
              <div class="timestamp-cell">
                <div>{{ formatDate(data.timestamp) }}</div>
                <div class="text-muted small">{{ formatTime(data.timestamp) }}</div>
              </div>
            </template>
          </Column>

          <Column field="adminEmail" :header="t('adminAuditInvitations.auditLogsPage.table.columns.admin')" style="min-width: 200px">
            <template #body="{ data }">
              <div class="flex align-items-center gap-2">
                <i class="pi pi-user"></i>
                <span>{{ data.adminEmail }}</span>
              </div>
            </template>
          </Column>

          <Column field="actionType" :header="t('adminAuditInvitations.auditLogsPage.table.columns.action')" style="min-width: 200px">
            <template #body="{ data }">
              <div class="flex align-items-center gap-2">
                <i :class="getActionIcon(data.actionType)" :style="{ color: getActionColor(data.actionType) }"></i>
                <Tag :severity="getActionSeverity(data.actionType)" :value="formatActionType(data.actionType)" />
              </div>
            </template>
          </Column>

          <Column field="targetType" :header="t('adminAuditInvitations.auditLogsPage.table.columns.targetType')" style="min-width: 150px">
            <template #body="{ data }">
              <Tag severity="secondary" :value="formatTargetType(data.targetType)" />
            </template>
          </Column>

          <Column field="targetId" :header="t('adminAuditInvitations.auditLogsPage.table.columns.targetId')" style="min-width: 200px">
            <template #body="{ data }">
              <span class="font-mono text-sm">{{ data.targetId || '-' }}</span>
            </template>
          </Column>

          <Column field="ipAddress" :header="t('adminAuditInvitations.auditLogsPage.table.columns.ipAddress')" style="min-width: 150px">
            <template #body="{ data }">
              <span class="font-mono text-sm">{{ data.ipAddress || '-' }}</span>
            </template>
          </Column>

          <template #expansion="{ data }">
            <div class="expansion-panel">
              <h3>{{ t('adminAuditInvitations.auditLogsPage.expansion.detailsHeader') }}</h3>
              <div class="details-content" v-if="data.details && Object.keys(data.details).length > 0">
                <pre class="json-viewer">{{ JSON.stringify(data.details, null, 2) }}</pre>
              </div>
              <div v-else class="text-muted">
                {{ t('adminAuditInvitations.auditLogsPage.expansion.noDetails') }}
              </div>

              <div class="expansion-meta">
                <div class="meta-item">
                  <span class="meta-label">{{ t('adminAuditInvitations.auditLogsPage.expansion.adminUserId') }}</span>
                  <span class="font-mono">{{ data.adminUserId }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">{{ t('adminAuditInvitations.auditLogsPage.expansion.timestampLabel') }}</span>
                  <span>{{ formatFullTimestamp(data.timestamp) }}</span>
                </div>
              </div>
            </div>
          </template>

          <template #empty>
            <div class="text-center p-4">
              {{ t('adminAuditInvitations.auditLogsPage.table.empty') }}
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
          {{ t('adminAuditInvitations.auditLogsPage.table.empty') }}
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
                <span class="stat-label">{{ t('adminAuditInvitations.auditLogsPage.table.columns.admin') }}</span>
                <div class="stat-value">
                  <i class="pi pi-user"></i>
                  <span>{{ log.adminEmail }}</span>
                </div>
              </div>
              <div class="audit-stat">
                <span class="stat-label">{{ t('adminAuditInvitations.auditLogsPage.table.columns.targetType') }}</span>
                <Tag severity="secondary" :value="formatTargetType(log.targetType)" />
              </div>
            </div>

            <div class="audit-meta">
              <div class="meta-row" v-if="log.targetId">
                <span class="meta-label">{{ t('adminAuditInvitations.auditLogsPage.mobile.targetId') }}</span>
                <code class="meta-value">{{ log.targetId }}</code>
              </div>
              <div class="meta-row" v-if="log.ipAddress">
                <span class="meta-label">{{ t('adminAuditInvitations.auditLogsPage.mobile.ipAddress') }}</span>
                <code class="meta-value">{{ log.ipAddress }}</code>
              </div>
            </div>

            <div v-if="log.details && Object.keys(log.details).length > 0" class="audit-details">
              <div class="details-header" @click="toggleDetails(log.id)">
                <span class="details-label">{{ t('adminAuditInvitations.auditLogsPage.mobile.detailsLabel') }}</span>
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
            {{ t('admin.usersPage.pageOf', { page: page + 1, total: Math.ceil(totalRecords / pageSize) }) }}
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
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
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
import { useTimezone } from '@/composables/useTimezone'
import { useAdminStore } from '@/stores/admin'

const { t } = useI18n()
const adminService = useAdminStore()
const router = useRouter()
const toast = useToast()
const timezone = useTimezone()

const breadcrumbHome = ref({
  icon: 'pi pi-home',
  command: () => router.push('/')
})
const breadcrumbItems = computed(() => [
  {
    label: t('admin.breadcrumb.administration'),
    command: () => router.push('/app/admin')
  },
  { label: t('admin.dashboardPage.auditLogs') }
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
const actionTypeLabels = computed(() => ({
  SETTING_CHANGED: t('adminAuditInvitations.auditLogsPage.actionTypes.settingChanged'),
  SETTING_RESET: t('adminAuditInvitations.auditLogsPage.actionTypes.settingReset'),
  USER_ENABLED: t('adminAuditInvitations.auditLogsPage.actionTypes.userEnabled'),
  USER_DISABLED: t('adminAuditInvitations.auditLogsPage.actionTypes.userDisabled'),
  USER_DELETED: t('adminAuditInvitations.auditLogsPage.actionTypes.userDeleted'),
  USER_ROLE_CHANGED: t('adminAuditInvitations.auditLogsPage.actionTypes.userRoleChanged'),
  USER_PASSWORD_RESET: t('adminAuditInvitations.auditLogsPage.actionTypes.userPasswordReset'),
  OIDC_PROVIDER_CREATED: t('adminAuditInvitations.auditLogsPage.actionTypes.oidcProviderCreated'),
  OIDC_PROVIDER_UPDATED: t('adminAuditInvitations.auditLogsPage.actionTypes.oidcProviderUpdated'),
  OIDC_PROVIDER_DELETED: t('adminAuditInvitations.auditLogsPage.actionTypes.oidcProviderDeleted'),
  OIDC_PROVIDER_RESET: t('adminAuditInvitations.auditLogsPage.actionTypes.oidcProviderReset'),
  INVITATION_CREATED: t('adminAuditInvitations.auditLogsPage.actionTypes.invitationCreated'),
  INVITATION_REVOKED: t('adminAuditInvitations.auditLogsPage.actionTypes.invitationRevoked'),
  TIMELINE_REGENERATION_CAMPAIGN_CREATED: t('adminAuditInvitations.auditLogsPage.actionTypes.timelineRegenerationCampaignCreated'),
  TIMELINE_REGENERATION_CAMPAIGN_RETRIED: t('adminAuditInvitations.auditLogsPage.actionTypes.timelineRegenerationCampaignRetried'),
  ADMIN_LOGIN: t('adminAuditInvitations.auditLogsPage.actionTypes.adminLogin')
}))

const targetTypeLabels = computed(() => ({
  SETTING: t('adminAuditInvitations.auditLogsPage.targetTypes.setting'),
  USER: t('adminAuditInvitations.auditLogsPage.targetTypes.user'),
  OIDC_PROVIDER: t('adminAuditInvitations.auditLogsPage.targetTypes.oidcProvider'),
  INVITATION: t('adminAuditInvitations.auditLogsPage.targetTypes.invitation'),
  TIMELINE_REGENERATION_CAMPAIGN: t('adminAuditInvitations.auditLogsPage.targetTypes.timelineRegenerationCampaign')
}))

const actionTypeOptions = computed(() =>
  Object.entries(actionTypeLabels.value).map(([value, label]) => ({ label, value }))
)

const targetTypeOptions = computed(() =>
  Object.entries(targetTypeLabels.value).map(([value, label]) => ({ label, value }))
)

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
    let logs = response.items
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
      summary: t('common.error'),
      detail: t('adminAuditInvitations.auditLogsPage.toasts.loadFailedDetail'),
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
  return timezone.formatDateDisplay(timestamp)
}

const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  return timezone.formatTime(timestamp, { withSeconds: true })
}

const formatFullTimestamp = (timestamp) => {
  if (!timestamp) return '-'
  return `${timezone.formatDateDisplay(timestamp)} ${timezone.formatTime(timestamp, { withSeconds: true })}`
}

const formatActionType = (actionType) => {
  if (!actionType) return '-'
  return actionTypeLabels.value[actionType] || actionType.replace(/_/g, ' ')
}

const formatTargetType = (targetType) => {
  if (!targetType) return '-'
  return targetTypeLabels.value[targetType] || targetType
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
    'TIMELINE_REGENERATION_CAMPAIGN_CREATED': 'pi pi-refresh',
    'TIMELINE_REGENERATION_CAMPAIGN_RETRIED': 'pi pi-replay',
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
.p-dark .audit-card {
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
