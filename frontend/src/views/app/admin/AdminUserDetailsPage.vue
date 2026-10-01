<template>
  <AppLayout :padding="'none'">
    <div class="admin-user-details">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <h1>{{ t('adminCampaignsAndUsers.userDetailsPage.title') }}</h1>
      </div>

      <DemoReadOnlyBanner />

    <div v-if="loading" class="text-center p-4">
      <i class="pi pi-spin pi-spinner text-4xl"></i>
    </div>

    <div v-else-if="user" class="user-details-container">
      <!-- User Header Card -->
      <div class="card user-header-card">
        <div class="user-header">
          <Avatar
            :image="user.avatar"
            :label="user.fullName?.charAt(0) || user.email?.charAt(0)"
            size="xlarge"
            shape="circle"
            class="user-avatar"
          />
          <div class="user-header-info">
            <h2>{{ user.fullName || t('adminCampaignsAndUsers.userDetailsPage.noName') }}</h2>
            <p class="user-email">{{ user.email }}</p>
            <div class="user-badges">
              <Tag :severity="user.role === 'ADMIN' ? 'warning' : 'info'" :value="user.role" />
              <Tag :severity="user.active ? 'success' : 'danger'" :value="user.active ? t('admin.usersPage.statusActive') : t('admin.usersPage.statusDisabled')" />
            </div>
          </div>
        </div>
      </div>

      <!-- Main Content Grid -->
      <div class="user-details-grid">
        <!-- Left Column: Info Cards -->
        <div class="left-column">
          <div class="info-cards-row">
            <!-- User Information Card -->
            <div class="card">
              <div class="card-title">
                <i class="pi pi-user"></i>
                <h3>{{ t('adminCampaignsAndUsers.userDetailsPage.infoCard.title') }}</h3>
              </div>
              <div class="card-content">
                <div class="info-group">
                  <div class="info-item">
                    <label><i class="pi pi-key"></i> {{ t('adminCampaignsAndUsers.userDetailsPage.infoCard.authentication') }}</label>
                    <span class="info-value">{{ user.hasPassword ? t('adminCampaignsAndUsers.userDetailsPage.infoCard.password') : t('adminCampaignsAndUsers.userDetailsPage.infoCard.oidcOnly') }}</span>
                  </div>
                  <div class="info-item">
                    <label><i class="pi pi-globe"></i> {{ t('adminCampaignsAndUsers.userDetailsPage.infoCard.timezone') }}</label>
                    <span class="info-value">{{ user.timezone }}</span>
                  </div>
                  <div class="info-item" v-if="user.linkedOidcProviders?.length">
                    <label><i class="pi pi-link"></i> {{ t('adminCampaignsAndUsers.userDetailsPage.infoCard.linkedOidcProviders') }}</label>
                    <span class="info-value">{{ user.linkedOidcProviders.join(', ') }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Activity & Stats Card -->
            <div class="card">
              <div class="card-title">
                <i class="pi pi-chart-line"></i>
                <h3>{{ t('adminCampaignsAndUsers.userDetailsPage.statsCard.title') }}</h3>
              </div>
              <div class="card-content">
                <div class="info-group">
                  <div class="info-item">
                    <label><i class="pi pi-map-marker"></i> {{ t('adminCampaignsAndUsers.userDetailsPage.statsCard.gpsPoints') }}</label>
                    <span class="info-value stat-value">{{ formatNumber(user.gpsPointsCount) }}</span>
                  </div>
                  <div class="info-item">
                    <label><i class="pi pi-clock"></i> {{ t('adminCampaignsAndUsers.userDetailsPage.statsCard.lastGpsPoint') }}</label>
                    <span class="info-value">{{ formatTimeAgo(user.lastGpsPointAt) }}</span>
                  </div>
                  <div class="info-item">
                    <label><i class="pi pi-calendar-plus"></i> {{ t('adminCampaignsAndUsers.userDetailsPage.statsCard.accountCreated') }}</label>
                    <span class="info-value">{{ formatDateTime(user.createdAt) }}</span>
                  </div>
                  <div class="info-item">
                    <label><i class="pi pi-calendar"></i> {{ t('adminCampaignsAndUsers.userDetailsPage.statsCard.lastUpdated') }}</label>
                    <span class="info-value">{{ formatDateTime(user.updatedAt) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Column: Actions Card -->
        <div class="right-column">
          <div class="card actions-card">
            <div class="card-title">
              <i class="pi pi-cog"></i>
              <h3>{{ t('adminCampaignsAndUsers.userDetailsPage.actionsCard.title') }}</h3>
            </div>
            <div class="card-content">
              <div class="actions-buttons">
                <Button
                  :label="user.active ? t('admin.usersPage.disableUser') : t('admin.usersPage.enableUser')"
                  :icon="user.active ? 'pi pi-ban' : 'pi pi-check'"
                  :severity="user.active ? 'warning' : 'success'"
                  @click="toggleStatus"
                  :disabled="adminReadOnly || isCurrentUser"
                  class="action-button-full"
                />

                <Button
                  :label="user.role === 'ADMIN' ? t('adminCampaignsAndUsers.userDetailsPage.actionsCard.demoteToUser') : t('adminCampaignsAndUsers.userDetailsPage.actionsCard.promoteToAdmin')"
                  :icon="user.role === 'ADMIN' ? 'pi pi-user' : 'pi pi-shield'"
                  severity="secondary"
                  outlined
                  @click="toggleRole"
                  :disabled="adminReadOnly"
                  class="action-button-full"
                />

                <Button
                  :label="t('adminCampaignsAndUsers.userDetailsPage.actionsCard.resetPassword')"
                  icon="pi pi-key"
                  severity="info"
                  outlined
                  @click="resetPassword"
                  :disabled="adminReadOnly"
                  class="action-button-full"
                />

                <Divider />

                <Button
                  :label="t('admin.usersPage.deleteUser')"
                  icon="pi pi-trash"
                  severity="danger"
                  @click="confirmDelete"
                  :disabled="adminReadOnly || isCurrentUser"
                  class="action-button-full"
                />
              </div>

              <div v-if="isCurrentUser" class="warning-message">
                <i class="pi pi-info-circle"></i>
                <span>{{ t('adminCampaignsAndUsers.userDetailsPage.actionsCard.warningMessage') }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="card api-tokens-card">
        <div class="card-title">
          <i class="pi pi-key"></i>
          <h3>{{ t('adminCampaignsAndUsers.userDetailsPage.apiTokensCard.title') }}</h3>
        </div>
        <DataTable
          :value="apiTokens"
          :loading="apiTokensLoading"
          dataKey="id"
          responsiveLayout="scroll"
        >
          <Column field="name" :header="t('adminCampaignsAndUsers.userDetailsPage.apiTokensCard.columnName')">
            <template #body="{ data }">
              <div class="token-name">{{ data.name }}</div>
              <div class="token-preview">{{ data.preview }}</div>
            </template>
          </Column>
          <Column field="status" :header="t('adminCampaignsAndUsers.userDetailsPage.apiTokensCard.columnStatus')">
            <template #body="{ data }">
              <Tag :value="formatTokenStatus(data.status)" :severity="tokenStatusSeverity(data.status)" />
            </template>
          </Column>
          <Column field="expiresAt" :header="t('adminCampaignsAndUsers.userDetailsPage.apiTokensCard.columnExpires')">
            <template #body="{ data }">
              {{ formatDateTime(data.expiresAt) || t('adminCampaignsAndUsers.userDetailsPage.never') }}
            </template>
          </Column>
          <Column field="lastUsedAt" :header="t('adminCampaignsAndUsers.userDetailsPage.apiTokensCard.columnLastUsed')">
            <template #body="{ data }">
              <div>{{ formatDateTime(data.lastUsedAt) || t('adminCampaignsAndUsers.userDetailsPage.never') }}</div>
              <small v-if="data.lastUsedIp" class="text-muted">{{ data.lastUsedIp }}</small>
            </template>
          </Column>
          <Column :header="t('adminCampaignsAndUsers.userDetailsPage.apiTokensCard.columnActions')" :exportable="false">
            <template #body="{ data }">
              <Button
                icon="pi pi-ban"
                rounded
                text
                severity="danger"
                :disabled="adminReadOnly || data.status === 'REVOKED'"
                @click="confirmRevokeApiToken(data)"
                v-tooltip="t('adminCampaignsAndUsers.userDetailsPage.apiTokensCard.revokeTooltip')"
              />
            </template>
          </Column>
          <template #empty>
            <div class="text-center p-4">{{ t('adminCampaignsAndUsers.userDetailsPage.apiTokensCard.empty') }}</div>
          </template>
        </DataTable>
      </div>
    </div>

    <div v-else class="card p-4 text-center">
      <p>{{ t('adminCampaignsAndUsers.userDetailsPage.notFound.message') }}</p>
      <router-link to="/app/admin/users">
        <Button :label="t('adminCampaignsAndUsers.userDetailsPage.notFound.backButton')" icon="pi pi-arrow-left" />
      </router-link>
    </div>

    <!-- Delete Confirmation Dialog -->
    <Dialog
      v-model:visible="deleteDialogVisible"
      :header="t('admin.usersPage.confirmDeleteHeader')"
      :modal="true"
      :style="{ width: '450px' }"
    >
      <div class="flex align-items-center gap-3 mb-3">
        <i class="pi pi-exclamation-triangle text-4xl text-red-500"></i>
        <span>
          <i18n-t keypath="adminCampaignsAndUsers.userDetailsPage.deleteDialog.message" tag="span">
            <template #email><strong>{{ user?.email }}</strong></template>
          </i18n-t>
          <br><br>
          {{ t('adminCampaignsAndUsers.userDetailsPage.deleteDialog.detail') }}
        </span>
      </div>
      <template #footer>
        <Button :label="t('common.cancel')" icon="pi pi-times" text @click="deleteDialogVisible = false" />
        <Button :label="t('admin.usersPage.delete')" icon="pi pi-trash" severity="danger" @click="deleteUser" :loading="deleting" :disabled="adminReadOnly" />
      </template>
    </Dialog>

    <!-- Password Reset Dialog -->
    <Dialog
      v-model:visible="passwordDialogVisible"
      :header="t('adminCampaignsAndUsers.userDetailsPage.passwordDialog.header')"
      :modal="true"
      :style="{ width: '450px' }"
    >
      <div class="mb-3">
        <p>
          <i18n-t keypath="adminCampaignsAndUsers.userDetailsPage.passwordDialog.message" tag="span">
            <template #email><strong>{{ user?.email }}</strong></template>
          </i18n-t>
        </p>
        <div class="p-inputgroup">
          <InputText v-model="tempPassword" readonly class="w-full" />
          <Button icon="pi pi-copy" @click="copyPassword" />
        </div>
        <small class="text-muted">{{ t('adminCampaignsAndUsers.userDetailsPage.passwordDialog.copyHint') }}</small>
      </div>
      <template #footer>
        <Button :label="t('adminCampaignsAndUsers.userDetailsPage.passwordDialog.close')" @click="passwordDialogVisible = false" />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="apiTokenRevokeDialogVisible"
      :header="t('adminCampaignsAndUsers.userDetailsPage.revokeDialog.header')"
      :modal="true"
      :style="{ width: '420px' }"
    >
      <p>
        <i18n-t keypath="adminCampaignsAndUsers.userDetailsPage.revokeDialog.message" tag="span">
          <template #tokenName><strong>{{ apiTokenToRevoke?.name }}</strong></template>
          <template #email><strong>{{ user?.email }}</strong></template>
        </i18n-t>
      </p>
      <template #footer>
        <Button :label="t('common.cancel')" icon="pi pi-times" text @click="apiTokenRevokeDialogVisible = false" />
        <Button
          :label="t('adminCampaignsAndUsers.userDetailsPage.revokeDialog.revokeButton')"
          icon="pi pi-ban"
          severity="danger"
          :loading="apiTokenRevoking"
          :disabled="adminReadOnly"
          @click="revokeApiToken"
        />
      </template>
    </Dialog>

    <Toast />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Divider from 'primevue/divider'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Toast from 'primevue/toast'
import Breadcrumb from 'primevue/breadcrumb'
import { useToast } from 'primevue/usetoast'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useTimezone } from '@/composables/useTimezone'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import DemoReadOnlyBanner from '@/components/admin/DemoReadOnlyBanner.vue'
import { useAdminStore } from '@/stores/admin'
import { copyToClipboard } from '@/utils/clipboardUtils'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const authStore = useAuthStore()
const adminStore = useAdminStore()
const { adminReadOnly } = storeToRefs(authStore)
const timezone = useTimezone()
const { timeAgo } = timezone
const { t } = useI18n()

const breadcrumbHome = ref({
  icon: 'pi pi-home',
  command: () => router.push('/')
})
const breadcrumbLastLabel = ref(t('adminCampaignsAndUsers.userDetailsPage.breadcrumb.loading'))
const breadcrumbItems = computed(() => [
  {
    label: t('admin.breadcrumb.administration'),
    command: () => router.push('/app/admin')
  },
  {
    label: t('adminCampaignsAndUsers.userDetailsPage.breadcrumb.users'),
    command: () => router.push('/app/admin/users')
  },
  { label: breadcrumbLastLabel.value }
])

const user = ref(null)
const loading = ref(true)
const deleteDialogVisible = ref(false)
const deleting = ref(false)
const passwordDialogVisible = ref(false)
const tempPassword = ref('')
const apiTokens = ref([])
const apiTokensLoading = ref(false)
const apiTokenRevokeDialogVisible = ref(false)
const apiTokenToRevoke = ref(null)
const apiTokenRevoking = ref(false)

const isCurrentUser = computed(() => user.value?.id === authStore.userId)

const loadUser = async () => {
  loading.value = true
  try {
    const response = await adminStore.getUserDetails(route.params.id)
    user.value = response
    // Update breadcrumb with user name
    breadcrumbLastLabel.value = user.value.fullName || user.value.email
    await loadApiTokens()
  } catch (error) {
    console.error('Failed to load user:', error)
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('adminCampaignsAndUsers.userDetailsPage.toasts.loadUserFailed'),
      life: 3000
    })
    breadcrumbLastLabel.value = t('adminCampaignsAndUsers.userDetailsPage.breadcrumb.notFound')
  } finally {
    loading.value = false
  }
}

const loadApiTokens = async () => {
  if (!route.params.id) return
  apiTokensLoading.value = true
  try {
    const response = await adminStore.getUserApiTokens(route.params.id, 0, 100)
    apiTokens.value = response?.items || []
  } catch (error) {
    console.error('Failed to load API tokens:', error)
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('adminCampaignsAndUsers.userDetailsPage.toasts.loadTokensFailed'),
      life: 3000
    })
  } finally {
    apiTokensLoading.value = false
  }
}

