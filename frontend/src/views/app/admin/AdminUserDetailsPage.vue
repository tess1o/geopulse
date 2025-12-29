<template>
  <AppLayout :padding="'none'">
    <div class="admin-user-details">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <h1>User Details</h1>
      </div>

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
            <h2>{{ user.fullName || 'No name' }}</h2>
            <p class="user-email">{{ user.email }}</p>
            <div class="user-badges">
              <Tag :severity="user.role === 'ADMIN' ? 'warning' : 'info'" :value="user.role" />
              <Tag :severity="user.active ? 'success' : 'danger'" :value="user.active ? 'Active' : 'Disabled'" />
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
                <h3>User Information</h3>
              </div>
              <div class="card-content">
                <div class="info-group">
                  <div class="info-item">
                    <label><i class="pi pi-key"></i> Authentication</label>
                    <span class="info-value">{{ user.hasPassword ? 'Password' : 'OIDC only' }}</span>
                  </div>
                  <div class="info-item">
                    <label><i class="pi pi-globe"></i> Timezone</label>
                    <span class="info-value">{{ user.timezone }}</span>
                  </div>
                  <div class="info-item" v-if="user.linkedOidcProviders?.length">
                    <label><i class="pi pi-link"></i> Linked OIDC Providers</label>
                    <span class="info-value">{{ user.linkedOidcProviders.join(', ') }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Activity & Stats Card -->
            <div class="card">
              <div class="card-title">
                <i class="pi pi-chart-line"></i>
                <h3>Activity & Statistics</h3>
              </div>
              <div class="card-content">
                <div class="info-group">
                  <div class="info-item">
                    <label><i class="pi pi-map-marker"></i> GPS Points</label>
                    <span class="info-value stat-value">{{ formatNumber(user.gpsPointsCount) }}</span>
                  </div>
                  <div class="info-item">
                    <label><i class="pi pi-clock"></i> Last GPS Point</label>
                    <span class="info-value">{{ formatTimeAgo(user.lastGpsPointAt) }}</span>
                  </div>
                  <div class="info-item">
                    <label><i class="pi pi-calendar-plus"></i> Account Created</label>
                    <span class="info-value">{{ formatDateTime(user.createdAt) }}</span>
                  </div>
                  <div class="info-item">
                    <label><i class="pi pi-calendar"></i> Last Updated</label>
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
              <h3>Administrative Actions</h3>
            </div>
            <div class="card-content">
              <div class="actions-buttons">
                <Button
                  :label="user.active ? 'Disable User' : 'Enable User'"
                  :icon="user.active ? 'pi pi-ban' : 'pi pi-check'"
                  :severity="user.active ? 'warning' : 'success'"
                  @click="toggleStatus"
                  :disabled="isCurrentUser"
                  class="action-button-full"
                />

                <Button
                  :label="user.role === 'ADMIN' ? 'Demote to User' : 'Promote to Admin'"
                  :icon="user.role === 'ADMIN' ? 'pi pi-user' : 'pi pi-shield'"
                  severity="secondary"
                  outlined
                  @click="toggleRole"
                  class="action-button-full"
                />

                <Button
                  label="Reset Password"
                  icon="pi pi-key"
                  severity="info"
                  outlined
                  @click="resetPassword"
                  class="action-button-full"
                />

                <Divider />

                <Button
                  label="Delete User"
                  icon="pi pi-trash"
                  severity="danger"
                  @click="confirmDelete"
                  :disabled="isCurrentUser"
                  class="action-button-full"
                />
              </div>

              <div v-if="isCurrentUser" class="warning-message">
                <i class="pi pi-info-circle"></i>
                <span>You cannot disable or delete your own account.</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="card p-4 text-center">
      <p>User not found</p>
      <router-link to="/app/admin/users">
        <Button label="Back to Users" icon="pi pi-arrow-left" />
      </router-link>
    </div>

    <!-- Delete Confirmation Dialog -->
    <Dialog
      v-model:visible="deleteDialogVisible"
      header="Confirm Delete"
      :modal="true"
      :style="{ width: '450px' }"
    >
      <div class="flex align-items-center gap-3 mb-3">
        <i class="pi pi-exclamation-triangle text-4xl text-red-500"></i>
        <span>
          Are you sure you want to delete user <strong>{{ user?.email }}</strong>?
          <br><br>
          This will permanently delete all their data.
        </span>
      </div>
      <template #footer>
        <Button label="Cancel" icon="pi pi-times" text @click="deleteDialogVisible = false" />
        <Button label="Delete" icon="pi pi-trash" severity="danger" @click="deleteUser" :loading="deleting" />
      </template>
    </Dialog>

    <!-- Password Reset Dialog -->
    <Dialog
      v-model:visible="passwordDialogVisible"
      header="Password Reset"
      :modal="true"
      :style="{ width: '450px' }"
    >
      <div class="mb-3">
        <p>Temporary password for <strong>{{ user?.email }}</strong>:</p>
        <div class="p-inputgroup">
          <InputText v-model="tempPassword" readonly class="w-full" />
          <Button icon="pi pi-copy" @click="copyPassword" />
        </div>
        <small class="text-muted">Share this password with the user securely.</small>
      </div>
      <template #footer>
        <Button label="Close" @click="passwordDialogVisible = false" />
      </template>
    </Dialog>

    <Toast />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Divider from 'primevue/divider'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Toast from 'primevue/toast'
import Breadcrumb from 'primevue/breadcrumb'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import { useTimezone } from '@/composables/useTimezone'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import apiService from '@/utils/apiService'
import { copyToClipboard } from '@/utils/clipboardUtils'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const authStore = useAuthStore()
const { timeAgo } = useTimezone()

const breadcrumbHome = ref({
  icon: 'pi pi-home',
  command: () => router.push('/')
})
const breadcrumbItems = ref([
  {
    label: 'Administration',
    command: () => router.push('/app/admin')
  },
  {
    label: 'Users',
    command: () => router.push('/app/admin/users')
  },
  { label: 'Loading...' }
])

const user = ref(null)
const loading = ref(true)
const deleteDialogVisible = ref(false)
const deleting = ref(false)
const passwordDialogVisible = ref(false)
const tempPassword = ref('')

const isCurrentUser = computed(() => user.value?.id === authStore.userId)

const loadUser = async () => {
  loading.value = true
  try {
    const response = await apiService.get(`/admin/users/${route.params.id}`)
    user.value = response
    // Update breadcrumb with user name
    breadcrumbItems.value[2].label = user.value.fullName || user.value.email
  } catch (error) {
    console.error('Failed to load user:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load user details',
      life: 3000
    })
    breadcrumbItems.value[2].label = 'User Not Found'
  } finally {
    loading.value = false
  }
}

