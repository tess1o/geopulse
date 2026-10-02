<template>
  <Card
    class="timeline-card timeline-card--overnight-stay"
    v-bind="longPressBindings"
    @click="handleClick"
    @contextmenu="showContextMenu"
  >
    <template #title>
      <div class="timeline-title-row">
        <p class="timeline-timestamp">
          🕐 {{ getTimestampText() }}
        </p>
        <div class="timeline-title-actions">
          <TimelineWeatherSummary :samples="weatherSamples" />
          <TimelineNotePreviewTrigger ref="notePreviewTrigger" :notes="matchingNotes" :allow-management="allowNoteCreation" @note-changed="handleNoteSaved" />
          <TimelinePhotoPreviewTrigger
            :photos="matchingPhotos"
            :auth-token="immichPhotoAuthToken"
            @photo-show-on-map="handlePhotoShowOnMap"
          />
        </div>
      </div>
    </template>

    <template #subtitle>
      <div class="timeline-subtitle">
        🏠 {{ t('timeline.stay.stayedAt') }}
        <span class="location-name">{{ stayItem.locationName }}</span>
        <span v-if="isManualStay" class="manual-gap-indicator">{{ t('timeline.stay.manualIndicator') }}</span>
        <button
          v-if="canRenameStay"
          class="location-edit-icon-btn"
          :aria-label="t('timeline.card.renameStayAria')"
          :title="readOnly ? t('timeline.card.renameDisabledDemo') : t('timeline.card.renameStayAria')"
          :disabled="readOnly"
          @click.stop="handleRenameStay"
        >
          <i class="pi pi-pencil"></i>
        </button>
        <button
          v-if="isManualStay"
          class="location-reset-icon-btn"
          :aria-label="resetManualStayLabel"
          :title="readOnly ? t('timeline.card.resetDisabledDemo') : resetManualStayLabel"
          :disabled="readOnly"
          @click.stop="handleResetManualStay"
        >
          <i class="pi pi-refresh"></i>
        </button>
      </div>
    </template>

    <template #content>
      <div class="overnight-stay-content">
        <p class="duration-detail">
          📈 {{ t('timeline.dataGap.totalDuration') }} <span class="duration-value">{{ formatDurationSmart(stayItem.stayDuration) }}</span>
        </p>
        <p class="duration-detail">
          ⏱️ {{ t('timeline.dataGap.onThisDay') }}
          <span class="duration-value"> {{ getOnThisDayText() }}</span>
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
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useTimezone } from '@/composables/useTimezone'
import { formatDurationSmart } from '@/utils/calculationsHelpers'
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

const { t } = useI18n()
const timezone = useTimezone()
const router = useRouter()
const notesStore = useNotesStore()

// Props
const props = defineProps({
  stayItem: {
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
  immichPhotoAuthToken: {
    type: String,
    default: null
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

// Emits
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
      label: t('timeline.card.viewAllVisits'),
      icon: 'pi pi-map-marker',
      command: () => {
        navigateToPlaceDetails()
      }
    })
  }

  if (canRenameStay.value) {
    items.push({
      label: t('timeline.card.renamePlace'),
      icon: 'pi pi-pencil',
      disabled: props.readOnly,
      command: () => {
        handleRenameStay()
      }
    })
  }

  if (canResetDataGapOverride.value) {
    items.push({
      label: t('timeline.card.resetDataGap'),
      icon: 'pi pi-refresh',
      disabled: props.readOnly,
      command: () => {
        handleResetDataGapOverride()
      }
    })
  }

  if (canResetTripSplitOverride.value) {
    items.push({
      label: t('timeline.card.undoTripSplit'),
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
      label: t('timeline.card.addNote'),
      icon: 'pi pi-file-edit',
      command: () => {
        noteEditorVisible.value = true
      }
    })
  }

  // Add city details option if available
  if (hasCity.value) {
    items.push({
      label: t('timeline.card.viewCityDetails', { city: props.stayItem.city }),
      icon: 'pi pi-building',
      command: () => {
        navigateToCityDetails()
      }
    })
  }

  // Add country details option if available
  if (hasCountry.value) {
    items.push({
      label: t('timeline.card.viewCountryDetails', { country: props.stayItem.country }),
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
      label: t('timeline.card.exportGpx'),
      icon: 'pi pi-download',
      command: () => {
        emit('export-gpx', props.stayItem)
      }
    }
  )

  return items
})

const { matchingPhotos } = useTimelineCardPhotoMatching({
  itemRef: computed(() => props.stayItem),
  immichPhotosRef: computed(() => props.immichPhotos),
  durationField: 'stayDuration',
  currentDateRef: computed(() => props.currentDate),
  clampToCurrentDay: true
})

const { matchingNotes } = useTimelineCardNoteMatching({
  itemRef: computed(() => props.stayItem),
  notesRef: computed(() => props.notes),
  durationField: 'stayDuration',
  currentDateRef: computed(() => props.currentDate),
  clampToCurrentDay: true
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
  canResetTripSplitOverride.value ? t('timeline.card.undoTripSplit') : t('timeline.card.resetDataGap')
))

const canManageMatchingNotes = computed(() => {
  return props.allowNoteCreation && matchingNotes.value.some((note) => (
    note?.source === 'GEOPULSE' && note?.editable !== false && note?.id != null
  ))
})

// Methods
const getTimestampText = () => {
  return timezone.getOvernightTimestampText(props.stayItem, props.currentDate)
}

const getOnThisDayText = () => {
  return timezone.getOvernightOnThisDayText(props.stayItem, props.currentDate)
}

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
    return matchingNotes.value.length === 1
      ? t('timeline.card.manageNoteSingle')
      : t('timeline.card.manageNotesMultiple', { count: matchingNotes.value.length })
  }
  return matchingNotes.value.length === 1
    ? t('timeline.card.viewNoteSingle')
    : t('timeline.card.viewNotesMultiple', { count: matchingNotes.value.length })
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
</script>

<style scoped src="./timeline-card.css"></style>

<style scoped>
/* Mobile optimizations */
@media (max-width: 768px) {
  .overnight-stay-content {
    margin-top: var(--gp-spacing-xs);
  }

  .duration-detail,
  .span-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }

  .span-detail {
    color: var(--gp-text-secondary, #64748b);
    font-style: italic;
  }
}

.timeline-card--overnight-stay {
  background-color: var(--gp-timeline-card-overnight);
  border-left: 4px solid var(--gp-primary-text);
}

.location-name {
  color: var(--gp-primary-text);
  font-weight: 700;
}

.location-edit-icon-btn {
  margin-left: 8px;
  border: none;
  background: transparent;
  color: var(--gp-primary-text);
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
  color: var(--gp-primary-text);
}
</style>
