<template>
  <section class="settings-group api-tokens-section" aria-labelledby="api-tokens-heading">
    <div class="settings-group-header has-action">
      <div>
        <h3 id="api-tokens-heading">{{ t('profile.access.apiTokens.heading') }}</h3>
        <p>{{ t('profile.access.apiTokens.description') }}</p>
      </div>
      <div>
        <Button
          :label="t('profile.access.apiTokens.create')"
          icon="pi pi-plus"
          size="small"
          :disabled="readOnly"
          @click="openCreateDialog"
        />
      </div>
    </div>

    <div class="settings-panel tokens-panel">
      <DataTable
        :value="tokens"
        :loading="loading"
        dataKey="id"
        responsiveLayout="scroll"
        class="tokens-table"
      >
        <Column field="name" :header="t('profile.access.apiTokens.columns.name')">
          <template #body="{ data }">
            <div class="token-name">{{ data.name }}</div>
            <div class="token-preview">{{ data.preview }}</div>
          </template>
        </Column>
        <Column field="status" :header="t('profile.access.apiTokens.columns.status')">
          <template #body="{ data }">
            <Tag :value="formatStatus(data.status)" :severity="statusSeverity(data.status)" />
          </template>
        </Column>
        <Column field="expiresAt" :header="t('profile.access.apiTokens.columns.expires')">
          <template #body="{ data }">
            {{ formatDateTime(data.expiresAt) || t('profile.access.apiTokens.never') }}
          </template>
        </Column>
        <Column field="lastUsedAt" :header="t('profile.access.apiTokens.columns.lastUsed')">
          <template #body="{ data }">
            <div>{{ formatDateTime(data.lastUsedAt) || t('profile.access.apiTokens.never') }}</div>
            <small v-if="data.lastUsedIp" class="muted">{{ data.lastUsedIp }}</small>
          </template>
        </Column>
        <Column :header="t('profile.access.apiTokens.columns.actions')" :exportable="false">
          <template #body="{ data }">
            <div class="row-actions">
              <Button
                icon="pi pi-pencil"
                rounded
                text
                severity="info"
                :disabled="readOnly || data.status === 'REVOKED'"
                @click="openEditDialog(data)"
                v-tooltip="t('profile.access.apiTokens.tooltips.edit')"
              />
              <Button
                icon="pi pi-ban"
                rounded
                text
                severity="danger"
                :disabled="readOnly || data.status === 'REVOKED'"
                @click="openRevokeDialog(data)"
                v-tooltip="t('profile.access.apiTokens.tooltips.revoke')"
              />
            </div>
          </template>
        </Column>
        <template #empty>
          <div class="empty-state">{{ t('profile.access.apiTokens.empty') }}</div>
        </template>
      </DataTable>
    </div>

    <Dialog
      v-model:visible="editDialogVisible"
      :header="editingToken ? t('profile.access.apiTokens.editDialog.editHeader') : t('profile.access.apiTokens.editDialog.createHeader')"
      :modal="true"
      :style="{ width: '440px' }"
    >
        <div class="dialog-form">
          <div class="form-field">
            <label for="api-token-name">{{ t('profile.access.apiTokens.editDialog.nameLabel') }}</label>
            <InputText
              id="api-token-name"
              v-model="form.name"
              :placeholder="t('profile.access.apiTokens.editDialog.namePlaceholder')"
              class="w-full"
              :invalid="!!formError"
              :disabled="readOnly"
            />
            <small v-if="formError" class="error-message">{{ formError }}</small>
          </div>
          <div class="form-field expiration-field">
            <label for="api-token-expiry">{{ t('profile.access.apiTokens.editDialog.expirationLabel') }}</label>
            <DatePicker
              id="api-token-expiry"
              v-model="form.expiresAt"
              showTime
              hourFormat="24"
              showButtonBar
              :minDate="new Date()"
              dateFormat="yy-mm-dd"
              :placeholder="t('profile.access.apiTokens.editDialog.expirationPlaceholder')"
              class="expiration-picker w-full"
              :disabled="readOnly"
            />
            <small class="muted">{{ t('profile.access.apiTokens.editDialog.expirationHint') }}</small>
          </div>
        </div>
        <template #footer>
          <Button :label="t('profile.access.apiTokens.cancel')" icon="pi pi-times" text @click="closeEditDialog" />
          <Button
            :label="editingToken ? t('profile.access.apiTokens.editDialog.save') : t('profile.access.apiTokens.editDialog.create')"
            icon="pi pi-check"
            :loading="saving"
            :disabled="readOnly"
            @click="saveToken"
          />
        </template>
    </Dialog>

    <Dialog
      v-model:visible="createdTokenDialogVisible"
      :header="t('profile.access.apiTokens.createdDialog.header')"
      :modal="true"
      :closable="false"
      :style="{ width: '560px' }"
    >
        <div class="created-token">
          <p>{{ t('profile.access.apiTokens.createdDialog.message') }}</p>
          <div class="token-secret">
            <InputText :modelValue="createdToken" readonly class="w-full" />
            <Button icon="pi pi-copy" @click="copyCreatedToken" />
          </div>
        </div>
        <template #footer>
          <Button :label="t('profile.access.apiTokens.createdDialog.confirm')" @click="closeCreatedTokenDialog" />
        </template>
    </Dialog>

    <Dialog
      v-model:visible="revokeDialogVisible"
      :header="t('profile.access.apiTokens.revokeDialog.header')"
      :modal="true"
      :style="{ width: '420px' }"
    >
        <p>
          {{ t('profile.access.apiTokens.revokeDialog.confirmPrefix') }}<strong>{{ tokenToRevoke?.name }}</strong>{{ t('profile.access.apiTokens.revokeDialog.confirmSuffix') }}
        </p>
        <template #footer>
          <Button :label="t('profile.access.apiTokens.cancel')" icon="pi pi-times" text @click="revokeDialogVisible = false" />
          <Button
            :label="t('profile.access.apiTokens.revokeDialog.confirm')"
            icon="pi pi-ban"
            severity="danger"
            :loading="revoking"
            :disabled="readOnly"
            @click="revokeToken"
          />
        </template>
    </Dialog>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { copyToClipboard } from '@/utils/clipboardUtils'

