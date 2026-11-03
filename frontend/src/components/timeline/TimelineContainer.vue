<template>
  <div ref="timelineContainerRef" class="timeline-container">
    <div class="timeline-header">
      <div class="timeline-header-top">
        <div class="timeline-title">{{ t('timeline.splitLayout.defaultExpandedLabel') }}</div>
        <Button v-if="!effectiveReadOnly && historyEdits.length" class="timeline-history-button" text
          :aria-label="t('timeline.edit.history')" :title="t('timeline.edit.history')" @click="openEditHistory(null)">
          <i class="pi pi-history" />
          <span class="timeline-history-count">{{ historyEdits.length }}</span>
        </Button>
      </div>
      <div
        v-if="isSingleDaySelected && selectedDateLabel"
        class="timeline-header-date-nav"
        :aria-label="t('timeline.splitLayout.dayNavigationAriaLabel')"
      >
        <button
          type="button"
          class="date-nav-button"
          :title="t('timeline.splitLayout.previousDay')"
          :aria-label="t('timeline.splitLayout.previousDay')"
          @click="navigateDay(-1)"
        >
          <i class="pi pi-chevron-left"></i>
        </button>
        <div class="timeline-header-date-text">{{ selectedDateLabel }}</div>
        <button
          type="button"
          class="date-nav-button"
          :title="t('timeline.splitLayout.nextDay')"
          :aria-label="t('timeline.splitLayout.nextDay')"
          @click="navigateDay(1)"
        >
          <i class="pi pi-chevron-right"></i>
        </button>
      </div>
    </div>

    <div v-if="timelineDataLoading" class="loading-messages">
      <ProgressSpinner />
    </div>

    <div v-show="timelineNoData && !timelineDataLoading && !onlyHiddenGaps" class="loading-messages timeline-no-data">
      <div>{{ t('timeline.container.noData') }}</div>
    </div>

    <div v-if="!timelineDataLoading && onlyHiddenGaps" class="loading-messages timeline-no-data">
      <div>{{ t('timeline.container.unknownActivityHidden') }}</div>
      <Button
        as="router-link"
        to="/app/profile?tab=timeline&setting=showUnknownActivity"
        :label="t('timeline.container.displaySettings')"
        icon="pi pi-cog"
        size="small"
      />
    </div>

    <!-- Warning for large datasets -->
    <div v-if="!timelineNoData && !timelineDataLoading && displayedTimelineData.length > displayLimit" class="timeline-warning">
      <i class="pi pi-info-circle"></i>
      <span>{{ t('timeline.container.showingItems', { shown: displayLimit, total: displayedTimelineData.length }) }}</span>
      <Button
        :label="t('timeline.container.loadMore')"
        icon="pi pi-plus"
        @click="loadMore"
        size="small"
        class="load-more-button"
      />
      <router-link to="/app/timeline-reports" class="reports-link">{{ t('timeline.container.viewAllInReports') }}</router-link>
    </div>

    <div v-show="!timelineNoData && !timelineDataLoading" class="timeline-content">
      <div v-for="dateGroup in groupedTimelineData" :key="dateGroup.date" class="date-group">
        <!-- Date Header Separator -->
        <div v-if="!isSingleDaySelected" class="date-separator">
          <div class="date-separator-line"></div>
          <!-- Toggles this day on its own on the map; the other days fade there. -->
          <button
            type="button"
            class="date-separator-text"
            :class="{ 'date-separator-text--focused': highlightStore.focusedDay === dateGroup.date }"
            :aria-pressed="highlightStore.focusedDay === dateGroup.date"
            :title="highlightStore.focusedDay === dateGroup.date
              ? t('timeline.container.showAllDaysOnMap')
              : t('timeline.container.showOnlyDayOnMap')"
            @click="toggleFocusedDay(dateGroup.date)"
          >
            <span
              v-if="dayColors?.[dateGroup.date]"
              class="date-separator-day-color"
              :style="{ backgroundColor: dayColors[dateGroup.date] }"
              aria-hidden="true"
            ></span>
            {{ dateGroup.dateLabel }}
          </button>
          <template v-if="showTimelineLabels">
            <span
              v-for="tag in getTimelineLabelsForDate(dateGroup.date)"
              :key="tag.id"
              class="gp-period-badge gp-period-badge--clickable"
              :style="{ backgroundColor: tag.color }"
              @click.stop="handleTagClick(tag)"
              role="button"
              :aria-label="t('timeline.container.viewPeriodAria', { name: tag.name })"
              tabindex="0"
              @keydown.enter="handleTagClick(tag)"
              @keydown.space.prevent="handleTagClick(tag)"
            >
              {{ tag.name }}
            </span>
          </template>
          <div class="date-separator-line"></div>
        </div>

        <!-- Timeline for this date -->
        <Timeline
          :value="dateGroup.items"
          align="left"
          class="custom-timeline date-timeline"
        >
          <template #marker="slotProps">
            <span class="timeline-marker" :class="getMarkerClassForItem(slotProps.item, dateGroup.date)">
              <i :class="getMarkerIconForItem(slotProps.item, dateGroup.date)" />
            </span>
          </template>

          <template #content="slotProps">
            <div
              class="timeline-item-content"
              :data-timeline-item-id="slotProps.item.id ?? ''"
              :data-timeline-item-timestamp="slotProps.item.timestamp || slotProps.item.startTime || ''"
            >
            <!-- Stay Cards -->
            <OvernightStayCard
              v-if="slotProps.item.type === 'stay' && isOvernightItem(slotProps.item)"
              :stay-item="slotProps.item"
              :force-zone-label="dateGroup.showZoneLabels"
              :current-date="dateGroup.date"
              :immich-photos="immichPhotosForCards"
              :immich-photo-auth-token="props.photoAuthToken"
              :notes="notesForCards"
              :weather-samples="getWeatherSamplesForItem(slotProps.item)"
              :allow-note-creation="!effectiveReadOnly"
              :read-only="effectiveReadOnly"
              @click="handleTimelineItemClick"
              @export-gpx="handleExportStayAsGpx"
              @photo-show-on-map="handlePhotoShowOnMap"
              @note-saved="handleNoteSaved"
              :allow-explanation="!isPublicView"
              @show-explanation="handleShowExplanation"
              @edit="openEditor"
              @show-edit-history="openEditor($event, true)"
            />

            <StayCard
              v-else-if="slotProps.item.type === 'stay'"
              :stay-item="slotProps.item"
              :force-zone-label="dateGroup.showZoneLabels"
              :immich-photos="immichPhotosForCards"
              :immich-photo-auth-token="props.photoAuthToken"
              :notes="notesForCards"
              :weather-samples="getWeatherSamplesForItem(slotProps.item)"
              :allow-note-creation="!effectiveReadOnly"
              :read-only="effectiveReadOnly"
              @click="handleTimelineItemClick"
              @export-gpx="handleExportStayAsGpx"
              @photo-show-on-map="handlePhotoShowOnMap"
              @note-saved="handleNoteSaved"
              :allow-explanation="!isPublicView"
              @show-explanation="handleShowExplanation"
              @edit="openEditor"
              @show-edit-history="openEditor($event, true)"
            />

            <!-- Trip Cards -->
            <OvernightTripCard
              v-if="slotProps.item.type === 'trip' && isOvernightItem(slotProps.item)"
              :trip-item="slotProps.item"
              :force-zone-label="dateGroup.showZoneLabels"
              :current-date="dateGroup.date"
              :immich-photos="immichPhotosForCards"
              :immich-photo-auth-token="props.photoAuthToken"
              :notes="notesForCards"
              :weather-samples="getWeatherSamplesForItem(slotProps.item)"
              :allow-note-creation="!effectiveReadOnly"
              :read-only="effectiveReadOnly"
              :map-matching-info="mapMatchingByTripId.get(Number(slotProps.item.id)) || null"
              @click="handleTimelineItemClick"
              @export-gpx="handleExportTripAsGpx"
              @show-classification="handleShowClassification"
              @photo-show-on-map="handlePhotoShowOnMap"
              @note-saved="handleNoteSaved"
              :allow-explanation="!isPublicView"
              @show-explanation="handleShowExplanation"
              @show-map-matching-details="handleShowMapMatchingDetails"
              @edit="openEditor"
              @show-edit-history="openEditor($event, true)"
            />

            <TripCard
              v-else-if="slotProps.item.type === 'trip'"
              :trip-item="slotProps.item"
              :force-zone-label="dateGroup.showZoneLabels"
              :next-item="getNextTimelineItem(slotProps.item)"
              :immich-photos="immichPhotosForCards"
              :immich-photo-auth-token="props.photoAuthToken"
              :notes="notesForCards"
              :weather-samples="getWeatherSamplesForItem(slotProps.item)"
              :allow-note-creation="!effectiveReadOnly"
              :read-only="effectiveReadOnly"
              :map-matching-info="mapMatchingByTripId.get(Number(slotProps.item.id)) || null"
              @click="handleTimelineItemClick"
              @export-gpx="handleExportTripAsGpx"
              @show-classification="handleShowClassification"
              @photo-show-on-map="handlePhotoShowOnMap"
              @note-saved="handleNoteSaved"
              :allow-explanation="!isPublicView"
              @show-explanation="handleShowExplanation"
              @show-map-matching-details="handleShowMapMatchingDetails"
              @edit="openEditor"
              @show-edit-history="openEditor($event, true)"
            />

            <!-- Data Gap Cards -->
            <OvernightDataGapCard
              v-if="slotProps.item.type === 'dataGap' && isOvernightItem(slotProps.item)"
              :data-gap-item="slotProps.item"
              :force-zone-label="dateGroup.showZoneLabels"
              :current-date="dateGroup.date"
              :immich-photos="immichPhotosForCards"
              :immich-photo-auth-token="props.photoAuthToken"
              :notes="notesForCards"
              :allow-note-creation="!effectiveReadOnly"
              :read-only="effectiveReadOnly"
              @click="handleTimelineItemClick"
              @photo-show-on-map="handlePhotoShowOnMap"
              @note-saved="handleNoteSaved"
              :allow-explanation="!isPublicView"
              @show-explanation="handleShowExplanation"
              @edit="openEditor"
              @show-edit-history="openEditor($event, true)"
            />

            <DataGapCard
              v-else-if="slotProps.item.type === 'dataGap'"
              :data-gap-item="slotProps.item"
              :force-zone-label="dateGroup.showZoneLabels"
              :immich-photos="immichPhotosForCards"
              :immich-photo-auth-token="props.photoAuthToken"
              :notes="notesForCards"
              :allow-note-creation="!effectiveReadOnly"
              :read-only="effectiveReadOnly"
              @click="handleTimelineItemClick"
              @photo-show-on-map="handlePhotoShowOnMap"
              @note-saved="handleNoteSaved"
              :allow-explanation="!isPublicView"
              @show-explanation="handleShowExplanation"
              @edit="openEditor"
              @show-edit-history="openEditor($event, true)"
            />
            </div>
          </template>
        </Timeline>
      </div>
    </div>

    <!-- Trip Classification Dialog -->
    <TripClassificationDialog
      :visible="classificationDialogVisible"
      :trip="selectedTripForClassification"
      :read-only="effectiveReadOnly"
      :show-explanation-link="!isPublicView"
      @movement-updated="handleMovementTypeUpdated"
      @show-explanation="handleShowExplanationFromClassification"
      @close="handleCloseClassificationDialog"
    />

    <TimelineEventExplanationDialog
      :visible="explanationDialogVisible"
      :kind="selectedItemForExplanation?.type || 'stay'"
      :item="selectedItemForExplanation"
      @show-classification="handleShowClassificationFromExplanation"
      @close="handleCloseExplanationDialog"
    />

    <TripStaySplitDialog
      :visible="tripSplitDialogVisible"
      :trip="selectedTripForSplit"
      :read-only="effectiveReadOnly"
      @split="handleTripSplitSaved"
      @close="handleCloseTripSplitDialog"
    />

    <TripMapMatchingDetailsDialog
      :visible="mapMatchingDetailsVisible"
      :trip="selectedTripForMapMatchingDetails"
      :info="selectedMapMatchingInfo"
      @close="handleCloseMapMatchingDetails"
    />

    <TimelineEditDrawer
      :focus-changes="focusEditChanges"
      :visible="editDrawerVisible"
      :item="selectedItemForEdit"
      :previous-item="getPreviousTimelineItem(selectedItemForEdit)"
      :next-item="getNextTimelineItem(selectedItemForEdit)"
      :merge-previous-item="getMergePreviousTimelineItem(selectedItemForEdit)"
      :merge-next-item="getMergeNextTimelineItem(selectedItemForEdit)"
      @close="closeEditor"
      @rename-place="handleRenameStay"
      @split-trip-with-stay="handleSplitTripFromEditor"
      @submitted="handleEditSubmitted"
    />
    <TimelineEditHistoryDrawer
      :visible="editHistoryVisible"
      :item="selectedItemForHistory"
      :edits="historyEdits"
      :reload="loadEditHistory"
      @close="closeEditHistory"
      @changed="handleEditSubmitted"
    />
  </div>
