<template>
  <AppLayout>
    <div class="admin-timeline-regeneration">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <div>
          <h1>Timeline Regeneration</h1>
          <p class="text-muted">Create and monitor forced timeline regeneration campaigns</p>
        </div>
        <div class="header-actions">
          <Button
            label="Refresh"
            icon="pi pi-refresh"
            severity="secondary"
            outlined
            :loading="loading"
            @click="loadCampaigns"
          />
          <Button
            label="Create Campaign"
            icon="pi pi-plus"
            @click="openCreateDialog"
          />
        </div>
      </div>

      <div class="card desktop-only">
        <DataTable
          :value="campaigns"
          :loading="loading"
          :paginator="campaigns.length > pageSize"
          :rows="pageSize"
          dataKey="id"
          responsiveLayout="scroll"
          :rowsPerPageOptions="[10, 25, 50]"
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
          currentPageReportTemplate="Showing {first} to {last} of {totalRecords} campaigns"
        >
          <Column field="campaignKey" header="Campaign" style="min-width: 220px">
            <template #body="{ data }">
              <div class="campaign-key">{{ data.campaignKey }}</div>
              <div class="campaign-reason" v-tooltip="data.reason">{{ truncate(data.reason, 90) }}</div>
            </template>
          </Column>

          <Column field="source" header="Source" style="min-width: 110px">
            <template #body="{ data }">
              <Tag :severity="sourceSeverity(data.source)" :value="data.source" />
            </template>
          </Column>

          <Column field="status" header="Status" style="min-width: 120px">
            <template #body="{ data }">
              <Tag :severity="statusSeverity(data.status)" :value="data.status" />
            </template>
          </Column>

          <Column field="affectedFrom" header="Affected From" style="min-width: 180px">
            <template #body="{ data }">
              {{ formatDateTime(data.affectedFrom) }}
            </template>
          </Column>

          <Column header="Progress" style="min-width: 220px">
            <template #body="{ data }">
              <div class="progress-text">{{ formatProgress(data) }}</div>
              <div class="progress-breakdown">
                <span>{{ data.pendingUsers }} pending</span>
                <span>{{ data.runningUsers }} running</span>
                <span>{{ data.completedUsers }} done</span>
              </div>
            </template>
          </Column>

          <Column field="failedUsers" header="Failed" style="min-width: 100px">
            <template #body="{ data }">
              <Tag
                :severity="data.failedUsers > 0 ? 'danger' : 'secondary'"
                :value="String(data.failedUsers)"
              />
            </template>
          </Column>

          <Column field="createdAt" header="Created" style="min-width: 180px">
            <template #body="{ data }">
              {{ formatDateTime(data.createdAt) }}
            </template>
          </Column>

          <Column field="completedAt" header="Completed" style="min-width: 180px">
            <template #body="{ data }">
              {{ formatDateTime(data.completedAt) }}
            </template>
          </Column>

          <Column header="Actions" :exportable="false" style="min-width: 150px">
            <template #body="{ data }">
              <div class="flex gap-2">
                <Button
                  icon="pi pi-eye"
                  rounded
                  text
                  severity="info"
                  v-tooltip="'View Details'"
                  @click="openDetails(data)"
                />
                <Button
                  icon="pi pi-replay"
                  rounded
                  text
                  severity="warning"
                  v-tooltip="'Retry Failed Users'"
                  :disabled="data.failedUsers === 0"
                  :loading="retryingCampaignId === data.id"
                  @click="retryFailed(data)"
                />
              </div>
            </template>
          </Column>

          <template #empty>
            <div class="text-center p-4">No timeline regeneration campaigns found.</div>
          </template>
        </DataTable>
      </div>

      <div class="mobile-only">
        <div v-if="loading" class="text-center p-4">
          <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
        </div>
        <div v-else-if="campaigns.length === 0" class="text-center p-4 card">
          No timeline regeneration campaigns found.
        </div>
        <div v-else class="campaign-cards">
          <div v-for="campaign in campaigns" :key="campaign.id" class="campaign-card">
            <div class="campaign-card-header">
              <div>
                <div class="campaign-key">{{ campaign.campaignKey }}</div>
                <div class="campaign-card-date">{{ formatDateTime(campaign.affectedFrom) }}</div>
              </div>
              <Tag :severity="statusSeverity(campaign.status)" :value="campaign.status" />
            </div>
            <div class="campaign-card-reason">{{ campaign.reason }}</div>
            <div class="campaign-card-stats">
              <div><span>Total</span><strong>{{ campaign.totalUsers }}</strong></div>
              <div><span>Done</span><strong>{{ campaign.completedUsers }}</strong></div>
              <div><span>Failed</span><strong :class="{ danger: campaign.failedUsers > 0 }">{{ campaign.failedUsers }}</strong></div>
            </div>
            <div class="campaign-card-actions">
              <Button
                label="Details"
                icon="pi pi-eye"
                size="small"
                text
                @click="openDetails(campaign)"
              />
              <Button
                label="Retry Failed"
                icon="pi pi-replay"
                size="small"
                text
                severity="warning"
                :disabled="campaign.failedUsers === 0"
                :loading="retryingCampaignId === campaign.id"
                @click="retryFailed(campaign)"
              />
            </div>
          </div>
        </div>
      </div>

      <Dialog
        v-model:visible="createDialogVisible"
        header="Create Timeline Regeneration Campaign"
        :modal="true"
        :style="{ width: '640px', maxWidth: '95vw' }"
      >
        <div class="dialog-form">
          <div class="form-field">
            <label for="campaignKey">Campaign Key</label>
            <InputText
              id="campaignKey"
              v-model="createForm.campaignKey"
              maxlength="120"
              placeholder="july-12-timeline-repair"
              class="w-full"
            />
          </div>

          <div class="form-field">
            <label for="affectedFrom">Regenerate From</label>
            <DatePicker
              id="affectedFrom"
              v-model="createForm.affectedFrom"
              showTime
              hourFormat="24"
              :dateFormat="timezone.getPrimeVueDatePickerFormat()"
              placeholder="Select cutoff date and time"
              class="w-full"
            />
          </div>

          <div class="form-field">
            <label for="reason">Reason</label>
            <Textarea
              id="reason"
              v-model="createForm.reason"
              rows="4"
              autoResize
              placeholder="Explain why timelines must be regenerated. Users will see this message."
              class="w-full"
            />
          </div>

          <div class="preview-panel" :class="{ stale: preview && !previewMatchesCurrentDate }">
            <div>
              <div class="preview-title">Affected Users</div>
              <div class="preview-value">
                <template v-if="preview && previewMatchesCurrentDate">{{ preview.affectedUsers }}</template>
                <template v-else>Preview required</template>
              </div>
              <div class="preview-help">
                Preview counts users with GPS data at or after the selected timestamp.
              </div>
            </div>
            <Button
              label="Run Preview"
              icon="pi pi-search"
              severity="secondary"
              outlined
              :disabled="!canPreview"
              :loading="previewLoading"
              @click="runPreview"
            />
          </div>
        </div>

        <template #footer>
          <Button label="Cancel" icon="pi pi-times" text @click="createDialogVisible = false" />
          <Button
            label="Review Create"
            icon="pi pi-check"
            :disabled="!canReviewCreate"
            @click="confirmationVisible = true"
          />
        </template>
      </Dialog>

      <Dialog
        v-model:visible="confirmationVisible"
        header="Confirm Timeline Regeneration"
        :modal="true"
        :style="{ width: '560px', maxWidth: '95vw' }"
      >
        <div class="confirmation-content">
          <div class="confirmation-row">
            <span>Campaign</span>
            <strong>{{ createForm.campaignKey.trim() }}</strong>
          </div>
          <div class="confirmation-row">
            <span>Regenerate From</span>
            <strong>{{ formatDateTime(currentAffectedFromIso) }}</strong>
          </div>
          <div class="confirmation-row">
            <span>Affected Users</span>
            <strong>{{ preview?.affectedUsers ?? 0 }}</strong>
          </div>
          <div class="confirmation-reason">
            <span>Reason</span>
            <p>{{ createForm.reason.trim() }}</p>
          </div>
        </div>

        <template #footer>
          <Button label="Back" icon="pi pi-arrow-left" text @click="confirmationVisible = false" />
          <Button
            label="Create Campaign"
            icon="pi pi-check"
            severity="warning"
            :loading="creating"
            :disabled="!canReviewCreate"
            @click="createCampaign"
          />
        </template>
      </Dialog>

      <Dialog
        v-model:visible="detailsVisible"
        header="Timeline Regeneration Details"
        :modal="true"
        :style="{ width: '900px', maxWidth: '95vw' }"
      >
        <div v-if="detailLoading" class="text-center p-4">
          <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
        </div>
        <div v-else-if="campaignDetails" class="details-content">
          <div class="details-grid">
            <div>
              <span class="detail-label">Campaign</span>
              <strong>{{ campaignDetails.campaign.campaignKey }}</strong>
            </div>
            <div>
              <span class="detail-label">Status</span>
              <Tag :severity="statusSeverity(campaignDetails.campaign.status)" :value="campaignDetails.campaign.status" />
            </div>
            <div>
              <span class="detail-label">Source</span>
              <Tag :severity="sourceSeverity(campaignDetails.campaign.source)" :value="campaignDetails.campaign.source" />
            </div>
            <div>
              <span class="detail-label">Affected From</span>
              <strong>{{ formatDateTime(campaignDetails.campaign.affectedFrom) }}</strong>
            </div>
          </div>

          <div class="details-reason">
            <span class="detail-label">Reason</span>
            <p>{{ campaignDetails.campaign.reason }}</p>
          </div>

          <div class="details-stats">
            <div><span>Total</span><strong>{{ campaignDetails.campaign.totalUsers }}</strong></div>
            <div><span>Pending</span><strong>{{ campaignDetails.campaign.pendingUsers }}</strong></div>
            <div><span>Running</span><strong>{{ campaignDetails.campaign.runningUsers }}</strong></div>
            <div><span>Completed</span><strong>{{ campaignDetails.campaign.completedUsers }}</strong></div>
            <div><span>Failed</span><strong :class="{ danger: campaignDetails.campaign.failedUsers > 0 }">{{ campaignDetails.campaign.failedUsers }}</strong></div>
            <div><span>Skipped</span><strong>{{ campaignDetails.campaign.skippedUsers }}</strong></div>
          </div>

          <div class="failed-users-header">
            <h3>Failed Users</h3>
            <Button
              label="Retry Failed"
              icon="pi pi-replay"
              severity="warning"
              size="small"
              :disabled="campaignDetails.campaign.failedUsers === 0"
              :loading="retryingCampaignId === campaignDetails.campaign.id"
              @click="retryFailed(campaignDetails.campaign)"
            />
          </div>

          <DataTable
            :value="campaignDetails.failedUsers || []"
            dataKey="id"
            responsiveLayout="scroll"
            :paginator="(campaignDetails.failedUsers || []).length > 10"
            :rows="10"
          >
            <Column field="email" header="Email" style="min-width: 220px">
              <template #body="{ data }">
                <div>{{ data.email }}</div>
                <div class="text-muted small">{{ data.fullName || data.userId }}</div>
              </template>
            </Column>
            <Column field="attempts" header="Attempts" style="min-width: 100px" />
            <Column field="lastError" header="Last Error" style="min-width: 260px">
              <template #body="{ data }">
                <span v-tooltip="data.lastError">{{ truncate(data.lastError || '-', 100) }}</span>
              </template>
            </Column>
            <Column field="updatedAt" header="Updated" style="min-width: 180px">
              <template #body="{ data }">
                {{ formatDateTime(data.updatedAt) }}
              </template>
            </Column>
            <template #empty>
              <div class="text-center p-4">No failed users.</div>
            </template>
          </DataTable>
        </div>

        <template #footer>
          <Button label="Close" icon="pi pi-times" text @click="detailsVisible = false" />
        </template>
      </Dialog>

      <Toast />
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import Toast from 'primevue/toast'
import Breadcrumb from 'primevue/breadcrumb'
import DatePicker from 'primevue/datepicker'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import adminService from '@/utils/adminService'
import { useTimezone } from '@/composables/useTimezone'

