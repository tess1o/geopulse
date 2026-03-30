<template>
  <Card
    class="timeline-card timeline-card--stay"
    @click="handleClick"
    @contextmenu="showContextMenu"
  >
    <template #title>
      <div class="timeline-title-row">
        <p class="timeline-timestamp">
          🕐 {{ formattedTimestamp }}
        </p>
        <TimelinePhotoPreviewTrigger
          :photos="matchingPhotos"
          @photo-show-on-map="handlePhotoShowOnMap"
        />
      </div>
    </template>

    <template #subtitle>
      <div class="timeline-subtitle">
        🏠 Stayed at
        <span class="location-name">
          {{ stayItem.locationName }}
        </span>
        <span v-if="canResetDataGapOverride" class="manual-gap-indicator">(Manual)</span>
        <button
          v-if="canRenameStay"
          class="location-edit-icon-btn"
          aria-label="Rename stay place"
          title="Rename stay place"
          @click.stop="handleRenameStay"
        >
          <i class="pi pi-pencil"></i>
        </button>
        <button
          v-if="canResetDataGapOverride"
          class="location-reset-icon-btn"
          aria-label="Reset data gap override"
          title="Reset to automatic data gap"
          @click.stop="handleResetDataGapOverride"
        >
          <i class="pi pi-refresh"></i>
        </button>
      </div>
    </template>

    <template #content>
      <div class="stay-content" v-if="!isOvernight">
        <span>For </span>
        <span class="duration-text">
          {{ formatDuration(stayItem.stayDuration) }}
        </span>
      </div>
      <div class="overnight-stay-content" v-else>
        <p class="duration-detail">
          📈 Total duration:
          <span class="duration-value">{{ formatDuration(stayItem.stayDuration) }}</span>
        </p>
        <p class="duration-detail">
          ⏱️ On this day:
          <span class="duration-value">{{ formatOnThisDayDuration(stayItem) }}</span>
        </p>
      </div>
    </template>
  </Card>

  <ContextMenu ref="contextMenu" :model="contextMenuItems" />
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatDuration } from '@/utils/calculationsHelpers'
import { useTimezone } from '@/composables/useTimezone'
import { useTimelineCardPhotoMatching } from '@/composables/useTimelineCardPhotoMatching'
import TimelinePhotoPreviewTrigger from './TimelinePhotoPreviewTrigger.vue'

const router = useRouter()

const props = defineProps({
  stayItem: {
    type: Object,
    required: true
  },
  immichPhotos: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['click', 'export-gpx', 'photo-show-on-map', 'rename-stay', 'reset-data-gap-override'])

const contextMenu = ref(null)

// Check if stay has city/country info
const hasCity = computed(() => props.stayItem.city && props.stayItem.city.trim().length > 0)
const hasCountry = computed(() => props.stayItem.country && props.stayItem.country.trim().length > 0)

const contextMenuItems = computed(() => {
  const items = [
    {
      label: 'View all visits to this place',
      icon: 'pi pi-map-marker',
      command: () => {
        navigateToPlaceDetails()
      }
    }
  ]

  if (canRenameStay.value) {
    items.push({
      label: 'Rename place...',
      icon: 'pi pi-pencil',
      command: () => {
        handleRenameStay()
      }
    })
  }

  if (canResetDataGapOverride.value) {
    items.push({
      label: 'Reset to automatic data gap',
      icon: 'pi pi-refresh',
      command: () => {
        handleResetDataGapOverride()
      }
    })
  }

  // Add city details option if available
  if (hasCity.value) {
    items.push({
      label: `View ${props.stayItem.city} Details`,
      icon: 'pi pi-building',
      command: () => {
        navigateToCityDetails()
      }
    })
  }

  // Add country details option if available
  if (hasCountry.value) {
    items.push({
      label: `View ${props.stayItem.country} Details`,
      icon: 'pi pi-globe',
      command: () => {
        navigateToCountryDetails()
      }
    })
  }

  items.push(
    {
      separator: true
    },
    {
      label: 'Export as GPX',
      icon: 'pi pi-download',
      command: () => {
        emit('export-gpx', props.stayItem)
      }
    }
  )

  return items
})

const timezone = useTimezone()

const { matchingPhotos } = useTimelineCardPhotoMatching({
  itemRef: computed(() => props.stayItem),
  immichPhotosRef: computed(() => props.immichPhotos),
  durationField: 'stayDuration'
})

const canRenameStay = computed(() => {
  return Boolean(props.stayItem.favoriteId || props.stayItem.geocodingId)
})

const canResetDataGapOverride = computed(() => {
  return Boolean(props.stayItem.dataGapOverrideId)
})

const handleClick = () => {
  emit('click', props.stayItem)
}

const handlePhotoShowOnMap = (photo) => {
  emit('photo-show-on-map', photo)
}

const handleRenameStay = () => {
  if (!canRenameStay.value) return
  emit('rename-stay', props.stayItem)
}

const handleResetDataGapOverride = () => {
  if (!canResetDataGapOverride.value) return
  emit('reset-data-gap-override', props.stayItem)
}

const showContextMenu = (event) => {
  event.preventDefault()
  contextMenu.value.show(event)
}

const navigateToPlaceDetails = () => {
  // Determine type (favorite or geocoding) and id
  const type = props.stayItem.favoriteId ? 'favorite' : 'geocoding'
  const id = props.stayItem.favoriteId || props.stayItem.geocodingId

  if (id) {
    router.push({
      name: 'Place Details',
      params: { type, id }
    })
  }
}

const navigateToCityDetails = () => {
  if (props.stayItem.city) {
    router.push({
      path: `/app/location-analytics/city/${encodeURIComponent(props.stayItem.city)}`
    })
  }
}

const navigateToCountryDetails = () => {
  if (props.stayItem.country) {
    router.push({
      path: `/app/location-analytics/country/${encodeURIComponent(props.stayItem.country)}`
    })
  }
}

const isOvernight = computed(() => {
  if (!props.stayItem.timestamp || !props.stayItem.stayDuration) return false;
  return timezone.isOvernightWithDuration(props.stayItem.timestamp, props.stayItem.stayDuration);
});

const formatOnThisDayDuration = (stayItem) => {
  const dateStr = stayItem.timestamp.substring(0, 10);
  return timezone.formatOnThisDayDuration(stayItem, dateStr, 'stay');
}

const formattedTimestamp = computed(() => {
  if (!props.stayItem.timestamp) return '';
  return `${timezone.formatDateDisplay(props.stayItem.timestamp)} ${timezone.formatTime(props.stayItem.timestamp)}`
})
</script>

<style scoped>
.timeline-card {
  margin-top: var(--gp-spacing-md);
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
  overflow: hidden;
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
}

/* Mobile optimizations */
@media (max-width: 768px) {
  .timeline-card {
    margin-top: var(--gp-spacing-sm);
    padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
  }
  
  .timeline-timestamp {
    font-size: 0.875rem;
  }
  
  .timeline-subtitle {
    margin: var(--gp-spacing-xs) 0 0 0;
    font-size: 0.875rem;
  }
  
  .stay-content {
    margin-top: var(--gp-spacing-xs);
    font-size: 0.875rem;
  }
  
  .overnight-stay-content {
    margin-top: var(--gp-spacing-xs);
  }
  
  .duration-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }
}

.timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.timeline-card--stay {
  background-color: var(--gp-timeline-blue-light);
  border-left: 4px solid var(--gp-primary);
}

.timeline-timestamp {
  color: var(--gp-primary);
  font-weight: 600;
  font-size: 0.95rem;
  margin: 0;
  line-height: 1.2;
}

.timeline-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
}

.timeline-subtitle {
  margin: var(--gp-spacing-xs) 0 0 0;
  color: var(--gp-text-primary);
  font-size: 0.9rem;
  line-height: 1.3;
}

.location-name {
  color: var(--gp-primary);
  font-weight: 700;
}

.location-edit-icon-btn {
  margin-left: 8px;
  border: none;
  background: transparent;
  color: var(--gp-primary);
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.location-edit-icon-btn i {
  font-size: 0.85rem;
}

.location-reset-icon-btn {
  margin-left: 8px;
  border: none;
  background: transparent;
  color: var(--gp-warning);
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.location-reset-icon-btn i {
  font-size: 0.85rem;
}

.manual-gap-indicator {
  margin-left: 6px;
  font-size: 0.75rem;
  color: var(--gp-warning);
  font-weight: 700;
}

.stay-content {
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-primary);
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  font-size: 0.9rem;
  line-height: 1.3;
}

.duration-text {
  color: var(--gp-primary);
  font-weight: 700;
}

.overnight-stay-content {
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-primary);
}

.duration-detail {
  margin: var(--gp-spacing-xs) 0;
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.3;
}

.duration-detail .duration-value {
  font-weight: 700;
  color: var(--gp-primary);
}

/* Dark mode adjustments */
.p-dark .timeline-card {
  border-color: var(--gp-border-medium);
}

.p-dark .timeline-card--stay {
  background-color: var(--gp-timeline-blue);
  border-left: 4px solid var(--gp-primary);
}

.p-dark .timeline-timestamp {
  color: var(--gp-primary);
}

.p-dark .location-name,
.p-dark .duration-text {
  color: var(--gp-primary);
}

.p-dark .timeline-subtitle,
.p-dark .stay-content {
  color: var(--gp-text-primary);
}

.p-dark .timeline-card:hover {
  box-shadow: var(--gp-shadow-medium);
}

.p-dark .overnight-stay-content,
.p-dark .duration-detail {
  color: var(--gp-text-primary);
}

.p-dark .duration-detail .duration-value {
  color: var(--gp-primary);
}
</style>
