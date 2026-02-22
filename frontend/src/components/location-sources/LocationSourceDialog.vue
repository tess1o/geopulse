<template>
  <Dialog
    v-model:visible="visible"
    :header="isEditMode ? 'Edit Location Source' : 'Add Location Source'"
    @hide="handleDialogHide"
    modal
    class="source-dialog"
  >
    <div class="dialog-content">
      <Stepper v-if="!isEditMode" v-model:value="addWizardStep" class="add-source-stepper">
        <StepList>
          <Step :value="1">Choose Source</Step>
          <Step :value="2">Configure</Step>
        </StepList>
      </Stepper>

      <div v-if="!isEditMode && addWizardStep === 1" class="source-type-selection">
        <label class="form-label">Choose Source Type</label>
        <small class="text-muted">Pick the source first. Configuration fields appear in the next step.</small>

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
              <div class="type-description type-description-compact">{{ type.description }}</div>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="isEditMode || addWizardStep === 2" class="source-summary">
        <div class="source-summary-header">
          <label class="form-label">{{ isEditMode ? 'Source' : 'Selected Source' }}</label>
          <Button
            v-if="!isEditMode"
            label="Change Source"
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

        <small v-if="isEditMode" class="text-muted">Source type cannot be changed while editing. Create a new source to switch type.</small>
      </div>

      <template v-if="isEditMode || addWizardStep === 2">
        <div v-if="formData.type === 'OWNTRACKS' || formData.type === 'GPSLOGGER'" class="form-section">
          <div v-if="formData.type === 'OWNTRACKS'" class="form-field">
            <label for="connectionType" class="form-label">Connection Type</label>
            <div class="connection-type-selection">
              <div
                :class="['connection-type-option', { active: formData.connectionType === 'HTTP' }]"
                @click="formData.connectionType = 'HTTP'"
              >
                <i class="pi pi-globe"></i>
                <div>
                  <div class="connection-type-name">HTTP</div>
                  <div class="connection-type-description">Standard HTTP endpoint</div>
                </div>
              </div>
              <div
                :class="['connection-type-option', { active: formData.connectionType === 'MQTT' }]"
                @click="formData.connectionType = 'MQTT'"
              >
                <i class="pi pi-send"></i>
                <div>
                  <div class="connection-type-name">MQTT</div>
                  <div class="connection-type-description">MQTT broker connection</div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="formData.type === 'GPSLOGGER'" class="step-value">
            GPSLogger uses HTTP only and sends an OwnTracks-compatible payload.
          </div>

          <div class="form-field">
            <label for="location-source-username" class="form-label">Username</label>
            <InputText
              id="location-source-username"
              v-model="formData.username"
              placeholder="Enter username"
              :invalid="!!formErrors.username"
            />
            <small v-if="formErrors.username" class="error-message">{{ formErrors.username }}</small>
          </div>

          <div class="form-field">
            <label for="location-source-password" class="form-label">Password</label>
            <Password
              id="location-source-password"
              v-model="formData.password"
              :placeholder="isEditMode ? 'Enter new password (leave empty to keep current)' : 'Enter password'"
              :feedback="false"
              toggleMask
              :invalid="!!formErrors.password"
            />
            <small v-if="formErrors.password" class="error-message">{{ formErrors.password }}</small>
          </div>
        </div>

        <div v-else-if="formData.type === 'OVERLAND'" class="form-section">
          <div class="form-field">
            <label for="location-source-token-overland" class="form-label">Access Token</label>
            <InputText
              id="location-source-token-overland"
              v-model="formData.token"
              placeholder="Enter access token"
              :invalid="!!formErrors.token"
            />
            <small v-if="formErrors.token" class="error-message">{{ formErrors.token }}</small>
          </div>
        </div>

        <div v-else-if="formData.type === 'DAWARICH'" class="form-section">
          <div class="form-field">
            <label for="location-source-token-dawarich" class="form-label">API Key</label>
            <InputText
              id="location-source-token-dawarich"
              v-model="formData.token"
              placeholder="Enter API key"
              :invalid="!!formErrors.token"
            />
            <small v-if="formErrors.token" class="error-message">{{ formErrors.token }}</small>
          </div>
        </div>

        <div v-else-if="formData.type === 'HOME_ASSISTANT'" class="form-section">
          <div class="form-field">
            <label for="location-source-token-ha" class="form-label">Token</label>
            <InputText
              id="location-source-token-ha"
              v-model="formData.token"
              placeholder="Enter token"
              :invalid="!!formErrors.token"
            />
            <small v-if="formErrors.token" class="error-message">{{ formErrors.token }}</small>
          </div>
        </div>

        <GpsFilteringSettings v-model:settings="formData" />
      </template>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <Button label="Cancel" outlined @click="close" />

        <Button
          v-if="!isEditMode && addWizardStep === 1"
          label="Continue"
          icon="pi pi-arrow-right"
          iconPos="right"
          @click="goToAddConfigStep"
        />

        <Button
          v-if="!isEditMode && addWizardStep === 2"
          label="Back"
          severity="secondary"
          outlined
          @click="goToAddSourceStep"
        />

        <Button
          v-if="isEditMode || addWizardStep === 2"
          :label="isEditMode ? 'Save Changes' : 'Add Source'"
          :loading="saving"
          @click="submit"
        />
      </div>
    </template>
  </Dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import GpsFilteringSettings from '@/components/GpsFilteringSettings.vue'
