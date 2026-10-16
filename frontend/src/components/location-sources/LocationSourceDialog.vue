<template>
  <Dialog
    v-model:visible="visible"
    :header="isEditMode ? t('locationSources.dialog.editHeader') : t('locationSources.dialog.addHeader')"
    @hide="handleDialogHide"
    modal
    class="source-dialog"
  >
    <div class="dialog-content">
      <Stepper v-if="!isEditMode" v-model:value="addWizardStep" class="add-source-stepper">
        <StepList>
          <Step :value="1">{{ t('locationSources.dialog.stepChooseSource') }}</Step>
          <Step :value="2">{{ t('locationSources.dialog.stepConfigure') }}</Step>
        </StepList>
      </Stepper>

      <div v-if="!isEditMode && addWizardStep === 1" class="source-type-selection">
        <label class="form-label">{{ t('locationSources.dialog.chooseSourceType') }}</label>
        <small class="text-muted">{{ t('locationSources.dialog.chooseSourceHint') }}</small>

        <div class="source-types source-type-grid">
          <div
            v-for="type in sourceTypes"
            :key="type.value"
            :class="['source-type-option', 'wizard', { active: formData.type === type.value }]"
            @click="selectSourceType(type.value)"
          >
            <i :class="type.icon"></i>
            <div>
              <div class="type-name">{{ type.label }}</div>
              <div class="type-description type-description-compact">{{ t(type.descriptionKey) }}</div>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="isEditMode || addWizardStep === 2" class="source-summary">
        <div class="source-summary-header">
          <label class="form-label">{{ isEditMode ? t('locationSources.dialog.source') : t('locationSources.dialog.selectedSource') }}</label>
          <Button
            v-if="!isEditMode"
            :label="t('locationSources.dialog.changeSource')"
            icon="pi pi-arrow-left"
            text
            size="small"
            @click="goToAddSourceStep"
          />
        </div>

        <div class="source-summary-card">
          <div class="source-summary-main">
            <i :class="getSourceIcon(formData.type)" class="source-summary-icon"></i>
            <div>
              <div class="source-summary-name">{{ getSourceDisplayName(formData.type) }}</div>
              <div class="source-summary-meta">{{ getSourceIdentifier(summarySource) }}</div>
            </div>
          </div>
          <div class="source-summary-badges">
            <Badge :value="getSourceDisplayName(formData.type)" severity="contrast" />
            <Badge
              v-if="formData.type === 'OWNTRACKS'"
              :value="formData.connectionType || 'HTTP'"
              :severity="(formData.connectionType || 'HTTP') === 'MQTT' ? 'info' : 'warn'"
            />
          </div>
        </div>

        <small v-if="isEditMode" class="text-muted">{{ t('locationSources.dialog.sourceTypeCannotChange') }}</small>
      </div>

      <template v-if="isEditMode || addWizardStep === 2">
        <div v-if="formData.type === 'OWNTRACKS' || formData.type === 'GPSLOGGER' || formData.type === 'COLOTA'" class="form-section">
          <div v-if="formData.type === 'OWNTRACKS'" class="form-field">
            <label for="connectionType" class="form-label">{{ t('locationSources.dialog.connectionType') }}</label>
            <div class="connection-type-selection">
              <div
                :class="['connection-type-option', { active: formData.connectionType === 'HTTP' }]"
                @click="formData.connectionType = 'HTTP'"
              >
                <i class="pi pi-globe"></i>
                <div>
                  <div class="connection-type-name">{{ t('locationSources.dialog.connectionTypeHttpName') }}</div>
                  <div class="connection-type-description">{{ t('locationSources.dialog.connectionTypeHttpDescription') }}</div>
                </div>
              </div>
              <div
                :class="['connection-type-option', { active: formData.connectionType === 'MQTT' }]"
                @click="formData.connectionType = 'MQTT'"
              >
                <i class="pi pi-send"></i>
                <div>
                  <div class="connection-type-name">{{ t('locationSources.dialog.connectionTypeMqttName') }}</div>
                  <div class="connection-type-description">{{ t('locationSources.dialog.connectionTypeMqttDescription') }}</div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="formData.type === 'GPSLOGGER'" class="step-value">
            {{ t('locationSources.dialog.gpsLoggerHttpOnlyNote') }}
          </div>

          <div class="form-field">
            <label for="location-source-username" class="form-label">{{ t('locationSources.dialog.username') }}</label>
            <InputText
              id="location-source-username"
              v-model="formData.username"
              :placeholder="t('locationSources.dialog.usernamePlaceholder')"
              :invalid="!!formErrors.username"
            />
            <small v-if="formErrors.username" class="error-message">{{ formErrors.username }}</small>
          </div>

          <div class="form-field">
            <label for="location-source-password" class="form-label">{{ t('locationSources.dialog.password') }}</label>
            <Password
              id="location-source-password"
              v-model="formData.password"
              :placeholder="isEditMode ? t('locationSources.dialog.passwordPlaceholderEdit') : t('locationSources.dialog.passwordPlaceholderAdd')"
              :feedback="false"
              toggleMask
              :invalid="!!formErrors.password"
            />
            <small v-if="formErrors.password" class="error-message">{{ formErrors.password }}</small>
          </div>

          <div v-if="formData.type === 'OWNTRACKS'" class="form-field">
            <label for="location-source-payload-encryption-secret" class="form-label">{{ t('locationSources.dialog.payloadEncryptionSecret') }}</label>
            <Password
              id="location-source-payload-encryption-secret"
              v-model="formData.payloadEncryptionSecret"
              :placeholder="isEditMode ? t('locationSources.dialog.payloadEncryptionSecretPlaceholderEdit') : t('locationSources.dialog.payloadEncryptionSecretPlaceholderAdd')"
              :feedback="false"
              toggleMask
              :invalid="!!formErrors.payloadEncryptionSecret"
            />
            <small v-if="formErrors.payloadEncryptionSecret" class="error-message">{{ formErrors.payloadEncryptionSecret }}</small>
            <small v-else class="text-muted">{{ t('locationSources.dialog.payloadEncryptionSecretHint') }}</small>
          </div>

          <div
            v-if="isEditMode && formData.type === 'OWNTRACKS' && editingSource?.hasPayloadEncryptionSecret"
            class="form-field clear-secret-field"
          >
            <Checkbox
              v-model="formData.clearPayloadEncryptionSecret"
              inputId="location-source-clear-payload-secret"
              binary
            />
            <label for="location-source-clear-payload-secret" class="form-label clear-secret-label">
              {{ t('locationSources.dialog.clearPayloadEncryptionSecret') }}
            </label>
          </div>
        </div>

        <div v-else-if="formData.type === 'OVERLAND'" class="form-section">
          <div class="form-field">
            <label for="location-source-token-overland" class="form-label">{{ t('locationSources.dialog.accessToken') }}</label>
            <InputText
              id="location-source-token-overland"
              v-model="formData.token"
              :placeholder="t('locationSources.dialog.accessTokenPlaceholder')"
              :invalid="!!formErrors.token"
            />
            <small v-if="formErrors.token" class="error-message">{{ formErrors.token }}</small>
          </div>
        </div>

        <div v-else-if="formData.type === 'TRACCAR'" class="form-section">
          <div class="form-field">
            <label for="location-source-token-traccar" class="form-label">{{ t('locationSources.dialog.forwardingToken') }}</label>
            <InputText
              id="location-source-token-traccar"
              v-model="formData.token"
              :placeholder="t('locationSources.dialog.forwardingTokenPlaceholder')"
              :invalid="!!formErrors.token"
            />
            <small v-if="formErrors.token" class="error-message">{{ formErrors.token }}</small>
          </div>

          <div class="form-field">
            <label for="location-source-device-id-traccar" class="form-label">{{ t('locationSources.dialog.deviceUniqueId') }}</label>
            <InputText
              id="location-source-device-id-traccar"
              v-model="formData.deviceId"
              :placeholder="t('locationSources.dialog.deviceUniqueIdPlaceholder')"
            />
            <small class="text-muted">
              <i18n-t keypath="locationSources.dialog.deviceUniqueIdHint" tag="span">
                <template #code><code>device.uniqueId</code></template>
              </i18n-t>
            </small>
          </div>
        </div>

        <div v-else-if="formData.type === 'DAWARICH'" class="form-section">
          <div class="form-field">
            <label for="location-source-token-dawarich" class="form-label">{{ t('locationSources.dialog.apiKeyLabel') }}</label>
            <InputText
              id="location-source-token-dawarich"
              v-model="formData.token"
              :placeholder="t('locationSources.dialog.apiKeyPlaceholder')"
              :invalid="!!formErrors.token"
            />
            <small v-if="formErrors.token" class="error-message">{{ formErrors.token }}</small>
          </div>
        </div>

        <div v-else-if="formData.type === 'HOME_ASSISTANT'" class="form-section">
          <small v-if="editingSource?.apiTokenId" class="text-muted">
            {{ t('locationSources.dialog.apiTokenLinkedNote', { name: editingSource.apiTokenName || '' }) }}
          </small>

          <template v-else>
            <div v-if="!isEditMode" class="form-field">
              <span class="form-label">{{ t('locationSources.dialog.homeAssistant.modeLabel') }}</span>
              <div class="connection-type-selection">
                <div
                  :class="['connection-type-option', { active: formData.haMode === 'sourceToken' }]"
                  data-testid="ha-mode-source-token"
                  @click="formData.haMode = 'sourceToken'"
                >
                  <i class="pi pi-key"></i>
                  <div>
                    <div class="connection-type-name">{{ t('locationSources.dialog.homeAssistant.sourceTokenTitle') }}</div>
                    <div class="connection-type-description">{{ t('locationSources.dialog.homeAssistant.sourceTokenDescription') }}</div>
                  </div>
                </div>
                <div
                  :class="['connection-type-option', { active: formData.haMode === 'apiToken' }]"
                  data-testid="ha-mode-api-token"
                  @click="formData.haMode = 'apiToken'"
                >
                  <i class="pi pi-id-card"></i>
                  <div>
                    <div class="connection-type-name">
                      {{ t('locationSources.dialog.homeAssistant.apiTokenTitle') }}
                      <Tag :value="t('locationSources.dialog.homeAssistant.recommended')" severity="success" class="recommended-tag" />
                    </div>
                    <div class="connection-type-description">{{ t('locationSources.dialog.homeAssistant.apiTokenDescription') }}</div>
                  </div>
                </div>
              </div>
              <i18n-t
                keypath="locationSources.dialog.homeAssistant.readOnlyHint"
                tag="small"
                class="text-muted read-only-hint"
                data-testid="ha-read-only-hint"
              >
                <template #link>
                  <router-link :to="readOnlyTokenRoute" @click="visible = false">{{ t('locationSources.dialog.homeAssistant.readOnlyHintLink') }}</router-link>
                </template>
              </i18n-t>
            </div>

            <div v-if="!isEditMode && formData.haMode === 'apiToken'" class="form-field">
              <span class="form-label">{{ t('locationSources.dialog.homeAssistant.tokenLabel') }}</span>
              <div class="token-choice">
                <label class="token-choice-option">
                  <RadioButton v-model="formData.haTokenChoice" value="new" inputId="ha-token-new" />
                  <span>{{ t('locationSources.dialog.homeAssistant.createToken') }}</span>
                </label>
                <InputText
                  v-if="formData.haTokenChoice === 'new'"
                  id="ha-token-name"
                  v-model="formData.haNewTokenName"
                  :placeholder="t('locationSources.dialog.homeAssistant.tokenNamePlaceholder')"
                  :aria-label="t('locationSources.dialog.homeAssistant.tokenNameLabel')"
                  :invalid="!!formErrors.haNewTokenName"
                  class="token-choice-input"
                />
                <small v-if="formErrors.haNewTokenName" class="error-message">{{ formErrors.haNewTokenName }}</small>

                <label class="token-choice-option">
                  <RadioButton
                    v-model="formData.haTokenChoice"
                    value="existing"
                    inputId="ha-token-existing"
                    :disabled="availableIngestTokens.length === 0"
                  />
                  <span>{{ t('locationSources.dialog.homeAssistant.useExistingToken') }}</span>
                </label>
                <template v-if="formData.haTokenChoice === 'existing'">
                  <Select
                    v-model="formData.haApiTokenId"
                    :options="availableIngestTokens"
                    optionLabel="label"
                    optionValue="id"
                    :placeholder="t('locationSources.dialog.homeAssistant.selectToken')"
                    :invalid="!!formErrors.haApiTokenId"
                    class="token-choice-input"
                  />
                  <small v-if="formErrors.haApiTokenId" class="error-message">{{ formErrors.haApiTokenId }}</small>
                  <small class="text-muted token-choice-input">{{ t('locationSources.dialog.homeAssistant.existingTokenHint') }}</small>
                </template>
                <small v-else-if="availableIngestTokens.length === 0" class="text-muted token-choice-input">
                  {{ t('locationSources.dialog.homeAssistant.noExistingTokens') }}
                </small>
              </div>
            </div>

            <div v-if="isEditMode || formData.haMode === 'sourceToken'" class="form-field">
              <label for="location-source-token-ha" class="form-label">{{ t('locationSources.dialog.homeAssistant.sourceTokenLabel') }}</label>
              <InputText
                id="location-source-token-ha"
                v-model="formData.token"
                :placeholder="t('locationSources.dialog.tokenPlaceholder')"
                :invalid="!!formErrors.token"
              />
              <small v-if="formErrors.token" class="error-message">{{ formErrors.token }}</small>
              <small class="text-muted">{{ t('locationSources.dialog.homeAssistant.sourceTokenHint') }}</small>
            </div>
          </template>
        </div>

        <GpsFilteringSettings v-model:settings="formData" />
      </template>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <Button :label="t('locationSources.dialog.cancel')" outlined @click="close" />

        <Button
          v-if="!isEditMode && addWizardStep === 1"
          :label="t('locationSources.dialog.continue')"
          icon="pi pi-arrow-right"
          iconPos="right"
          @click="goToAddConfigStep"
        />

        <Button
          v-if="!isEditMode && addWizardStep === 2"
          :label="t('locationSources.dialog.back')"
          severity="secondary"
          outlined
          @click="goToAddSourceStep"
        />

        <Button
          v-if="isEditMode || addWizardStep === 2"
          :label="isEditMode ? t('locationSources.dialog.saveChanges') : t('locationSources.dialog.addSource')"
          :loading="saving"
          @click="submit"
        />
      </div>
    </template>
  </Dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import GpsFilteringSettings from '@/components/GpsFilteringSettings.vue'
