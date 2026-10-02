<template>
  <Card class="profile-settings-card">
    <template #content>
      <div class="security-tab settings-tab">
        <div class="settings-tab-header">
          <div class="settings-tab-icon"><i class="pi pi-shield"></i></div>
          <div class="settings-tab-info">
            <h3 class="settings-tab-title">{{ t('profile.security.title') }}</h3>
            <p class="settings-tab-description">{{ t('profile.security.description') }}</p>
          </div>
        </div>

        <form @submit.prevent="handleSubmit" class="settings-group" aria-labelledby="password-group-heading">
          <div class="settings-group-header">
            <h3 id="password-group-heading">{{ hasPassword ? t('profile.security.changePasswordHeading') : t('profile.security.setPasswordHeading') }}</h3>
            <p>{{ hasPassword ? t('profile.security.changePasswordDescription') : t('profile.security.setPasswordDescription') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              v-if="hasPassword"
              :title="t('profile.security.currentPassword.title')"
              :description="t('profile.security.currentPassword.description')"
              setting-id="currentPassword"
            >
              <template #control>
                <div class="field-control">
                  <Password id="currentPassword" v-model="form.currentPassword" :placeholder="t('profile.security.currentPassword.placeholder')" :feedback="false" toggleMask :invalid="!!errors.currentPassword" :disabled="readOnly" class="w-full" :aria-label="t('profile.security.currentPassword.title')" />
                  <small v-if="errors.currentPassword" class="error-message">{{ errors.currentPassword }}</small>
                </div>
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.security.newPassword.title')"
              :description="t('profile.security.newPassword.description')"
              setting-id="newPassword"
            >
              <template #control>
                <div class="field-control">
                  <Password id="newPassword" v-model="form.newPassword" :placeholder="t('profile.security.newPassword.placeholder')" :feedback="true" toggleMask :invalid="!!errors.newPassword" :disabled="readOnly" class="w-full" :aria-label="t('profile.security.newPassword.title')" />
                  <small v-if="errors.newPassword" class="error-message">{{ errors.newPassword }}</small>
                </div>
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.security.confirmPassword.title')"
              :description="t('profile.security.confirmPassword.description')"
              setting-id="confirmPassword"
            >
              <template #control>
                <div class="field-control">
                  <Password id="confirmPassword" v-model="form.confirmPassword" :placeholder="t('profile.security.confirmPassword.placeholder')" :feedback="false" toggleMask :invalid="!!errors.confirmPassword" :disabled="readOnly" class="w-full" :aria-label="t('profile.security.confirmPassword.title')" />
                  <small v-if="errors.confirmPassword" class="error-message">{{ errors.confirmPassword }}</small>
                </div>
              </template>
            </SettingCard>
          </div>

          <div class="settings-actions">
            <Button
              type="button"
              :label="t('profile.security.cancel')"
              outlined
              @click="handleReset"
              :disabled="loading || readOnly"
            />
            <Button
              type="submit"
              :label="hasPassword ? t('profile.security.changePasswordSubmit') : t('profile.security.setPasswordSubmit')"
              :loading="loading"
              :disabled="!hasChanges || readOnly"
            />
          </div>
        </form>

        <OidcManagement :read-only="readOnly" />
        <ApiTokensManagement :read-only="readOnly" />
      </div>
    </template>
  </Card>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import OidcManagement from '@/components/auth/OidcManagement.vue'
import ApiTokensManagement from '@/components/profile/ApiTokensManagement.vue'
import SettingCard from '@/components/ui/forms/SettingCard.vue'

const { t } = useI18n()

// Props
const props = defineProps({
  readOnly: {
    type: Boolean,
    default: false
  },
  hasPassword: {
    type: Boolean,
    required: true
  }
})

// Emits
const emit = defineEmits(['save', 'dirty-change'])

// State
const loading = ref(false)
const form = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const errors = ref({})

// Computed
const hasChanges = computed(() => {
  return form.value.currentPassword ||
         form.value.newPassword ||
         form.value.confirmPassword
})

watch(hasChanges, (changed) => {
  emit('dirty-change', Boolean(changed))
})

// Methods
const validate = () => {
  errors.value = {}

  // Only require current password if user has a password
  if (props.hasPassword && !form.value.currentPassword) {
    errors.value.currentPassword = t('profile.security.validation.currentRequired')
  }

  if (!form.value.newPassword) {
    errors.value.newPassword = t('profile.security.validation.newRequired')
  } else if (form.value.newPassword.length < 6) {
    errors.value.newPassword = t('profile.security.validation.newTooShort')
  }

  if (!form.value.confirmPassword) {
    errors.value.confirmPassword = t('profile.security.validation.confirmRequired')
  } else if (form.value.newPassword !== form.value.confirmPassword) {
    errors.value.confirmPassword = t('profile.security.validation.mismatch')
  }

  return Object.keys(errors.value).length === 0
}

const handleSubmit = async () => {
  if (props.readOnly) return
  if (!validate()) return

  loading.value = true

  try {
    await emit('save', {
      currentPassword: props.hasPassword ? form.value.currentPassword : null,
      newPassword: form.value.newPassword
    })

    // Reset form after successful save
    handleReset()
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  if (props.readOnly) return
  form.value = {
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  }
  errors.value = {}
}

// Watchers
watch(() => [form.value.newPassword, form.value.confirmPassword], () => {
  if (errors.value.confirmPassword && form.value.newPassword === form.value.confirmPassword) {
    delete errors.value.confirmPassword
  }
})
</script>

<style scoped>
:deep(.p-password) {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

:deep(.p-password-input) {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
