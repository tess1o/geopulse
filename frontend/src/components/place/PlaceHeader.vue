<template>
  <div class="place-header">
    <div class="place-header-content">
      <div class="place-icon">
        <i :class="placeIconClass"></i>
      </div>
      <div class="place-info">
        <div class="place-name-section">
          <div class="name-with-edit">
            <h1 v-if="!isEditing" class="place-name">{{ displayName }}</h1>
            <InputText
              v-else
              v-model="editedName"
              class="place-name-input"
              @keyup.enter="saveName"
              @keyup.escape="cancelEdit"
              ref="nameInput"
            />
            <div class="action-buttons">
              <Button
                v-if="canEdit && !isEditing"
                icon="pi pi-pencil"
                class="p-button-outlined p-button-sm edit-button"
                @click="startEdit"
                v-tooltip.top="'Rename this place'"
                label="Rename"
              />
              <Button
                v-if="placeType === 'geocoding' && !isEditing"
                icon="pi pi-cog"
                class="p-button-outlined p-button-sm"
                @click="openAdvancedEdit"
                v-tooltip.top="'Edit location details'"
                label="Edit Details"
              />
            </div>
          </div>
          <div v-if="isEditing" class="edit-actions">
            <Button
              icon="pi pi-check"
              class="p-button-success p-button-sm"
              @click="saveName"
              v-tooltip.top="'Save'"
              label="Save"
            />
            <Button
              icon="pi pi-times"
              class="p-button-outlined p-button-sm"
              @click="cancelEdit"
              v-tooltip.top="'Cancel'"
              label="Cancel"
            />
          </div>
        </div>
        <div class="place-metadata">
          <div class="place-type-badge">
            <Tag :value="typeLabel" :severity="typeSeverity" />
          </div>
          <div v-if="displayCoordinates" class="place-coordinates">
            <i class="pi pi-map-marker"></i>
            <span>{{ displayCoordinates }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import Tag from 'primevue/tag'

const props = defineProps({
  placeDetails: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update-name', 'edit-details'])

const isEditing = ref(false)
const editedName = ref('')
const nameInput = ref(null)

const displayName = computed(() => props.placeDetails?.locationName || 'Unknown Place')
const canEdit = computed(() => props.placeDetails?.canEdit || false)
const placeType = computed(() => props.placeDetails?.type || 'unknown')

const typeLabel = computed(() => {
  return placeType.value === 'favorite' ? 'Favorite' : 'Geocoded Location'
})

const typeSeverity = computed(() => {
  return placeType.value === 'favorite' ? 'success' : 'info'
})

const placeIconClass = computed(() => {
  if (props.placeDetails?.geometry?.type === 'area') {
    return 'pi pi-th-large place-icon-area'
  }
  return 'pi pi-map-marker place-icon-point'
})

const displayCoordinates = computed(() => {
  const geometry = props.placeDetails?.geometry
  if (!geometry) return null

  if (geometry.type === 'point' && geometry.latitude && geometry.longitude) {
    return `${geometry.latitude.toFixed(4)}, ${geometry.longitude.toFixed(4)}`
  } else if (geometry.type === 'area' && geometry.latitude && geometry.longitude) {
    // Show center coordinates for areas
    return `Center: ${geometry.latitude.toFixed(4)}, ${geometry.longitude.toFixed(4)}`
  }

  return null
})

const startEdit = () => {
  editedName.value = displayName.value
  isEditing.value = true
  nextTick(() => {
    nameInput.value?.$el?.focus()
  })
}

const saveName = () => {
  if (editedName.value.trim() && editedName.value !== displayName.value) {
    emit('update-name', editedName.value.trim())
  }
  isEditing.value = false
}

const cancelEdit = () => {
  editedName.value = ''
  isEditing.value = false
}

const openAdvancedEdit = () => {
  emit('edit-details')
}
</script>

<style scoped>
.place-header {
  margin-bottom: var(--gp-spacing-xl);
  padding: var(--gp-spacing-lg);
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
}

.place-header-content {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-lg);
}

.place-icon {
  flex-shrink: 0;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gp-primary);
  color: white;
  border-radius: var(--gp-radius-medium);
  font-size: 2rem;
}

.place-icon-area {
  font-size: 1.75rem;
}

.place-info {
  flex: 1;
  min-width: 0;
}

.place-name-section {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  margin-bottom: var(--gp-spacing-sm);
}

.name-with-edit {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  flex-wrap: wrap;
}

.place-name {
  margin: 0;
  font-size: 1.75rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  line-height: 1.3;
}

.place-name-input {
  font-size: 1.5rem;
  font-weight: 600;
  padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
  flex: 1;
  max-width: 500px;
}

.action-buttons {
  display: flex;
  gap: var(--gp-spacing-sm);
  flex-wrap: wrap;
}

.edit-button {
  flex-shrink: 0;
  white-space: nowrap;
}

.edit-actions {
  display: flex;
  gap: var(--gp-spacing-sm);
  flex-wrap: wrap;
}

.place-metadata {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.place-type-badge {
  display: inline-block;
}

.place-coordinates {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  color: var(--gp-text-secondary);
  font-size: 0.875rem;
  font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, 'Courier New', monospace;
}

.place-coordinates i {
  color: var(--gp-primary);
  font-size: 0.75rem;
}

/* Dark mode */
.p-dark .place-header {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .place-name {
  color: var(--gp-text-primary);
}

/* Responsive design */
@media (max-width: 768px) {
  .place-header-content {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .place-icon {
    width: 50px;
    height: 50px;
    font-size: 1.5rem;
  }

  .place-name {
    font-size: 1.5rem;
  }

  .place-name-section {
    flex-direction: column;
    align-items: center;
  }

  .place-name-input {
    font-size: 1.25rem;
    max-width: 100%;
  }
}
</style>
