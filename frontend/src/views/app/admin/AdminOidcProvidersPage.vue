<template>
  <AppLayout>
    <div class="admin-oidc-providers">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <h1>OIDC Providers</h1>
        <p class="text-muted">Manage OAuth/OIDC authentication providers</p>
      </div>

      <div class="card">
        <DataTable
          :value="providers"
          :loading="loading"
          dataKey="name"
          responsiveLayout="scroll"
        >
          <template #header>
            <div class="table-header">
              <span class="text-xl font-semibold">Configured Providers</span>
              <Button
                label="Add Provider"
                icon="pi pi-plus"
                @click="openCreateDialog"
                class="add-provider-button"
              />
            </div>
          </template>

          <Column field="name" header="Name" sortable style="min-width: 150px">
            <template #body="{ data }">
              <div class="flex align-items-center gap-2">
                <i :class="data.icon || 'pi pi-key'" class="text-xl"></i>
                <span class="font-semibold">{{ data.name }}</span>
              </div>
            </template>
          </Column>

          <Column field="displayName" header="Display Name" sortable />

          <Column field="enabled" header="Enabled" sortable style="min-width: 100px">
            <template #body="{ data }">
              <Tag :severity="data.enabled ? 'success' : 'danger'" :value="data.enabled ? 'Yes' : 'No'" />
            </template>
          </Column>

          <Column field="source" header="Source" sortable style="min-width: 120px">
            <template #body="{ data }">
              <Tag
                :severity="data.source === 'ENVIRONMENT' ? 'info' : 'success'"
                :value="data.source === 'ENVIRONMENT' ? 'Environment' : 'Custom'"
              />
            </template>
          </Column>

          <Column field="metadataValid" header="Metadata" style="min-width: 120px">
            <template #body="{ data }">
              <Tag
                :severity="data.metadataValid ? 'success' : 'warning'"
                :value="data.metadataValid ? 'Cached' : 'Not Cached'"
              />
            </template>
          </Column>

          <Column field="clientId" header="Client ID" style="min-width: 200px">
            <template #body="{ data }">
              <code class="text-sm">{{ data.clientId }}</code>
            </template>
          </Column>

          <Column header="Actions" :exportable="false" style="min-width: 250px">
            <template #body="{ data }">
              <div class="flex gap-2">
                <Button
                  icon="pi pi-pencil"
                  rounded
                  text
                  severity="info"
                  @click="openEditDialog(data)"
                  v-tooltip="'Edit Provider'"
                />
                <Button
                  :icon="data.enabled ? 'pi pi-ban' : 'pi pi-check'"
                  rounded
                  text
                  :severity="data.enabled ? 'warning' : 'success'"
                  @click="toggleProviderStatus(data)"
                  v-tooltip="data.enabled ? 'Disable Provider' : 'Enable Provider'"
                />
                <Button
                  icon="pi pi-wifi"
                  rounded
                  text
                  severity="success"
                  @click="testProvider(data)"
                  v-tooltip="'Test Connection'"
                  :loading="testingProvider === data.name"
                />
                <Button
                  icon="pi pi-trash"
                  rounded
                  text
                  severity="danger"
                  @click="confirmDelete(data)"
                  v-tooltip="getDeleteTooltip(data)"
                  :disabled="!canDeleteProvider(data)"
                />
              </div>
            </template>
          </Column>

          <template #empty>
            <div class="text-center p-4">
              No OIDC providers configured.
            </div>
          </template>
        </DataTable>
      </div>

      <!-- Create/Edit Provider Dialog -->
      <OidcProviderDialog
        v-model:visible="providerDialogVisible"
        :provider="selectedProvider"
        :is-edit="isEditMode"
        @save="handleSaveProvider"
      />

      <!-- Delete Confirmation Dialog -->
      <Dialog
        v-model:visible="deleteDialogVisible"
        header="Confirm Delete"
        :modal="true"
        :style="{ width: '500px' }"
      >
        <div class="flex align-items-center gap-3 mb-3">
          <i class="pi pi-exclamation-triangle text-4xl text-red-500"></i>
          <div>
            <p class="mb-2">Are you sure you want to delete provider <strong>{{ providerToDelete?.name }}</strong>?</p>

            <div v-if="isEnvironmentProvider(providerToDelete)" class="p-3 bg-blue-50 border-round mt-3">
              <strong>ℹ️ Note:</strong> This provider is also defined in environment variables.
              <br>
              Deleting will remove the custom database configuration and revert to environment defaults.
            </div>

            <div v-else class="p-3 bg-red-50 border-round mt-3">
              <strong>⚠️ Warning:</strong> This is a custom provider. Deletion is permanent.
            </div>
          </div>
        </div>
        <template #footer>
          <Button label="Cancel" icon="pi pi-times" text @click="deleteDialogVisible = false" />
          <Button
            :label="isEnvironmentProvider(providerToDelete) ? 'Revert to Environment' : 'Delete'"
            icon="pi pi-trash"
            severity="danger"
            @click="deleteProvider"
            :loading="deleting"
          />
        </template>
      </Dialog>

      <!-- Test Result Dialog -->
      <Dialog
        v-model:visible="testResultDialogVisible"
        header="Provider Test Result"
        :modal="true"
        :style="{ width: '600px' }"
      >
        <div v-if="testResult" class="flex flex-column gap-3">
          <div class="flex align-items-center gap-3">
            <i
              :class="testResult.success ? 'pi pi-check-circle text-4xl text-green-500' : 'pi pi-times-circle text-4xl text-red-500'"
            ></i>
            <span class="text-xl font-semibold">
              {{ testResult.message }}
            </span>
          </div>

          <div v-if="testResult.success" class="mt-3">
            <h4>Discovered Endpoints:</h4>
            <div class="flex flex-column gap-2 mt-2">
              <div><strong>Authorization:</strong> <code class="text-sm">{{ testResult.authorizationEndpoint }}</code></div>
              <div><strong>Token:</strong> <code class="text-sm">{{ testResult.tokenEndpoint }}</code></div>
              <div><strong>UserInfo:</strong> <code class="text-sm">{{ testResult.userinfoEndpoint }}</code></div>
              <div><strong>JWKS:</strong> <code class="text-sm">{{ testResult.jwksUri }}</code></div>
              <div><strong>Issuer:</strong> <code class="text-sm">{{ testResult.issuer }}</code></div>
            </div>
          </div>

          <div v-else class="mt-3">
            <h4>Error Details:</h4>
            <div class="p-3 bg-red-50 border-round">
              <strong>Type:</strong> {{ testResult.errorType }}<br>
              <strong>Details:</strong> {{ testResult.errorDetails }}
            </div>
          </div>
        </div>
        <template #footer>
          <Button label="Close" icon="pi pi-times" @click="testResultDialogVisible = false" />
        </template>
      </Dialog>

      <Toast />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import Toast from 'primevue/toast'
