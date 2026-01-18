<template>
  <div>
    <Card class="security-card">
      <template #content>
        <form @submit.prevent="handleSubmit" class="security-form">
          <div class="security-header">
            <div class="security-icon">
              <i class="pi pi-lock"></i>
            </div>
            <div class="security-info">
              <h3 class="security-title">{{ hasPassword ? 'Change Password' : 'Set Password' }}</h3>
              <p class="security-description">
                {{ hasPassword ? 'Update your password to keep your account secure' : 'Set a password for your account' }}
              </p>
            </div>
          </div>

          <div class="form-section">
            <div v-if="hasPassword" class="form-field">
              <label for="currentPassword" class="form-label">Current Password</label>
              <Password
                id="currentPassword"
                v-model="form.currentPassword"
                placeholder="Enter current password"
                :feedback="false"
                toggleMask
                :invalid="!!errors.currentPassword"
                class="w-full"
              />
              <small v-if="errors.currentPassword" class="error-message">
                {{ errors.currentPassword }}
              </small>
            </div>

            <div class="form-field">
              <label for="newPassword" class="form-label">New Password</label>
              <Password
                id="newPassword"
                v-model="form.newPassword"
                placeholder="Enter new password"
                :feedback="true"
                toggleMask
                :invalid="!!errors.newPassword"
                class="w-full"
              />
              <small v-if="errors.newPassword" class="error-message">
                {{ errors.newPassword }}
              </small>
            </div>

            <div class="form-field">
              <label for="confirmPassword" class="form-label">Confirm New Password</label>
              <Password
                id="confirmPassword"
                v-model="form.confirmPassword"
                placeholder="Confirm new password"
                :feedback="false"
                toggleMask
                :invalid="!!errors.confirmPassword"
                class="w-full"
              />
              <small v-if="errors.confirmPassword" class="error-message">
                {{ errors.confirmPassword }}
              </small>
            </div>
          </div>

          <!-- Action Buttons -->
          <div class="form-actions">
            <Button
              type="button"
              label="Cancel"
              outlined
              @click="handleReset"
              :disabled="loading"
            />
            <Button
              type="submit"
              :label="hasPassword ? 'Change Password' : 'Set Password'"
              :loading="loading"
              :disabled="!hasChanges"
            />
          </div>
        </form>
      </template>
    </Card>

    <OidcManagement class="mt-6" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import OidcManagement from '@/components/auth/OidcManagement.vue'

// Props
const props = defineProps({
  hasPassword: {
    type: Boolean,
    required: true
  }
})

// Emits
const emit = defineEmits(['save'])

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

// Methods
const validate = () => {
  errors.value = {}

  // Only require current password if user has a password
  if (props.hasPassword && !form.value.currentPassword) {
    errors.value.currentPassword = 'Current password is required'
  }

  if (!form.value.newPassword) {
    errors.value.newPassword = 'New password is required'
  } else if (form.value.newPassword.length < 6) {
    errors.value.newPassword = 'Password must be at least 6 characters'
  }

  if (!form.value.confirmPassword) {
    errors.value.confirmPassword = 'Please confirm your new password'
  } else if (form.value.newPassword !== form.value.confirmPassword) {
    errors.value.confirmPassword = 'Passwords do not match'
  }

  return Object.keys(errors.value).length === 0
}

const handleSubmit = async () => {
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
.security-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  width: 100%;
  box-sizing: border-box;
}

.security-card :deep(.p-card-content) {
  width: 100%;
  box-sizing: border-box;
  padding: 1.5rem;
}

/* Security Section */
.security-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 2rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.security-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.security-info {
  flex: 1;
}

.security-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.25rem 0;
}

.security-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

/* Form Sections */
.form-section {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-label {
  font-weight: 600;
  color: var(--gp-text-primary);
  font-size: 0.9rem;
}

.error-message {
  color: var(--gp-danger);
  font-size: 0.85rem;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
}

/* Password Input Styling */
:deep(.p-password) {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

:deep(.p-password-input) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.75rem 1rem;
  transition: all 0.2s ease;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.p-password-input:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

/* Button Styling */
:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  font-weight: 600;
  padding: 0.75rem 1.5rem;
  transition: all 0.2s ease;
}

:deep(.p-button:not(.p-button-outlined)) {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
}

:deep(.p-button:not(.p-button-outlined):hover) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-medium);
}

:deep(.p-button-outlined) {
  border-color: var(--gp-border-medium);
  color: var(--gp-text-primary);
}

:deep(.p-button-outlined:hover) {
  background: var(--gp-surface-light);
  border-color: var(--gp-primary);
  color: var(--gp-primary);
}

/* Responsive Design */
@media (max-width: 768px) {
  .security-header {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }

  .form-actions {
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .security-card {
    border-radius: 0;
    border-left: none;
    border-right: none;
  }

  .security-card :deep(.p-card-content) {
    padding: 1rem;
  }

  .form-actions .p-button {
    width: 100%;
    min-height: 48px;
  }

  .form-label {
    font-size: 0.9rem;
  }

  .error-message {
    font-size: 0.8rem;
  }
}
</style>