const { t } = useI18n()
const toast = useToast()
const authStore = useAuthStore()

const props = defineProps({
  readOnly: {
    type: Boolean,
    default: false
  }
})

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
    tokens.value = await authStore.listApiTokens()
  } catch (error) {
    toast.add({ severity: 'error', summary: t('profile.access.apiTokens.toasts.error'), detail: t('profile.access.apiTokens.toasts.loadFailed'), life: 3000 })
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  if (props.readOnly) return
  editingToken.value = null
  form.value = { name: '', expiresAt: null }
  formError.value = ''
  editDialogVisible.value = true
}

const openEditDialog = (token) => {
  if (props.readOnly) return
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
  if (props.readOnly) return
  const name = form.value.name.trim()
  if (!name) {
    formError.value = t('profile.access.apiTokens.validation.nameRequired')
    return
  }

  saving.value = true
  try {
    const payload = {
      name,
      expiresAt: toInstantOrNull(form.value.expiresAt)
    }

    if (editingToken.value) {
      await authStore.saveApiToken(editingToken.value.id, payload)
      toast.add({ severity: 'success', summary: t('profile.access.apiTokens.toasts.saved.title'), detail: t('profile.access.apiTokens.toasts.saved.detail'), life: 2500 })
    } else {
      const response = await authStore.saveApiToken(null, payload)
      createdToken.value = response?.token || ''
      createdTokenDialogVisible.value = !!createdToken.value
      toast.add({ severity: 'success', summary: t('profile.access.apiTokens.toasts.created.title'), detail: t('profile.access.apiTokens.toasts.created.detail'), life: 2500 })
    }

    closeEditDialog()
    await loadTokens()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('profile.access.apiTokens.toasts.error'),
      detail: formatApiErrorDetail(error, t('profile.access.apiTokens.toasts.saveFailed')),
      life: 3500
    })
  } finally {
    saving.value = false
  }
}

const openRevokeDialog = (token) => {
  if (props.readOnly) return
  tokenToRevoke.value = token
  revokeDialogVisible.value = true
}

const revokeToken = async () => {
  if (props.readOnly) return
  if (!tokenToRevoke.value) return

  revoking.value = true
  try {
    await authStore.revokeApiToken(tokenToRevoke.value.id)
    toast.add({ severity: 'success', summary: t('profile.access.apiTokens.toasts.revoked.title'), detail: t('profile.access.apiTokens.toasts.revoked.detail'), life: 2500 })
    revokeDialogVisible.value = false
    tokenToRevoke.value = null
    await loadTokens()
  } catch (error) {
    toast.add({ severity: 'error', summary: t('profile.access.apiTokens.toasts.error'), detail: t('profile.access.apiTokens.toasts.revokeFailed'), life: 3000 })
  } finally {
    revoking.value = false
  }
}

const copyCreatedToken = async () => {
  const copied = await copyToClipboard(createdToken.value)
  toast.add({
    severity: copied ? 'success' : 'warn',
    summary: copied ? t('profile.access.apiTokens.toasts.copied.title') : t('profile.access.apiTokens.toasts.copyFailed.title'),
    detail: copied ? t('profile.access.apiTokens.toasts.copied.detail') : t('profile.access.apiTokens.toasts.copyFailed.detail'),
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

// Backend `TokenStatus` -> catalog key. The enum itself is compared in code (`statusSeverity`, the
// row actions) and is never translated; only the rendered label is. An unknown status falls through
// to its raw value, which is what this used to render for every status.
const statusKeys = {
  ACTIVE: 'active',
  EXPIRED: 'expired',
  REVOKED: 'revoked'
}

const formatStatus = (status) => {
  if (!status) return ''
  const key = statusKeys[status]
  return key ? t(`profile.access.apiTokens.status.${key}`) : status
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
.tokens-panel {
  overflow-x: auto;
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
