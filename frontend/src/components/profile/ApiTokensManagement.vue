<template>
  <Card class="profile-section-card api-tokens-card">
    <template #title>API Tokens</template>
    <template #subtitle>Create named tokens for bots, MCP clients, and automation.</template>
    <template #content>
      <div class="tokens-toolbar">
        <Button
          label="Create Token"
          icon="pi pi-plus"
          size="small"
          @click="openCreateDialog"
        />
      </div>

      <DataTable
        :value="tokens"
        :loading="loading"
        dataKey="id"
        responsiveLayout="scroll"
        class="tokens-table"
      >
        <Column field="name" header="Name">
          <template #body="{ data }">
            <div class="token-name">{{ data.name }}</div>
            <div class="token-preview">{{ data.preview }}</div>
          </template>
        </Column>
        <Column field="status" header="Status">
          <template #body="{ data }">
            <Tag :value="formatStatus(data.status)" :severity="statusSeverity(data.status)" />
          </template>
        </Column>
        <Column field="expiresAt" header="Expires">
          <template #body="{ data }">
            {{ formatDateTime(data.expiresAt) || 'Never' }}
          </template>
        </Column>
        <Column field="lastUsedAt" header="Last Used">
          <template #body="{ data }">
            <div>{{ formatDateTime(data.lastUsedAt) || 'Never' }}</div>
            <small v-if="data.lastUsedIp" class="muted">{{ data.lastUsedIp }}</small>
          </template>
        </Column>
        <Column header="Actions" :exportable="false">
          <template #body="{ data }">
            <div class="row-actions">
              <Button
                icon="pi pi-pencil"
                rounded
                text
                severity="info"
                :disabled="data.status === 'REVOKED'"
                @click="openEditDialog(data)"
                v-tooltip="'Edit token'"
              />
              <Button
                icon="pi pi-ban"
                rounded
                text
                severity="danger"
                :disabled="data.status === 'REVOKED'"
                @click="openRevokeDialog(data)"
                v-tooltip="'Revoke token'"
              />
            </div>
          </template>
        </Column>
        <template #empty>
          <div class="empty-state">No API tokens created.</div>
        </template>
      </DataTable>

      <Dialog
        v-model:visible="editDialogVisible"
        :header="editingToken ? 'Edit API Token' : 'Create API Token'"
        :modal="true"
        :style="{ width: '440px' }"
      >
        <div class="dialog-form">
          <div class="form-field">
            <label for="api-token-name">Name</label>
            <InputText
              id="api-token-name"
              v-model="form.name"
              placeholder="Automation token"
              class="w-full"
              :invalid="!!formError"
            />
            <small v-if="formError" class="error-message">{{ formError }}</small>
          </div>
          <div class="form-field expiration-field">
            <label for="api-token-expiry">Expiration</label>
            <DatePicker
              id="api-token-expiry"
              v-model="form.expiresAt"
              showTime
              hourFormat="24"
              showButtonBar
              :minDate="new Date()"
              dateFormat="yy-mm-dd"
              placeholder="No expiration"
              class="expiration-picker w-full"
            />
            <small class="muted">Leave empty for no expiration.</small>
          </div>
        </div>
        <template #footer>
          <Button label="Cancel" icon="pi pi-times" text @click="closeEditDialog" />
          <Button
            :label="editingToken ? 'Save' : 'Create'"
            icon="pi pi-check"
            :loading="saving"
            @click="saveToken"
          />
        </template>
      </Dialog>

      <Dialog
        v-model:visible="createdTokenDialogVisible"
        header="API Token Created"
        :modal="true"
        :closable="false"
        :style="{ width: '560px' }"
      >
        <div class="created-token">
          <p>This token is shown once. Store it securely before closing this dialog.</p>
          <div class="token-secret">
            <InputText :modelValue="createdToken" readonly class="w-full" />
            <Button icon="pi pi-copy" @click="copyCreatedToken" />
          </div>
        </div>
        <template #footer>
          <Button label="I have stored this token" @click="closeCreatedTokenDialog" />
        </template>
      </Dialog>

      <Dialog
        v-model:visible="revokeDialogVisible"
        header="Revoke API Token"
        :modal="true"
        :style="{ width: '420px' }"
      >
        <p>
          Revoke <strong>{{ tokenToRevoke?.name }}</strong>? Automation using this token will stop immediately.
        </p>
        <template #footer>
          <Button label="Cancel" icon="pi pi-times" text @click="revokeDialogVisible = false" />
          <Button
            label="Revoke"
            icon="pi pi-ban"
            severity="danger"
            :loading="revoking"
            @click="revokeToken"
          />
        </template>
      </Dialog>
    </template>
  </Card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import { useToast } from 'primevue/usetoast'
import apiService from '@/utils/apiService'
import { copyToClipboard } from '@/utils/clipboardUtils'