const toggleStatus = async () => {
  try {
    await adminStore.updateUserStatus(user.value.id, !user.value.active)

    user.value.active = !user.value.active

    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: user.value.active ? t('admin.usersPage.toasts.userEnabled') : t('admin.usersPage.toasts.userDisabled'),
      life: 3000
    })
  } catch (error) {
    console.error('Failed to update user status:', error)
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: formatApiErrorDetail(error, t('admin.usersPage.toasts.updateStatusFailedFallback')),
      life: 3000
    })
  }
}

const toggleRole = async () => {
  const newRole = user.value.role === 'ADMIN' ? 'USER' : 'ADMIN'

  try {
    await adminStore.updateUserRole(user.value.id, newRole)

    user.value.role = newRole

    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('adminCampaignsAndUsers.userDetailsPage.toasts.roleChanged', { role: newRole }),
      life: 3000
    })
  } catch (error) {
    console.error('Failed to change user role:', error)
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: formatApiErrorDetail(error, t('adminCampaignsAndUsers.userDetailsPage.toasts.roleChangeFailed')),
      life: 3000
    })
  }
}

const resetPassword = async () => {
  try {
    const response = await adminStore.resetUserPassword(user.value.id)
    tempPassword.value = response.temporaryPassword
    passwordDialogVisible.value = true

    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('adminCampaignsAndUsers.userDetailsPage.toasts.passwordResetSuccess'),
      life: 3000
    })
  } catch (error) {
    console.error('Failed to reset password:', error)
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('adminCampaignsAndUsers.userDetailsPage.toasts.passwordResetFailed'),
      life: 3000
    })
  }
}

