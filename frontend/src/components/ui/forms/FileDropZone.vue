<template>
  <div
      class="file-drop-zone"
      :class="{ 'is-dragging': isDragging, 'is-disabled': disabled, 'has-file': !!modelValue }"
      role="button"
      :tabindex="disabled ? -1 : 0"
      :aria-disabled="disabled"
      @click="openFilePicker"
      @keydown.enter.prevent="openFilePicker"
      @keydown.space.prevent="openFilePicker"
      @dragenter.prevent="onDragEnter"
      @dragover.prevent
      @dragleave.prevent="onDragLeave"
      @drop.prevent="onDrop"
  >
    <input
        ref="fileInput"
        type="file"
        class="file-drop-input"
        :accept="accept"
        :disabled="disabled"
        @change="onFileInputChange"
    />

    <div v-if="!modelValue" class="drop-prompt">
      <i class="pi pi-cloud-upload drop-icon"></i>
      <p class="drop-text">{{ promptText }}</p>
      <p v-if="hintText" class="drop-hint">{{ hintText }}</p>
    </div>

    <div v-else class="drop-file-info">
      <i class="pi pi-file drop-icon"></i>
      <p class="drop-file-name">{{ modelValue.name }}</p>
      <p class="drop-file-size">{{ formatFileSize(modelValue.size) }}</p>
      <Button
          :label="removeLabel"
          icon="pi pi-times"
          size="small"
          severity="danger"
          text
          :disabled="disabled"
          class="drop-remove-button"
          @click.stop="clearFile"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: {
    type: Object,
    default: null
  },
  // Comma-separated extensions, same syntax as <input accept>, e.g. ".gpx,.zip". Empty accepts anything.
  accept: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  promptText: {
    type: String,
    required: true
  },
  hintText: {
    type: String,
    default: ''
  },
  removeLabel: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'invalid'])

const fileInput = ref(null)
const isDragging = ref(false)
// dragenter/dragleave also fire when the pointer crosses child elements, so count them.
let dragDepth = 0

const acceptedExtensions = computed(() =>
  props.accept
    .split(',')
    .map(ext => ext.trim().toLowerCase())
    .filter(Boolean)
)

const isAccepted = (file) => {
  if (acceptedExtensions.value.length === 0) {
    return true
  }
  const name = file.name.toLowerCase()
  return acceptedExtensions.value.some(ext => name.endsWith(ext))
}

const selectFile = (file) => {
  if (!file || props.disabled) {
    return
  }
  if (!isAccepted(file)) {
    emit('invalid', file)
    return
  }
  emit('update:modelValue', file)
}

const openFilePicker = () => {
  if (!props.disabled) {
    fileInput.value?.click()
  }
}

const onFileInputChange = (event) => {
  selectFile(event.target.files?.[0])
  // Reset so picking the same file again still fires change.
  event.target.value = ''
}

const onDragEnter = () => {
  if (props.disabled) {
    return
  }
  dragDepth += 1
  isDragging.value = true
}

const onDragLeave = () => {
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) {
    isDragging.value = false
  }
}

const onDrop = (event) => {
  dragDepth = 0
  isDragging.value = false
  selectFile(event.dataTransfer?.files?.[0])
}

const clearFile = () => {
  emit('update:modelValue', null)
}

const formatFileSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}
</script>

<style scoped>
.file-drop-zone {
  width: 100%;
  border: 2px dashed var(--gp-border);
  border-radius: 8px;
  padding: 2.5rem 2rem;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.file-drop-zone:hover,
.file-drop-zone:focus-visible {
  border-color: var(--gp-primary);
  background: var(--gp-surface-ground);
  outline: none;
}

.file-drop-zone.is-dragging {
  border-color: var(--gp-primary);
  border-style: solid;
  background: var(--gp-primary-soft);
}

.file-drop-zone.is-disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.file-drop-zone.is-disabled:hover {
  border-color: var(--gp-border);
  background: transparent;
}

.file-drop-input {
  display: none;
}

.drop-icon {
  font-size: 3rem;
  color: var(--gp-text-secondary);
  margin-bottom: 1rem;
}

.is-dragging .drop-icon {
  color: var(--gp-primary);
}

.drop-text {
  font-size: 1.1rem;
  font-weight: 500;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.drop-hint {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  margin: 0;
}

.drop-file-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.drop-file-name {
  font-size: 1.1rem;
  font-weight: 500;
  color: var(--gp-text-primary);
  margin: 0;
  word-break: break-all;
}

.drop-file-size {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  margin: 0 0 0.5rem 0;
}

@media (max-width: 768px) {
  .file-drop-zone {
    padding: 2rem 1rem;
  }
}
</style>