const toast = useToast()

const tokens = ref([])
const loading = ref(false)
const saving = ref(false)
const revoking = ref(false)
const editDialogVisible = ref(false)
const createdTokenDialogVisible = ref(false)
const revokeDialogVisible = ref(false)
const editingToken = ref(null)
const tokenToRevoke = ref(null)
const createdToken = ref('')
const formError = ref('')
const form = ref({
  name: '',
  expiresAt: null
})

const loadTokens = async () => {
  loading.value = true
  try {
    const response = await apiService.get('/api-tokens')
    tokens.value = response?.data || []
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load API tokens', life: 3000 })
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  editingToken.value = null
  form.value = { name: '', expiresAt: null }
  formError.value = ''
  editDialogVisible.value = true
}

const openEditDialog = (token) => {
  editingToken.value = token
  form.value = {
    name: token.name || '',
    expiresAt: toDatePickerValue(token.expiresAt)
  }
  formError.value = ''
  editDialogVisible.value = true
}

const closeEditDialog = () => {
  editDialogVisible.value = false
  editingToken.value = null
  formError.value = ''
}

const saveToken = async () => {
  const name = form.value.name.trim()
  if (!name) {
    formError.value = 'Token name is required'
    return
  }

  saving.value = true
  try {
    const payload = {
      name,
      expiresAt: toInstantOrNull(form.value.expiresAt)
    }

    if (editingToken.value) {
      await apiService.put(`/api-tokens/${editingToken.value.id}`, payload)
      toast.add({ severity: 'success', summary: 'Saved', detail: 'API token updated', life: 2500 })
    } else {
      const response = await apiService.post('/api-tokens', payload)
      createdToken.value = response?.data?.token || ''
      createdTokenDialogVisible.value = !!createdToken.value
      toast.add({ severity: 'success', summary: 'Created', detail: 'API token created', life: 2500 })
    }

    closeEditDialog()
    await loadTokens()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.message || error.response?.data?.error || 'Failed to save API token',
      life: 3500
    })
  } finally {
    saving.value = false
  }
}

const openRevokeDialog = (token) => {
  tokenToRevoke.value = token
  revokeDialogVisible.value = true
}

const revokeToken = async () => {
  if (!tokenToRevoke.value) return

  revoking.value = true
  try {
    await apiService.delete(`/api-tokens/${tokenToRevoke.value.id}`)
    toast.add({ severity: 'success', summary: 'Revoked', detail: 'API token revoked', life: 2500 })
    revokeDialogVisible.value = false
    tokenToRevoke.value = null
    await loadTokens()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to revoke API token', life: 3000 })
  } finally {
    revoking.value = false
  }
}

const copyCreatedToken = async () => {
  const copied = await copyToClipboard(createdToken.value)
  toast.add({
    severity: copied ? 'success' : 'warn',
    summary: copied ? 'Copied' : 'Copy failed',
    detail: copied ? 'Token copied to clipboard' : 'Select the token and copy it manually',
    life: 2500
  })
}

const closeCreatedTokenDialog = () => {
  createdTokenDialogVisible.value = false
  createdToken.value = ''
}

const statusSeverity = (status) => {
  if (status === 'ACTIVE') return 'success'
  if (status === 'EXPIRED') return 'warning'
  return 'danger'
}

const formatStatus = (status) => {
  if (!status) return ''
  return status.charAt(0) + status.slice(1).toLowerCase()
}

const formatDateTime = (value) => {
  if (!value) return ''
  return new Date(value).toLocaleString()
}

const toDatePickerValue = (value) => {
  return value ? new Date(value) : null
}

const toInstantOrNull = (value) => {
  if (!value) return null
  return value instanceof Date ? value.toISOString() : new Date(value).toISOString()
}

onMounted(loadTokens)
</script>

<style scoped>
.api-tokens-card {
  width: 100%;
}

.api-tokens-card :deep(.p-card-content) {
  width: 100%;
  box-sizing: border-box;
}

.tokens-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 1rem;
}

.muted {
  color: var(--gp-text-secondary);
  font-size: 0.88rem;
}

.token-name {
  font-weight: 600;
}

.token-preview {
  color: var(--gp-text-secondary);
  font-family: monospace;
  font-size: 0.85rem;
}

.row-actions,
.token-secret {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.empty-state {
  padding: 1rem;
  text-align: center;
  color: var(--gp-text-secondary);
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.error-message {
  color: var(--gp-danger);
  font-size: 0.85rem;
}

.created-token {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

@media (max-width: 640px) {
  .section-header {
    align-items: stretch;
    flex-direction: column;
  }

  .expiration-field {
    align-items: flex-start;
  }

  .expiration-picker {
    width: min(100%, 14rem);
  }

  .dialog-form :deep(.p-inputtext) {
    font-size: 16px;
  }
}
</style>
