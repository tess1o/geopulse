<template>
  <AppLayout>
    <div class="admin-invitations">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <div>
          <h1>User Invitations</h1>
          <p class="text-muted">Manage user invitation links</p>
        </div>
        <div class="header-actions">
          <router-link to="/app/admin/users" class="no-underline">
            <Button
              label="Manage Users"
              icon="pi pi-users"
              severity="secondary"
              outlined
            />
          </router-link>
          <Button
            label="Create Invitation"
            icon="pi pi-plus"
            @click="showCreateDialog = true"
          />
        </div>
      </div>

      <!-- Status Filter (Mobile & Desktop) -->
      <div class="filter-container">
        <Select
          v-model="statusFilter"
          :options="statusOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="Filter by status"
          @change="onFilterChange"
          class="w-full"
        />
      </div>

      <!-- Desktop Table View -->
      <div class="card desktop-only">
        <DataTable
          :value="invitations"
          :loading="loading"
          :paginator="true"
          :rows="pageSize"
          :totalRecords="totalRecords"
          :lazy="true"
          @page="onPage"
          dataKey="id"
          responsiveLayout="scroll"
          :rowsPerPageOptions="[10, 25, 50]"
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
          currentPageReportTemplate="Showing {first} to {last} of {totalRecords} invitations"
        >
          <Column field="token" header="Token" style="max-width: 150px">
            <template #body="{ data }">
              <span class="font-mono">{{ data.token.substring(0, 12) }}...</span>
            </template>
          </Column>

          <Column field="createdBy.email" header="Created By">
            <template #body="{ data }">
              {{ data.createdBy?.email || '-' }}
            </template>
          </Column>

          <Column field="createdAt" header="Created" sortable>
            <template #body="{ data }">
              {{ formatDateTime(data.createdAt) }}
            </template>
          </Column>

          <Column field="expiresAt" header="Expires" sortable>
            <template #body="{ data }">
              {{ formatDateTime(data.expiresAt) }}
            </template>
          </Column>

          <Column field="status" header="Status">
            <template #body="{ data }">
              <Tag :severity="getStatusSeverity(data.status)" :value="data.status" />
            </template>
          </Column>

          <Column field="usedBy.email" header="Used By">
            <template #body="{ data }">
              {{ data.usedBy?.email || '-' }}
            </template>
          </Column>

          <Column header="Actions" :exportable="false" style="min-width: 150px">
            <template #body="{ data }">
              <div class="flex gap-2">
                <Button
                  icon="pi pi-copy"
                  rounded
                  text
                  severity="info"
                  @click="copyInvitationLink(data)"
                  v-tooltip="'Copy Link'"
                  :disabled="data.status !== 'PENDING'"
                />
                <Button
                  icon="pi pi-ban"
                  rounded
                  text
                  severity="warning"
                  @click="confirmRevoke(data)"
                  v-tooltip="'Revoke Invitation'"
                  :disabled="data.status !== 'PENDING'"
                />
              </div>
            </template>
          </Column>

          <template #empty>
            <div class="text-center p-4">
              No invitations found.
            </div>
          </template>
        </DataTable>
      </div>

      <!-- Mobile Card View -->
      <div class="mobile-only">
        <div v-if="loading" class="text-center p-4">
          <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
        </div>

        <div v-else-if="invitations.length === 0" class="text-center p-4 card">
          No invitations found.
        </div>

        <div v-else class="invitation-cards">
          <div v-for="invitation in invitations" :key="invitation.id" class="invitation-card">
            <div class="invitation-card-header">
              <div class="invitation-info">
                <div class="invitation-token">{{ invitation.token.substring(0, 16) }}...</div>
                <div class="invitation-creator">{{ invitation.createdBy?.email || '-' }}</div>
              </div>
              <Tag :severity="getStatusSeverity(invitation.status)" :value="invitation.status" />
            </div>

            <div class="invitation-card-body">
              <div class="invitation-stat">
                <span class="stat-label">Created</span>
                <span class="stat-value">{{ formatDateTime(invitation.createdAt) }}</span>
              </div>
              <div class="invitation-stat">
                <span class="stat-label">Expires</span>
                <span class="stat-value">{{ formatDateTime(invitation.expiresAt) }}</span>
              </div>
            </div>

            <div v-if="invitation.usedBy" class="invitation-used">
              <i class="pi pi-user"></i>
              <span>Used by: {{ invitation.usedBy.email }}</span>
            </div>

            <div class="invitation-card-actions">
              <Button
                icon="pi pi-copy"
                label="Copy Link"
                rounded
                text
                severity="info"
                @click="copyInvitationLink(invitation)"
                :disabled="invitation.status !== 'PENDING'"
                size="small"
              />
              <Button
                icon="pi pi-ban"
                label="Revoke"
                rounded
                text
                severity="warning"
                @click="confirmRevoke(invitation)"
                :disabled="invitation.status !== 'PENDING'"
                size="small"
              />
            </div>
          </div>
        </div>

        <!-- Mobile Pagination -->
        <div class="mobile-pagination" v-if="invitations.length > 0">
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

      <!-- Create Invitation Dialog -->
      <Dialog
        v-model:visible="showCreateDialog"
        header="Create Invitation"
        :modal="true"
        :style="{ width: '450px' }"
        @show="initializeNewInvitation"
      >
        <div class="invitation-dialog-form">
          <div class="form-field">
            <label for="expiresAt">Expiration Date</label>
            <DatePicker
              id="expiresAt"
              v-model="newInvitation.expiresAt"
              showTime
              hourFormat="24"
              :minDate="new Date()"
              placeholder="Select expiration date"
              class="w-full"
            />
          </div>
        </div>

        <template #footer>
          <Button label="Cancel" icon="pi pi-times" text @click="showCreateDialog = false" />
          <Button
            label="Create"
            icon="pi pi-check"
            @click="createInvitation"
            :loading="creating"
          />
        </template>
      </Dialog>

      <!-- Show Link Dialog -->
      <Dialog
        v-model:visible="showLinkDialog"
        header="Invitation Link Created"
        :modal="true"
        :style="{ width: '650px' }"
      >
        <div class="invitation-dialog-content">
          <p class="invitation-dialog-text">Share this link with the user you want to invite:</p>

          <div class="invitation-link-container">
            <InputText
              :value="generatedLink"
              readonly
              class="invitation-link-input"
            />
            <Button
              icon="pi pi-copy"
              @click="copyToClipboard(generatedLink)"
              v-tooltip="'Copy to clipboard'"
              class="copy-button"
            />
          </div>

          <div class="invitation-expiry">
            <i class="pi pi-clock"></i>
            <span>Expires: {{ formatDateTime(generatedExpiry) }}</span>
          </div>

          <Message severity="info" :closable="false" class="invitation-message">
            This link can only be used once and will expire on the date shown above.
          </Message>
        </div>

        <template #footer>
          <Button label="Close" @click="showLinkDialog = false" />
        </template>
      </Dialog>

      <!-- Revoke Confirmation Dialog -->
      <Dialog
        v-model:visible="revokeDialogVisible"
        header="Confirm Revoke"
        :modal="true"
        :style="{ width: '450px' }"
      >
        <div class="flex align-items-center gap-3 mb-3">
          <i class="pi pi-exclamation-triangle text-4xl text-orange-500"></i>
          <span>
            Are you sure you want to revoke this invitation?
            <br><br>
            The invitation link will no longer be usable.
          </span>
        </div>
        <template #footer>
          <Button label="Cancel" icon="pi pi-times" text @click="revokeDialogVisible = false" />
          <Button
            label="Revoke"
            icon="pi pi-ban"
            severity="warning"
            @click="revokeInvitation"
            :loading="revoking"
          />
        </template>
      </Dialog>

      <Toast />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import Toast from 'primevue/toast'
