<template>
  <!-- This component has no template as it manages Leaflet markers directly -->
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import L from 'leaflet'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  latLng: {
    type: Array,
    required: true,
    validator: (value) => Array.isArray(value) && value.length === 2
  },
  icon: {
    type: Object,
    required: true
  },
  options: {
    type: Object,
    default: () => ({})
  },
  visible: {
    type: Boolean,
    default: true
  },
  popup: {
    type: [String, Object],
    default: null
  },
  tooltip: {
    type: [String, Object],
    default: null
  }
})

const emit = defineEmits([
  'marker-ready',
  'marker-click',
  'marker-hover',
  'marker-contextmenu'
])

// State
const marker = ref(null)
const isReady = ref(false)

// Initialize marker
const initializeMarker = () => {
  if (!props.map || !props.latLng || !props.icon) return

  try {
    marker.value = L.marker(props.latLng, {
      icon: props.icon,
      ...props.options
    })

    // Set up event listeners
    setupEventListeners()

    // Add popup if provided
    if (props.popup) {
      if (typeof props.popup === 'string') {
        marker.value.bindPopup(props.popup)
      } else {
        marker.value.bindPopup(props.popup.content, props.popup.options || {})
      }
    }

    // Add tooltip if provided
    if (props.tooltip) {
      if (typeof props.tooltip === 'string') {
        marker.value.bindTooltip(props.tooltip)
      } else {
        marker.value.bindTooltip(props.tooltip.content, props.tooltip.options || {})
      }
    }

    // Add to map if visible
    if (props.visible) {
      marker.value.addTo(props.map)
    }

    isReady.value = true
    emit('marker-ready', marker.value)
  } catch (error) {
    console.error('Failed to initialize marker:', error)
  }
}

const setupEventListeners = () => {
  if (!marker.value) return

  marker.value.on('click', (e) => {
    emit('marker-click', e)
  })

  marker.value.on('mouseover', (e) => {
    emit('marker-hover', e)
  })

  marker.value.on('contextmenu', (e) => {
    emit('marker-contextmenu', e)
  })
}

// Public methods
const setLatLng = (latLng) => {
  if (marker.value && latLng) {
    marker.value.setLatLng(latLng)
  }
}

const setIcon = (icon) => {
  if (marker.value && icon) {
    marker.value.setIcon(icon)
  }
}

const openPopup = () => {
  if (marker.value) {
    marker.value.openPopup()
  }
}

const closePopup = () => {
  if (marker.value) {
    marker.value.closePopup()
  }
}

const openTooltip = () => {
  if (marker.value) {
    marker.value.openTooltip()
  }
}

const closeTooltip = () => {
  if (marker.value) {
    marker.value.closeTooltip()
  }
}

const showMarker = () => {
  if (marker.value && props.map && !props.map.hasLayer(marker.value)) {
    marker.value.addTo(props.map)
  }
}

const hideMarker = () => {
  if (marker.value && props.map && props.map.hasLayer(marker.value)) {
    props.map.removeLayer(marker.value)
  }
}

// Watch for changes
watch(() => props.latLng, (newLatLng) => {
  setLatLng(newLatLng)
}, { deep: true })

watch(() => props.icon, (newIcon) => {
  setIcon(newIcon)
})

watch(() => props.visible, (newVisible) => {
  if (newVisible) {
    showMarker()
  } else {
    hideMarker()
  }
})

watch(() => props.popup, (newPopup) => {
  if (marker.value) {
    if (newPopup) {
      if (typeof newPopup === 'string') {
        marker.value.bindPopup(newPopup)
      } else {
        marker.value.bindPopup(newPopup.content, newPopup.options || {})
      }
    } else {
      marker.value.unbindPopup()
    }
  }
})

// Expose methods and state
defineExpose({
  marker: readonly(marker),
  isReady: readonly(isReady),
  setLatLng,
  setIcon,
  openPopup,
  closePopup,
  openTooltip,
  closeTooltip,
  showMarker,
  hideMarker
})

// Lifecycle
onMounted(() => {
  if (props.map) {
    initializeMarker()
  }
})

onUnmounted(() => {
  if (marker.value) {
    hideMarker()
    marker.value = null
  }
})
</script>