const copyPassword = async () => {
  const success = await copyToClipboard(tempPassword.value)

  if (success) {
    toast.add({
      severity: 'success',
      summary: t('common.clipboard.copied'),
      detail: t('adminCampaignsAndUsers.userDetailsPage.toasts.passwordCopied'),
      life: 2000
    })
  } else {
    console.error('Failed to copy password')
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('adminCampaignsAndUsers.userDetailsPage.toasts.passwordCopyFailed'),
      life: 3000
    })
  }
}

const confirmRevokeApiToken = (token) => {
  apiTokenToRevoke.value = token
  apiTokenRevokeDialogVisible.value = true
}

const revokeApiToken = async () => {
  if (!apiTokenToRevoke.value) return

  apiTokenRevoking.value = true
  try {
    await adminStore.revokeUserApiToken(apiTokenToRevoke.value.id)
    toast.add({
      severity: 'success',
      summary: t('adminCampaignsAndUsers.userDetailsPage.toasts.tokenRevokedSummary'),
      detail: t('adminCampaignsAndUsers.userDetailsPage.toasts.tokenRevokedDetail'),
      life: 3000
    })
    apiTokenRevokeDialogVisible.value = false
    apiTokenToRevoke.value = null
    await loadApiTokens()
  } catch (error) {
    console.error('Failed to revoke API token:', error)
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: formatApiErrorDetail(error, t('adminCampaignsAndUsers.userDetailsPage.toasts.tokenRevokeFailed')),
      life: 3000
    })
  } finally {
    apiTokenRevoking.value = false
  }
}