const router = useRouter()
const toast = useToast()
const timezone = useTimezone()

const breadcrumbHome = ref({
  icon: 'pi pi-home',
  command: () => router.push('/')
})
const breadcrumbItems = ref([
  {
    label: 'Administration',
    command: () => router.push('/app/admin')
  },
  { label: 'Timeline Regeneration' }
])

const campaigns = ref([])
const loading = ref(false)
const pageSize = ref(10)
const retryingCampaignId = ref(null)

const createDialogVisible = ref(false)
const confirmationVisible = ref(false)
const creating = ref(false)
const previewLoading = ref(false)
const preview = ref(null)
const previewDateIso = ref(null)
const createForm = ref({
  campaignKey: '',
  affectedFrom: null,
  reason: ''
})

const detailsVisible = ref(false)
const detailLoading = ref(false)
const campaignDetails = ref(null)

function toApiInstant(value) {
  if (!value) {
    return null
  }
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  return date.toISOString()
}

const currentAffectedFromIso = computed(() => toApiInstant(createForm.value.affectedFrom))
const previewMatchesCurrentDate = computed(() => (
  !!preview.value
  && !!previewDateIso.value
  && previewDateIso.value === currentAffectedFromIso.value
))
const canPreview = computed(() => !!currentAffectedFromIso.value && !previewLoading.value)
const canReviewCreate = computed(() => (
  createForm.value.campaignKey.trim().length > 0
  && createForm.value.reason.trim().length > 0
  && previewMatchesCurrentDate.value
  && !creating.value
))