import Breadcrumb from 'primevue/breadcrumb'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import apiService from '@/utils/apiService'
import { copyToClipboard as copyTextToClipboard } from '@/utils/clipboardUtils'

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
  { label: 'Invitations' }
])

const invitations = ref([])
const loading = ref(false)
const totalRecords = ref(0)
const page = ref(0)
const pageSize = ref(10)
const statusFilter = ref(null)

const statusOptions = ref([
  { label: 'All Statuses', value: null },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Used', value: 'USED' },
  { label: 'Expired', value: 'EXPIRED' },
  { label: 'Revoked', value: 'REVOKED' }
])

const showCreateDialog = ref(false)
const showLinkDialog = ref(false)
const revokeDialogVisible = ref(false)
const creating = ref(false)
const revoking = ref(false)

const newInvitation = ref({
  expiresAt: null
})

const generatedLink = ref('')
const generatedExpiry = ref(null)
const invitationToRevoke = ref(null)
const baseUrl = ref('')

const loadInvitations = async () => {
  loading.value = true
  try {
    const params = new URLSearchParams({
      page: page.value.toString(),
      size: pageSize.value.toString()
    })

    if (statusFilter.value) {
      params.append('status', statusFilter.value)
    }

    const response = await apiService.get(`/admin/invitations?${params.toString()}`)
    invitations.value = response.content
    totalRecords.value = response.totalElements
  } catch (error) {
    console.error('Failed to load invitations:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load invitations',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const loadBaseUrl = async () => {
  try {
    const response = await apiService.get('/admin/invitations/base-url')
    baseUrl.value = response.baseUrl || ''
  } catch (error) {
    console.error('Failed to load base URL:', error)
  }
}

const onPage = (event) => {
  page.value = event.page
  pageSize.value = event.rows
  loadInvitations()
}

const onFilterChange = () => {
  page.value = 0
  loadInvitations()
}

const initializeNewInvitation = () => {
  // Set default expiration to 7 days from now
  const defaultExpiry = new Date()
  defaultExpiry.setDate(defaultExpiry.getDate() + 7)
  newInvitation.value.expiresAt = defaultExpiry
}

const createInvitation = async () => {
  creating.value = true
  try {
    const payload = {}
    if (newInvitation.value.expiresAt) {
      payload.expiresAt = newInvitation.value.expiresAt.toISOString()
    }

    const response = await apiService.post('/admin/invitations', payload)

    // Build the full URL
    const effectiveBaseUrl = baseUrl.value || window.location.origin
    generatedLink.value = `${effectiveBaseUrl}/register/invite/${response.token}`
    generatedExpiry.value = response.expiresAt

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Invitation created successfully',
      life: 3000
    })

    showCreateDialog.value = false
    showLinkDialog.value = true
    newInvitation.value.expiresAt = null
    loadInvitations()
  } catch (error) {
    console.error('Failed to create invitation:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.error || 'Failed to create invitation',
      life: 3000
    })
  } finally {
    creating.value = false
  }
}