</template>

<script setup>
import { computed, ref, defineAsyncComponent, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Timeline from 'primevue/timeline'
import ProgressSpinner from 'primevue/progressspinner'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import { useExportImportStore } from '@/stores/exportImport'
import { useTimelineLabelsStore } from '@/stores/timelineLabels'
import { useImmichStore } from '@/stores/immich'
import { useTimelineStore } from '@/stores/timeline'
import { useNotesStore } from '@/stores/notes'
import { useHighlightStore } from '@/stores/highlight'
import StayCard from './StayCard.vue'
import TripCard from './TripCard.vue'
import DataGapCard from './DataGapCard.vue'
import OvernightStayCard from './OvernightStayCard.vue'
import OvernightTripCard from './OvernightTripCard.vue'
import OvernightDataGapCard from './OvernightDataGapCard.vue'
import TimelineEditDrawer from './TimelineEditDrawer.vue'
import TimelineEditHistoryDrawer from './TimelineEditHistoryDrawer.vue'
import { useTimezone } from '@/composables/useTimezone'
import { getTimelineItemIconClass } from '@/utils/timelineIconUtils'
import { getWeatherSamplesForTimelineItem } from '@/utils/weatherDisplay'
import { showDemoModeToast } from '@/utils/demoMode'
import { canMergeTimelineEvents } from '@/utils/timelineEditPlace'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

// Lazy load the classification dialog
const TripClassificationDialog = defineAsyncComponent(() =>
  import('@/components/dialogs/TripClassificationDialog.vue')
)
const TimelineEventExplanationDialog = defineAsyncComponent(() =>
  import('@/components/dialogs/TimelineEventExplanationDialog.vue')
)
const TripStaySplitDialog = defineAsyncComponent(() =>
  import('@/components/dialogs/TripStaySplitDialog.vue')
)
const TripMapMatchingDetailsDialog = defineAsyncComponent(() =>
  import('@/components/dialogs/TripMapMatchingDetailsDialog.vue')
)

const { t } = useI18n()
const toast = useToast()
const exportImportStore = useExportImportStore()
const timelineLabelsStore = useTimelineLabelsStore()
const immichStore = useImmichStore()
const notesStore = useNotesStore()
const timelineStore = useTimelineStore()

// Display limit for progressive loading
const displayLimit = ref(50)

// Classification dialog state
const classificationDialogVisible = ref(false)
const selectedTripForClassification = ref(null)
const explanationDialogVisible = ref(false)
const selectedItemForExplanation = ref(null)
const tripSplitDialogVisible = ref(false)
const selectedTripForSplit = ref(null)
const mapMatchingDetailsVisible = ref(false)
const selectedTripForMapMatchingDetails = ref(null)
const selectedMapMatchingInfo = ref(null)
const timelineContainerRef = ref(null)
const editDrawerVisible = ref(false)
const selectedItemForEdit = ref(null)
const editHistoryVisible = ref(false)
const selectedItemForHistory = ref(null)

// Props
const props = defineProps({
  showUnknownActivity: {
    type: Boolean,
    default: true
  },
  timelineNoData: {
    type: Boolean,
    default: false
  },
  timelineData: {
    type: Array,
    default: () => []
  },
  timelineDataLoading: {
    type: Boolean,
    default: false
  },
  dateRange: {
    type: Array,
    default: () => []
  },
  // UTC range the shown items cover when it differs from dateRange (local days in location time mode)
  dataWindow: {
    type: Array,
    default: null
  },
  // { 'YYYY-MM-DD': color } while the map colors the path by day; shown on the day headers.
  dayColors: {
    type: Object,
    default: null
  },
  loadImmichPhotos: {
    type: Boolean,
    default: true
  },
  loadNotes: {
    type: Boolean,
    default: true
  },
  notes: {
    type: Array,
    default: null
  },
  photos: {
    type: Array,
    default: null
  },
  photoAuthToken: {
    type: String,
    default: null
  },
  weatherSamples: {
    type: Array,
    default: () => []
  },
  isPublicView: {
    type: Boolean,
    default: false
  },
  readOnly: {
    type: Boolean,
    default: false
  },
  showTimelineLabels: {
    type: Boolean,
    default: true
  },
  mapMatchingByTripId: {
    type: Object,
    default: () => new Map()
  }
})

// Emits
const emit = defineEmits([
  'timeline-item-click',
  'tag-clicked',
  'photo-show-on-map',
  'rename-stay',
  'timeline-refresh-requested',
  'reset-data-gap-override',
  'reset-trip-split-override',
  'navigate-date',
  'day-focus-change',
  'timeline-edit-submitted'
])

// A public share is read-only even if a caller forgets to pass read-only explicitly.
const effectiveReadOnly = computed(() => props.readOnly || props.isPublicView)

// Composables
const timezone = useTimezone()
const highlightStore = useHighlightStore()

const toggleFocusedDay = (dateKey) => {
  highlightStore.toggleFocusedDay(dateKey)
  emit('day-focus-change', highlightStore.focusedDay)
}

// Check if an item spans multiple days (overnight)
const isOvernightItem = (item) => {
  return timezone.getTotalDaysSpanned(item) > 1
}

// Marker icons based on item type
const getMarkerIconForItem = computed(() => (item) => (
  getTimelineItemIconClass(item, { isOvernight: isOvernightItem(item) })
))

const getMarkerClass = computed(() => (type) => {
  if (type === 'stay') return 'marker-stay'
  if (type === 'trip') return 'marker-trip'
  if (type === 'dataGap') return 'marker-data-gap'
  return 'marker-default'
})

// Marker classes based on item type  
const getMarkerClassForItem = computed(() => (item, dateKey) => {
  const baseClass = getMarkerClass.value(item.type)
  return isOvernightItem(item) ? `${baseClass} marker-overnight` : baseClass
})

const showReadOnlyToast = () => {
  showDemoModeToast(toast)
}

// Get timeline labels for a specific date
const getTimelineLabelsForDate = (dateString) => {
  if (!props.showTimelineLabels) {
    return []
  }
  return timelineLabelsStore.getTimelineLabelsForDate(dateString)
}

const selectedSingleDayDate = computed(() => {
  if (!props.dateRange || props.dateRange.length !== 2) {
    return null
  }

  const dates = timezone.getDateRangeArray(props.dateRange[0], props.dateRange[1])
  return dates.length === 1 ? dates[0] : null
})

const isSingleDaySelected = computed(() => Boolean(selectedSingleDayDate.value))

const selectedDateLabel = computed(() => (
  selectedSingleDayDate.value ? timezone.formatDateLong(selectedSingleDayDate.value) : ''
))

const displayedTimelineData = computed(() => props.showUnknownActivity
  ? props.timelineData
  : props.timelineData.filter(item => item.type !== 'dataGap'))
const onlyHiddenGaps = computed(() => props.timelineData.length > 0 && displayedTimelineData.value.length === 0)

// Group timeline data by date with proper overnight stay handling
const groupedTimelineData = computed(() => {
  if (!displayedTimelineData.value || displayedTimelineData.value.length === 0) {
    return []
  }

  // For large datasets, progressively load items based on displayLimit
  const itemsToProcess = displayedTimelineData.value.length > displayLimit.value
    ? displayedTimelineData.value.slice(0, displayLimit.value)
    : displayedTimelineData.value

  if (displayedTimelineData.value.length > displayLimit.value) {
    console.log(`Timeline: Showing ${displayLimit.value} of ${displayedTimelineData.value.length} items`)
  }

  const allDates = new Set();
  if (props.dateRange && props.dateRange.length === 2) {
    // Generate date range using timezone composable
    const dateArray = timezone.getDateRangeArray(props.dateRange[0], props.dateRange[1])
    dateArray.forEach(date => allDates.add(date))
  } else {
    itemsToProcess.forEach(item => {
      const itemStartTime = item.timestamp || item.startTime
      // Location time mode dates items by their local days; profile mode by the profile timezone
      const itemDate = timezone.isLocationTimeMode()
        ? timezone.create(timezone.getItemLocalDaySpan(item).startDate)
        : timezone.fromUtc(itemStartTime)
      allDates.add(itemDate.format('YYYY-MM-DD'));
      
      // Add all days this item spans to the date range
      const totalDays = timezone.getTotalDaysSpanned(item);
      if (totalDays > 1) {
        for (let i = 1; i < totalDays; i++) {
          const spanDate = itemDate.add(i, 'day');
          allDates.add(spanDate.format('YYYY-MM-DD'));
        }
      }
    });
  }

  const dateGroups = new Map();
  allDates.forEach(dateKey => {
    dateGroups.set(dateKey, {
      date: dateKey,
      dateLabel: timezone.formatDateLong(dateKey),
      items: []
    });
  });

  itemsToProcess.forEach(item => {
    allDates.forEach(dateKey => {
      if (timezone.shouldItemAppearOnDate(item, dateKey)) {
        dateGroups.get(dateKey)?.items.push(item);
      }
    });
  });

  return Array.from(dateGroups.values())
    .filter(group => group.items.length > 0)
    .map(group => ({
      ...group,
      // Location-time mode: on a day that mixes zones (any item abroad or in another UTC offset), every card
      // shows its zone label so the day reads consistently. Always false in profile mode.
      showZoneLabels: group.items.some(item => timezone.isItemForeign(item))
    }))
    .sort((a, b) => timezone.diff(a.date, b.date, 'day'));
})

const immichPhotosForCards = computed(() => {
  if (Array.isArray(props.photos)) {
    return props.photos
  }

  if (!immichStore.isConfigured) {
    return []
  }

  return Array.isArray(immichStore.photos) ? immichStore.photos : []
})

const notesForCards = computed(() => {
  if (Array.isArray(props.notes)) {
    return props.notes
  }
  return Array.isArray(notesStore.notes) ? notesStore.notes : []
})

const nextItemByTimelineOrder = computed(() => {
  const map = new Map()
  const items = props.timelineData || []

  for (let i = 0; i < items.length - 1; i++) {
    map.set(items[i], items[i + 1])
  }

  return map
})

const previousItemByTimelineOrder = computed(() => {
  const map = new Map()
  const items = props.timelineData || []
  for (let i = 1; i < items.length; i++) map.set(items[i], items[i - 1])
  return map
})

const historyEdits = ref([])
let historyLoadVersion = 0

const getNextTimelineItem = (item) => {
  return nextItemByTimelineOrder.value.get(item) || null
}

const getPreviousTimelineItem = (item) => previousItemByTimelineOrder.value.get(item) || null

const getSameTypeTimelineItem = (item, step) => {
  const items = props.timelineData || []
  const index = items.indexOf(item)
  if (index === -1) return null
  const immediate = items[index + step]
  const candidate = immediate?.type === item?.type ? immediate : items[index + (2 * step)]
  if (candidate?.type === item?.type && canMergeTimelineEvents(item, candidate)) return candidate
  return null
}

const getMergePreviousTimelineItem = (item) => getSameTypeTimelineItem(item, -1)
const getMergeNextTimelineItem = (item) => getSameTypeTimelineItem(item, 1)

watch(() => props.timelineData, items => {
  if (!editDrawerVisible.value || !selectedItemForEdit.value) return
  const previous = selectedItemForEdit.value
  const timestamp = previous.timestamp || previous.startTime
  const start = Date.parse(timestamp)
  const endOf = item => Date.parse(item.endTime || new Date(Date.parse(item.timestamp || item.startTime) + (item.stayDuration ?? item.tripDuration ?? item.durationSeconds ?? 0) * 1000).toISOString())
  const end = endOf(previous)
  const candidates = items.filter(item => item.type === previous.type).map(item => ({
    item, overlap: Math.max(0, Math.min(end, endOf(item)) - Math.max(start, Date.parse(item.timestamp || item.startTime)))
  })).filter(candidate => candidate.overlap > 0).sort((a, b) => b.overlap - a.overlap)
  const match = items.find(item => item.type === previous.type && (item.timestamp || item.startTime) === timestamp)
    || (candidates[0]?.overlap > (candidates[1]?.overlap || 0) ? candidates[0].item : null)
  if (match) selectedItemForEdit.value = match
})
const focusEditChanges = ref(false)
const openEditor = (item, focusChanges = false) => {
  focusEditChanges.value = focusChanges
  if (effectiveReadOnly.value) return
  selectedItemForEdit.value = item
  editDrawerVisible.value = true
}

const closeEditor = () => {
  editDrawerVisible.value = false
  selectedItemForEdit.value = null
}

const openEditHistory = (item) => {
  if (effectiveReadOnly.value) return
  selectedItemForHistory.value = item
  editHistoryVisible.value = true
}

const closeEditHistory = () => {
  editHistoryVisible.value = false
  selectedItemForHistory.value = null
}

const handleEditSubmitted = (result) => {
  if (!result.operation || result.operation === 'create') closeEditor()
  const refresh = callback => async () => {
    try {
      await callback?.()
    } finally {
      await refreshEditHistory()
    }
  }
  emit('timeline-edit-submitted', {
    ...result,
    onUpdated: refresh(result.onUpdated),
    onUpdateFailed: refresh(result.onUpdateFailed)
  })
}

const getWeatherSamplesForItem = (item) => {
  if (props.isPublicView) {
    return []
  }
  return getWeatherSamplesForTimelineItem(item, props.weatherSamples)
}

// Methods
const navigateDay = (offset) => {
  if (!selectedSingleDayDate.value) {
    return
  }

  const targetDate = timezone.create(selectedSingleDayDate.value)
    .add(offset, 'day')
    .format('YYYY-MM-DD')
  emit('navigate-date', targetDate)
}

const loadMore = () => {
  // Load 100 more items each time
  displayLimit.value = Math.min(displayLimit.value + 100, displayedTimelineData.value.length)
  console.log(`Timeline: Loading more items. Now showing ${displayLimit.value} of ${displayedTimelineData.value.length}`)
}

const handleTimelineItemClick = (item) => {
  emit('timeline-item-click', item)
}

const handleTagClick = (tag) => {
  emit('tag-clicked', tag)
}

const handlePhotoShowOnMap = (photo) => {
  emit('photo-show-on-map', photo)
}

const handleNoteSaved = async () => {
  await loadNotesForCards(true)
}

const scrollToTimelineItem = (item) => {
  if (!item || !timelineContainerRef.value) {
    return false
  }

  const itemId = item.id !== undefined && item.id !== null ? String(item.id) : ''
  const itemTimestamp = item.timestamp || item.startTime || ''
  const items = Array.from(timelineContainerRef.value.querySelectorAll('.timeline-item-content'))
  const target = items.find((element) => (
    (itemId && element.dataset.timelineItemId === itemId) ||
    (itemTimestamp && element.dataset.timelineItemTimestamp === itemTimestamp)
  ))

  if (!target) {
    return false
  }

  target.scrollIntoView({ behavior: 'smooth', block: 'center' })
  target.animate?.([{ backgroundColor: 'var(--gp-primary-soft)' }, { backgroundColor: 'transparent' }], { duration: 1800 })
  return true
}

const handleRenameStay = (stayItem) => {
  if (effectiveReadOnly.value) {
    showReadOnlyToast()
    return
  }
  emit('rename-stay', stayItem)
}

const handleResetDataGapOverride = (stayItem, overrideId) => {
  if (effectiveReadOnly.value) {
    showReadOnlyToast()
    return
  }
  emit('reset-data-gap-override', stayItem, overrideId)
}

const handleResetTripSplitOverride = (stayItem, overrideId) => {
  if (effectiveReadOnly.value) {
    showReadOnlyToast()
    return
  }
  emit('reset-trip-split-override', stayItem, overrideId)
}

const handleExportTripAsGpx = async (tripItem) => {
  try {
    await exportImportStore.exportTripAsGpx(tripItem.id)
    toast.add({
      severity: 'success',
      summary: t('timeline.container.exportStartedTitle'),
      detail: t('timeline.container.tripExportingDetail'),
      life: 3000
    })
  } catch (error) {
    console.error('Failed to export trip as GPX:', error)
    toast.add({
      severity: 'error',
      summary: t('timeline.container.exportFailedTitle'),
      detail: formatApiErrorDetail(error, t('timeline.container.tripExportFailedDetail')),
      life: 5000
    })
  }
}

const handleExportStayAsGpx = async (stayItem) => {
  try {
    await exportImportStore.exportStayAsGpx(stayItem.id)
    toast.add({
      severity: 'success',
      summary: t('timeline.container.exportStartedTitle'),
      detail: t('timeline.container.stayExportingDetail'),
      life: 3000
    })
  } catch (error) {
    console.error('Failed to export stay as GPX:', error)
    toast.add({
      severity: 'error',
      summary: t('timeline.container.exportFailedTitle'),
      detail: formatApiErrorDetail(error, t('timeline.container.stayExportFailedDetail')),
      life: 5000
    })
  }
}

const handleShowClassification = (tripItem) => {
  selectedTripForClassification.value = tripItem
  classificationDialogVisible.value = true
}

const handleCloseClassificationDialog = () => {
  classificationDialogVisible.value = false
  selectedTripForClassification.value = null
}

const handleShowExplanation = (item) => {
  selectedItemForExplanation.value = item
  explanationDialogVisible.value = true
}

const handleCloseExplanationDialog = () => {
  explanationDialogVisible.value = false
  selectedItemForExplanation.value = null
}

// The explanation and classification dialogs of a trip link to each other; only one is open at a time
const handleShowClassificationFromExplanation = (tripItem) => {
  handleCloseExplanationDialog()
  handleShowClassification(tripItem)
}

const handleShowExplanationFromClassification = (tripItem) => {
  handleCloseClassificationDialog()
  handleShowExplanation(tripItem)
}

const handleShowMapMatchingDetails = (tripItem) => {
  selectedTripForMapMatchingDetails.value = tripItem
  selectedMapMatchingInfo.value = props.mapMatchingByTripId.get(Number(tripItem?.id)) || null
  mapMatchingDetailsVisible.value = true
}

const handleCloseMapMatchingDetails = () => {
  mapMatchingDetailsVisible.value = false
  selectedTripForMapMatchingDetails.value = null
  selectedMapMatchingInfo.value = null
}

const handleSplitTripWithStay = (tripItem) => {
  if (effectiveReadOnly.value) {
    showReadOnlyToast()
    return
  }
  selectedTripForSplit.value = tripItem
  tripSplitDialogVisible.value = true
}

const handleCloseTripSplitDialog = () => {
  tripSplitDialogVisible.value = false
  selectedTripForSplit.value = null
  if (splitReturnItem.value) { openEditor(splitReturnItem.value); splitReturnItem.value = null }
}

const splitReturnItem = ref(null)
const handleSplitTripFromEditor = (tripItem) => {
  splitReturnItem.value = tripItem
  closeEditor()
  handleSplitTripWithStay(tripItem)
}

// The split is a unified timeline edit, so it follows the same job, refresh and undo path as the drawer's edits.
const handleTripSplitSaved = (result) => {
  splitReturnItem.value = null
  emit('timeline-edit-submitted', result)
}

const handleMovementTypeUpdated = (updated) => {
  if (!updated?.tripId) return
  if (selectedTripForClassification.value?.id === updated.tripId) {
    selectedTripForClassification.value.movementType = updated.movementType
    selectedTripForClassification.value.movementTypeSource = updated.movementTypeSource
  }
}

// Range to load labels, photos and notes for: the data window when the page provides one
const loadRange = computed(() => (
  Array.isArray(props.dataWindow) && props.dataWindow.length === 2 ? props.dataWindow : props.dateRange
))

async function loadEditHistory() {
  const version = ++historyLoadVersion
  const range = loadRange.value
  historyEdits.value = []
  if (effectiveReadOnly.value || range?.length !== 2) return
  const start = Date.parse(range[0])
  const end = Date.parse(range[1])
  if (!Number.isFinite(start) || !Number.isFinite(end) || start >= end) return
  try {
    const edits = await timelineStore.fetchTimelineEdits()
    if (version !== historyLoadVersion) return
    historyEdits.value = edits.filter(edit =>
      Date.parse(edit.affectedFrom || edit.anchorStart) < end &&
      Date.parse(edit.affectedTo || edit.anchorEnd) > start)
  } catch (error) {
    if (version === historyLoadVersion) throw error
  }
}

async function refreshEditHistory() {
  try {
    await loadEditHistory()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('timeline.edit.historyFailed'),
      detail: formatApiErrorDetail(error, t('timeline.edit.historyFailed')),
      life: 5000
    })
  }
}

