<template>
  <AppLayout>
    <div class="admin-user-details">
      <div class="page-header">
        <router-link to="/app/admin/users" class="back-link">
          <i class="pi pi-arrow-left"></i> Back to Users
        </router-link>
        <h1>User Details</h1>
      </div>

    <div v-if="loading" class="text-center p-4">
      <i class="pi pi-spin pi-spinner text-4xl"></i>
    </div>

    <div v-else-if="user" class="user-details-grid">
      <!-- User Info Card -->
      <div class="card user-info-card">
        <div class="card-header">
          <Avatar
            :image="user.avatar"
            :label="user.fullName?.charAt(0) || user.email?.charAt(0)"
            size="xlarge"
            shape="circle"
          />
          <div>
            <h2>{{ user.fullName || 'No name' }}</h2>
            <p class="text-muted">{{ user.email }}</p>
          </div>
        </div>

        <div class="card-body">
          <div class="info-grid">
            <div class="info-item">
              <label>Role</label>
              <Tag :severity="user.role === 'ADMIN' ? 'warning' : 'info'" :value="user.role" />
            </div>
            <div class="info-item">
              <label>Status</label>
              <Tag :severity="user.active ? 'success' : 'danger'" :value="user.active ? 'Active' : 'Disabled'" />
            </div>
            <div class="info-item">
              <label>Has Password</label>
              <span>{{ user.hasPassword ? 'Yes' : 'No (OIDC only)' }}</span>
            </div>
            <div class="info-item">
              <label>Timezone</label>
              <span>{{ user.timezone }}</span>
            </div>
            <div class="info-item">
              <label>Created</label>
              <span>{{ formatDateTime(user.createdAt) }}</span>
            </div>
            <div class="info-item">
              <label>Updated</label>
              <span>{{ formatDateTime(user.updatedAt) }}</span>
            </div>
            <div class="info-item" v-if="user.linkedOidcProviders?.length">
              <label>Linked OIDC</label>
              <span>{{ user.linkedOidcProviders.join(', ') }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Actions Card -->
      <div class="card actions-card">
        <div class="card-body">
          <h3>Actions</h3>

          <div class="actions-buttons">
            <!-- Toggle Status -->
            <Button
              :label="user.active ? 'Disable User' : 'Enable User'"
              :icon="user.active ? 'pi pi-ban' : 'pi pi-check'"
              :severity="user.active ? 'warning' : 'success'"
              @click="toggleStatus"
              :disabled="isCurrentUser"
            />

            <!-- Change Role -->
            <Button
              :label="user.role === 'ADMIN' ? 'Demote to User' : 'Promote to Admin'"
              :icon="user.role === 'ADMIN' ? 'pi pi-user' : 'pi pi-shield'"
              severity="secondary"
              @click="toggleRole"
            />

            <!-- Reset Password -->
            <Button
              label="Reset Password"
              icon="pi pi-key"
              severity="info"
              @click="resetPassword"
            />

            <Divider />

            <!-- Delete User -->
            <Button
              label="Delete User"
              icon="pi pi-trash"
              severity="danger"
              @click="confirmDelete"
              :disabled="isCurrentUser"
            />
          </div>

          <small v-if="isCurrentUser" class="text-muted">
            You cannot disable or delete your own account.
          </small>
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
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Divider from 'primevue/divider'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import apiService from '@/utils/apiService'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const authStore = useAuthStore()

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
  } catch (error) {
    console.error('Failed to load user:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load user details',
      life: 3000
    })
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
    tempPassword.value = response.data.temporaryPassword
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
  try {
    await navigator.clipboard.writeText(tempPassword.value)
    toast.add({
      severity: 'success',
      summary: 'Copied',
      detail: 'Password copied to clipboard',
      life: 2000
    })
  } catch (error) {
    console.error('Failed to copy password:', error)
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

onMounted(() => {
  loadUser()
})
</script>

<style scoped>
.admin-user-details {
  padding: 1.5rem;
}

.page-header {
  margin-bottom: 1.5rem;
}

.page-header h1 {
  margin: 0.5rem 0 0 0;
  font-size: 1.75rem;
  color: var(--text-color);
}

.back-link {
  color: var(--text-color-secondary);
  text-decoration: none;
  font-size: 0.875rem;
}

.back-link:hover {
  color: var(--primary-color);
}

/* Main Grid Layout */
.user-details-grid {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 1.5rem;
}

@media (max-width: 1024px) {
  .user-details-grid {
    grid-template-columns: 1fr;
  }
}

.card {
  background: var(--surface-card);
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem;
  border-bottom: 1px solid var(--surface-border);
}

.card-header h2 {
  margin: 0;
  font-size: 1.25rem;
  color: var(--text-color);
}

.card-header p {
  margin: 0.25rem 0 0 0;
}

.card-body {
  padding: 1.5rem;
}

.card-body h3 {
  margin: 0 0 1rem 0;
  font-size: 1rem;
  color: var(--text-color);
}

/* Info Grid */
.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}

@media (max-width: 768px) {
  .info-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-item label {
  font-size: 0.75rem;
  text-transform: uppercase;
  color: var(--text-color-secondary);
  font-weight: 600;
}

.info-item span {
  color: var(--text-color);
  word-break: break-all;
}

/* Actions Card */
.actions-card {
  height: fit-content;
}

.actions-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.actions-buttons :deep(.p-button) {
  width: 100%;
  justify-content: center;
}

.actions-buttons :deep(.p-divider) {
  margin: 0.5rem 0;
}

.text-muted {
  color: var(--text-color-secondary);
  display: block;
  margin-top: 0.75rem;
}
</style>