watch(currentAffectedFromIso, (value) => {
  if (previewDateIso.value && previewDateIso.value !== value) {
    preview.value = null
    previewDateIso.value = null
  }
})

const loadCampaigns = async () => {
  loading.value = true
  try {
    campaigns.value = await adminService.getTimelineRegenerationCampaigns()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: extractApiErrorMessage(error, 'Failed to load timeline regeneration campaigns'),
      life: 4000
    })
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  const now = new Date()
  createForm.value = {
    campaignKey: `admin-${now.toISOString().slice(0, 19).replaceAll('-', '').replaceAll(':', '').replace('T', '')}`,
    affectedFrom: null,
    reason: ''
  }
  preview.value = null
  previewDateIso.value = null
  confirmationVisible.value = false
  createDialogVisible.value = true
}

const runPreview = async () => {
  if (!currentAffectedFromIso.value) {
    return
  }
  previewLoading.value = true
  try {
    const selectedIso = currentAffectedFromIso.value
    preview.value = await adminService.previewTimelineRegenerationCampaign({
      affectedFrom: selectedIso
    })
    previewDateIso.value = selectedIso
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Preview Failed',
      detail: extractApiErrorMessage(error, 'Failed to preview affected users'),
      life: 5000
    })
  } finally {
    previewLoading.value = false
  }
}

