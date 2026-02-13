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

          <div class="form-field">
            <label for="measureUnit" class="form-label">
              Measurement Unit
              <i class="pi pi-info-circle" v-tooltip.right="'Choose your preferred unit for distance and speed.'"></i>
            </label>
            <Dropdown
                id="measureUnit"
                v-model="form.measureUnit"
                :options="measureUnitOptions"
                optionLabel="label"
                optionValue="value"
                placeholder="Select your measurement unit"
                class="w-full"
            />
            <small class="help-text">
              Affects how distances and speeds are displayed across the app.
            </small>
          </div>

          <div class="form-field">
            <label for="defaultRedirectUrl" class="form-label">
              Default Home Page
              <i class="pi pi-info-circle" v-tooltip.right="'Choose where you want to land after login or when you visit the homepage.'"></i>
            </label>
            <Dropdown
                id="defaultRedirectUrl"
                v-model="form.defaultRedirectUrl"
                :options="defaultRedirectUrlOptions"
                optionLabel="label"
                optionValue="value"
                placeholder="Select your default page"
                :invalid="!!errors.defaultRedirectUrl"
                class="w-full"
                showClear
            />
            <small v-if="errors.defaultRedirectUrl" class="error-message">
              {{ errors.defaultRedirectUrl }}
            </small>
            <small v-else class="help-text">
              Choose your preferred default page. Leave empty to use default behavior.
            </small>

            <!-- Custom URL input (shown when Custom option is selected) -->
            <div v-if="form.defaultRedirectUrl === 'custom'" class="custom-url-field">
              <label for="customRedirectUrl" class="form-label">Custom URL</label>
              <InputText
                id="customRedirectUrl"
                v-model="form.customRedirectUrl"
                placeholder="/app/your-custom-page"
                :invalid="!!errors.customRedirectUrl"
                class="w-full"
              />
              <small v-if="errors.customRedirectUrl" class="error-message">
                {{ errors.customRedirectUrl }}
              </small>
              <small v-else class="help-text">
                Enter an internal path starting with / (e.g., /app/dashboard).
              </small>
            </div>
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
  },
  userMeasureUnit: {
    type: String,
    default: 'METRIC'
  },
  userDefaultRedirectUrl: {
    type: String,
    default: ''
  }
})

// Emits
const emit = defineEmits(['save'])