import { useAuthStore } from '@/stores/auth'
import {
  LOCATION_SOURCE_OPTIONS,
  getLocationSourceDisplayName,
  getLocationSourceIcon,
  getLocationSourceIdentifier
} from '@/components/location-sources/locationSourceMeta'

const { t } = useI18n()

// Showing GeoPulse data in Home Assistant without sending locations needs only a read-only API token, not a source
const readOnlyTokenRoute = { path: '/app/profile', query: { tab: 'security', setting: 'api-tokens-heading' } }

const props = defineProps({
  saving: {
    type: Boolean,
    default: false
  },
  defaultFilteringValues: {
    type: Object,
    default: null
  },
  // API tokens already connected to a Home Assistant source; they can't be connected twice
  linkedApiTokenIds: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['submit'])

const authStore = useAuthStore()

const visible = ref(false)
const isEditMode = ref(false)
const editingSource = ref(null)
const addWizardStep = ref(1)

const formData = ref({
  haMode: 'apiToken',
  haTokenChoice: 'new',
  haNewTokenName: 'Home Assistant',
  haApiTokenId: null,
  type: 'OWNTRACKS',
  username: '',
  password: '',
  token: '',
  deviceId: '',
  payloadEncryptionSecret: '',
  clearPayloadEncryptionSecret: false,
  connectionType: 'HTTP',
  filterInaccurateData: null,
  maxAllowedAccuracy: null,
  maxAllowedSpeed: null,
  enableDuplicateDetection: null,
  duplicateDetectionThresholdMinutes: null
})

const formErrors = ref({})

const sourceTypes = LOCATION_SOURCE_OPTIONS

const summarySource = computed(() => editingSource.value || formData.value)

const ingestTokens = ref([])
const availableIngestTokens = computed(() => ingestTokens.value
  .filter(token => !props.linkedApiTokenIds.includes(token.id))
  .map(token => ({ id: token.id, label: `${token.name} (${token.preview})` })))

const loadIngestTokens = async () => {
  try {
    ingestTokens.value = (await authStore.listApiTokens('gps:ingest')) || []
  } catch {
    ingestTokens.value = []
  }
}

watch(
  () => visible.value && !isEditMode.value && formData.value.type === 'HOME_ASSISTANT',
  (needsTokens) => {
    if (needsTokens) loadIngestTokens()
  }
)

const getDefaultFiltering = () => {
  if (props.defaultFilteringValues) {
    return {
      filterInaccurateData: props.defaultFilteringValues.filterInaccurateData,
      maxAllowedAccuracy: props.defaultFilteringValues.maxAllowedAccuracy,
      maxAllowedSpeed: props.defaultFilteringValues.maxAllowedSpeed,
      enableDuplicateDetection: props.defaultFilteringValues.enableDuplicateDetection,
      duplicateDetectionThresholdMinutes: props.defaultFilteringValues.duplicateDetectionThresholdMinutes
    }
  }
  return {
    filterInaccurateData: null,
    maxAllowedAccuracy: null,
    maxAllowedSpeed: null,
    enableDuplicateDetection: null,
    duplicateDetectionThresholdMinutes: null
  }
}

const resetDialogForm = () => {
  const defaults = getDefaultFiltering()
  formData.value = {
    haMode: 'apiToken',
    haTokenChoice: 'new',
    haNewTokenName: 'Home Assistant',
    haApiTokenId: null,
    type: 'OWNTRACKS',
    username: '',
    password: '',
    token: '',
    deviceId: '',
    payloadEncryptionSecret: '',
    clearPayloadEncryptionSecret: false,
    connectionType: 'HTTP',
    filterInaccurateData: defaults.filterInaccurateData,
    maxAllowedAccuracy: defaults.maxAllowedAccuracy,
    maxAllowedSpeed: defaults.maxAllowedSpeed,
    enableDuplicateDetection: defaults.enableDuplicateDetection,
    duplicateDetectionThresholdMinutes: defaults.duplicateDetectionThresholdMinutes
  }
  formErrors.value = {}
}

const openAdd = () => {
  isEditMode.value = false
  editingSource.value = null
  addWizardStep.value = 1
  resetDialogForm()
  visible.value = true
}

const openQuickSetup = (type) => {
  isEditMode.value = false
  editingSource.value = null
  addWizardStep.value = 2
  resetDialogForm()
  formData.value.type = type
  visible.value = true
}

/** Add dialog for a Home Assistant source using this existing API token. */
const openHomeAssistantApiToken = (apiTokenId) => {
  openQuickSetup('HOME_ASSISTANT')
  formData.value.haMode = 'apiToken'
  formData.value.haTokenChoice = 'existing'
  formData.value.haApiTokenId = apiTokenId
}

const openEdit = (source) => {
  isEditMode.value = true
  editingSource.value = source
  addWizardStep.value = 2
  formErrors.value = {}
  formData.value = {
    haMode: 'sourceToken',
    haTokenChoice: 'new',
    haNewTokenName: 'Home Assistant',
    haApiTokenId: null,
    type: source.type,
    username: source.username || '',
    password: '',
    token: source.token || '',
    deviceId: source.deviceId || '',
    payloadEncryptionSecret: '',
    clearPayloadEncryptionSecret: false,
    connectionType: source.connectionType || 'HTTP',
    filterInaccurateData: source.filterInaccurateData ?? false,
    maxAllowedAccuracy: source.maxAllowedAccuracy ?? null,
    maxAllowedSpeed: source.maxAllowedSpeed ?? null,
    enableDuplicateDetection: source.enableDuplicateDetection ?? false,
    duplicateDetectionThresholdMinutes: source.duplicateDetectionThresholdMinutes ?? null
  }
  visible.value = true
}

const close = () => {
  visible.value = false
}

const handleDialogHide = () => {
  isEditMode.value = false
  editingSource.value = null
  addWizardStep.value = 1
  resetDialogForm()
}

const selectSourceType = (type) => {
  formData.value.type = type
}

const goToAddConfigStep = () => {
  addWizardStep.value = 2
}

const goToAddSourceStep = () => {
  addWizardStep.value = 1
}

const isBlank = (value) => !String(value ?? '').trim()

const clearErrorIfFieldNowValid = (field) => {
  if (!formErrors.value[field]) return

  if (field === 'username' && !isBlank(formData.value.username)) {
    delete formErrors.value.username
  }

  if (field === 'password') {
    const passwordProvided = String(formData.value.password ?? '').length > 0
    if (isEditMode.value || passwordProvided) {
      delete formErrors.value.password
    }
  }

  if (field === 'token' && !isBlank(formData.value.token)) {
    delete formErrors.value.token
  }

  if (field === 'payloadEncryptionSecret' && !isPayloadEncryptionSecretTooLong()) {
    delete formErrors.value.payloadEncryptionSecret
  }
}

watch(() => formData.value.username, () => clearErrorIfFieldNowValid('username'))
watch(() => formData.value.password, () => clearErrorIfFieldNowValid('password'))
watch(() => formData.value.token, () => clearErrorIfFieldNowValid('token'))
watch(
  () => [formData.value.haMode, formData.value.haTokenChoice, formData.value.haApiTokenId, formData.value.haNewTokenName],
  () => {
    delete formErrors.value.haNewTokenName
    delete formErrors.value.haApiTokenId
  }
)
watch(() => formData.value.payloadEncryptionSecret, () => clearErrorIfFieldNowValid('payloadEncryptionSecret'))

watch(() => formData.value.type, () => {
  // Switching source type changes required fields; clear stale errors.
  formErrors.value = {}
})

const validateForm = () => {
  formErrors.value = {}

  if (formData.value.type === 'OWNTRACKS' || formData.value.type === 'GPSLOGGER' || formData.value.type === 'COLOTA') {
    if (isBlank(formData.value.username)) {
      formErrors.value.username = t('locationSources.dialog.validation.usernameRequired')
    }
    if (!isEditMode.value && isBlank(formData.value.password)) {
      formErrors.value.password = t('locationSources.dialog.validation.passwordRequired')
    }
    if (formData.value.type === 'OWNTRACKS' && isPayloadEncryptionSecretTooLong()) {
      formErrors.value.payloadEncryptionSecret = t('locationSources.dialog.validation.secretTooLong')
    }
  } else if (formData.value.type === 'OVERLAND') {
    if (isBlank(formData.value.token)) {
      formErrors.value.token = t('locationSources.dialog.validation.accessTokenRequired')
    }
  } else if (formData.value.type === 'TRACCAR') {
    if (isBlank(formData.value.token)) {
      formErrors.value.token = t('locationSources.dialog.validation.forwardingTokenRequired')
    }
  } else if (formData.value.type === 'DAWARICH') {
    if (isBlank(formData.value.token)) {
      formErrors.value.token = t('locationSources.dialog.validation.apiKeyRequired')
    }
  } else if (formData.value.type === 'HOME_ASSISTANT' && !isEditMode.value && formData.value.haMode === 'apiToken') {
    if (formData.value.haTokenChoice === 'new' && isBlank(formData.value.haNewTokenName)) {
      formErrors.value.haNewTokenName = t('locationSources.dialog.validation.tokenNameRequired')
    }
    if (formData.value.haTokenChoice === 'existing' && !formData.value.haApiTokenId) {
      formErrors.value.haApiTokenId = t('locationSources.dialog.validation.apiTokenRequired')
    }
  } else if (formData.value.type === 'HOME_ASSISTANT') {
    // Sources connected to a personal API token have no token of their own
    if (!editingSource.value?.apiTokenId && isBlank(formData.value.token)) {
      formErrors.value.token = t('locationSources.dialog.validation.tokenRequired')
    }
  }

  return Object.keys(formErrors.value).length === 0
}

const isPayloadEncryptionSecretTooLong = () => {
  if (isBlank(formData.value.payloadEncryptionSecret)) return false
  return new TextEncoder().encode(formData.value.payloadEncryptionSecret).length > 32
}

const submit = () => {
  if (!validateForm()) return

  emit('submit', {
    isEditMode: isEditMode.value,
    editingSource: editingSource.value,
    formData: { ...formData.value }
  })
}

const getSourceIcon = getLocationSourceIcon
const getSourceDisplayName = getLocationSourceDisplayName
const getSourceIdentifier = getLocationSourceIdentifier

defineExpose({
  openAdd,
  openQuickSetup,
  openHomeAssistantApiToken,
  openEdit,
  close
})
</script>

<style scoped>
.source-dialog {
  min-width: 500px;
}

.recommended-tag {
  margin-left: 0.4rem;
  font-size: 0.7rem;
  vertical-align: middle;
}

.token-choice {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.token-choice-option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
}

.token-choice-input {
  margin-left: 1.75rem;
}

.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.add-source-stepper {
  margin-bottom: 0.25rem;
}

.add-source-stepper :deep(.p-stepper),
.add-source-stepper :deep(.p-steplist) {
  background: transparent;
}

.source-type-selection {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.source-summary {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.source-summary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.source-summary-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  padding: 0.9rem 1rem;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-ground);
}

.source-summary-main {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  min-width: 0;
}

.source-summary-icon {
  font-size: 1.1rem;
  color: var(--gp-primary);
  margin-top: 0.125rem;
}

.source-summary-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  line-height: 1.2;
}