const createCampaign = async () => {
  if (!canReviewCreate.value) {
    return
  }
  creating.value = true
  try {
    await adminService.createTimelineRegenerationCampaign({
      campaignKey: createForm.value.campaignKey.trim(),
      affectedFrom: currentAffectedFromIso.value,
      reason: createForm.value.reason.trim()
    })
    toast.add({
      severity: 'success',
      summary: 'Campaign Created',
      detail: 'Timeline regeneration campaign was created.',
      life: 4000
    })
    confirmationVisible.value = false
    createDialogVisible.value = false
    await loadCampaigns()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Create Failed',
      detail: extractApiErrorMessage(error, 'Failed to create timeline regeneration campaign'),
      life: 5000
    })
  } finally {
    creating.value = false
  }
}

const openDetails = async (campaign) => {
  detailsVisible.value = true
  detailLoading.value = true
  campaignDetails.value = null
  try {
    campaignDetails.value = await adminService.getTimelineRegenerationCampaign(campaign.id)
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: extractApiErrorMessage(error, 'Failed to load campaign details'),
      life: 4000
    })
    detailsVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const retryFailed = async (campaign) => {
  if (!campaign || campaign.failedUsers === 0) {
    return
  }
  retryingCampaignId.value = campaign.id
  try {
    await adminService.retryTimelineRegenerationCampaignFailedUsers(campaign.id)
    toast.add({
      severity: 'success',
      summary: 'Retry Queued',
      detail: 'Failed campaign users were queued for retry.',
      life: 4000
    })
    await loadCampaigns()
    if (detailsVisible.value) {
      campaignDetails.value = await adminService.getTimelineRegenerationCampaign(campaign.id)
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Retry Failed',
      detail: extractApiErrorMessage(error, 'Failed to retry campaign users'),
      life: 5000
    })
  } finally {
    retryingCampaignId.value = null
  }
}