watch([loadRange, () => props.timelineData, effectiveReadOnly], refreshEditHistory, { deep: true, immediate: true })

// Load timeline labels when dateRange changes
const loadTimelineLabels = async () => {
  if (!props.showTimelineLabels) {
    return
  }
  const range = loadRange.value
  if (range && range.length === 2) {
    try {
      await timelineLabelsStore.fetchTimelineLabelsForTimeRange(
        range[0],
        range[1]
      )
    } catch (error) {
      console.error('Failed to load timeline labels:', error)
    }
  }
}

const loadImmichPhotosForCards = async () => {
  if (!props.loadImmichPhotos || props.isPublicView) {
    return
  }

  if (!loadRange.value || loadRange.value.length !== 2) {
    return
  }

  const [startDate, endDate] = loadRange.value
  if (!startDate || !endDate) {
    return
  }

  try {
    if (!immichStore.hasConfig && !immichStore.configLoading) {
      await immichStore.fetchConfig()
    }

    if (!immichStore.isConfigured) {
      return
    }

    await immichStore.fetchPhotos(startDate, endDate)
  } catch (error) {
    console.warn('Failed to load Immich photos for timeline cards:', error)
  }
}

const loadNotesForCards = async (forceRefresh = false) => {
  if (!props.loadNotes || props.isPublicView) {
    return
  }

  if (!loadRange.value || loadRange.value.length !== 2) {
    return
  }

  const [startDate, endDate] = loadRange.value
  if (!startDate || !endDate) {
    return
  }

  try {
    if (!notesStore.hasMemosConfig && !notesStore.configLoading) {
      await notesStore.fetchMemosConfig()
    }
    await notesStore.fetchNotes(startDate, endDate, {
      includeExternal: true,
      forceRefresh
    })
  } catch (error) {
    console.warn('Failed to load notes for timeline cards:', error)
  }
}