const toggleStatus = async () => {
  try {
    await apiService.put(`/admin/users/${user.value.id}/status`, {
      active: !user.value.active
    })

    user.value.active = !user.value.active

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: `User ${user.value.active ? 'enabled' : 'disabled'}`,
      life: 3000
    })
  } catch (error) {
    console.error('Failed to update user status:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.error || 'Failed to update user status',
      life: 3000
    })
  }
}

const toggleRole = async () => {
  const newRole = user.value.role === 'ADMIN' ? 'USER' : 'ADMIN'

  try {
    await apiService.put(`/admin/users/${user.value.id}/role`, {
      role: newRole
    })

    user.value.role = newRole

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: `User role changed to ${newRole}`,
      life: 3000
    })
  } catch (error) {
    console.error('Failed to change user role:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.error || 'Failed to change user role',
      life: 3000
    })
  }
}

const resetPassword = async () => {
  try {
    const response = await apiService.post(`/admin/users/${user.value.id}/reset-password`)
    tempPassword.value = response.temporaryPassword
    passwordDialogVisible.value = true

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Password reset successfully',
      life: 3000
    })
  } catch (error) {
    console.error('Failed to reset password:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to reset password',
      life: 3000
    })
  }
}

const copyPassword = async () => {
  const success = await copyToClipboard(tempPassword.value)

  if (success) {
    toast.add({
      severity: 'success',
      summary: 'Copied',
      detail: 'Password copied to clipboard',
      life: 2000
    })
  } else {
    console.error('Failed to copy password')
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to copy password to clipboard',
      life: 3000
    })
  }
}

const confirmDelete = () => {
  deleteDialogVisible.value = true
}

const deleteUser = async () => {
  deleting.value = true
  try {
    await apiService.delete(`/admin/users/${user.value.id}`)

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'User deleted successfully',
      life: 3000
    })

    router.push('/app/admin/users')
  } catch (error) {
    console.error('Failed to delete user:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.error || 'Failed to delete user',
      life: 3000
    })
  } finally {
    deleting.value = false
    deleteDialogVisible.value = false
  }
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}

const formatNumber = (num) => {
  if (num === null || num === undefined) return '0'
  return num.toLocaleString()
}

const formatTimeAgo = (dateStr) => {
  if (!dateStr) return 'Never'
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
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
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
  border-bottom: 2px solid var(--gp-border-light);
  background: var(--gp-surface-light);
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
  background: var(--gp-warning-light);
  border: 1px solid var(--gp-warning);
  border-radius: var(--gp-radius-medium);
  margin-top: 1rem;
}

.warning-message i {
  color: var(--gp-warning-dark);
  font-size: 1.25rem;
  flex-shrink: 0;
}

.warning-message span {
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.5;
}

/* Dark Mode Warning Message */
.p-dark .warning-message {
  background: rgba(245, 158, 11, 0.15);
  border-color: var(--gp-warning);
}

.p-dark .warning-message i {
  color: var(--gp-warning);
}

.p-dark .warning-message span {
  color: var(--gp-text-primary);
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