const copyInvitationLink = async (invitation) => {
  const effectiveBaseUrl = baseUrl.value || window.location.origin
  const link = `${effectiveBaseUrl}/register/invite/${invitation.token}`
  await copyToClipboard(link)
}

const copyToClipboard = async (text) => {
  const success = await copyTextToClipboard(text)

  if (success) {
    toast.add({
      severity: 'success',
      summary: 'Copied',
      detail: 'Link copied to clipboard',
      life: 3000
    })
  } else {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to copy to clipboard',
      life: 3000
    })
  }
}

const confirmRevoke = (invitation) => {
  invitationToRevoke.value = invitation
  revokeDialogVisible.value = true
}

const revokeInvitation = async () => {
  if (!invitationToRevoke.value) return

  revoking.value = true
  try {
    await apiService.delete(`/admin/invitations/${invitationToRevoke.value.id}`)

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Invitation revoked successfully',
      life: 3000
    })

    revokeDialogVisible.value = false
    invitationToRevoke.value = null
    loadInvitations()
  } catch (error) {
    console.error('Failed to revoke invitation:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.error || 'Failed to revoke invitation',
      life: 3000
    })
  } finally {
    revoking.value = false
  }
}

const getStatusSeverity = (status) => {
  const severityMap = {
    PENDING: 'info',
    USED: 'success',
    EXPIRED: 'warn',
    REVOKED: 'danger'
  }
  return severityMap[status] || 'secondary'
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString()
}

// Mobile pagination methods
const goToFirstPage = () => {
  page.value = 0
  loadInvitations()
}

const goToPrevPage = () => {
  if (page.value > 0) {
    page.value--
    loadInvitations()
  }
}

const goToNextPage = () => {
  if ((page.value + 1) * pageSize.value < totalRecords.value) {
    page.value++
    loadInvitations()
  }
}