const tokenStatusSeverity = (status) => {
  if (status === 'ACTIVE') return 'success'
  if (status === 'EXPIRED') return 'warning'
  return 'danger'
}

const tokenStatusLabels = {
  ACTIVE: 'adminCampaignsAndUsers.userDetailsPage.apiTokensCard.status.active',
  EXPIRED: 'adminCampaignsAndUsers.userDetailsPage.apiTokensCard.status.expired',
  REVOKED: 'adminCampaignsAndUsers.userDetailsPage.apiTokensCard.status.revoked'
}

const formatTokenStatus = (status) => {
  if (!status) return ''
  const key = tokenStatusLabels[status]
  return key ? t(key) : status
}

const confirmDelete = () => {
  deleteDialogVisible.value = true
}

const deleteUser = async () => {
  deleting.value = true
  try {
    await adminStore.deleteUser(user.value.id)

    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('admin.usersPage.toasts.deletedDetail'),
      life: 3000
    })

    router.push('/app/admin/users')
  } catch (error) {
    console.error('Failed to delete user:', error)
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: formatApiErrorDetail(error, t('admin.usersPage.toasts.deleteFailedFallback')),
      life: 3000
    })
  } finally {
    deleting.value = false
    deleteDialogVisible.value = false
  }
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  return `${timezone.formatDateDisplay(dateStr)} ${timezone.formatTime(dateStr, { withSeconds: true })}`
}