import {
  LOCATION_SOURCE_OPTIONS,
  getLocationSourceDisplayName,
  getLocationSourceIcon,
  getLocationSourceIdentifier
} from '@/components/location-sources/locationSourceMeta'

const props = defineProps({
  saving: {
    type: Boolean,
    default: false
  },
  defaultFilteringValues: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['submit'])

const visible = ref(false)
const isEditMode = ref(false)
const editingSource = ref(null)
const addWizardStep = ref(1)

const formData = ref({
  type: 'OWNTRACKS',
  username: '',
  password: '',
  token: '',
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
    type: 'OWNTRACKS',
    username: '',
    password: '',
    token: '',
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

const openEdit = (source) => {
  isEditMode.value = true
  editingSource.value = source
  addWizardStep.value = 2
  formErrors.value = {}
  formData.value = {
    type: source.type,
    username: source.username || '',
    password: '',
    token: source.token || '',
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
}

watch(() => formData.value.username, () => clearErrorIfFieldNowValid('username'))
watch(() => formData.value.password, () => clearErrorIfFieldNowValid('password'))
watch(() => formData.value.token, () => clearErrorIfFieldNowValid('token'))

watch(() => formData.value.type, () => {
  // Switching source type changes required fields; clear stale errors.
  formErrors.value = {}
})

const validateForm = () => {
  formErrors.value = {}

  if (formData.value.type === 'OWNTRACKS' || formData.value.type === 'GPSLOGGER') {
    if (isBlank(formData.value.username)) {
      formErrors.value.username = 'Username is required'
    }
    if (!isEditMode.value && isBlank(formData.value.password)) {
      formErrors.value.password = 'Password is required'
    }
  } else if (formData.value.type === 'OVERLAND') {
    if (isBlank(formData.value.token)) {
      formErrors.value.token = 'Access token is required'
    }
  } else if (formData.value.type === 'DAWARICH') {
    if (isBlank(formData.value.token)) {
      formErrors.value.token = 'API key is required'
    }
  } else if (formData.value.type === 'HOME_ASSISTANT') {
    if (isBlank(formData.value.token)) {
      formErrors.value.token = 'Token is required'
    }
  }

  return Object.keys(formErrors.value).length === 0
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
  openEdit,
  close
})
</script>

<style scoped>
.source-dialog {
  min-width: 500px;
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
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
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
  font-family: var(--font-mono, monospace);
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
  border: 2px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--gp-surface-white);
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
  background: var(--gp-surface-light);
}

.source-type-option.active {
  border-color: var(--gp-primary);
  background: var(--gp-surface-white);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

.source-type-option.active:hover {
  background: var(--gp-surface-white);
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

.form-label {
  font-weight: 600;
  color: var(--gp-text-primary);
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
  border: 2px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--gp-surface-white);
  flex: 1;
}

.connection-type-option:hover {
  border-color: var(--gp-border-medium);
  background: var(--gp-surface-light);
}

.connection-type-option.active {
  border-color: var(--gp-primary);
  background: var(--gp-surface-white);
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

.error-message {
  color: var(--gp-danger);
  font-size: 0.85rem;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
}

.p-dark .source-summary-card {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .source-type-option {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .source-type-option:hover {
  background: var(--gp-surface-light);
  border-color: var(--gp-border-medium);
}

.p-dark .source-type-option.active {
  background: var(--gp-surface-dark);
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.p-dark .type-name,
.p-dark .form-label,
.p-dark .source-summary-name,
.p-dark .connection-type-name {
  color: var(--gp-text-primary);
}

.p-dark .type-description,
.p-dark .text-muted,
.p-dark .source-summary-meta,
.p-dark .step-value,
.p-dark .connection-type-description {
  color: var(--gp-text-secondary);
}

.p-dark .connection-type-option {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .connection-type-option:hover {
  background: var(--gp-surface-light);
  border-color: var(--gp-border-medium);
}

.p-dark .connection-type-option.active {
  background: var(--gp-surface-dark);
  border-color: var(--gp-primary);
}

.p-dark .dialog-footer {
  border-top-color: var(--gp-border-dark);
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