const formatDateTime = (value) => {
  if (!value) {
    return '-'
  }
  return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value, { withSeconds: true })}`
}

const formatProgress = (campaign) => {
  const processed = campaign.completedUsers + campaign.failedUsers + campaign.skippedUsers
  return `${processed} / ${campaign.totalUsers} processed`
}

const truncate = (value, length) => {
  if (!value) {
    return '-'
  }
  return value.length > length ? `${value.slice(0, length - 1)}...` : value
}

const statusSeverity = (status) => {
  if (status === 'ACTIVE') return 'info'
  if (status === 'COMPLETED') return 'success'
  if (status === 'CANCELLED') return 'secondary'
  return 'secondary'
}

const sourceSeverity = (source) => {
  if (source === 'ADMIN') return 'warning'
  if (source === 'RELEASE') return 'info'
  return 'secondary'
}

const extractApiErrorMessage = (error, fallback) => (
  error?.response?.data?.message
  || error?.response?.data?.error
  || error?.response?.data?.data?.message
  || error?.userMessage
  || error?.message
  || fallback
)

onMounted(() => {
  loadCampaigns()
})
</script>

<style scoped>
.admin-timeline-regeneration {
  padding: 1.5rem;
}

.admin-breadcrumb {
  margin-bottom: 1.5rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
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

.small {
  font-size: 0.8rem;
}

.header-actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.desktop-only {
  display: block;
}

.mobile-only {
  display: none;
}

.campaign-key {
  font-weight: 600;
  color: var(--text-color);
  word-break: break-word;
}

.campaign-reason,
.campaign-card-date {
  color: var(--text-color-secondary);
  font-size: 0.875rem;
  margin-top: 0.25rem;
}

.progress-text {
  font-weight: 600;
}

.progress-breakdown {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  color: var(--text-color-secondary);
  font-size: 0.8rem;
  margin-top: 0.25rem;
}

.campaign-cards {
  display: grid;
  gap: 1rem;
}

.campaign-card {
  background: var(--surface-card);
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  padding: 1rem;
}

.campaign-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.campaign-card-reason {
  margin: 0.75rem 0;
  color: var(--text-color-secondary);
}

.campaign-card-stats,
.details-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
}

.campaign-card-stats div,
.details-stats div {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  padding: 0.75rem;
}

.campaign-card-stats span,
.details-stats span,
.detail-label {
  display: block;
  color: var(--text-color-secondary);
  font-size: 0.8rem;
  margin-bottom: 0.25rem;
}

.campaign-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 0.75rem;
}

.dialog-form {
  display: grid;
  gap: 1rem;
}

.form-field label {
  display: block;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.preview-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  padding: 1rem;
  background: var(--surface-ground);
}

.preview-panel.stale {
  border-color: var(--yellow-500);
}

.preview-title {
  font-size: 0.8rem;
  color: var(--text-color-secondary);
}

.preview-value {
  font-size: 1.25rem;
  font-weight: 700;
}

.preview-help {
  color: var(--text-color-secondary);
  font-size: 0.85rem;
  margin-top: 0.25rem;
}

.confirmation-content {
  display: grid;
  gap: 1rem;
}

.confirmation-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid var(--surface-border);
  padding-bottom: 0.75rem;
}

.confirmation-row span,
.confirmation-reason span {
  color: var(--text-color-secondary);
}

.confirmation-reason p,
.details-reason p {
  margin: 0.5rem 0 0;
  white-space: pre-wrap;
}

.details-content {
  display: grid;
  gap: 1.25rem;
}

.details-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
}

.failed-users-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.failed-users-header h3 {
  margin: 0;
}

.danger {
  color: var(--red-500);
}

@media (max-width: 960px) {
  .desktop-only {
    display: none;
  }

  .mobile-only {
    display: block;
  }

  .page-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    justify-content: stretch;
  }

  .header-actions :deep(.p-button) {
    flex: 1;
  }

  .details-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .admin-timeline-regeneration {
    padding: 1rem;
  }

  .preview-panel,
  .confirmation-row,
  .failed-users-header {
    flex-direction: column;
    align-items: stretch;
  }

  .campaign-card-stats,
  .details-stats,
  .details-grid {
    grid-template-columns: 1fr;
  }
}
</style>
