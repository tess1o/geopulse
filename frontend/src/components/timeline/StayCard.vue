<template>
  <Card
    class="timeline-card timeline-card--stay"
    v-bind="longPressBindings"
    @click="handleClick"
    @contextmenu="showContextMenu"
  >
    <template #title>
      <div class="timeline-title-row">
        <p class="timeline-timestamp">
          🕐 {{ formattedTimestamp }}
        </p>
        <div class="timeline-title-actions">
          <TimelineWeatherSummary :samples="weatherSamples" />
          <TimelineNotePreviewTrigger ref="notePreviewTrigger" :notes="matchingNotes" :allow-management="allowNoteCreation" @note-changed="handleNoteSaved" />
          <TimelinePhotoPreviewTrigger
            :photos="matchingPhotos"
            @photo-show-on-map="handlePhotoShowOnMap"
          />
        </div>
      </div>
    </template>

    <template #subtitle>
      <div class="timeline-subtitle">
        🏠 Stayed at
        <span class="location-name">
          {{ stayItem.locationName }}
        </span>
        <span v-if="isManualStay" class="manual-gap-indicator">(Manual)</span>
        <button
          v-if="canRenameStay"
          class="location-edit-icon-btn"
          aria-label="Rename stay place"
          :title="readOnly ? 'Rename is disabled in demo mode' : 'Rename stay place'"
          :disabled="readOnly"
          @click.stop="handleRenameStay"
        >
          <i class="pi pi-pencil"></i>
        </button>
        <button
          v-if="isManualStay"
          class="location-reset-icon-btn"
          :aria-label="resetManualStayLabel"
          :title="readOnly ? 'Reset is disabled in demo mode' : resetManualStayLabel"
          :disabled="readOnly"
          @click.stop="handleResetManualStay"
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

  <ContextMenu ref="contextMenu" :model="contextMenuItems" :base-z-index="1200" />
  <NoteEditorDialog
    v-model:visible="noteEditorVisible"
    anchor-type="STAY"
    :anchor-id="stayItem.id"
    :event-time="stayItem.timestamp"
    :latitude="stayItem.latitude"
    :longitude="stayItem.longitude"
    :memos-configured="notesStore.isMemosConfigured"
    :default-destination="notesStore.defaultSaveDestination"
    :default-visibility="notesStore.defaultVisibility"
    @saved="handleNoteSaved"
  />
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatDuration } from '@/utils/calculationsHelpers'
import { useTimezone } from '@/composables/useTimezone'
import { useTimelineCardPhotoMatching } from '@/composables/useTimelineCardPhotoMatching'
import { useTimelineCardNoteMatching } from '@/composables/useTimelineCardNoteMatching'
import { useLongPressContextMenu } from '@/composables/useLongPressContextMenu'
import { useExclusiveContextMenu } from '@/composables/useExclusiveContextMenu'
import { useTimelineGpsDrilldown } from '@/composables/useTimelineGpsDrilldown'
import { useNotesStore } from '@/stores/notes'
import { getStayPlaceDetailsRoute } from '@/maps/shared/timelinePlaceRoute'
import TimelinePhotoPreviewTrigger from './TimelinePhotoPreviewTrigger.vue'
import TimelineNotePreviewTrigger from './TimelineNotePreviewTrigger.vue'
import NoteEditorDialog from './NoteEditorDialog.vue'
import TimelineWeatherSummary from './weather/TimelineWeatherSummary.vue'

const router = useRouter()
const notesStore = useNotesStore()

