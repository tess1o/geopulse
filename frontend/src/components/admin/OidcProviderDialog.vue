<template>
  <Dialog
    :visible="visible"
    :header="isEdit ? 'Edit OIDC Provider' : 'Create OIDC Provider'"
    :modal="true"
    class="gp-dialog-md"
    @update:visible="$emit('update:visible', $event)"
  >
    <div class="provider-form">
      <div class="field">
        <label for="name">Provider Name *</label>
        <InputText
          id="name"
          v-model="formData.name"
          placeholder="e.g., google, keycloak, okta"
          :disabled="isEdit"
          class="w-full"
        />
        <small class="field-hint">Lowercase alphanumeric with hyphens only</small>
      </div>

      <div class="field">
        <label for="displayName">Display Name *</label>
        <InputText
          id="displayName"
          v-model="formData.displayName"
          placeholder="e.g., Google, Keycloak, Okta"
          class="w-full"
        />
        <small class="field-hint">Name shown to users on login page</small>
      </div>

      <div class="field">
        <label for="clientId">Client ID *</label>
        <InputText
          id="clientId"
          v-model="formData.clientId"
          placeholder="OAuth2 Client ID"
          class="w-full"
        />
      </div>

      <div class="field">
        <label for="clientSecret">Client Secret {{ isEdit ? '' : '*' }}</label>
        <Password
          id="clientSecret"
          v-model="formData.clientSecret"
          placeholder="OAuth2 Client Secret"
          :feedback="false"
          toggleMask
          class="w-full"
        />
        <small v-if="isEdit" class="field-hint">Leave empty to keep existing secret</small>
      </div>

      <div class="field">
        <label for="discoveryUrl">Discovery URL *</label>
        <InputText
          id="discoveryUrl"
          v-model="formData.discoveryUrl"
          placeholder="https://provider.com/.well-known/openid-configuration"
          class="w-full"
        />
        <small class="field-hint">OIDC discovery endpoint URL</small>
      </div>

      <div class="field">
        <label for="icon">Icon Class</label>
        <InputText
          id="icon"
          v-model="formData.icon"
          placeholder="pi pi-google"
          class="w-full"
        />
        <small class="field-hint">PrimeIcons class (e.g., pi pi-google, pi pi-shield)</small>
      </div>

      <div class="field">
        <label for="scopes">OAuth Scopes</label>
        <InputText
          id="scopes"
          v-model="formData.scopes"
          placeholder="openid profile email"
          class="w-full"
        />
      </div>

      <div class="field-checkbox">
        <Checkbox
          id="enabled"
          v-model="formData.enabled"
          :binary="true"
        />
        <label for="enabled" class="ml-2">Enable this provider</label>
      </div>
    </div>

    <template #footer>
      <Button
        label="Cancel"
        icon="pi pi-times"
        text
        @click="$emit('update:visible', false)"
      />
      <Button
        :label="isEdit ? 'Update' : 'Create'"
        icon="pi pi-check"
        @click="handleSave"
        :disabled="!isFormValid"
        :loading="saving"
      />
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'

const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  },
  provider: {
    type: Object,
    default: null
  },
  isEdit: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'save'])

// Form data
const formData = ref({
  name: '',
  displayName: '',
  enabled: true,
  clientId: '',
  clientSecret: '',
  discoveryUrl: '',
  icon: '',
  scopes: 'openid profile email'
})

const saving = ref(false)

// Computed
const isFormValid = computed(() => {
  if (props.isEdit) {
    // For edit, client secret is optional
    return formData.value.name &&
           formData.value.displayName &&
           formData.value.clientId &&
           formData.value.discoveryUrl
  } else {
    // For create, all required fields must be filled
    return formData.value.name &&
           formData.value.displayName &&
           formData.value.clientId &&
           formData.value.clientSecret &&
           formData.value.discoveryUrl
  }
})

// Methods
const handleSave = () => {
  if (!isFormValid.value) return

  saving.value = true

  // Create payload
  const payload = {
    name: formData.value.name.toLowerCase().trim(),
    displayName: formData.value.displayName.trim(),
    enabled: formData.value.enabled,
    clientId: formData.value.clientId.trim(),
    discoveryUrl: formData.value.discoveryUrl.trim(),
    icon: formData.value.icon.trim() || undefined,
    scopes: formData.value.scopes.trim() || 'openid profile email'
  }

  // Only include client secret if it's provided
  if (formData.value.clientSecret && formData.value.clientSecret.trim()) {
    payload.clientSecret = formData.value.clientSecret.trim()
  }

  emit('save', payload)
  saving.value = false
}

const resetForm = () => {
  formData.value = {
    name: '',
    displayName: '',
    enabled: true,
    clientId: '',
    clientSecret: '',
    discoveryUrl: '',
    icon: '',
    scopes: 'openid profile email'
  }
}

// Watchers
watch(() => props.visible, (newVal) => {
  if (newVal) {
    if (props.isEdit && props.provider) {
      // Populate form with provider data for editing
      formData.value = {
        name: props.provider.name || '',
        displayName: props.provider.displayName || '',
        enabled: props.provider.enabled !== undefined ? props.provider.enabled : true,
        clientId: props.provider.clientId || '',
        clientSecret: '', // Never pre-fill client secret
        discoveryUrl: props.provider.discoveryUrl || '',
        icon: props.provider.icon || '',
        scopes: props.provider.scopes || 'openid profile email'
      }
    } else {
      resetForm()
    }
  }
})
</script>

<style scoped>
.provider-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding: 1rem 0;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.field label {
  font-weight: 600;
  color: var(--text-color);
}

.field-hint {
  color: var(--text-color-secondary);
  font-size: 0.875rem;
  margin-top: -0.25rem;
}

.field-checkbox {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

:deep(.p-password) {
  width: 100%;
}

:deep(.p-password-input) {
  width: 100%;
}

/* GeoPulse Dialog Styling */
:deep(.p-dialog) {
  border-radius: var(--gp-radius-large, 12px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

:deep(.p-dialog-header) {
  background: var(--surface-card);
  border-bottom: 1px solid var(--surface-border);
  border-radius: 12px 12px 0 0;
  padding: 1.5rem;
}

:deep(.p-dialog-title) {
  font-weight: 600;
  color: var(--text-color);
  font-size: 1.25rem;
}

:deep(.p-dialog-content) {
  background: var(--surface-card);
  padding: 0 1.5rem;
  color: var(--text-color);
}

:deep(.p-dialog-footer) {
  background: var(--surface-card);
  border-top: 1px solid var(--surface-border);
  border-radius: 0 0 12px 12px;
  padding: 1rem 1.5rem;
}
</style>
