<template>
  <Dialog v-model:visible="dialogVisible" modal :header="dialogHeader"
          class="gp-dialog-md"
          @hide="onHide">

    <!-- Success State - Show Created Link -->
    <div v-if="showSuccessState" class="success-state">
      <div class="success-icon">
        <i class="pi pi-check-circle"></i>
      </div>
      <h3>Timeline Share Created!</h3>
      <p>Your timeline share link is ready. Copy and share it with others:</p>

      <div class="link-display">
        <InputText :value="createdShareUrl" readonly class="share-url-input" />
        <Button icon="pi pi-copy" @click="copyLinkToClipboard"
                v-tooltip="'Copy to clipboard'" class="copy-btn" />
      </div>

      <div class="success-actions">
        <Button label="Create Another" icon="pi pi-plus" @click="resetToForm" outlined />
        <Button label="Done" icon="pi pi-check" @click="dialogVisible = false" />
      </div>
    </div>

    <!-- Form State -->
    <div v-else class="timeline-share-form">
      <div class="field">
        <label for="name">Name (optional)</label>
        <InputText id="name" v-model="formData.name" placeholder="e.g., Italy Vacation 2025"
                   class="w-full" />
      </div>

      <div class="field">
        <label for="start-date">Start Date *</label>
        <Calendar id="start-date" v-model="formData.start_date"
                  dateFormat="yy-mm-dd" showIcon showButtonBar
                  class="w-full" :class="{'p-invalid': errors.start_date}"
                  placeholder="Select start date" />
        <small v-if="errors.start_date" class="p-error">{{ errors.start_date }}</small>
      </div>

      <div class="field">
        <label for="end-date">End Date *</label>
        <Calendar id="end-date" v-model="formData.end_date"
                  dateFormat="yy-mm-dd" showIcon showButtonBar
                  class="w-full" :class="{'p-invalid': errors.end_date}"
                  placeholder="Select end date" />
        <small v-if="errors.end_date" class="p-error">{{ errors.end_date }}</small>
      </div>

      <div class="field">
        <label for="expires-at">Link Expiration</label>
        <Calendar id="expires-at" v-model="formData.expires_at"
                  dateFormat="yy-mm-dd" showIcon showButtonBar
                  class="w-full"
                  placeholder="Leave empty for no expiration" />
        <small class="p-text-secondary">When the link becomes inaccessible</small>
      </div>

      <div class="field-checkbox">
        <Checkbox id="show-current" v-model="formData.show_current_location" :binary="true" />
        <label for="show-current">Show current location during trip</label>
        <small class="p-text-secondary block ml-4">Only visible when viewing during the trip dates</small>
      </div>

      <div class="field-checkbox">
        <Checkbox id="show-photos" v-model="formData.show_photos" :binary="true" />
        <label for="show-photos">Include photos from Immich (if available)</label>
      </div>

      <div class="field-checkbox">
        <Checkbox id="use-custom-tiles" v-model="formData.use_custom_tiles" :binary="true" />
        <label for="use-custom-tiles">Use custom map tiles</label>
      </div>

      <div v-if="formData.use_custom_tiles" class="custom-tiles-section">
        <Message severity="warn" :closable="false" class="security-warning">
          <div class="warning-content">
            <i class="pi pi-exclamation-triangle"></i>
            <div>
              <strong>Security Notice:</strong>
              The custom tile URL (including any API keys) will be visible to all viewers.
              Only enable if you trust the recipients.
            </div>
          </div>
        </Message>

        <div class="field">
          <label for="custom-tile-url">Custom Tile URL</label>
          <InputText
            id="custom-tile-url"
            v-model="formData.custom_map_tile_url"
            placeholder="https://tile-provider.com/{z}/{x}/{y}.png"
            class="w-full"
            :class="{'p-invalid': errors.custom_map_tile_url}"
          />
          <small class="p-text-secondary">
            Placeholders: {z} zoom, {x}/{y} coordinates, {s} subdomains
          </small>
          <small v-if="errors.custom_map_tile_url" class="p-error">
            {{ errors.custom_map_tile_url }}
          </small>
        </div>
      </div>

      <div class="field-checkbox">
        <Checkbox id="has-password" v-model="formData.has_password" :binary="true" />
        <label for="has-password">Password protect this link</label>
      </div>

      <div v-if="formData.has_password" class="field">
        <label for="password">Password</label>
        <Password id="password" v-model="formData.password" toggleMask
                  :feedback="false" class="w-full"
                  :class="{'p-invalid': errors.password}"
                  placeholder="Enter password (min 6 characters)" />
        <small v-if="errors.password" class="p-error">{{ errors.password }}</small>
      </div>
    </div>

    <template #footer>
      <div v-if="!showSuccessState">
        <Button label="Cancel" icon="pi pi-times" text @click="dialogVisible = false" />
        <Button :label="isEditMode ? 'Update' : 'Create Link'" icon="pi pi-check"
                @click="handleSubmit" :loading="loading" />
      </div>
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useShareLinksStore } from '@/stores/shareLinks'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Calendar from 'primevue/calendar'
import Checkbox from 'primevue/checkbox'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { copyToClipboard } from '@/utils/clipboardUtils'