import Breadcrumb from 'primevue/breadcrumb'
import { useToast } from 'primevue/usetoast'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import OidcProviderDialog from '@/components/admin/OidcProviderDialog.vue'
import adminService from '@/utils/adminService'

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
  { label: 'OIDC Providers' }
])

// State
const providers = ref([])
const loading = ref(false)
const providerDialogVisible = ref(false)
const deleteDialogVisible = ref(false)
const testResultDialogVisible = ref(false)
const selectedProvider = ref(null)
const providerToDelete = ref(null)
const isEditMode = ref(false)
const deleting = ref(false)
const testingProvider = ref(null)
const testResult = ref(null)

// Methods
const loadProviders = async () => {
  loading.value = true
  try {
    providers.value = await adminService.getAllOidcProviders()
  } catch (error) {
    console.error('Failed to load providers:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load OIDC providers',
      life: 5000
    })
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  selectedProvider.value = null
  isEditMode.value = false
  providerDialogVisible.value = true
}

const openEditDialog = (provider) => {
  selectedProvider.value = { ...provider }
  isEditMode.value = true
  providerDialogVisible.value = true
}

const handleSaveProvider = async (providerData) => {
  try {
    if (isEditMode.value) {
      await adminService.updateOidcProvider(providerData.name, providerData)
      toast.add({
        severity: 'success',
        summary: 'Success',
        detail: 'Provider updated successfully',
        life: 3000
      })
    } else {
      await adminService.createOidcProvider(providerData)
      toast.add({
        severity: 'success',
        summary: 'Success',
        detail: 'Provider created successfully',
        life: 3000
      })
    }
    providerDialogVisible.value = false
    await loadProviders()
  } catch (error) {
    console.error('Failed to save provider:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.error || 'Failed to save provider',
      life: 5000
    })
  }
}

