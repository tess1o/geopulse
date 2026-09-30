<template>
  <Dialog
    :visible="visible"
    :header="isEdit ? t('admin.oidcProviderDialog.editHeader') : t('admin.oidcProviderDialog.createHeader')"
    :modal="true"
    class="gp-dialog-md"
    @update:visible="$emit('update:visible', $event)"
  >
    <div class="provider-form">
      <div class="field">
        <label for="name">{{ t('admin.oidcProviderDialog.nameLabel') }}</label>
        <InputText
          id="name"
          v-model="formData.name"
          :placeholder="t('admin.oidcProviderDialog.namePlaceholder')"
          :disabled="isEdit"
          class="w-full"
        />
        <small class="field-hint">{{ t('admin.oidcProviderDialog.nameHint') }}</small>
      </div>

      <div class="field">
        <label for="displayName">{{ t('admin.oidcProviderDialog.displayNameLabel') }}</label>
        <InputText
          id="displayName"
          v-model="formData.displayName"
          :placeholder="t('admin.oidcProviderDialog.displayNamePlaceholder')"
          class="w-full"
        />
        <small class="field-hint">{{ t('admin.oidcProviderDialog.displayNameHint') }}</small>
      </div>

      <div class="field">
        <label for="clientId">{{ t('admin.oidcProviderDialog.clientIdLabel') }}</label>
        <InputText
          id="clientId"
          v-model="formData.clientId"
          :placeholder="t('admin.oidcProviderDialog.clientIdPlaceholder')"
          class="w-full"
        />
      </div>

      <div class="field">
        <label for="clientSecret">{{ t('admin.oidcProviderDialog.clientSecretLabel', { required: isEdit ? '' : '*' }) }}</label>
        <Password
          id="clientSecret"
          v-model="formData.clientSecret"
          :placeholder="t('admin.oidcProviderDialog.clientSecretPlaceholder')"
          :feedback="false"
          toggleMask
          class="w-full"
        />
        <small v-if="isEdit" class="field-hint">{{ t('admin.oidcProviderDialog.clientSecretHint') }}</small>
      </div>

      <div class="field">
        <label for="discoveryUrl">{{ t('admin.oidcProviderDialog.discoveryUrlLabel') }}</label>
        <InputText
          id="discoveryUrl"
          v-model="formData.discoveryUrl"
          :placeholder="t('admin.oidcProviderDialog.discoveryUrlPlaceholder')"
          class="w-full"
        />
        <small class="field-hint">{{ t('admin.oidcProviderDialog.discoveryUrlHint') }}</small>
      </div>

      <div class="field">
        <label for="icon">{{ t('admin.oidcProviderDialog.iconLabel') }}</label>
        <InputText
          id="icon"
          v-model="formData.icon"
          :placeholder="t('admin.oidcProviderDialog.iconPlaceholder')"
          class="w-full"
        />
        <small class="field-hint">
          {{ t('admin.oidcProviderDialog.iconHint') }}
        </small>
        <div v-if="formData.icon || formData.name" class="icon-preview">
          <span class="preview-label">{{ t('admin.oidcProviderDialog.previewLabel') }}</span>
          <ProviderIcon
            :provider="{ name: formData.name, icon: formData.icon }"
            size="large"
            :alt="formData.displayName || t('admin.oidcProviderDialog.providerIconAltFallback')"
          />
        </div>
      </div>

      <div class="field">
        <label for="scopes">{{ t('admin.oidcProviderDialog.scopesLabel') }}</label>
        <InputText
          id="scopes"
          v-model="formData.scopes"
          :placeholder="t('admin.oidcProviderDialog.scopesPlaceholder')"
          class="w-full"
        />
      </div>

      <div class="field-checkbox">
        <Checkbox
          id="enabled"
          v-model="formData.enabled"
          :binary="true"
        />
        <label for="enabled" class="ml-2">{{ t('admin.oidcProviderDialog.enableLabel') }}</label>
      </div>
    </div>

    <template #footer>
      <Button
        :label="t('admin.oidcProviderDialog.cancel')"
        icon="pi pi-times"
        text
        @click="$emit('update:visible', false)"
      />
      <Button
        :label="isEdit ? t('admin.oidcProviderDialog.update') : t('admin.oidcProviderDialog.create')"
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
import { useI18n } from 'vue-i18n'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'
import ProviderIcon from '@/components/common/ProviderIcon.vue'

const { t } = useI18n()

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

.icon-preview {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.5rem;
  padding: 0.75rem;
  background: var(--surface-100);
  border-radius: var(--border-radius);
}

.preview-label {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
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
</style>