const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  },
  editingShare: {
    type: Object,
    default: null
  },
  prefillDates: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:visible', 'created', 'updated'])

const toast = useToast()
const shareLinksStore = useShareLinksStore()

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

const isEditMode = computed(() => !!props.editingShare)
const loading = ref(false)
const showSuccessState = ref(false)
const createdShareUrl = ref('')

const dialogHeader = computed(() => {
  if (showSuccessState.value) return 'Share Link Created'
  return isEditMode.value ? 'Edit Timeline Share' : 'Create Timeline Share'
})

const formData = ref({
  name: '',
  start_date: null,
  end_date: null,
  expires_at: null,
  show_current_location: true,
  show_photos: false,
  has_password: false,
  password: '',
  use_custom_tiles: false,
  custom_map_tile_url: ''
})

const errors = ref({
  start_date: null,
  end_date: null,
  password: null,
  custom_map_tile_url: null
})

// Initialize form data when dialog opens
watch(() => props.visible, (visible) => {
  if (visible) {
    if (props.editingShare) {
      // Edit mode - populate with existing data
      formData.value = {
        name: props.editingShare.name || '',
        start_date: props.editingShare.start_date ? new Date(props.editingShare.start_date) : null,
        end_date: props.editingShare.end_date ? new Date(props.editingShare.end_date) : null,
        expires_at: props.editingShare.expires_at ? new Date(props.editingShare.expires_at) : null,
        show_current_location: props.editingShare.show_current_location ?? true,
        show_photos: props.editingShare.show_photos ?? false,
        has_password: props.editingShare.has_password || false,
        password: '',
        use_custom_tiles: !!(props.editingShare.custom_map_tile_url),
        custom_map_tile_url: props.editingShare.custom_map_tile_url || ''
      }
    } else if (props.prefillDates) {
      // Create mode with prefilled dates from TimelinePage
      const endDate = props.prefillDates.end ? new Date(props.prefillDates.end) : null
      const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
      formData.value = {
        name: '',
        start_date: props.prefillDates.start ? new Date(props.prefillDates.start) : null,
        end_date: endDate,
        expires_at: expiresAt,
        show_current_location: true,
        show_photos: false,
        has_password: false,
        password: '',
        use_custom_tiles: false,
        custom_map_tile_url: ''
      }
    } else {
      // Create mode - reset to defaults
      resetForm()
    }
    errors.value = { start_date: null, end_date: null, password: null, custom_map_tile_url: null }
  }
})

function resetForm() {
  formData.value = {
    name: '',
    start_date: null,
    end_date: null,
    expires_at: null,
    show_current_location: true,
    show_photos: false,
    has_password: false,
    password: '',
    use_custom_tiles: false,
    custom_map_tile_url: ''
  }
}

// Watch for custom tiles checkbox changes
watch(() => formData.value.use_custom_tiles, (enabled) => {
  if (enabled && !formData.value.custom_map_tile_url) {
    // Pre-fill with user's profile custom tile URL if available
    try {
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      if (userInfo.customMapTileUrl && userInfo.customMapTileUrl.trim()) {
        formData.value.custom_map_tile_url = userInfo.customMapTileUrl.trim()
      }
    } catch (error) {
      console.error('Error reading user custom tile URL:', error)
    }
  }
})

function validateForm() {
  errors.value = { start_date: null, end_date: null, password: null, custom_map_tile_url: null }
  let isValid = true

  if (!formData.value.start_date) {
    errors.value.start_date = 'Start date is required'
    isValid = false
  }

  if (!formData.value.end_date) {
    errors.value.end_date = 'End date is required'
    isValid = false
  }

  if (formData.value.start_date && formData.value.end_date) {
    if (formData.value.end_date < formData.value.start_date) {
      errors.value.end_date = 'End date must be after start date'
      isValid = false
    }
  }

  if (formData.value.has_password && (!formData.value.password || formData.value.password.length < 2)) {
    errors.value.password = 'Password must be at least 2 characters'
    isValid = false
  }

  if (formData.value.use_custom_tiles) {
    if (!formData.value.custom_map_tile_url || !formData.value.custom_map_tile_url.trim()) {
      errors.value.custom_map_tile_url = 'Custom tile URL required when enabled'
      isValid = false
    } else if (formData.value.custom_map_tile_url.length > 1000) {
      errors.value.custom_map_tile_url = 'URL cannot exceed 1000 characters'
      isValid = false
    }
  }

  return isValid
}