const goToLastPage = () => {
  page.value = Math.floor(totalRecords.value / pageSize.value)
  loadInvitations()
}

onMounted(() => {
  loadBaseUrl()
  loadInvitations()
})
</script>

<style scoped>
.admin-invitations {
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

.header-actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.no-underline {
  text-decoration: none;
}

.card {
  background: var(--surface-card);
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  padding: 1rem;
}

.text-muted {
  color: var(--text-color-secondary);
}

.font-mono {
  font-family: monospace;
}

/* Invitation Dialog Styles */
.invitation-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.invitation-dialog-text {
  margin: 0 0 0.5rem 0;
  font-size: 0.95rem;
  color: var(--text-color);
}

.invitation-link-container {
  display: flex;
  gap: 0.75rem;
  align-items: stretch;
}

.invitation-link-input {
  flex: 1;
  font-family: monospace;
  font-size: 0.875rem;
  min-width: 0;
}

.copy-button {
  flex-shrink: 0;
}

.invitation-expiry {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: var(--surface-100);
  border-radius: 6px;
  font-size: 0.875rem;
  color: var(--text-color-secondary);
}

.invitation-expiry i {
  color: var(--primary-color);
}

.invitation-message {
  margin: 0;
}

/* Create Invitation Dialog Form */
.invitation-dialog-form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-field label {
  font-weight: 500;
  color: var(--text-color);
}

.help-text {
  display: block;
  margin-top: 0.25rem;
  font-size: 0.875rem;
  line-height: 1.4;
}

/* Filter Container */
.filter-container {
  margin-bottom: 1rem;
}

/* Desktop/Mobile Toggle */
.desktop-only {
  display: block;
}

.mobile-only {
  display: none;
}

/* Mobile Invitation Cards */
.invitation-cards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0.5rem;
  border-radius: 8px;
}

.invitation-card {
  background: var(--gp-surface-white);
  border: 2px solid var(--surface-border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(255, 255, 255, 0.05);
  transition: all 0.2s ease;
}

/* Dark theme specific */
:global(.p-dark) .invitation-card,
:global([data-theme="dark"]) .invitation-card,
:global(html.dark) .invitation-card {
  background: var(--gp-surface-dark);
}

.invitation-card:active {
  transform: scale(0.98);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}

.invitation-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.75rem;
  gap: 0.5rem;
}

.invitation-info {
  flex: 1;
  min-width: 0;
}

.invitation-token {
  font-family: monospace;
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--text-color);
  margin-bottom: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
}

.invitation-creator {
  font-size: 0.85rem;
  color: var(--text-color-secondary);
}

.invitation-card-body {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--surface-border);
}

.invitation-stat {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  flex: 1;
}

.stat-label {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  text-transform: uppercase;
}

.stat-value {
  font-weight: 500;
  font-size: 0.85rem;
}

.invitation-used {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem;
  margin-bottom: 0.5rem;
  background: var(--surface-100);
  border-radius: 6px;
  font-size: 0.85rem;
  color: var(--text-color-secondary);
}

.invitation-used i {
  color: var(--primary-color);
}

.invitation-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--surface-border);
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

/* Responsive adjustments */
@media (max-width: 768px) {
  .admin-invitations {
    padding: 0.75rem;
  }

  .admin-breadcrumb {
    margin-bottom: 0.75rem;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
    margin-bottom: 1rem;
  }

  .page-header h1 {
    font-size: 1.5rem;
  }

  .page-header p {
    margin: 0.25rem 0 0 0;
  }

  .header-actions {
    width: 100%;
    flex-direction: column;
  }

  .header-actions .no-underline,
  .header-actions > button {
    width: 100%;
  }

  .header-actions button {
    width: 100%;
  }

  .filter-container {
    margin-bottom: 0.75rem;
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

  .invitation-link-container {
    flex-direction: column;
  }

  .invitation-link-input {
    width: 100%;
  }

  .copy-button {
    width: 100%;
  }
}

/* Extra small screens */
@media (max-width: 480px) {
  .admin-invitations {
    padding: 0.5rem;
  }

  .page-header h1 {
    font-size: 1.25rem;
  }

  .invitation-card {
    padding: 0.75rem;
  }

  .invitation-card-body {
    gap: 1rem;
  }
}
</style>