const formatNumber = (num) => {
  if (num === null || num === undefined) return '0'
  return num.toLocaleString()
}

const formatTimeAgo = (dateStr) => {
  if (!dateStr) return t('adminCampaignsAndUsers.userDetailsPage.never')
  return timeAgo(dateStr)
}

onMounted(() => {
  loadUser()
})
</script>

<style scoped>
.admin-user-details {
  width: 100%;
  padding: 1.5rem 2rem;
  box-sizing: border-box;
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
  font-weight: 600;
  color: var(--gp-text-primary);
}

/* Container */
.user-details-container {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* User Header Card */
.user-header-card {
  background: linear-gradient(135deg, var(--gp-primary) 0%, var(--gp-primary-dark) 100%);
  border: none;
  box-shadow: var(--gp-shadow-card);
}

.user-header {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  padding: 1.5rem;
}

.user-avatar {
  border: 3px solid rgba(255, 255, 255, 0.3);
  box-shadow: var(--gp-shadow-medium);
}

.user-header-info {
  flex: 1;
}

.user-header-info h2 {
  margin: 0 0 0.25rem 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: white;
}

.user-email {
  margin: 0 0 0.75rem 0;
  color: rgba(255, 255, 255, 0.9);
  font-size: 1rem;
}

.user-badges {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

/* Main Grid Layout */
.user-details-grid {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 2rem;
  align-items: start;
}

.left-column {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.info-cards-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
}

@media (max-width: 1200px) {
  .info-cards-row {
    grid-template-columns: 1fr;
  }
}

.right-column {
  position: sticky;
  top: 1.5rem;
}

@media (max-width: 1024px) {
  .user-details-grid {
    grid-template-columns: 1fr;
  }

  .right-column {
    position: static;
  }
}

/* Card Styles */
.card {
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-large);
  box-shadow: var(--gp-shadow-card);
  transition: all 0.3s ease;
}

.card:hover {
  box-shadow: var(--gp-shadow-card-hover);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  border-bottom: 2px solid var(--gp-border);
  background: var(--gp-surface-muted);
}

.card-title i {
  color: var(--gp-primary);
  font-size: 1rem;
}

.card-title h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.card-content {
  padding: 1.25rem;
}

/* Info Group */
.info-group {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--gp-border-subtle);
}

