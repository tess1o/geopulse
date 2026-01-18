<template>
  <AppLayout>
    <PageContainer>
    <div class="admin-oidc-providers">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <div>
          <h1>OIDC Providers</h1>
          <p class="text-muted">Manage OAuth/OIDC authentication providers</p>
        </div>
        <Button
          label="Add Provider"
          icon="pi pi-plus"
          @click="openCreateDialog"
          class="add-provider-button"
        />
      </div>

      <!-- Desktop Table View -->
      <div class="card desktop-only">
        <DataTable
          :value="providers"
          :loading="loading"
          dataKey="name"
          responsiveLayout="scroll"
        >
          <template #header>
            <div class="table-header">
              <span class="text-xl font-semibold">Configured Providers</span>
            </div>
          </template>

          <Column field="name" header="Name" sortable style="min-width: 150px">
            <template #body="{ data }">
              <div class="flex align-items-center gap-2">
                <ProviderIcon :provider="data" size="medium" :alt="`${data.displayName} icon`" />
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

      <!-- Mobile Card View -->
      <div class="mobile-only">
        <div v-if="loading" class="text-center p-4">
          <i class="pi pi-spin pi-spinner" style="font-size: 2rem"></i>
        </div>

        <div v-else-if="providers.length === 0" class="text-center p-4 card">
          No OIDC providers configured.
        </div>

        <div v-else class="provider-cards">
          <div v-for="provider in providers" :key="provider.name" class="provider-card">
            <div class="provider-card-header">
              <div class="provider-info">
                <div class="provider-name">
                  <ProviderIcon :provider="provider" size="medium" :alt="`${provider.displayName} icon`" custom-class="provider-icon" />
                  <span>{{ provider.name }}</span>
                </div>
                <div class="provider-display-name">{{ provider.displayName }}</div>
              </div>
              <div class="provider-badges">
                <Tag :severity="provider.enabled ? 'success' : 'danger'" :value="provider.enabled ? 'Enabled' : 'Disabled'" />
              </div>
            </div>

            <div class="provider-card-body">
              <div class="provider-stat">
                <span class="stat-label">Source</span>
                <Tag
                  :severity="provider.source === 'ENVIRONMENT' ? 'info' : 'success'"
                  :value="provider.source === 'ENVIRONMENT' ? 'Environment' : 'Custom'"
                />
              </div>
              <div class="provider-stat">
                <span class="stat-label">Metadata</span>
                <Tag
                  :severity="provider.metadataValid ? 'success' : 'warning'"
                  :value="provider.metadataValid ? 'Cached' : 'Not Cached'"
                />
              </div>
            </div>

            <div class="provider-client-id">
              <span class="stat-label">Client ID</span>
              <code class="client-id-value">{{ provider.clientId }}</code>
            </div>

            <div class="provider-card-actions">
              <Button
                icon="pi pi-pencil"
                label="Edit"
                rounded
                text
                severity="info"
                @click="openEditDialog(provider)"
                size="small"
              />
              <Button
                :icon="provider.enabled ? 'pi pi-ban' : 'pi pi-check'"
                :label="provider.enabled ? 'Disable' : 'Enable'"
                rounded
                text
                :severity="provider.enabled ? 'warning' : 'success'"
                @click="toggleProviderStatus(provider)"
                size="small"
              />
              <Button
                icon="pi pi-wifi"
                label="Test"
                rounded
                text
                severity="success"
                @click="testProvider(provider)"
                :loading="testingProvider === provider.name"
                size="small"
              />
              <Button
                icon="pi pi-trash"
                label="Delete"
                rounded
                text
                severity="danger"
                @click="confirmDelete(provider)"
                :disabled="!canDeleteProvider(provider)"
                size="small"
              />
            </div>
          </div>
        </div>
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
        <div v-if="testResult" class="test-result-content">
          <div class="test-result-header">
            <i
              :class="testResult.success ? 'pi pi-check-circle text-3xl text-green-500' : 'pi pi-times-circle text-3xl text-red-500'"
            ></i>
            <span class="test-result-message">
              {{ testResult.message }}
            </span>
          </div>

          <div v-if="testResult.success">
            <h4 class="endpoints-title">Discovered Endpoints:</h4>
            <div class="endpoint-list">
              <div class="endpoint-item">
                <span class="endpoint-label">Authorization:</span>
                <code class="endpoint-value">{{ testResult.authorizationEndpoint }}</code>
              </div>
              <div class="endpoint-item">
                <span class="endpoint-label">Token:</span>
                <code class="endpoint-value">{{ testResult.tokenEndpoint }}</code>
              </div>
              <div class="endpoint-item">
                <span class="endpoint-label">UserInfo:</span>
                <code class="endpoint-value">{{ testResult.userinfoEndpoint }}</code>
              </div>
              <div class="endpoint-item">
                <span class="endpoint-label">JWKS:</span>
                <code class="endpoint-value">{{ testResult.jwksUri }}</code>
              </div>
              <div class="endpoint-item">
                <span class="endpoint-label">Issuer:</span>
                <code class="endpoint-value">{{ testResult.issuer }}</code>
              </div>
            </div>
          </div>

          <div v-else>
            <h4 class="endpoints-title">Error Details:</h4>
            <div class="error-details">
              <div class="endpoint-item">
                <span class="endpoint-label">Type:</span>
                <code class="endpoint-value">{{ testResult.errorType }}</code>
              </div>
              <div class="endpoint-item">
                <span class="endpoint-label">Details:</span>
                <code class="endpoint-value error-value">{{ testResult.errorDetails }}</code>
              </div>
            </div>
          </div>
        </div>
        <template #footer>
          <Button label="Close" icon="pi pi-times" @click="testResultDialogVisible = false" />
        </template>
      </Dialog>

      <Toast />
    </div>
    </PageContainer>
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
import ProviderIcon from '@/components/common/ProviderIcon.vue'
import adminService from '@/utils/adminService'
import PageContainer from "@/components/ui/layout/PageContainer.vue";

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
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 2rem;
  gap: 1rem;
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