const confirmDelete = (provider) => {
  providerToDelete.value = provider
  deleteDialogVisible.value = true
}

const deleteProvider = async () => {
  deleting.value = true
  try {
    const result = await adminService.deleteOidcProvider(providerToDelete.value.name)

    // Show appropriate message based on whether it reverted to env or was deleted
    const message = isEnvironmentProvider(providerToDelete.value)
      ? 'Custom configuration removed. Provider reverted to environment defaults.'
      : 'Provider deleted successfully'

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: result.message || message,
      life: 3000
    })
    deleteDialogVisible.value = false
    await loadProviders()
  } catch (error) {
    console.error('Failed to delete provider:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.error || 'Failed to delete provider',
      life: 5000
    })
  } finally {
    deleting.value = false
  }
}

const testProvider = async (provider) => {
  testingProvider.value = provider.name
  try {
    const result = await adminService.testOidcProvider(provider.name)
    testResult.value = result
    testResultDialogVisible.value = true

    if (result.success) {
      toast.add({
        severity: 'success',
        summary: 'Connection Successful',
        detail: 'Provider connection tested successfully',
        life: 3000
      })
    } else {
      toast.add({
        severity: 'error',
        summary: 'Connection Failed',
        detail: 'Failed to connect to provider',
        life: 5000
      })
    }
  } catch (error) {
    console.error('Failed to test provider:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to test provider connection',
      life: 5000
    })
  } finally {
    testingProvider.value = null
  }
}

const toggleProviderStatus = async (provider) => {
  try {
    const newStatus = !provider.enabled
    await adminService.updateOidcProvider(provider.name, {
      displayName: provider.displayName,
      enabled: newStatus,
      clientId: provider.clientId,
      discoveryUrl: provider.discoveryUrl,
      icon: provider.icon,
      scopes: provider.scopes
    })

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: `Provider ${newStatus ? 'enabled' : 'disabled'} successfully`,
      life: 3000
    })
    await loadProviders()
  } catch (error) {
    console.error('Failed to toggle provider status:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.error || 'Failed to update provider status',
      life: 5000
    })
  }
}

const canDeleteProvider = (provider) => {
  // Can only delete if provider is in database (source = DATABASE)
  // Cannot delete pure environment providers (source = ENVIRONMENT)
  return provider.source === 'DATABASE'
}

const isEnvironmentProvider = (provider) => {
  if (!provider) return false
  // Check if provider also exists in environment variables
  return provider.hasEnvironmentConfig === true
}

const getDeleteTooltip = (provider) => {
  if (!canDeleteProvider(provider)) {
    return 'Environment-only providers cannot be deleted. Remove from env vars to delete.'
  }
  if (isEnvironmentProvider(provider)) {
    return 'Delete custom config and revert to environment defaults'
  }
  return 'Permanently delete this custom provider'
}

onMounted(() => {
  loadProviders()
})
</script>

<style scoped>
.admin-oidc-providers {
  padding: 1.5rem;
}

.admin-breadcrumb {
  margin-bottom: 1.5rem;
}

.page-header {
  margin-bottom: 2rem;
}

.page-header h1 {
  margin: 0 0 0.5rem 0;
  font-size: 2rem;
  color: var(--text-color);
}

.text-muted {
  color: var(--text-color-secondary);
}

.card {
  background: var(--surface-card);
  padding: 1.5rem;
  border-radius: 12px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

code {
  background-color: var(--surface-100);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: 1rem;
}

.add-provider-button {
  margin-left: auto;
}
</style>