.info-item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.info-item label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.info-item label i {
  color: var(--gp-primary);
  font-size: 0.8125rem;
}

.info-value {
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  word-break: break-word;
  padding-left: 1.3125rem;
}

.stat-value {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--gp-primary);
}

/* Actions Card */
.actions-card {
  height: fit-content;
}

.actions-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.action-button-full {
  width: 100%;
}

.actions-buttons :deep(.p-button) {
  justify-content: center;
}

.actions-buttons :deep(.p-divider) {
  margin: 0.25rem 0;
}

/* Warning Message */
.warning-message {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  background: var(--gp-warning-soft);
  border: 1px solid var(--gp-warning);
  border-radius: var(--gp-radius-medium);
  margin-top: 1rem;
}

.warning-message i {
  color: var(--gp-warning-text);
  font-size: 1.25rem;
  flex-shrink: 0;
}

.warning-message span {
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.5;
}

/* Text Utilities */
.text-muted {
  color: var(--gp-text-secondary);
}

/* Responsive Design */
@media (max-width: 768px) {
  .admin-user-details {
    padding: 1rem;
  }

  .user-header {
    padding: 1.5rem;
    flex-direction: column;
    text-align: center;
  }

  .user-header-info h2 {
    font-size: 1.5rem;
  }

  .user-email {
    font-size: 1rem;
  }

  .user-badges {
    justify-content: center;
  }

  .page-header h1 {
    font-size: 1.5rem;
  }
}
</style>
