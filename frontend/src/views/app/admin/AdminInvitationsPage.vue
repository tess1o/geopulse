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

      <div class="card">
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
          <template #header>
            <div class="flex justify-content-between align-items-center">
              <Select
                v-model="statusFilter"
                :options="statusOptions"
                optionLabel="label"
                optionValue="value"
                placeholder="Filter by status"
                @change="onFilterChange"
                class="w-15rem"
              />
            </div>
          </template>

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
  try {
    await navigator.clipboard.writeText(text)
    toast.add({
      severity: 'success',
      summary: 'Copied',
      detail: 'Link copied to clipboard',
      life: 3000
    })
  } catch (error) {
    console.error('Failed to copy to clipboard:', error)
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

/* Responsive adjustments */
@media (max-width: 768px) {
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
</style>
