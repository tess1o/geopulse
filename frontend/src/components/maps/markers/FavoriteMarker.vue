<template>
  <BaseMarker
    :map="map"
    :lat-lng="[favorite.latitude, favorite.longitude]"
    :icon="markerIcon"
    :options="markerOptions"
    :visible="visible"
    :popup="popupConfig"
    @marker-click="handleMarkerClick"
    @marker-hover="handleMarkerHover"
    @marker-contextmenu="handleMarkerContextMenu"
  />
</template>

<script setup>
import { computed } from 'vue'
import BaseMarker from './BaseMarker.vue'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()
import { createCustomDivIcon, MARKER_COLORS, MARKER_SIZES } from '@/utils/mapHelpers'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  favorite: {
    type: Object,
    required: true
  },
  visible: {
    type: Boolean,
    default: true
  },
  showPopup: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['marker-click', 'marker-hover', 'marker-contextmenu', 'edit-favorite', 'delete-favorite'])

// Computed
const markerIcon = computed(() => {
  const iconName = props.favorite.icon || 'star'
  const iconClass = iconName.startsWith('fa-') ? iconName : `fas fa-${iconName}`
  
  return createCustomDivIcon({
    color: props.favorite.color || MARKER_COLORS.FAVORITE,
    icon: iconClass,
    size: MARKER_SIZES.HIGHLIGHT,
    className: 'custom-marker favorite-marker',
    shape: 'pin',
    customStyle: {
      background: `linear-gradient(135deg, ${props.favorite.color || MARKER_COLORS.FAVORITE}, #AD1457)`,
      border: '3px solid white',
      boxShadow: '0 0 15px rgba(233, 30, 99, 0.6)'
    }
  })
})

const markerOptions = computed(() => ({
  favorite: props.favorite,
  alt: `${props.favorite.name || 'Favorite place'}`
}))

const popupConfig = computed(() => {
  if (!props.showPopup) return null

  let content = '<div class="favorite-marker-popup">'
  
  // Header with icon and name
  content += '<div class="popup-header">'
  const iconName = props.favorite.icon || 'star'
  const iconClass = iconName.startsWith('fa-') ? iconName : `fas fa-${iconName}`
  content += `<div class="popup-icon" style="background: ${props.favorite.color || MARKER_COLORS.FAVORITE}">`
  content += `<i class="${iconClass}"></i>`
  content += '</div>'
  content += '<div class="popup-info">'
  content += `<div class="popup-name">${props.favorite.name || 'Favorite Place'}</div>`
  if (props.favorite.category) {
    content += `<div class="popup-category">${props.favorite.category}</div>`
  }
  content += '</div>'
  content += '</div>'
  
  // Description
  if (props.favorite.description) {
    content += `<div class="popup-description">${props.favorite.description}</div>`
  }
  
  // Address
  if (props.favorite.address) {
    content += '<div class="popup-address">'
    content += '<i class="fas fa-map-marker-alt"></i>'
    content += `<span>${props.favorite.address}</span>`
    content += '</div>'
  }
  
  // Tags
  if (props.favorite.tags && props.favorite.tags.length > 0) {
    content += '<div class="popup-tags">'
    content += '<div class="popup-tags-label">Tags:</div>'
    content += '<div class="popup-tags-list">'
    props.favorite.tags.forEach(tag => {
      content += `<span class="popup-tag">${tag}</span>`
    })
    content += '</div>'
    content += '</div>'
  }
  
  // Visit count
  if (props.favorite.visitCount) {
    content += `<div class="popup-visits">Visited ${props.favorite.visitCount} times</div>`
  }
  
  // Created date
  if (props.favorite.createdAt) {
    const dateStr = timezone.formatDate(props.favorite.createdAt)
    content += `<div class="popup-created">Added ${dateStr}</div>`
  }
  
  // Actions
  content += '<div class="popup-actions">'
  content += `<button class="popup-button edit" onclick="window.editFavorite && window.editFavorite('${props.favorite.id}')">Edit</button>`
  content += `<button class="popup-button navigate" onclick="window.navigateToFavorite && window.navigateToFavorite('${props.favorite.id}')">Navigate</button>`
  content += `<button class="popup-button delete" onclick="window.deleteFavorite && window.deleteFavorite('${props.favorite.id}')">Delete</button>`
  content += '</div>'
  
  content += '</div>'
  
  return {
    content,
    options: {
      closeButton: true,
      autoClose: false,
      className: 'favorite-popup'
    }
  }
})