// State
const loading = ref(false)
const localAvatar = ref('')
const form = ref({
  fullName: '',
  timezone: '',
  measureUnit: 'METRIC', // Default value
  defaultRedirectUrl: '',
  customRedirectUrl: ''
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
  { label: 'America/Anchorage GMT-9', value: 'America/Anchorage' },
  { label: 'America/Los_Angeles GMT-8', value: 'America/Los_Angeles' },
  { label: 'America/Vancouver GMT-8', value: 'America/Vancouver' },
  { label: 'America/Denver GMT-7', value: 'America/Denver' },
  { label: 'America/Chicago GMT-6', value: 'America/Chicago' },
  { label: 'America/Mexico_City GMT-6', value: 'America/Mexico_City' },
  { label: 'America/New_York GMT-5', value: 'America/New_York' },
  { label: 'America/Toronto GMT-5', value: 'America/Toronto' },
  { label: 'America/Halifax GMT-4', value: 'America/Halifax' },
  { label: 'America/St_Johns GMT-3:30', value: 'America/St_Johns' },
  { label: 'America/Sao_Paulo GMT-3', value: 'America/Sao_Paulo' },
  { label: 'America/Argentina/Buenos_Aires GMT-3', value: 'America/Argentina/Buenos_Aires' },
  { label: 'Asia/Dubai GMT+4', value: 'Asia/Dubai' },
  { label: 'Asia/Karachi GMT+5', value: 'Asia/Karachi' },
  { label: 'Asia/Tashkent GMT+5', value: 'Asia/Tashkent' },
  { label: 'Asia/Kolkata GMT+5:30', value: 'Asia/Kolkata' },
  { label: 'Asia/Kathmandu GMT+5:45', value: 'Asia/Kathmandu' },
  { label: 'Asia/Dhaka GMT+6', value: 'Asia/Dhaka' },
  { label: 'Asia/Yangon GMT+6:30', value: 'Asia/Yangon' },
  { label: 'Asia/Bangkok GMT+7', value: 'Asia/Bangkok' },
  { label: 'Asia/Jakarta GMT+7', value: 'Asia/Jakarta' },
  { label: 'Asia/Ho_Chi_Minh GMT+7', value: 'Asia/Ho_Chi_Minh' },
  { label: 'Asia/Shanghai GMT+8', value: 'Asia/Shanghai' },
  { label: 'Asia/Hong_Kong GMT+8', value: 'Asia/Hong_Kong' },
  { label: 'Asia/Singapore GMT+8', value: 'Asia/Singapore' },
  { label: 'Asia/Manila GMT+8', value: 'Asia/Manila' },
  { label: 'Australia/Perth GMT+8', value: 'Australia/Perth' },
  { label: 'Asia/Tokyo GMT+9', value: 'Asia/Tokyo' },
  { label: 'Asia/Seoul GMT+9', value: 'Asia/Seoul' },
  { label: 'Australia/Darwin GMT+9:30', value: 'Australia/Darwin' },
  { label: 'Australia/Brisbane GMT+10', value: 'Australia/Brisbane' },
  { label: 'Australia/Sydney GMT+10', value: 'Australia/Sydney' },
  { label: 'Australia/Melbourne GMT+10', value: 'Australia/Melbourne' },
  { label: 'Australia/Adelaide GMT+10:30', value: 'Australia/Adelaide' },
  { label: 'Pacific/Noumea GMT+11', value: 'Pacific/Noumea' },
  { label: 'Pacific/Fiji GMT+12', value: 'Pacific/Fiji' },
  { label: 'Pacific/Auckland GMT+12', value: 'Pacific/Auckland' },
  { label: 'Pacific/Tongatapu GMT+13', value: 'Pacific/Tongatapu' },
  { label: 'Pacific/Honolulu GMT-10', value: 'Pacific/Honolulu' },
  { label: 'Asia/Tehran GMT+3:30', value: 'Asia/Tehran' },
  { label: 'Africa/Lagos GMT+1', value: 'Africa/Lagos' },
  { label: 'Africa/Cairo GMT+2', value: 'Africa/Cairo' },
  { label: 'Africa/Johannesburg GMT+2', value: 'Africa/Johannesburg' },
  { label: 'Africa/Nairobi GMT+3', value: 'Africa/Nairobi' }
]

const measureUnitOptions = [
  { label: 'Metric (kilometers, meters)', value: 'METRIC' },
  { label: 'Imperial (miles, feet)', value: 'IMPERIAL' }
]

const defaultRedirectUrlOptions = [
  { label: 'Timeline', value: '/app/timeline' },
  { label: 'Dashboard', value: '/app/dashboard' },
  { label: 'Journey Insights', value: '/app/journey-insights' },
  { label: 'Friends', value: '/app/friends' },
  { label: 'Rewind', value: '/app/rewind' },
  { label: 'GPS Data', value: '/app/gps-data' },
  { label: 'Location Sources', value: '/app/location-sources' },
  { label: 'Custom URL...', value: 'custom' }
]

// Computed
const hasChanges = computed(() => {
  const effectiveRedirectUrl = form.value.defaultRedirectUrl === 'custom'
    ? form.value.customRedirectUrl
    : form.value.defaultRedirectUrl

  return form.value.fullName !== props.userName ||
         localAvatar.value !== props.userAvatar ||
         form.value.timezone !== props.userTimezone ||
         form.value.measureUnit !== props.userMeasureUnit ||
         effectiveRedirectUrl !== props.userDefaultRedirectUrl
})

// Methods
const validate = () => {
  errors.value = {}

  if (!form.value.fullName?.trim()) {
    errors.value.fullName = 'Full name is required'
  } else if (form.value.fullName.trim().length < 2) {
    errors.value.fullName = 'Full name must be at least 2 characters'
  }

  // Validate custom redirect URL if "custom" option is selected
  if (form.value.defaultRedirectUrl === 'custom') {
    if (!form.value.customRedirectUrl || !form.value.customRedirectUrl.trim()) {
      errors.value.customRedirectUrl = 'Custom URL is required'
    } else {
      const url = form.value.customRedirectUrl.trim()

      if (!url.startsWith('/')) {
        errors.value.customRedirectUrl = 'URL must be an internal path starting with /'
      } else if (url.includes('..')) {
        errors.value.customRedirectUrl = 'Invalid URL format'
      } else if (url.length > 1000) {
        errors.value.customRedirectUrl = 'URL is too long (max 1000 characters)'
      }
    }
  }

  return Object.keys(errors.value).length === 0
}

const handleSubmit = async () => {
  if (!validate()) return

  loading.value = true

  try {
    // Use custom URL if "custom" option is selected, otherwise use dropdown value
    const effectiveRedirectUrl = form.value.defaultRedirectUrl === 'custom'
      ? form.value.customRedirectUrl?.trim() || ''
      : form.value.defaultRedirectUrl?.trim() || ''

    await emit('save', {
      fullName: form.value.fullName.trim(),
      avatar: localAvatar.value,
      timezone: form.value.timezone,
      measureUnit: form.value.measureUnit,
      defaultRedirectUrl: effectiveRedirectUrl
    })
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  form.value.fullName = props.userName || ''
  form.value.timezone = props.userTimezone || 'UTC'
  form.value.measureUnit = props.userMeasureUnit || 'METRIC'

  // Check if the stored URL matches any predefined option
  const userRedirectUrl = props.userDefaultRedirectUrl || ''
  const matchesPredefined = defaultRedirectUrlOptions.some(opt => opt.value === userRedirectUrl && opt.value !== 'custom')

  if (matchesPredefined) {
    form.value.defaultRedirectUrl = userRedirectUrl
    form.value.customRedirectUrl = ''
  } else if (userRedirectUrl) {
    // It's a custom URL
    form.value.defaultRedirectUrl = 'custom'
    form.value.customRedirectUrl = userRedirectUrl
  } else {
    // No URL set
    form.value.defaultRedirectUrl = ''
    form.value.customRedirectUrl = ''
  }

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
watch(() => [props.userName, props.userAvatar, props.userTimezone, props.userMeasureUnit, props.userDefaultRedirectUrl], () => {
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

/* Location Sharing Field */
.location-sharing-field {
  position: relative;
}

.location-sharing-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
  margin-bottom: 0.5rem;
}

.location-sharing-row .form-label {
  margin: 0;
  flex: 1;
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

/* Custom URL Field */
.custom-url-field {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
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
  .profile-info-card {
    border-radius: 0;
    border-left: none;
    border-right: none;
  }

  .profile-info-card :deep(.p-card-content) {
    padding: 1rem;
  }

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

  .location-sharing-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
  }

  .location-sharing-row .form-label {
    width: 100%;
  }
}
</style>
