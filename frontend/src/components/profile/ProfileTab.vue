<template>
  <Card class="profile-info-card profile-settings-card">
    <template #content>
      <form @submit.prevent="handleSubmit" class="profile-form settings-tab">
        <div class="settings-tab-header">
          <div class="settings-tab-icon"><i class="pi pi-user"></i></div>
          <div class="settings-tab-info">
            <h3 class="settings-tab-title">{{ t('profile.general.title') }}</h3>
            <p class="settings-tab-description">{{ t('profile.general.description') }}</p>
          </div>
        </div>

        <section class="settings-group" aria-labelledby="profile-group-heading">
          <div class="settings-group-header">
            <h3 id="profile-group-heading">{{ t('profile.general.profile.heading') }}</h3>
            <p>{{ t('profile.general.profile.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              :title="t('profile.general.profile.fullName.title')"
              :description="t('profile.general.profile.fullName.description')"
              setting-id="fullName"
            >
              <template #control>
                <div class="field-control">
                  <InputText
                    id="fullName"
                    v-model="form.fullName"
                    :placeholder="t('profile.general.profile.fullName.placeholder')"
                    :invalid="!!errors.fullName"
                    :disabled="readOnly"
                    class="w-full"
                    :aria-label="t('profile.general.profile.fullName.title')"
                  />
                  <small v-if="errors.fullName" class="error-message">{{ errors.fullName }}</small>
                </div>
              </template>
            </SettingCard>

            <details class="avatar-setting" data-setting-id="profileImage">
              <summary class="avatar-setting-summary">
                <div class="avatar-setting-heading">
                  <h4>{{ t('profile.general.profile.image.heading') }} <span>{{ t('profile.general.profile.image.optional') }}</span></h4>
                  <p>{{ t('profile.general.profile.image.description') }}</p>
                </div>
                <div class="avatar-setting-preview">
                  <Avatar :image="currentAvatarImage" size="large" class="user-avatar" />
                  <i class="pi pi-chevron-down" aria-hidden="true"></i>
                </div>
              </summary>

              <div class="avatar-setting-content">
                <div class="avatar-actions">
                  <Button type="button" :label="t('profile.general.profile.image.upload')" icon="pi pi-upload" size="small" outlined :disabled="readOnly" @click="triggerAvatarUpload" />
                  <input ref="avatarFileInput" type="file" accept="image/png,image/jpeg,image/webp" class="hidden-avatar-input" @change="handleAvatarFileChange" />
                  <small class="help-text">{{ t('profile.general.profile.image.formats') }}</small>
                  <small v-if="selectedAvatarFile" class="help-text">{{ t('profile.general.profile.image.selected', { name: selectedAvatarFile.name }) }}</small>
                  <small v-if="errors.avatar" class="error-message">{{ errors.avatar }}</small>
                </div>

                <div class="avatar-grid">
                  <button
                    v-for="(avatar, index) in avatarOptions"
                    :key="index"
                    type="button"
                    :class="['avatar-option', { active: avatar === localAvatar }]"
                    :disabled="readOnly"
                    :aria-label="t('profile.general.profile.image.chooseAria', { index: index + 1 })"
                    :aria-pressed="avatar === localAvatar"
                    @click="selectBuiltInAvatar(avatar)"
                  >
                    <Avatar :image="avatar" size="large" />
                  </button>
                </div>
              </div>
            </details>
          </div>
        </section>

        <section class="settings-group" aria-labelledby="regional-group-heading">
          <div class="settings-group-header">
            <h3 id="regional-group-heading">{{ t('profile.general.regional.heading') }}</h3>
            <p>{{ t('profile.general.regional.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              :title="t('settings.language.title')"
              :description="t('settings.language.description')"
              setting-id="language"
            >
              <template #control>
                <div class="field-control">
                  <Dropdown
                    id="language"
                    v-model="form.language"
                    :options="languageOptions"
                    optionLabel="label"
                    optionValue="value"
                    :disabled="readOnly"
                    class="w-full"
                    :aria-label="t('settings.language.title')"
                  />
                </div>
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.general.regional.timezone.title')"
              :description="t('profile.general.regional.timezone.description')"
              setting-id="timezone"
            >
              <template #control>
                <div class="field-control">
                  <Dropdown
                    id="timezone"
                    v-model="form.timezone"
                    :options="timezoneOptions"
                    optionLabel="label"
                    optionValue="value"
                    :placeholder="t('profile.general.regional.timezone.placeholder')"
                    filter
                    :filterMatchMode="'contains'"
                    :invalid="!!errors.timezone"
                    :disabled="readOnly"
                    class="w-full"
                    scrollHeight="300px"
                    :aria-label="t('profile.general.regional.timezone.title')"
                  />
                  <small v-if="errors.timezone" class="error-message">{{ errors.timezone }}</small>
                </div>
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.general.regional.dateFormat.title')"
              :description="t('profile.general.regional.dateFormat.description')"
              :details="t('profile.general.regional.dateFormat.details')"
              setting-id="dateFormat"
            >
              <template #control>
                <Dropdown id="dateFormat" v-model="form.dateFormat" :options="dateFormatOptions" optionLabel="label" optionValue="value" :placeholder="t('profile.general.regional.dateFormat.placeholder')" :disabled="readOnly" class="w-full" :aria-label="t('profile.general.regional.dateFormat.title')" />
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.general.regional.timeFormat.title')"
              :description="t('profile.general.regional.timeFormat.description')"
              setting-id="timeFormat"
            >
              <template #control>
                <Dropdown id="timeFormat" v-model="form.timeFormat" :options="timeFormatOptions" optionLabel="label" optionValue="value" :placeholder="t('profile.general.regional.timeFormat.placeholder')" :disabled="readOnly" class="w-full" :aria-label="t('profile.general.regional.timeFormat.title')" />
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.general.regional.distanceUnit.title')"
              :description="t('profile.general.regional.distanceUnit.description')"
              setting-id="distanceUnit"
            >
              <template #control>
                <Dropdown id="distanceUnit" v-model="form.distanceUnit" :options="distanceUnitOptions" optionLabel="label" optionValue="value" :placeholder="t('profile.general.regional.distanceUnit.placeholder')" :disabled="readOnly" class="w-full" :aria-label="t('profile.general.regional.distanceUnit.title')" />
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.general.regional.temperatureUnit.title')"
              :description="t('profile.general.regional.temperatureUnit.description')"
              setting-id="temperatureUnit"
            >
              <template #control>
                <Dropdown id="temperatureUnit" v-model="form.temperatureUnit" :options="temperatureUnitOptions" optionLabel="label" optionValue="value" :placeholder="t('profile.general.regional.temperatureUnit.placeholder')" :disabled="readOnly" class="w-full" :aria-label="t('profile.general.regional.temperatureUnit.title')" />
              </template>
            </SettingCard>
          </div>
        </section>

        <section class="settings-group" aria-labelledby="navigation-group-heading">
          <div class="settings-group-header">
            <h3 id="navigation-group-heading">{{ t('profile.general.navigation.heading') }}</h3>
            <p>{{ t('profile.general.navigation.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              :title="t('profile.general.navigation.homePage.title')"
              :description="t('profile.general.navigation.homePage.description')"
              :details="t('profile.general.navigation.homePage.details')"
              setting-id="defaultRedirectUrl"
            >
              <template #control>
                <div class="field-control">
                  <Dropdown
                    id="defaultRedirectUrl"
                    v-model="form.defaultRedirectUrl"
                    :options="defaultRedirectUrlOptions"
                    optionLabel="label"
                    optionValue="value"
                    :placeholder="t('profile.general.navigation.homePage.placeholder')"
                    :invalid="!!errors.defaultRedirectUrl"
                    :disabled="readOnly"
                    class="w-full"
                    showClear
                    :aria-label="t('profile.general.navigation.homePage.title')"
                  />
                  <small v-if="errors.defaultRedirectUrl" class="error-message">{{ errors.defaultRedirectUrl }}</small>

                  <div v-if="form.defaultRedirectUrl === 'custom'" class="field-control custom-url-field" data-setting-id="customRedirectUrl">
                    <label for="customRedirectUrl" class="field-sub-label">{{ t('profile.general.navigation.homePage.customLabel') }}</label>
                    <InputText
                      id="customRedirectUrl"
                      v-model="form.customRedirectUrl"
                      :placeholder="t('profile.general.navigation.homePage.customPlaceholder')"
                      :invalid="!!errors.customRedirectUrl"
                      :disabled="readOnly"
                      class="w-full"
                    />
                    <small v-if="errors.customRedirectUrl" class="error-message">{{ errors.customRedirectUrl }}</small>
                  </div>
                </div>
              </template>
            </SettingCard>
          </div>
        </section>

        <div class="settings-actions is-sticky">
          <Button
            type="button"
            :label="t('profile.general.actions.reset')"
            outlined
            @click="handleReset"
            :disabled="loading || readOnly"
          />
          <Button
            type="submit"
            :label="t('profile.general.actions.save')"
            :loading="loading"
            :disabled="!hasChanges || readOnly"
          />
        </div>
      </form>
    </template>
  </Card>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import { LOCALE_OPTIONS } from '@/composables/useLocale'

const { t } = useI18n()

// Props
const props = defineProps({
  readOnly: {
    type: Boolean,
    default: false
  },
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
  userDistanceUnit: {
    type: String,
    default: 'KILOMETERS'
  },
  userTemperatureUnit: {
    type: String,
    default: 'CELSIUS'
  },
  userDefaultRedirectUrl: {
    type: String,
    default: ''
  },
  userDateFormat: {
    type: String,
    default: 'MDY'
  },
  userTimeFormat: {
    type: String,
    default: '24h'
  },
  userLanguage: {
    type: String,
    default: 'en'
  }
})

// Emits
const emit = defineEmits(['save', 'dirty-change'])

// State
const loading = ref(false)
const localAvatar = ref('')
const avatarFileInput = ref(null)
const selectedAvatarFile = ref(null)
const avatarPreviewUrl = ref('')
const form = ref({
  fullName: '',
  timezone: '',
  language: 'en',
  dateFormat: 'MDY',
  timeFormat: '24h',
  distanceUnit: 'KILOMETERS', // Default value
  temperatureUnit: 'CELSIUS',
  defaultRedirectUrl: '',
  customRedirectUrl: ''
})
const errors = ref({})
const AVATAR_TARGET_SIZE = 96
const AVATAR_MAX_BYTES = 1024 * 1024
const SUPPORTED_AVATAR_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp'])

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

const distanceUnitOptions = computed(() => [
  { label: t('profile.general.distanceUnitOptions.kilometers'), value: 'KILOMETERS' },
  { label: t('profile.general.distanceUnitOptions.miles'), value: 'MILES' }
])

const temperatureUnitOptions = computed(() => [
  { label: t('profile.general.temperatureUnitOptions.celsius'), value: 'CELSIUS' },
  { label: t('profile.general.temperatureUnitOptions.fahrenheit'), value: 'FAHRENHEIT' }
])

const dateFormatOptions = computed(() => [
  { label: t('profile.general.dateFormatOptions.dmy'), value: 'DMY' },
  { label: t('profile.general.dateFormatOptions.mdy'), value: 'MDY' },
  { label: t('profile.general.dateFormatOptions.ymd'), value: 'YMD' }
])

const timeFormatOptions = computed(() => [
  { label: t('profile.general.timeFormatOptions.h24'), value: '24h' },
  { label: t('profile.general.timeFormatOptions.h12'), value: '12h' }
])

// Language names are endonyms and stay untranslated -- see LOCALE_OPTIONS in useLocale.
const languageOptions = LOCALE_OPTIONS

const defaultRedirectUrlOptions = computed(() => [
  { label: t('nav.items.timeline'), value: '/app/timeline' },
  { label: t('nav.items.dashboard'), value: '/app/dashboard' },
  { label: t('nav.items.journey-insights'), value: '/app/journey-insights' },
  { label: t('nav.items.coverage-explorer'), value: '/app/coverage' },
  { label: t('nav.items.friends'), value: '/app/friends' },
  { label: t('nav.items.rewind'), value: '/app/rewind' },
  { label: t('nav.items.gps-data'), value: '/app/gps-data' },
  { label: t('nav.items.location-sources'), value: '/app/location-sources' },
  { label: t('profile.general.navigation.homePage.customOption'), value: 'custom' }
])

// Computed
const hasChanges = computed(() => {
  const effectiveRedirectUrl = form.value.defaultRedirectUrl === 'custom'
    ? form.value.customRedirectUrl
    : form.value.defaultRedirectUrl

  const accountChanged = selectedAvatarFile.value !== null || form.value.fullName !== props.userName || localAvatar.value !== props.userAvatar
  const preferencesChanged = form.value.timezone !== props.userTimezone ||
    form.value.dateFormat !== props.userDateFormat || form.value.timeFormat !== props.userTimeFormat ||
    form.value.language !== props.userLanguage ||
    form.value.distanceUnit !== props.userDistanceUnit || form.value.temperatureUnit !== props.userTemperatureUnit ||
    effectiveRedirectUrl !== props.userDefaultRedirectUrl

  return accountChanged || preferencesChanged
})
const currentAvatarImage = computed(() => avatarPreviewUrl.value || localAvatar.value || '/avatars/avatar1.png')

watch(hasChanges, (changed) => {
  emit('dirty-change', Boolean(changed))
})

const revokeAvatarPreview = () => {
  if (avatarPreviewUrl.value) {
    URL.revokeObjectURL(avatarPreviewUrl.value)
    avatarPreviewUrl.value = ''
  }
}

const clearCustomAvatarSelection = () => {
  selectedAvatarFile.value = null
  revokeAvatarPreview()
  if (avatarFileInput.value) {
    avatarFileInput.value.value = ''
  }
}

const selectBuiltInAvatar = (avatar) => {
  if (props.readOnly) return
  clearCustomAvatarSelection()
  delete errors.value.avatar
  localAvatar.value = avatar
}

const triggerAvatarUpload = () => {
  if (props.readOnly) return
  avatarFileInput.value?.click()
}

const loadImageFromFile = (file) => {
  return new Promise((resolve, reject) => {
    const imageUrl = URL.createObjectURL(file)
    const image = new Image()

    image.onload = () => {
      URL.revokeObjectURL(imageUrl)
      resolve(image)
    }

    image.onerror = () => {
      URL.revokeObjectURL(imageUrl)
      reject(new Error(t('profile.general.errors.imageLoad')))
    }

    image.src = imageUrl
  })
}

const canvasToBlob = (canvas, mimeType, quality) => {
  return new Promise((resolve) => {
    canvas.toBlob((blob) => resolve(blob), mimeType, quality)
  })
}

const compressAvatar = async (canvas) => {
  const qualityLevels = [0.85, 0.75, 0.65, 0.55]
  for (const mimeType of ['image/webp', 'image/jpeg']) {
    for (const quality of qualityLevels) {
      const blob = await canvasToBlob(canvas, mimeType, quality)
      if (blob && blob.size <= AVATAR_MAX_BYTES) {
        return blob
      }
    }
  }
  throw new Error(t('profile.general.errors.imageTooLarge'))
}

const preprocessAvatarFile = async (file) => {
  const normalizedType = file.type?.toLowerCase() || ''
  if (!SUPPORTED_AVATAR_TYPES.has(normalizedType)) {
    throw new Error(t('profile.general.errors.imageFormat'))
  }

  const image = await loadImageFromFile(file)
  const canvas = document.createElement('canvas')
  canvas.width = AVATAR_TARGET_SIZE
  canvas.height = AVATAR_TARGET_SIZE

  const sourceSize = Math.min(image.width, image.height)
  const sourceX = (image.width - sourceSize) / 2
  const sourceY = (image.height - sourceSize) / 2

  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error(t('profile.general.errors.imageUnsupported'))
  }
  context.drawImage(
    image,
    sourceX,
    sourceY,
    sourceSize,
    sourceSize,
    0,
    0,
    AVATAR_TARGET_SIZE,
    AVATAR_TARGET_SIZE
  )

  const compressedBlob = await compressAvatar(canvas)
  const extension = compressedBlob.type === 'image/webp' ? 'webp' : 'jpg'
  return new File([compressedBlob], `avatar-${Date.now()}.${extension}`, { type: compressedBlob.type })
}

const handleAvatarFileChange = async (event) => {
  const file = event.target?.files?.[0]
  if (!file) {
    return
  }

  try {
    const optimizedFile = await preprocessAvatarFile(file)
    delete errors.value.avatar
    selectedAvatarFile.value = optimizedFile
    localAvatar.value = ''
    revokeAvatarPreview()
    avatarPreviewUrl.value = URL.createObjectURL(optimizedFile)
  } catch (error) {
    clearCustomAvatarSelection()
    errors.value.avatar = error.message || t('profile.general.errors.imageProcess')
  }
}

// Methods
const validate = () => {
  errors.value = {}

  if (!form.value.fullName?.trim()) {
    errors.value.fullName = t('profile.general.errors.fullNameRequired')
  } else if (form.value.fullName.trim().length < 2) {
    errors.value.fullName = t('profile.general.errors.fullNameTooShort')
  }

  // Validate custom redirect URL if "custom" option is selected
  if (form.value.defaultRedirectUrl === 'custom') {
    if (!form.value.customRedirectUrl || !form.value.customRedirectUrl.trim()) {
      errors.value.customRedirectUrl = t('profile.general.errors.customUrlRequired')
    } else {
      const url = form.value.customRedirectUrl.trim()

      if (!url.startsWith('/')) {
        errors.value.customRedirectUrl = t('profile.general.errors.customUrlInternal')
      } else if (url.includes('..')) {
        errors.value.customRedirectUrl = t('profile.general.errors.customUrlInvalid')
      } else if (url.length > 1000) {
        errors.value.customRedirectUrl = t('profile.general.errors.customUrlTooLong')
      }
    }
  }

  if (selectedAvatarFile.value && selectedAvatarFile.value.size > AVATAR_MAX_BYTES) {
    errors.value.avatar = t('profile.general.errors.avatarTooLarge')
  }

  return Object.keys(errors.value).length === 0
}

const handleSubmit = async () => {
  if (props.readOnly) return
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
      avatarFile: selectedAvatarFile.value,
      timezone: form.value.timezone,
      language: form.value.language,
      dateFormat: form.value.dateFormat,
      timeFormat: form.value.timeFormat,
      distanceUnit: form.value.distanceUnit,
      temperatureUnit: form.value.temperatureUnit,
      defaultRedirectUrl: effectiveRedirectUrl
    })
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  if (props.readOnly) return
  form.value.fullName = props.userName || ''
  form.value.timezone = props.userTimezone || 'UTC'
  form.value.language = props.userLanguage || 'en'
  form.value.dateFormat = props.userDateFormat || 'MDY'
  form.value.timeFormat = props.userTimeFormat || '24h'
  form.value.distanceUnit = props.userDistanceUnit || 'KILOMETERS'
  form.value.temperatureUnit = props.userTemperatureUnit || 'CELSIUS'

  // Check if the stored URL matches any predefined option. The option list is a computed (its labels
  // resolve through the catalogs), so read `.value` here.
  const userRedirectUrl = props.userDefaultRedirectUrl || ''
  const matchesPredefined = defaultRedirectUrlOptions.value.some(opt => opt.value === userRedirectUrl && opt.value !== 'custom')

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

  clearCustomAvatarSelection()
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
watch(() => [props.userName, props.userAvatar, props.userTimezone, props.userDateFormat, props.userTimeFormat, props.userLanguage, props.userDistanceUnit, props.userTemperatureUnit, props.userDefaultRedirectUrl], () => {
  handleReset()
})

onUnmounted(() => {
  revokeAvatarPreview()
})
</script>

<style scoped>
.avatar-setting {
  color: var(--gp-text-primary);
}

.avatar-setting-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-lg);
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
  cursor: pointer;
  list-style: none;
}

.avatar-setting-summary::-webkit-details-marker {
  display: none;
}

.avatar-setting-summary:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: -2px;
}

.avatar-setting-heading {
  min-width: 0;
}

.avatar-setting-heading h4 {
  margin: 0;
  color: var(--gp-text-primary);
  font-size: 1rem;
  font-weight: 600;
}

.avatar-setting-heading h4 span {
  margin-left: var(--gp-spacing-sm);
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
  font-weight: 400;
}

.avatar-setting-heading p {
  margin: var(--gp-spacing-xs) 0 0;
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
  line-height: 1.4;
}

.avatar-setting-preview {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  flex: 0 0 auto;
}

.avatar-setting-preview > i {
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
  transition: transform 0.2s ease;
}

.avatar-setting[open] .avatar-setting-preview > i {
  transform: rotate(180deg);
}

.avatar-setting-content {
  padding: 0 var(--gp-spacing-lg) var(--gp-spacing-lg);
  border-top: 1px solid var(--gp-border);
}

.user-avatar {
  width: 44px !important;
  height: 44px !important;
  border: 2px solid var(--gp-primary);
  flex-shrink: 0;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--gp-spacing-xs);
  margin-top: var(--gp-spacing-lg);
}

.hidden-avatar-input {
  display: none;
}

.avatar-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(64px, 1fr));
  gap: var(--gp-spacing-sm);
  margin-top: var(--gp-spacing-md);
  padding: var(--gp-spacing-md);
  background: color-mix(in srgb, var(--gp-surface-card) 45%, var(--gp-surface-muted));
  border-radius: var(--gp-radius-medium);
  max-height: 200px;
  overflow-y: auto;
}

.avatar-option {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: var(--gp-spacing-sm);
  border: 2px solid transparent;
  border-radius: var(--gp-radius-small);
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--gp-surface-card);
}

.avatar-option:hover {
  border-color: var(--gp-border-medium);
}

.avatar-option.active {
  border-color: var(--gp-primary);
  background: var(--gp-primary-light);
}

.avatar-option:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.avatar-option:disabled:hover {
  border-color: transparent;
}

.help-text {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
}

.custom-url-field {
  margin-top: var(--gp-spacing-xs);
  padding-top: var(--gp-spacing-sm);
  border-top: 1px solid var(--gp-border);
}

@media (max-width: 768px) {
  .avatar-grid {
    grid-template-columns: repeat(4, 1fr);
    max-height: 150px;
  }
}

@media (max-width: 480px) {
  .avatar-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .help-text {
    font-size: 0.75rem;
  }
}
</style>
