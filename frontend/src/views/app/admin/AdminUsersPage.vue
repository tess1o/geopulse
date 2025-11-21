<template>
  <AppLayout>
    <div class="admin-users">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <h1>User Management</h1>
        <p class="text-muted">Manage user accounts</p>
      </div>

    <div class="card">
      <DataTable
        :value="users"
        :loading="loading"
        :paginator="true"
        :rows="pageSize"
        :totalRecords="totalRecords"
        :lazy="true"
        @page="onPage"
        @sort="onSort"
        dataKey="id"
        responsiveLayout="scroll"
        :rowsPerPageOptions="[10, 25, 50]"
        paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
        currentPageReportTemplate="Showing {first} to {last} of {totalRecords} users"
      >
        <template #header>
          <div class="flex justify-content-between align-items-center">
            <span class="p-input">
              <InputText v-model="searchQuery" placeholder="Search users..." @input="onSearch" />
            </span>
          </div>
        </template>

        <Column field="email" header="Email" sortable>
          <template #body="{ data }">
            <router-link :to="`/app/admin/users/${data.id}`" class="text-primary no-underline">
              {{ data.email }}
            </router-link>
          </template>
        </Column>

        <Column field="fullName" header="Name" sortable />

        <Column field="role" header="Role" sortable>
          <template #body="{ data }">
            <Tag :severity="data.role === 'ADMIN' ? 'warning' : 'info'" :value="data.role" />
          </template>
        </Column>

        <Column field="active" header="Status" sortable>
          <template #body="{ data }">
            <Tag :severity="data.active ? 'success' : 'danger'" :value="data.active ? 'Active' : 'Disabled'" />
          </template>
        </Column>

        <Column field="gpsPointsCount" header="GPS Points">
          <template #body="{ data }">
            {{ formatNumber(data.gpsPointsCount) }}
          </template>
        </Column>

        <Column field="createdAt" header="Created" sortable>
          <template #body="{ data }">
            {{ formatDate(data.createdAt) }}
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
                @click="viewUser(data)"
                v-tooltip="'View Details'"
              />
              <Button
                :icon="data.active ? 'pi pi-ban' : 'pi pi-check'"
                rounded
                text
                :severity="data.active ? 'warning' : 'success'"
                @click="toggleUserStatus(data)"
                v-tooltip="data.active ? 'Disable User' : 'Enable User'"
                :disabled="data.id === currentUserId"
              />
              <Button
                icon="pi pi-trash"
                rounded
                text
                severity="danger"
                @click="confirmDelete(data)"
                v-tooltip="'Delete User'"
                :disabled="data.id === currentUserId"
              />
            </div>
          </template>
        </Column>

        <template #empty>
          <div class="text-center p-4">
            No users found.
          </div>
        </template>
      </DataTable>
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
          Are you sure you want to delete user <strong>{{ userToDelete?.email }}</strong>?
          <br><br>
          This will permanently delete all their data including GPS points, timeline, and settings.
        </span>
      </div>
      <template #footer>
        <Button label="Cancel" icon="pi pi-times" text @click="deleteDialogVisible = false" />
        <Button label="Delete" icon="pi pi-trash" severity="danger" @click="deleteUser" :loading="deleting" />
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
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import Toast from 'primevue/toast'
import Breadcrumb from 'primevue/breadcrumb'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import apiService from '@/utils/apiService'

const router = useRouter()
const toast = useToast()
const authStore = useAuthStore()

const breadcrumbHome = ref({
  icon: 'pi pi-home',
  command: () => router.push('/')
})
const breadcrumbItems = ref([
  {
    label: 'Administration',
    command: () => router.push('/app/admin')
  },
  { label: 'Users' }
])

const users = ref([])
const loading = ref(false)
const totalRecords = ref(0)
const page = ref(0)
const pageSize = ref(10)
const sortField = ref('createdAt')
const sortOrder = ref(-1)
const searchQuery = ref('')
const searchTimeout = ref(null)

const deleteDialogVisible = ref(false)
const userToDelete = ref(null)
const deleting = ref(false)

const currentUserId = computed(() => authStore.userId)

const loadUsers = async () => {
  loading.value = true
  try {
    const params = new URLSearchParams({
      page: page.value.toString(),
      size: pageSize.value.toString(),
      sortBy: sortField.value,
      sortDir: sortOrder.value === 1 ? 'asc' : 'desc'
    })

    if (searchQuery.value) {
      params.append('search', searchQuery.value)
    }

    const response = await apiService.get(`/admin/users?${params.toString()}`)
    users.value = response.content
    totalRecords.value = response.totalElements
  } catch (error) {
    console.error('Failed to load users:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load users',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const onPage = (event) => {
  page.value = event.page
  pageSize.value = event.rows
  loadUsers()
}

const onSort = (event) => {
  sortField.value = event.sortField
  sortOrder.value = event.sortOrder
  loadUsers()
}

const onSearch = () => {
  // Debounce search
  if (searchTimeout.value) {
    clearTimeout(searchTimeout.value)
  }
  searchTimeout.value = setTimeout(() => {
    page.value = 0
    loadUsers()
  }, 300)
}

const viewUser = (user) => {
  router.push(`/app/admin/users/${user.id}`)
}

const toggleUserStatus = async (user) => {
  try {
    await apiService.put(`/admin/users/${user.id}/status`, {
      active: !user.active
    })

    user.active = !user.active

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: `User ${user.active ? 'enabled' : 'disabled'}`,
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

const confirmDelete = (user) => {
  userToDelete.value = user
  deleteDialogVisible.value = true
}

const deleteUser = async () => {
  if (!userToDelete.value) return

  deleting.value = true
  try {
    await apiService.delete(`/admin/users/${userToDelete.value.id}`)

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'User deleted successfully',
      life: 3000
    })

    deleteDialogVisible.value = false
    userToDelete.value = null
    loadUsers()
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
  }
}

const formatNumber = (num) => {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M'
  } else if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num?.toString() || '0'
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString()
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.admin-users {
  padding: 1.5rem;
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
</style>
