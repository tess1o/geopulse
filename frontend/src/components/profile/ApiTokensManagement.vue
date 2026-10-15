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
        <Column field="scopes" :header="t('profile.access.apiTokens.columns.access')">
          <template #body="{ data }">
            <div>{{ formatAccess(data.scopes) }}</div>
            <small v-if="presetForScopes(data.scopes) === 'custom'" class="muted">{{ formatScopeList(data.scopes) }}</small>
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
      :style="{ width: '520px' }"
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
          <div class="form-field">
            <span class="field-label">{{ t('profile.access.apiTokens.access.label') }}</span>
            <template v-if="editingToken">
              <div>{{ formatAccess(editingToken.scopes) }}</div>
              <small class="muted">{{ t('profile.access.apiTokens.access.fixedHint') }}</small>
            </template>
            <template v-else>
              <div class="access-options" role="radiogroup">
                <label v-for="preset in API_TOKEN_PRESETS" :key="preset.id" class="access-option">
                  <RadioButton v-model="form.preset" :value="preset.id" :inputId="`api-token-preset-${preset.id}`" :disabled="readOnly" />
                  <span>
                    <span class="access-title">{{ t(`profile.access.apiTokens.access.presets.${preset.id}.title`) }}</span>
                    <small class="muted">{{ t(`profile.access.apiTokens.access.presets.${preset.id}.description`) }}</small>
                  </span>
                </label>
              </div>
              <div v-if="form.preset === 'custom'" class="custom-scopes">
                <label v-for="scope in API_TOKEN_SCOPES" :key="scope" class="access-option">
                  <Checkbox
                    :modelValue="form.customScopes.includes(scope)"
                    binary
                    :inputId="`api-token-scope-${scope}`"
                    :disabled="readOnly"
                    @update:modelValue="(checked) => form.customScopes = toggleCustomScope(form.customScopes, scope, checked)"
                  />
                  <span>
                    <span class="access-title">{{ t(`profile.access.apiTokens.access.scopes.${scopeKey(scope)}.title`) }}</span>
                    <small class="muted">{{ t(`profile.access.apiTokens.access.scopes.${scopeKey(scope)}.description`) }}</small>
                  </span>
                </label>
              </div>
              <small v-if="scopeError" class="error-message">{{ scopeError }}</small>
              <small class="muted">{{ t('profile.access.apiTokens.access.fixedHint') }}</small>
            </template>
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
          <Message v-if="createdPreset === 'homeAssistant'" severity="info" :closable="false">
            <div class="home-assistant-next">
              <span>{{ t('profile.access.apiTokens.createdDialog.homeAssistantNext') }}</span>
              <Button
                :label="t('profile.access.apiTokens.createdDialog.homeAssistantLink')"
                icon="pi pi-arrow-right"
                iconPos="right"
                size="small"
                @click="openHomeAssistantSetup"
              />
            </div>
          </Message>
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
import RadioButton from 'primevue/radiobutton'
import Checkbox from 'primevue/checkbox'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { copyToClipboard } from '@/utils/clipboardUtils'
import {
  API_TOKEN_PRESETS,
  API_TOKEN_SCOPES,
  FULL_ACCESS_SCOPE,
  presetForScopes,
  scopesForPreset,
  toggleCustomScope
} from '@/components/profile/apiTokenScopes'

const { t } = useI18n()
const toast = useToast()
const authStore = useAuthStore()
const router = useRouter()

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
const createdPreset = ref(null)
const createdTokenId = ref(null)
const formError = ref('')
const scopeError = ref('')
const emptyForm = () => ({ name: '', expiresAt: null, preset: 'readOnly', customScopes: ['read'] })
const form = ref(emptyForm())

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
  form.value = emptyForm()
  formError.value = ''
  scopeError.value = ''
  editDialogVisible.value = true
}

const openEditDialog = (token) => {
  if (props.readOnly) return
  editingToken.value = token
  form.value = {
    ...emptyForm(),
    name: token.name || '',
    expiresAt: toDatePickerValue(token.expiresAt)
  }
  formError.value = ''
  scopeError.value = ''
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

  const scopes = scopesForPreset(form.value.preset, form.value.customScopes)
  if (!editingToken.value && scopes.length === 0) {
    scopeError.value = t('profile.access.apiTokens.validation.scopeRequired')
    return
  }
  scopeError.value = ''

  saving.value = true
  try {
    const payload = {
      name,
      expiresAt: toInstantOrNull(form.value.expiresAt)
    }
    // Scopes are fixed at creation; the edit request only changes name and expiry
    if (!editingToken.value) {
      payload.scopes = scopes
    }

    if (editingToken.value) {
      await authStore.saveApiToken(editingToken.value.id, payload)
      toast.add({ severity: 'success', summary: t('profile.access.apiTokens.toasts.saved.title'), detail: t('profile.access.apiTokens.toasts.saved.detail'), life: 2500 })
    } else {
      const response = await authStore.saveApiToken(null, payload)
      createdToken.value = response?.token || ''
      createdPreset.value = form.value.preset
      createdTokenId.value = response?.apiToken?.id || null
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

// Copy the token first: it is shown only once and Home Assistant needs it
const openHomeAssistantSetup = () => {
  const apiTokenId = createdTokenId.value
  closeCreatedTokenDialog()
  router.push({ path: '/app/location-sources', query: { add: 'home-assistant', apiTokenId } })
}

const closeCreatedTokenDialog = () => {
  createdTokenDialogVisible.value = false
  createdToken.value = ''
  createdPreset.value = null
  createdTokenId.value = null
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

const scopeKey = (scope) => (scope === 'gps:ingest' ? 'gpsIngest' : scope)

const formatScopeList = (scopes) => (scopes || [])
  .map(scope => (scope === FULL_ACCESS_SCOPE
    ? t('profile.access.apiTokens.access.presets.full.title')
    : t(`profile.access.apiTokens.access.scopes.${scopeKey(scope)}.title`)))
  .join(', ')

const formatAccess = (scopes) => {
  const preset = presetForScopes(scopes)
  return preset === 'custom'
    ? t('profile.access.apiTokens.access.presets.custom.title')
    : t(`profile.access.apiTokens.access.presets.${preset}.title`)
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
  font-family: var(--gp-font-mono);
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

.field-label {
  font-weight: 600;
}

.access-options,
.custom-scopes {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.custom-scopes {
  padding-left: 1.75rem;
}

.access-option {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  cursor: pointer;
}

.access-option > span {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.access-title {
  font-weight: 500;
}

.error-message {
  color: var(--gp-danger);
  font-size: 0.85rem;
}

.home-assistant-next {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.75rem;
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
