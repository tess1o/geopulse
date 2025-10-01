<template>
  <Card class="profile-info-card">
    <template #content>
      <form @submit.prevent="handleSubmit" class="profile-form">
        <!-- Avatar Section -->
        <div class="avatar-section">
          <div class="avatar-preview">
            <Avatar
              :image="localAvatar || '/avatars/avatar1.png'"
              size="xlarge"
              class="user-avatar"
            />
            <div class="avatar-info">
              <h3 class="avatar-title">Profile Picture</h3>
              <p class="avatar-description">Choose your avatar from the options below</p>
            </div>
          </div>

          <div class="avatar-grid">
            <div
              v-for="(avatar, index) in avatarOptions"
              :key="index"
              :class="['avatar-option', { active: avatar === localAvatar }]"
              @click="localAvatar = avatar"
            >
              <Avatar :image="avatar" size="large" />
            </div>
          </div>
        </div>

        <!-- Full Name Field -->
        <div class="form-section">
          <div class="form-field">
            <label for="fullName" class="form-label">Full Name</label>
            <InputText
              id="fullName"
              v-model="form.fullName"
              placeholder="Enter your full name"
              :invalid="!!errors.fullName"
              class="w-full"
            />
            <small v-if="errors.fullName" class="error-message">
              {{ errors.fullName }}
            </small>
          </div>

          <div class="form-field">
            <label for="email" class="form-label">Email Address</label>
            <InputText
              id="email"
              :value="userEmail"
              disabled
              class="w-full"
            />
            <small class="help-text">Email cannot be changed</small>
          </div>

          <div class="form-field">
            <label for="timezone" class="form-label">Timezone</label>
            <Dropdown
              id="timezone"
              v-model="form.timezone"
              :options="timezoneOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Select your timezone"
              filter
              :filterMatchMode="'contains'"
              :invalid="!!errors.timezone"
              class="w-full"
              scrollHeight="300px"
            />
            <small v-if="errors.timezone" class="error-message">
              {{ errors.timezone }}
            </small>
            <small v-else class="help-text">
              Your timezone is used for date displays and statistics
            </small>
          </div>
        </div>

        <!-- Action Buttons -->
        <div class="form-actions">
          <Button
            type="button"
            label="Reset"
            outlined
            @click="handleReset"
            :disabled="loading"
          />
          <Button
            type="submit"
            label="Save Changes"
            :loading="loading"
            :disabled="!hasChanges"
          />
        </div>
      </form>
    </template>
  </Card>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'

// Props
const props = defineProps({
  userName: {
    type: String,
    required: true
  },
  userEmail: {
    type: String,
    required: true
  },
  userAvatar: {
    type: String,
    required: true
  },
  userTimezone: {
    type: String,
    required: true
  }
})

// Emits
const emit = defineEmits(['save'])

// State
const loading = ref(false)
const localAvatar = ref('')
const form = ref({
  fullName: '',
  timezone: ''
})
const errors = ref({})

// Avatar options
const avatarOptions = [
  '/avatars/avatar1.png',
  '/avatars/avatar2.png',
  '/avatars/avatar3.png',
  '/avatars/avatar4.png',
  '/avatars/avatar5.png',
  '/avatars/avatar6.png',
  '/avatars/avatar7.png',
  '/avatars/avatar8.png',
  '/avatars/avatar9.png',
  '/avatars/avatar10.png',
  '/avatars/avatar11.png',
  '/avatars/avatar12.png',
  '/avatars/avatar13.png',
  '/avatars/avatar14.png',
  '/avatars/avatar15.png',
  '/avatars/avatar16.png',
  '/avatars/avatar17.png',
  '/avatars/avatar18.png',
  '/avatars/avatar19.png',
  '/avatars/avatar20.png',
]