async function handleSubmit() {
  if (!validateForm()) {
    return
  }

  loading.value = true

  try {
    const payload = {
      share_type: 'TIMELINE',
      name: formData.value.name || 'Timeline Share',
      start_date: formData.value.start_date.toISOString(),
      end_date: formData.value.end_date.toISOString(),
      expires_at: formData.value.expires_at ? formData.value.expires_at.toISOString() : null,
      show_current_location: formData.value.show_current_location,
      show_photos: formData.value.show_photos,
      password: formData.value.has_password ? formData.value.password : null,
      custom_map_tile_url: formData.value.use_custom_tiles ? formData.value.custom_map_tile_url : null,
      // Keep live location fields for compatibility
      show_history: false,
      history_hours: 0
    }

    if (isEditMode.value) {
      const updatedShare = await shareLinksStore.updateShareLink(props.editingShare.id, payload)
      emit('updated', updatedShare)
      dialogVisible.value = false
    } else {
      const newShare = await shareLinksStore.createShareLink(payload)
      emit('created', newShare)

      // Generate share URL
      const baseUrl = shareLinksStore.baseUrl || window.location.origin
      const sanitizedBaseUrl = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl
      createdShareUrl.value = `${sanitizedBaseUrl}/shared-timeline/${newShare.id}`

      // Show success state instead of closing
      showSuccessState.value = true
    }
  } catch (error) {
    console.error('Failed to save timeline share:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.userMessage || error.message || 'Failed to save timeline share',
      life: 5000
    })
  } finally {
    loading.value = false
  }
}

async function copyLinkToClipboard() {
  const success = await copyToClipboard(createdShareUrl.value)

  if (success) {
    toast.add({
      severity: 'success',
      summary: 'Copied!',
      detail: 'Share link copied to clipboard',
      life: 2000
    })
  } else {
    toast.add({
      severity: 'error',
      summary: 'Copy Failed',
      detail: 'Could not copy to clipboard',
      life: 3000
    })
  }
}

function resetToForm() {
  showSuccessState.value = false
  createdShareUrl.value = ''
  resetForm()
  errors.value = { start_date: null, end_date: null, password: null }
}

function onHide() {
  showSuccessState.value = false
  createdShareUrl.value = ''
  resetForm()
  errors.value = { start_date: null, end_date: null, password: null }
}
</script>

<style scoped>
.timeline-share-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding: 0.5rem 0;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.field label {
  font-weight: 600;
  font-size: 0.95rem;
}

.field-checkbox {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.field-checkbox label {
  font-weight: 500;
  cursor: pointer;
  margin-top: 0.1rem;
}

.field-checkbox small {
  color: var(--text-color-secondary);
  font-size: 0.85rem;
  margin-top: 0.25rem;
}

.w-full {
  width: 100%;
}

/* Success State Styles */
.success-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 2rem 1rem;
  text-align: center;
}

.success-icon {
  font-size: 4rem;
  color: var(--green-500);
}

.success-state h3 {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
  color: var(--text-color);
}

.success-state p {
  color: var(--text-color-secondary);
  margin: 0;
  max-width: 400px;
}

.link-display {
  display: flex;
  gap: 0.5rem;
  width: 100%;
  margin-top: 0.5rem;
}

.share-url-input {
  flex: 1;
  font-family: monospace;
  font-size: 0.9rem;
}

.copy-btn {
  flex-shrink: 0;
}

.success-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
  margin-top: 1rem;
  width: 100%;
}

.success-actions button {
  flex: 1;
  max-width: 200px;
}

/* Custom Tiles Section Styles */
.custom-tiles-section {
  margin-left: 1.75rem;
  padding-left: 1rem;
  border-left: 3px solid var(--orange-500);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.security-warning {
  margin: 0;
}

.warning-content {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.warning-content i {
  font-size: 1.25rem;
  color: var(--orange-500);
  flex-shrink: 0;
  margin-top: 0.1rem;
}

.warning-content div {
  flex: 1;
  font-size: 0.9rem;
  line-height: 1.5;
}

/* Mobile responsive */
@media (max-width: 640px) {
  .timeline-share-form {
    gap: 1.25rem;
  }

  .field label {
    font-size: 0.9rem;
  }

  .custom-tiles-section {
    margin-left: 1rem;
  }
}
</style>