.source-summary-meta {
  margin-top: 0.2rem;
  font-size: 0.82rem;
  color: var(--gp-text-secondary);
  font-family: var(--gp-font-mono, monospace);
  word-break: break-all;
}

.source-summary-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  justify-content: flex-end;
  flex-shrink: 0;
}

.source-types {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.source-type-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem;
}

.source-type-option {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  border: 2px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--gp-surface-card);
}

.source-type-option.wizard {
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.8rem;
  min-height: 84px;
}

.source-type-option.wizard i {
  margin-top: 0.1rem;
}

.source-type-option:hover {
  border-color: var(--gp-border-medium);
  background: var(--gp-surface-muted);
}

.source-type-option.active {
  border-color: var(--gp-primary);
  background: var(--gp-surface-card);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

.source-type-option.active:hover {
  background: var(--gp-surface-card);
}

.type-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
}

.type-description {
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
  line-height: 1.3;
}

.type-description-compact {
  margin-bottom: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.clear-secret-field {
  flex-direction: row;
  align-items: center;
  gap: 0.5rem;
}

.form-label {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.clear-secret-label {
  margin: 0;
}

.connection-type-selection {
  display: flex;
  gap: 0.75rem;
}

.connection-type-option {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border: 2px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--gp-surface-card);
  flex: 1;
}

.connection-type-option:hover {
  border-color: var(--gp-border-medium);
  background: var(--gp-surface-muted);
}

.connection-type-option.active {
  border-color: var(--gp-primary);
  background: var(--gp-surface-card);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

.connection-type-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.125rem;
  font-size: 0.9rem;
}

.connection-type-description {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  line-height: 1.2;
}

.step-value {
  color: var(--gp-text-secondary);
  line-height: 1.4;
}

.text-muted {
  color: var(--gp-text-muted, #6b7280);
  font-size: 0.85rem;
  margin-top: 0.25rem;
}

.read-only-hint a {
  color: var(--gp-primary);
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: .18em;
}

.error-message {
  color: var(--gp-danger);
  font-size: 0.85rem;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border);
}

@media (max-width: 768px) {
  .source-dialog {
    min-width: 90vw;
  }

  .source-type-grid {
    grid-template-columns: 1fr;
  }

  .source-summary-card {
    flex-direction: column;
    align-items: stretch;
  }

  .source-summary-badges {
    justify-content: flex-start;
  }

  .connection-type-selection {
    flex-direction: column;
  }
}
</style>