const props = defineProps({
  stayItem: {
    type: Object,
    required: true
  },
  immichPhotos: {
    type: Array,
    default: () => []
  },
  notes: {
    type: Array,
    default: () => []
  },
  weatherSamples: {
    type: Array,
    default: () => []
  },
  allowNoteCreation: {
    type: Boolean,
    default: true
  },
  readOnly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click', 'export-gpx', 'photo-show-on-map', 'rename-stay', 'reset-data-gap-override', 'reset-trip-split-override', 'note-saved'])

const contextMenu = ref(null)
const notePreviewTrigger = ref(null)
const noteEditorVisible = ref(false)
const { show: showExclusiveContextMenu } = useExclusiveContextMenu(contextMenu, 'timeline-card')

const openContextMenu = (event) => {
  showExclusiveContextMenu(event)
}

const {
  longPressBindings,
  handleContextMenu: showContextMenu,
  shouldSuppressClick
} = useLongPressContextMenu({
  open: openContextMenu
})

const { appendGpsPointsMenuItem } = useTimelineGpsDrilldown(computed(() => props.stayItem))

// Check if stay has city/country info
const hasCity = computed(() => props.stayItem.city && props.stayItem.city.trim().length > 0)
const hasCountry = computed(() => props.stayItem.country && props.stayItem.country.trim().length > 0)

const contextMenuItems = computed(() => {
  const items = []

  if (canViewPlaceDetails.value) {
    items.push({
      label: 'View all visits to this place',
      icon: 'pi pi-map-marker',
      command: () => {
        navigateToPlaceDetails()
      }
    })
  }

  if (canRenameStay.value) {
    items.push({
      label: 'Rename place...',
      icon: 'pi pi-pencil',
      disabled: props.readOnly,
      command: () => {
        handleRenameStay()
      }
    })
  }

  if (canResetDataGapOverride.value) {
    items.push({
      label: 'Reset to automatic data gap',
      icon: 'pi pi-refresh',
      disabled: props.readOnly,
      command: () => {
        handleResetDataGapOverride()
      }
    })
  }

  if (canResetTripSplitOverride.value) {
    items.push({
      label: 'Undo manual trip split',
      icon: 'pi pi-refresh',
      disabled: props.readOnly,
      command: () => {
        handleResetTripSplitOverride()
      }
    })
  }

  if (matchingNotes.value.length > 0) {
    items.push({
      label: getViewNotesLabel(),
      icon: 'pi pi-file-edit',
      command: () => {
        openNotesViewer()
      }
    })
  }

  if (props.allowNoteCreation) {
    items.push({
      label: 'Add note...',
      icon: 'pi pi-file-edit',
      command: () => {
        noteEditorVisible.value = true
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

  appendGpsPointsMenuItem(items)

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

const { matchingNotes } = useTimelineCardNoteMatching({
  itemRef: computed(() => props.stayItem),
  notesRef: computed(() => props.notes),
  durationField: 'stayDuration'
})

const stayPlaceDetailsRoute = computed(() => getStayPlaceDetailsRoute(props.stayItem))

const canViewPlaceDetails = computed(() => Boolean(stayPlaceDetailsRoute.value))

const canRenameStay = computed(() => {
  return canViewPlaceDetails.value
})

const canResetDataGapOverride = computed(() => {
  return Boolean(props.stayItem.dataGapOverrideId)
})

const canResetTripSplitOverride = computed(() => {
  return Boolean(props.stayItem.tripSplitOverrideId)
})

const isManualStay = computed(() => canResetDataGapOverride.value || canResetTripSplitOverride.value)

const resetManualStayLabel = computed(() => (
  canResetTripSplitOverride.value ? 'Undo manual trip split' : 'Reset to automatic data gap'
))

const canManageMatchingNotes = computed(() => {
  return props.allowNoteCreation && matchingNotes.value.some((note) => (
    note?.source === 'GEOPULSE' && note?.editable !== false && note?.id != null
  ))
})

const handleClick = (event) => {
  if (shouldSuppressClick(event)) return
  emit('click', props.stayItem)
}

const handlePhotoShowOnMap = (photo) => {
  emit('photo-show-on-map', photo)
}

const handleNoteSaved = (note) => {
  emit('note-saved', note)
}

const openNotesViewer = () => {
  notePreviewTrigger.value?.openNotes()
}

const getViewNotesLabel = () => {
  if (canManageMatchingNotes.value) {
    return matchingNotes.value.length === 1 ? 'Manage note...' : `Manage notes (${matchingNotes.value.length})...`
  }
  return matchingNotes.value.length === 1 ? 'View note...' : `View notes (${matchingNotes.value.length})...`
}

const handleRenameStay = () => {
  if (!canRenameStay.value || props.readOnly) return
  emit('rename-stay', props.stayItem)
}

const handleResetDataGapOverride = () => {
  if (!canResetDataGapOverride.value || props.readOnly) return
  emit('reset-data-gap-override', props.stayItem)
}

const handleResetTripSplitOverride = () => {
  if (!canResetTripSplitOverride.value || props.readOnly) return
  emit('reset-trip-split-override', props.stayItem)
}

const handleResetManualStay = () => {
  if (canResetTripSplitOverride.value) {
    handleResetTripSplitOverride()
    return
  }
  handleResetDataGapOverride()
}

const navigateToPlaceDetails = () => {
  if (stayPlaceDetailsRoute.value) {
    router.push(stayPlaceDetailsRoute.value)
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

@media (hover: none) and (pointer: coarse) {
  .timeline-card {
    -webkit-touch-callout: none;
    -webkit-user-select: none;
    user-select: none;
    touch-action: pan-y;
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
  flex: 1 1 auto;
  min-width: 0;
}

.timeline-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
  flex-wrap: wrap;
}

.timeline-title-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
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

.location-edit-icon-btn:disabled,
.location-reset-icon-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
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