// Watch for date range changes
watch(loadRange, () => {
  loadTimelineLabels()
  loadImmichPhotosForCards()
  loadNotesForCards()
}, { deep: true, immediate: true })

defineExpose({
  scrollToTimelineItem
})
</script>

<style scoped>
.timeline-container {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.timeline-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0 var(--gp-spacing-md);
}

.timeline-header {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--gp-spacing-sm);
  width: 100%;
  justify-content: center;
  text-align: center;
  color: var(--gp-primary-text);
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: var(--gp-spacing-lg);
  padding: 0 0 var(--gp-spacing-xs);
  border-bottom: 2px solid var(--gp-primary-light);
  background: var(--gp-surface-card);
}

.timeline-title {
  line-height: 1.3;
}

.timeline-header-top {
  position: relative;
  width: 100%;
  min-height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 2.4rem;
}

.timeline-history-button {
  position: absolute;
  right: .25rem;
  top: 50%;
  min-width: 2.15rem;
  height: 2.15rem;
  padding: 0 .45rem !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: .25rem;
  transform: translateY(-50%);
  color: var(--gp-text-secondary) !important;
}

.timeline-history-button:hover {
  color: var(--gp-primary-text) !important;
  background: var(--gp-surface-hover) !important;
}

.timeline-history-count {
  min-width: 1.1rem;
  height: 1.1rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 .25rem;
  border-radius: var(--gp-radius-pill);
  background: var(--gp-primary);
  color: var(--gp-primary-contrast);
  font-size: .62rem;
  font-weight: 750;
  line-height: 1;
}