// Event handlers
const handleMarkerClick = (event) => {
  emit('marker-click', {
    favorite: props.favorite,
    event
  })
}

const handleMarkerHover = (event) => {
  emit('marker-hover', {
    favorite: props.favorite,
    event
  })
}

const handleMarkerContextMenu = (event) => {
  emit('marker-contextmenu', {
    favorite: props.favorite,
    event
  })
}
</script>

<style>
/* Favorite marker popup styling */
.favorite-marker-popup {
  font-family: var(--font-family, system-ui);
  font-size: 0.875rem;
  line-height: 1.4;
  max-width: 300px;
}

.popup-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--gp-border-light, #e2e8f0);
}

.popup-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 1.2rem;
  border: 2px solid white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.popup-info {
  flex: 1;
}

.popup-name {
  font-weight: 600;
  color: var(--gp-text-primary, #1e293b);
  font-size: 1rem;
  margin-bottom: 0.25rem;
}

.popup-category {
  color: var(--gp-text-muted, #94a3b8);
  font-size: 0.8rem;
  text-transform: capitalize;
}

.popup-description {
  color: var(--gp-text-secondary, #64748b);
  font-size: 0.85rem;
  margin-bottom: 0.75rem;
  line-height: 1.4;
}

.popup-address {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--gp-text-secondary, #64748b);
  font-size: 0.8rem;
  margin-bottom: 0.5rem;
}

.popup-address i {
  color: var(--gp-primary, #1a56db);
  width: 12px;
}

.popup-tags {
  margin-bottom: 0.5rem;
}

.popup-tags-label {
  color: var(--gp-text-secondary, #64748b);
  font-size: 0.75rem;
  margin-bottom: 0.25rem;
}

.popup-tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.popup-tag {
  background: var(--gp-surface-light, #f8fafc);
  color: var(--gp-text-secondary, #64748b);
  padding: 0.125rem 0.375rem;
  border-radius: var(--gp-radius-small, 4px);
  font-size: 0.7rem;
  border: 1px solid var(--gp-border-light, #e2e8f0);
}

.popup-visits {
  color: var(--gp-success, #10b981);
  font-size: 0.8rem;
  margin-bottom: 0.25rem;
  font-weight: 500;
}

.popup-created {
  color: var(--gp-text-muted, #94a3b8);
  font-size: 0.75rem;
  margin-bottom: 0.75rem;
}

.popup-actions {
  display: flex;
  gap: 0.375rem;
  padding-top: 0.5rem;
  border-top: 1px solid var(--gp-border-light, #e2e8f0);
}

.popup-button {
  flex: 1;
  padding: 0.375rem 0.5rem;
  border: none;
  border-radius: var(--gp-radius-small, 4px);
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.popup-button.edit {
  background: var(--gp-primary, #1a56db);
  color: white;
}

.popup-button.edit:hover {
  background: var(--gp-primary-dark, #1e40af);
}

.popup-button.navigate {
  background: var(--gp-success, #10b981);
  color: white;
}

.popup-button.navigate:hover {
  background: var(--gp-success-dark, #059669);
}

.popup-button.delete {
  background: var(--gp-danger, #ef4444);
  color: white;
}

.popup-button.delete:hover {
  background: var(--gp-danger-dark, #dc2626);
}

/* Dark mode */
.p-dark .favorite-marker-popup {
  background: var(--gp-surface-dark, #1e293b);
}

.p-dark .popup-header {
  border-bottom-color: var(--gp-border-dark, rgba(255, 255, 255, 0.1));
}

.p-dark .popup-name {
  color: var(--gp-text-primary, #f1f5f9);
}

.p-dark .popup-tag {
  background: var(--gp-surface-darker, #0f172a);
  color: var(--gp-text-secondary, #cbd5e1);
  border-color: var(--gp-border-dark, rgba(255, 255, 255, 0.1));
}

.p-dark .popup-actions {
  border-top-color: var(--gp-border-dark, rgba(255, 255, 255, 0.1));
}
</style>