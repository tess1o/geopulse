<template>
  <AppLayout>
    <div class="admin-users">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <div>
          <h1>User Management</h1>
          <p class="text-muted">Manage user accounts</p>
        </div>
        <router-link to="/app/admin/invitations" class="no-underline">
          <Button
            label="User Invitations"
            icon="pi pi-send"
            severity="secondary"
            outlined
          />
        </router-link>
      </div>

    <!-- Search Bar (Mobile & Desktop) -->
    <div class="search-container">
      <InputText
        v-model="searchQuery"
        placeholder="Search users..."
        @input="onSearch"
        class="w-full"
      />
    </div>

    <!-- Desktop Table View -->
    <div class="card desktop-only">
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

    <!-- Mobile Card View -->
    <div class="mobile-only">
      <div v-if="loading" class="text-center p-4">
        <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
      </div>

      <div v-else-if="users.length === 0" class="text-center p-4 card">
        No users found.
      </div>

      <div v-else class="user-cards">
        <div v-for="user in users" :key="user.id" class="user-card" @click="viewUser(user)">
          <div class="user-card-header">
            <div class="user-info">
              <div class="user-email">{{ user.email }}</div>
              <div class="user-name">{{ user.fullName }}</div>
            </div>
            <div class="user-badges">
              <Tag :severity="user.role === 'ADMIN' ? 'warning' : 'info'" :value="user.role" />
              <Tag :severity="user.active ? 'success' : 'danger'" :value="user.active ? 'Active' : 'Disabled'" />
            </div>
          </div>

          <div class="user-card-body">
            <div class="user-stat">
              <span class="stat-label">GPS Points</span>
              <span class="stat-value">{{ formatNumber(user.gpsPointsCount) }}</span>
            </div>
            <div class="user-stat">
              <span class="stat-label">Created</span>
              <span class="stat-value">{{ formatDate(user.createdAt) }}</span>
            </div>
          </div>

          <div class="user-card-actions" @click.stop>
            <Button
              icon="pi pi-eye"
              rounded
              text
              severity="info"
              @click="viewUser(user)"
              size="small"
            />
            <Button
              :icon="user.active ? 'pi pi-ban' : 'pi pi-check'"
              rounded
              text
              :severity="user.active ? 'warning' : 'success'"
              @click="toggleUserStatus(user)"
              :disabled="user.id === currentUserId"
              size="small"
            />
            <Button
              icon="pi pi-trash"
              rounded
              text
              severity="danger"
              @click="confirmDelete(user)"
              :disabled="user.id === currentUserId"
              size="small"
            />
          </div>
        </div>
      </div>

      <!-- Mobile Pagination -->
      <div class="mobile-pagination" v-if="users.length > 0">
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

// Mobile pagination methods
const goToFirstPage = () => {
  page.value = 0
  loadUsers()
}

const goToPrevPage = () => {
  if (page.value > 0) {
    page.value--
    loadUsers()
  }
}

const goToNextPage = () => {
  if ((page.value + 1) * pageSize.value < totalRecords.value) {
    page.value++
    loadUsers()
  }
}

const goToLastPage = () => {
  page.value = Math.floor(totalRecords.value / pageSize.value)
  loadUsers()
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
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
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

/* Search Container */
.search-container {
  margin-bottom: 1rem;
}

/* Desktop/Mobile Toggle */
.desktop-only {
  display: block;
}

.mobile-only {
  display: none;
}

/* Mobile User Cards */
.user-cards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0.5rem;
  border-radius: 8px;
}

.user-card {
  background: var(--gp-surface-white);
  border: 2px solid var(--surface-border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(255, 255, 255, 0.05);
  cursor: pointer;
  transition: all 0.2s ease;
}

/* Dark theme specific */
:global(.p-dark) .user-card,
:global([data-theme="dark"]) .user-card,
:global(html.dark) .user-card {
  background: var(--gp-surface-dark);
}

.user-card:active {
  transform: scale(0.98);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}

.user-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.75rem;
  gap: 0.5rem;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-email {
  font-weight: 600;
  color: var(--primary-color);
  margin-bottom: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-name {
  font-size: 0.9rem;
  color: var(--text-color-secondary);
}

.user-badges {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  align-items: flex-end;
}

.user-card-body {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--surface-border);
}

.user-stat {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.stat-label {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  text-transform: uppercase;
}

.stat-value {
  font-weight: 600;
  font-size: 0.9rem;
}

.user-card-actions {
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

/* Mobile Responsive Styles */
@media (max-width: 768px) {
  .admin-users {
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

  .search-container {
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
}

/* Extra small screens */
@media (max-width: 480px) {
  .admin-users {
    padding: 0.5rem;
  }

  .page-header h1 {
    font-size: 1.25rem;
  }

  .user-card {
    padding: 0.75rem;
  }

  .user-card-body {
    gap: 1rem;
  }
}
</style>