/* Test Result Dialog */
.test-result-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.test-result-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--surface-border);
}

.test-result-message {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-color);
}

.endpoints-title {
  margin: 0 0 0.75rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-color-secondary);
}

.endpoint-list,
.error-details {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.endpoint-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.endpoint-label {
  font-weight: 600;
  color: var(--text-color);
  font-size: 0.875rem;
}

.endpoint-value {
  background-color: var(--surface-100);
  padding: 0.5rem 0.75rem;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 0.8rem;
  word-break: break-all;
  display: block;
}

.endpoint-value.error-value {
  background-color: var(--red-50);
  color: var(--red-700);
}

/* Desktop/Mobile Toggle */
.desktop-only {
  display: block;
}

.mobile-only {
  display: none;
}

/* Mobile Provider Cards */
.provider-cards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0.5rem;
  border-radius: 8px;
}

.provider-card {
  background: var(--gp-surface-white);
  border: 2px solid var(--surface-border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(255, 255, 255, 0.05);
  transition: all 0.2s ease;
}

/* Dark theme specific */
:global(.p-dark) .provider-card,
:global([data-theme="dark"]) .provider-card,
:global(html.dark) .provider-card {
  background: var(--gp-surface-dark);
}

.provider-card:active {
  transform: scale(0.98);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}

.provider-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.75rem;
  gap: 0.5rem;
}

.provider-info {
  flex: 1;
  min-width: 0;
}

.provider-name {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  font-size: 1rem;
  color: var(--text-color);
  margin-bottom: 0.25rem;
}

.provider-icon {
  font-size: 1.25rem;
  color: var(--primary-color);
}

.provider-display-name {
  font-size: 0.9rem;
  color: var(--text-color-secondary);
}

.provider-badges {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  align-items: flex-end;
}

.provider-card-body {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--surface-border);
}

.provider-stat {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  flex: 1;
}

.stat-label {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  text-transform: uppercase;
  font-weight: 600;
}

.provider-client-id {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.75rem;
}

.client-id-value {
  background-color: var(--surface-100);
  padding: 0.5rem;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 0.75rem;
  word-break: break-all;
  display: block;
}

.provider-card-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--surface-border);
}

/* Mobile Responsive Styles */
@media (max-width: 768px) {
  .admin-oidc-providers {
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

  .add-provider-button {
    width: 100%;
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
  .admin-oidc-providers {
    padding: 0.5rem;
  }

  .page-header h1 {
    font-size: 1.25rem;
  }

  .provider-card {
    padding: 0.75rem;
  }

  .provider-card-body {
    gap: 1rem;
  }
}
</style>