.timeline-header-date-nav {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--gp-spacing-sm);
  max-width: 100%;
}

.timeline-header-date-text {
  flex: 0 0 clamp(12rem, 58vw, 18rem);
  width: clamp(12rem, 58vw, 18rem);
  min-width: 0;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
  font-weight: 600;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Mobile optimizations */
@media (max-width: 768px) {
  .timeline-header {
    font-size: 1rem;
    margin-bottom: var(--gp-spacing-md);
    padding-bottom: var(--gp-spacing-xs);
  }

  .timeline-header-date-nav {
    gap: var(--gp-spacing-xs);
  }

  .timeline-header-date-text {
    flex-basis: clamp(10rem, 52vw, 14rem);
    width: clamp(10rem, 52vw, 14rem);
    font-size: 0.8rem;
  }
}

.loading-messages {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  text-align: center;
  padding: var(--gp-spacing-lg);
  margin: var(--gp-spacing-lg);
  color: var(--gp-text-secondary);
  background: var(--gp-surface-muted);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  font-size: 0.875rem;
  font-weight: 500;
}

.loading-messages .p-progressspinner {
  margin-bottom: 1rem;
}

.timeline-warning {
  padding: var(--gp-spacing-md);
  margin: 0 var(--gp-spacing-lg) var(--gp-spacing-lg);
  color: var(--gp-warning-text);
  background: var(--gp-warning-soft);
  border: 1px solid var(--gp-warning);
  border-radius: var(--gp-radius-medium);
  font-size: 0.875rem;
  font-weight: 500;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--gp-spacing-sm);
}

