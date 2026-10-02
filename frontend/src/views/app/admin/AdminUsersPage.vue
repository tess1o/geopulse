<template>
  <AppLayout>
    <div class="gp-admin-page">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="gp-admin-breadcrumb" />

      <div class="gp-page-header">
        <div class="gp-page-header-content">
          <div class="gp-page-header-text">
            <h1 class="gp-page-title">{{ t('admin.usersPage.title') }}</h1>
            <p class="gp-page-subtitle">{{ t('admin.usersPage.subtitle') }}</p>
          </div>
          <div class="gp-page-actions">
            <router-link to="/app/admin/invitations" class="no-underline">
              <Button
                :label="t('admin.usersPage.userInvitations')"
                icon="pi pi-send"
                severity="secondary"
                outlined
              />
            </router-link>
          </div>
        </div>
      </div>

      <DemoReadOnlyBanner />

    <!-- Search Bar (Mobile & Desktop) -->
    <div class="search-container">
      <InputText
        v-model="searchQuery"
        :placeholder="t('admin.usersPage.searchPlaceholder')"
        @input="onSearch"
        class="w-full"
      />
    </div>

    <!-- Desktop Table View -->
    <div class="gp-admin-card desktop-only">
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
        :currentPageReportTemplate="t('admin.usersPage.currentPageReport')"
      >
        <Column field="email" :header="t('admin.usersPage.columnEmail')" sortable>
          <template #body="{ data }">
            <router-link :to="`/app/admin/users/${data.id}`" class="text-primary no-underline">
              {{ data.email }}
            </router-link>
          </template>
        </Column>

        <Column field="fullName" :header="t('admin.usersPage.columnName')" sortable />

        <Column field="role" :header="t('admin.usersPage.columnRole')" sortable>
          <template #body="{ data }">
            <Tag :severity="data.role === 'ADMIN' ? 'warning' : 'info'" :value="data.role" />
          </template>
        </Column>

        <Column field="active" :header="t('admin.usersPage.columnStatus')" sortable>
          <template #body="{ data }">
            <Tag :severity="data.active ? 'success' : 'danger'" :value="data.active ? t('admin.usersPage.statusActive') : t('admin.usersPage.statusDisabled')" />
          </template>
        </Column>

        <Column field="gpsPointsCount" :header="t('admin.usersPage.columnGpsPoints')">
          <template #body="{ data }">
            {{ formatNumber(data.gpsPointsCount) }}
          </template>
        </Column>

        <Column field="createdAt" :header="t('admin.usersPage.columnCreated')" sortable>
          <template #body="{ data }">
            {{ formatDate(data.createdAt) }}
          </template>
        </Column>

        <Column :header="t('admin.usersPage.columnActions')" :exportable="false" style="min-width: 150px">
          <template #body="{ data }">
            <div class="flex gap-2">
              <Button
                icon="pi pi-eye"
                rounded
                text
                severity="info"
                @click="viewUser(data)"
                v-tooltip="t('admin.usersPage.viewDetails')"
              />
              <Button
                :icon="data.active ? 'pi pi-ban' : 'pi pi-check'"
                rounded
                text
                :severity="data.active ? 'warning' : 'success'"
                @click="toggleUserStatus(data)"
                v-tooltip="data.active ? t('admin.usersPage.disableUser') : t('admin.usersPage.enableUser')"
                :disabled="adminReadOnly || data.id === currentUserId"
              />
              <Button
                icon="pi pi-trash"
                rounded
                text
                severity="danger"
                @click="confirmDelete(data)"
                v-tooltip="t('admin.usersPage.deleteUser')"
                :disabled="adminReadOnly || data.id === currentUserId"
              />
            </div>
          </template>
        </Column>

        <template #empty>
          <div class="text-center p-4">
            {{ t('admin.usersPage.noUsersFound') }}
          </div>
        </template>
      </DataTable>
    </div>

    <!-- Mobile Card View -->
    <div class="mobile-only">
      <div v-if="loading" class="text-center p-4">
        <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
      </div>

      <div v-else-if="users.length === 0" class="text-center p-4 gp-admin-card">
        {{ t('admin.usersPage.noUsersFound') }}
      </div>

      <div v-else class="gp-admin-list">
        <div v-for="user in users" :key="user.id" class="gp-admin-list-card gp-admin-list-card--interactive" @click="viewUser(user)">
          <div class="gp-admin-list-card-header">
            <div class="user-info">
              <div class="user-email">{{ user.email }}</div>
              <div class="user-name">{{ user.fullName }}</div>
            </div>
            <div class="user-badges">
              <Tag :severity="user.role === 'ADMIN' ? 'warning' : 'info'" :value="user.role" />
              <Tag :severity="user.active ? 'success' : 'danger'" :value="user.active ? t('admin.usersPage.statusActive') : t('admin.usersPage.statusDisabled')" />
            </div>
          </div>

          <div class="gp-admin-list-card-body">
            <div class="user-stat">
              <span class="stat-label">{{ t('admin.usersPage.columnGpsPoints') }}</span>
              <span class="stat-value">{{ formatNumber(user.gpsPointsCount) }}</span>
            </div>
            <div class="user-stat">
              <span class="stat-label">{{ t('admin.usersPage.columnCreated') }}</span>
              <span class="stat-value">{{ formatDate(user.createdAt) }}</span>
            </div>
          </div>

          <div class="gp-admin-list-card-actions user-card-actions" @click.stop>
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
              :disabled="adminReadOnly || user.id === currentUserId"
              size="small"
            />
            <Button
              icon="pi pi-trash"
              rounded
              text
              severity="danger"
              @click="confirmDelete(user)"
              :disabled="adminReadOnly || user.id === currentUserId"
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
          <i18n-t keypath="admin.usersPage.confirmDeleteMessage" tag="span">
            <template #email><strong>{{ userToDelete?.email }}</strong></template>
          </i18n-t>
          <br><br>
          {{ t('admin.usersPage.confirmDeleteDetail') }}
        </span>
      </div>
      <template #footer>
        <Button :label="t('admin.usersPage.cancel')" icon="pi pi-times" text @click="deleteDialogVisible = false" />
        <Button :label="t('admin.usersPage.delete')" icon="pi pi-trash" severity="danger" @click="deleteUser" :loading="deleting" />
      </template>
    </Dialog>

    <Toast />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
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
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import DemoReadOnlyBanner from '@/components/admin/DemoReadOnlyBanner.vue'
import { useTimezone } from '@/composables/useTimezone'
import { useAdminStore } from '@/stores/admin'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { t } = useI18n()
const router = useRouter()
const toast = useToast()
const authStore = useAuthStore()
const adminStore = useAdminStore()
const { adminReadOnly } = storeToRefs(authStore)
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
  { label: t('admin.usersPage.breadcrumbTitle') }
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

    const response = await adminStore.getUsers(Object.fromEntries(params))
    users.value = response.items
    totalRecords.value = response.totalElements
  } catch (error) {
    console.error('Failed to load users:', error)
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('admin.usersPage.toasts.loadFailedDetail'),
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
    await adminStore.updateUserStatus(user.id, !user.active)

    user.active = !user.active

    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: user.active ? t('admin.usersPage.toasts.userEnabled') : t('admin.usersPage.toasts.userDisabled'),
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

const confirmDelete = (user) => {
  userToDelete.value = user
  deleteDialogVisible.value = true
}

const deleteUser = async () => {
  if (!userToDelete.value) return

  deleting.value = true
  try {
    await adminStore.deleteUser(userToDelete.value.id)

    toast.add({
      severity: 'success',
      summary: t('common.success'),
      detail: t('admin.usersPage.toasts.deletedDetail'),
      life: 3000
    })

    deleteDialogVisible.value = false
    userToDelete.value = null
    loadUsers()
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
  return timezone.formatDateDisplay(dateStr)
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
.no-underline {
  text-decoration: none;
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

.user-info {
  flex: 1;
  min-width: 0;
}

.user-email {
  font-weight: 600;
  color: var(--gp-primary);
  margin-bottom: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-name {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
}

.user-badges {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  align-items: flex-end;
}

.user-stat {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.stat-label {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
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
  border-top: 1px solid var(--gp-border);
}

/* Mobile Pagination */
.mobile-pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1rem;
  padding: 1rem;
  background: var(--gp-surface-card);
  border-radius: 8px;
}

.pagination-info {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  padding: 0 0.5rem;
}

/* Mobile Responsive Styles */
@media (max-width: 768px) {
  .search-container {
    margin-bottom: 0.75rem;
  }

  .desktop-only {
    display: none;
  }

  .mobile-only {
    display: block;
  }

}

</style>
