<template>
  <Card
    class="timeline-card timeline-card--overnight-trip"
    @click="handleClick"
    @contextmenu="showContextMenu"
  >
    <template #title>
      <div class="timeline-title-row">
        <p class="timeline-timestamp">
          🕐 {{ getTimestampText() }}
        </p>
        <div class="timeline-title-actions">
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
        <p class="transition-title">
          🔄 Transition to new place
        </p>
      </div>
    </template>

    <template #content>
      <div class="trip-content">
        <p class="trip-detail">
          📈 Total duration: <span class="font-bold">{{ formatDurationSmart(tripItem.tripDuration) }}</span>
        </p>
        <p class="trip-detail">
          ⏱️ On this day:
          <span class="font-bold"> {{ getOnThisDayText() }}</span>
        </p>
        <p v-if="tripItem.distanceMeters" class="trip-detail">
          📏 Distance: <span class="font-bold">{{ formatDistance(tripItem.distanceMeters) }}</span>
        </p>
        <p class="trip-detail">
          🚦 Movement:
          <span class="font-bold">
            {{ getMovementIcon() }}
            {{ formatMovementType(tripItem.movementType) }}
            <span v-if="tripItem.movementTypeSource === 'MANUAL'" class="manual-indicator">(Manual)</span>
            <button
              v-if="showInlineEditIcon"
              class="movement-edit-icon-btn"
              aria-label="Edit movement type"
              title="Edit movement type"
              @click.stop="handleEditMovementType"
            >
              <i class="pi pi-pencil"></i>
            </button>
            <button
              v-if="isUnknownAuto"
              class="movement-set-btn"
              @click.stop="handleEditMovementType"
            >
              Set movement type
            </button>
          </span>
        </p>
        <p v-if="tripItem.movementType === 'UNKNOWN'" class="trip-hint">
          Algorithm did not recognize this trip.
        </p>
      </div>
    </template>
  </Card>

  <ContextMenu ref="contextMenu" :model="contextMenuItems" />
  <NoteEditorDialog
    v-model:visible="noteEditorVisible"
    anchor-type="TRIP"
    :anchor-id="tripItem.id"
    :event-time="tripItem.timestamp"
    :latitude="tripItem.latitude"
    :longitude="tripItem.longitude"
    :memos-configured="notesStore.isMemosConfigured"
    :default-destination="notesStore.defaultSaveDestination"
    :default-visibility="notesStore.defaultVisibility"
    @saved="handleNoteSaved"
  />
</template>

<script setup>
import { ref, computed } from 'vue'
import { useTimezone } from '@/composables/useTimezone';
import { formatDurationSmart, formatDistance } from '@/utils/calculationsHelpers';
import { useTimelineCardPhotoMatching } from '@/composables/useTimelineCardPhotoMatching'
import { useTimelineCardNoteMatching } from '@/composables/useTimelineCardNoteMatching'
import { useNotesStore } from '@/stores/notes'
import TimelinePhotoPreviewTrigger from './TimelinePhotoPreviewTrigger.vue'
import TimelineNotePreviewTrigger from './TimelineNotePreviewTrigger.vue'
import NoteEditorDialog from './NoteEditorDialog.vue'

const notesStore = useNotesStore()