.timeline-warning i {
  font-size: 1rem;
}

.load-more-button {
  font-weight: 600;
}

.reports-link {
  color: var(--gp-primary-text);
  font-weight: 500;
  text-decoration: none;
  font-size: 0.8125rem;
}

.reports-link:hover {
  text-decoration: underline;
}

.timeline-marker {
  @apply flex w-8 h-8 items-center justify-center;
  color: white;
  border-radius: 50%;
  box-shadow: var(--gp-shadow-light);
}

.timeline-marker.marker-stay {
  background: var(--gp-primary);
}

.timeline-marker.marker-trip {
  background: var(--gp-success);
}

.timeline-marker.marker-data-gap {
  background: var(--gp-warning);
}

/* Dark mode adjustments */

.timeline-no-data {
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

/* Date separator styles */
.date-group {
  margin-bottom: var(--gp-spacing-xl);
}

.date-separator {
  display: flex;
  align-items: center;
  margin: var(--gp-spacing-xl) 0 var(--gp-spacing-lg) 0;
  gap: var(--gp-spacing-md);
}

.date-separator-line {
  flex: 1;
  height: 1px;
  background: var(--gp-border);
}

.date-separator-text {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--gp-text-secondary);
  font: inherit;
  font-weight: 600;
  font-size: 0.9rem;
  padding: 0.15rem var(--gp-spacing-sm);
  border: 1px solid transparent;
  border-radius: 999px;
  background: var(--gp-surface-card);
  white-space: nowrap;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.date-separator-text:hover {
  border-color: var(--gp-border-medium);
  color: var(--gp-text-primary);
}

.date-separator-text:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

/* Shown by the border and weight as well as color, so the state does not rely on color alone. */
.date-separator-text--focused {
  border-color: var(--gp-primary);
  color: var(--gp-text-primary);
  font-weight: 700;
}

/* Same shape as the map legend swatch, so it reads as "this day's line". */
.date-separator-day-color {
  display: inline-block;
  width: 1.1rem;
  height: 0.35rem;
  border-radius: 999px;
  flex-shrink: 0;
}

.date-nav-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.5rem;
  height: 1.5rem;
  padding: 0;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-card);
  color: var(--gp-text-secondary);
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.date-nav-button:hover {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  color: #fff;
}

.date-nav-button:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

.date-nav-button i {
  font-size: 0.7rem;
}

.date-timeline {
  margin-top: 0;
}

/* Mobile optimizations for date separators */
@media (max-width: 768px) {
  .date-separator {
    margin: var(--gp-spacing-lg) 0 var(--gp-spacing-md) 0;
  }
  
  .date-separator-text {
    font-size: 0.8rem;
    padding: 0 var(--gp-spacing-xs);
  }

  .date-separator {
    gap: var(--gp-spacing-xs);
  }
  
  .date-group {
    margin-bottom: var(--gp-spacing-lg);
  }
}

/* Clickable period badge styles */
.gp-period-badge--clickable {
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
  user-select: none;
}

.gp-period-badge--clickable:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  opacity: 0.9;
}

.gp-period-badge--clickable:active {
  transform: translateY(0);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}

.gp-period-badge--clickable:focus {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}
</style>
