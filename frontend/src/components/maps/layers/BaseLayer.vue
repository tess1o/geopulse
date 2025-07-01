<template>
  <!-- This component has no template as it manages Leaflet layers directly -->
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, toRaw, readonly } from 'vue'
import L from 'leaflet'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  visible: {
    type: Boolean,
    default: true
  },
  options: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['layer-ready', 'layer-click', 'layer-error'])

// State
const layerGroup = ref(null)
const isReady = ref(false)

// Initialize layer
const initializeLayer = () => {
  if (!props.map) return

  try {
    layerGroup.value = L.layerGroup([], props.options)
    
    if (props.visible) {
      layerGroup.value.addTo(toRaw(props.map))
    }

    isReady.value = true
    emit('layer-ready', layerGroup.value)
  } catch (error) {
    console.error('Failed to initialize layer:', error)
    emit('layer-error', error)
  }
}

// Public methods
const addToLayer = (layer) => {
  if (layerGroup.value && layer) {
    layer.addTo(layerGroup.value)
  }
}

const removeFromLayer = (layer) => {
  if (layerGroup.value && layer) {
    layerGroup.value.removeLayer(layer)
  }
}

const clearLayer = () => {
  if (layerGroup.value) {
    layerGroup.value.clearLayers()
  }
}

const showLayer = () => {
  if (layerGroup.value && props.map && !props.map.hasLayer(layerGroup.value)) {
    layerGroup.value.addTo(toRaw(props.map))
  }
}

const hideLayer = () => {
  if (layerGroup.value && props.map && props.map.hasLayer(layerGroup.value)) {
    props.map.removeLayer(layerGroup.value)
  }
}

// Watch visibility changes
watch(() => props.visible, (newVisible) => {
  if (!layerGroup.value) return
  
  if (newVisible) {
    showLayer()
  } else {
    hideLayer()
  }
})

// Watch map changes
watch(() => props.map, (newMap) => {
  if (newMap && !isReady.value) {
    initializeLayer()
  }
}, { immediate: true })

// Expose methods and state
defineExpose({
  layerGroup: readonly(layerGroup),
  isReady: readonly(isReady),
  addToLayer,
  removeFromLayer,
  clearLayer,
  showLayer,
  hideLayer
})

// Lifecycle
onMounted(() => {
  if (props.map) {
    initializeLayer()
  }
})

onUnmounted(() => {
  if (layerGroup.value) {
    clearLayer()
    if (props.map && props.map.hasLayer(layerGroup.value)) {
      props.map.removeLayer(layerGroup.value)
    }
    layerGroup.value = null
  }
})
</script>