const props = defineProps({
  tripItem: {
    type: Object,
    required: true
  },
  currentDate: {
    type: String,
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
  allowNoteCreation: {
    type: Boolean,
    default: true
  }
});

const emit = defineEmits(['click', 'export-gpx', 'show-classification', 'edit-movement-type', 'photo-show-on-map', 'note-saved']);

const contextMenu = ref(null)
const notePreviewTrigger = ref(null)
const noteEditorVisible = ref(false)
const contextMenuItems = computed(() => {
  const items = [
    {
      label: 'Change movement type...',
      icon: 'pi pi-pencil',
      command: () => {
        emit('edit-movement-type', props.tripItem)
      }
    },
    {
      label: 'Why this classification?',
      icon: 'pi pi-question-circle',
      command: () => {
        emit('show-classification', props.tripItem)
      }
    }
  ]

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

  items.push({
    label: 'Export as GPX',
    icon: 'pi pi-download',
    command: () => {
      emit('export-gpx', props.tripItem)
    }
  })

  return items
})

const timezone = useTimezone();

const { matchingPhotos } = useTimelineCardPhotoMatching({
  itemRef: computed(() => props.tripItem),
  immichPhotosRef: computed(() => props.immichPhotos),
  durationField: 'tripDuration',
  currentDateRef: computed(() => props.currentDate),
  clampToCurrentDay: true
})

const { matchingNotes } = useTimelineCardNoteMatching({
  itemRef: computed(() => props.tripItem),
  notesRef: computed(() => props.notes),
  durationField: 'tripDuration',
  currentDateRef: computed(() => props.currentDate),
  clampToCurrentDay: true
})

// Methods
const getTimestampText = () => {
  return timezone.getOvernightTimestampText(props.tripItem, props.currentDate)
}

const getMovementIcon = () => {
  switch (props.tripItem.movementType) {
    case 'WALK': return '🚶'
    case 'BICYCLE': return '🚴'
    case 'RUNNING': return '🏃'
    case 'CAR': return '🚗'
    case 'MOTORCYCLE': return '🏍️'
    case 'TRAIN': return '🚊'
    case 'FLIGHT': return '✈️'
    case 'BOAT': return '⛵'
    case 'UNKNOWN': return '❓'
    default: return '❓'
  }
}

const getOnThisDayText = () => {
  return timezone.getOvernightOnThisDayText(props.tripItem, props.currentDate)
}

const formatMovementType = (type) => {
  const movementTypeMap = {
    WALK: 'Walk',
    BICYCLE: 'Bicycle',
    RUNNING: 'Running',
    CAR: 'Car',
    MOTORCYCLE: 'Motorcycle',
    TRAIN: 'Train',
    FLIGHT: 'Flight',
    BOAT: 'Boat',
    UNKNOWN: 'Unknown'
  }
  return movementTypeMap[type] || type
}

const movementTypeSource = computed(() => props.tripItem.movementTypeSource || 'AUTO')
const movementType = computed(() => props.tripItem.movementType || 'UNKNOWN')
const isUnknownAuto = computed(() => movementType.value === 'UNKNOWN' && movementTypeSource.value === 'AUTO')
const showInlineEditIcon = computed(() => !isUnknownAuto.value)
const canManageMatchingNotes = computed(() => {
  return props.allowNoteCreation && matchingNotes.value.some((note) => (
    note?.source === 'GEOPULSE' && note?.editable !== false && note?.id != null
  ))
})

const handleClick = () => {
  emit('click', props.tripItem);
};

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

const handleEditMovementType = () => {
  emit('edit-movement-type', props.tripItem)
}

const showContextMenu = (event) => {
  event.preventDefault()
  contextMenu.value.show(event)
}
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
  
  .trip-content {
    margin-top: var(--gp-spacing-xs);
  }
  
  .trip-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }
  
  .transition-title {
    font-size: 0.875rem;
  }
}

.timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.timeline-card--overnight-trip {
  background-color: var(--gp-timeline-green-light);
  border-left: 4px solid var(--gp-success);
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

.timeline-title-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.timeline-subtitle {
  margin: var(--gp-spacing-xs) 0 0 0;
  color: var(--gp-text-primary);
}

.transition-title {
  color: var(--gp-primary);
  font-weight: 700;
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.3;
}

.trip-content {
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-primary);
}

.trip-detail {
  margin: var(--gp-spacing-xs) 0;
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.3;
}

.trip-detail .font-bold {
  font-weight: 700;
  color: var(--gp-primary);
}

.manual-indicator {
  margin-left: 6px;
  font-size: 0.75rem;
  color: var(--gp-warning);
  font-weight: 700;
}

.movement-set-btn {
  margin-left: 8px;
  border: none;
  background: transparent;
  color: var(--gp-primary);
  font-weight: 700;
  font-size: 0.75rem;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}

.movement-edit-icon-btn {
  margin-left: 8px;
  border: none;
  background: transparent;
  color: var(--gp-primary);
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.movement-edit-icon-btn i {
  font-size: 0.85rem;
}

.trip-hint {
  margin: 4px 0 0 0;
  color: var(--gp-warning);
  font-size: 0.78rem;
  font-weight: 600;
}

/* Dark mode adjustments */
.p-dark .timeline-card {
  border-color: var(--gp-border-medium);
}

.p-dark .timeline-card--overnight-trip {
  background-color: var(--gp-timeline-green);
  border-left: 4px solid var(--gp-success);
}

.p-dark .timeline-timestamp,
.p-dark .transition-title {
  color: var(--gp-primary);
}

.p-dark .trip-detail .font-bold {
  color: var(--gp-primary);
}

.p-dark .timeline-subtitle,
.p-dark .trip-content,
.p-dark .trip-detail {
  color: var(--gp-text-primary);
}

.p-dark .timeline-card:hover {
  box-shadow: var(--gp-shadow-medium);
}
</style>