// Timezone options
const timezoneOptions = [
  { label: 'UTC', value: 'UTC' },
  { label: 'Europe/London GMT+0', value: 'Europe/London' },
  { label: 'Europe/Paris GMT+1', value: 'Europe/Paris' },
  { label: 'Europe/Berlin GMT+1', value: 'Europe/Berlin' },
  { label: 'Europe/Rome GMT+1', value: 'Europe/Rome' },
  { label: 'Europe/Madrid GMT+1', value: 'Europe/Madrid' },
  { label: 'Europe/Amsterdam GMT+1', value: 'Europe/Amsterdam' },
  { label: 'Europe/Brussels GMT+1', value: 'Europe/Brussels' },
  { label: 'Europe/Vienna GMT+1', value: 'Europe/Vienna' },
  { label: 'Europe/Stockholm GMT+1', value: 'Europe/Stockholm' },
  { label: 'Europe/Copenhagen GMT+1', value: 'Europe/Copenhagen' },
  { label: 'Europe/Oslo GMT+1', value: 'Europe/Oslo' },
  { label: 'Europe/Helsinki GMT+2', value: 'Europe/Helsinki' },
  { label: 'Europe/Athens GMT+2', value: 'Europe/Athens' },
  { label: 'Europe/Bucharest GMT+2', value: 'Europe/Bucharest' },
  { label: 'Europe/Kyiv GMT+2', value: 'Europe/Kyiv' },
  { label: 'Europe/Warsaw GMT+1', value: 'Europe/Warsaw' },
  { label: 'Europe/Prague GMT+1', value: 'Europe/Prague' },
  { label: 'Europe/Budapest GMT+1', value: 'Europe/Budapest' },
  { label: 'Europe/Moscow GMT+3', value: 'Europe/Moscow' },
  { label: 'America/New_York GMT-5', value: 'America/New_York' },
  { label: 'America/Chicago GMT-6', value: 'America/Chicago' },
  { label: 'America/Denver GMT-7', value: 'America/Denver' },
  { label: 'America/Los_Angeles GMT-8', value: 'America/Los_Angeles' },
  { label: 'America/Toronto GMT-5', value: 'America/Toronto' },
  { label: 'America/Vancouver GMT-8', value: 'America/Vancouver' },
  { label: 'America/Mexico_City GMT-6', value: 'America/Mexico_City' },
  { label: 'America/Sao_Paulo GMT-3', value: 'America/Sao_Paulo' },
  { label: 'America/Argentina/Buenos_Aires GMT-3', value: 'America/Argentina/Buenos_Aires' },
  { label: 'Asia/Tokyo GMT+9', value: 'Asia/Tokyo' },
  { label: 'Asia/Shanghai GMT+8', value: 'Asia/Shanghai' },
  { label: 'Asia/Hong_Kong GMT+8', value: 'Asia/Hong_Kong' },
  { label: 'Asia/Singapore GMT+8', value: 'Asia/Singapore' },
  { label: 'Asia/Seoul GMT+9', value: 'Asia/Seoul' },
  { label: 'Asia/Bangkok GMT+7', value: 'Asia/Bangkok' },
  { label: 'Asia/Jakarta GMT+7', value: 'Asia/Jakarta' },
  { label: 'Asia/Manila GMT+8', value: 'Asia/Manila' },
  { label: 'Asia/Kolkata GMT+5:30', value: 'Asia/Kolkata' },
  { label: 'Asia/Dubai GMT+4', value: 'Asia/Dubai' },
  { label: 'Asia/Tehran GMT+3:30', value: 'Asia/Tehran' },
  { label: 'Australia/Sydney GMT+10', value: 'Australia/Sydney' },
  { label: 'Australia/Melbourne GMT+10', value: 'Australia/Melbourne' },
  { label: 'Australia/Perth GMT+8', value: 'Australia/Perth' },
  { label: 'Pacific/Auckland GMT+12', value: 'Pacific/Auckland' },
  { label: 'Africa/Cairo GMT+2', value: 'Africa/Cairo' },
  { label: 'Africa/Johannesburg GMT+2', value: 'Africa/Johannesburg' },
  { label: 'Africa/Lagos GMT+1', value: 'Africa/Lagos' },
  { label: 'Africa/Nairobi GMT+3', value: 'Africa/Nairobi' }
]

// Computed
const hasChanges = computed(() => {
  return form.value.fullName !== props.userName ||
         localAvatar.value !== props.userAvatar ||
         form.value.timezone !== props.userTimezone
})

// Methods
const validate = () => {
  errors.value = {}

  if (!form.value.fullName?.trim()) {
    errors.value.fullName = 'Full name is required'
  } else if (form.value.fullName.trim().length < 2) {
    errors.value.fullName = 'Full name must be at least 2 characters'
  }

  return Object.keys(errors.value).length === 0
}

const handleSubmit = async () => {
  if (!validate()) return

  loading.value = true

  try {
    await emit('save', {
      fullName: form.value.fullName.trim(),
      avatar: localAvatar.value,
      timezone: form.value.timezone
    })
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  form.value.fullName = props.userName || ''
  form.value.timezone = props.userTimezone || 'UTC'
  localAvatar.value = props.userAvatar || '/avatars/avatar1.png'
  errors.value = {}
}

// Watchers
watch(() => form.value.fullName, () => {
  if (errors.value.fullName) {
    validate()
  }
})

// Initialize form
onMounted(() => {
  handleReset()
})

// Watch props changes
watch(() => [props.userName, props.userAvatar, props.userTimezone], () => {
  handleReset()
})
</script>

<style scoped>
.profile-info-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  width: 100%;
  box-sizing: border-box;
}

.profile-info-card :deep(.p-card-content) {
  width: 100%;
  box-sizing: border-box;
  padding: 1.5rem;
}

/* Avatar Section */
.avatar-section {
  margin-bottom: 2rem;
}

.avatar-preview {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  margin-bottom: 1.5rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.user-avatar {
  width: 80px !important;
  height: 80px !important;
  border: 3px solid var(--gp-primary);
  flex-shrink: 0;
}

.avatar-info {
  flex: 1;
}

.avatar-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.25rem 0;
}

.avatar-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

.avatar-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
  gap: 0.75rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  max-height: 200px;
  overflow-y: auto;
}

.avatar-option {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0.5rem;
  border: 2px solid transparent;
  border-radius: var(--gp-radius-small);
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--gp-surface-white);
}

.avatar-option:hover {
  border-color: var(--gp-border-medium);
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-light);
}

.avatar-option.active {
  border-color: var(--gp-primary);
  background: var(--gp-primary-light);
  box-shadow: 0 0 0 2px rgba(26, 86, 219, 0.1);
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

.help-text {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
  font-style: italic;
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

/* Input Styling */
:deep(.p-inputtext) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.75rem 1rem;
  transition: all 0.2s ease;
}

:deep(.p-inputtext:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

:deep(.p-inputtext:disabled) {
  background: var(--gp-surface-light);
  color: var(--gp-text-secondary);
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
  .avatar-preview {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }

  .avatar-grid {
    grid-template-columns: repeat(4, 1fr);
    max-height: 150px;
  }

  .form-actions {
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .avatar-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .form-actions .p-button {
    width: 100%;
    min-height: 48px;
  }

  .form-label {
    font-size: 0.9rem;
  }

  .help-text {
    font-size: 0.75rem;
  }

  .error-message {
    font-size: 0.8rem;
  }
}
</style>
