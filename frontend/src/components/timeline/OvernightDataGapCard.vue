<template>
  <Card
    class="timeline-card timeline-card--overnight-data-gap"
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
        <span class="gap-label">📵 {{ t('timeline.dataGap.subtitle') }}</span>
      </div>
    </template>

    <template #content>
      <div class="overnight-data-gap-content">
        <p class="duration-detail">
          📈 {{ t('timeline.dataGap.totalDuration') }} <span class="duration-value">{{ getGapDuration() }}</span>
        </p>
        <p class="duration-detail">
          ⏱️ {{ t('timeline.dataGap.onThisDay') }}
          <span class="duration-value"> {{ getOnThisDayText() }}</span>
        </p>

        <button
          v-if="dataGapItem.id && !dataGapItem.ongoing"
          class="convert-gap-btn"
          :title="t('timeline.dataGap.convertTooltip')"
          @click.stop="handleConvertToStay"
        >
          {{ t('timeline.dataGap.convertButton') }}
        </button>

        <!-- Help section for gap configuration guidance -->
        <DataGapHelpSection :duration-seconds="gapDurationSeconds" />
      </div>
    </template>
  </Card>

  <ContextMenu ref="contextMenu" :model="contextMenuItems" :base-z-index="1200" />
  <NoteEditorDialog
    v-model:visible="noteEditorVisible"
    anchor-type="TIMESTAMP"
    :event-time="dataGapItem.startTime"
    :memos-configured="notesStore.isMemosConfigured"
    :default-destination="notesStore.defaultSaveDestination"
    :default-visibility="notesStore.defaultVisibility"
    @saved="handleNoteSaved"
  />
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ContextMenu from 'primevue/contextmenu'
import { useTimezone } from '@/composables/useTimezone';
import { formatDurationSmart } from '@/utils/calculationsHelpers';
import { useTimelineCardPhotoMatching } from '@/composables/useTimelineCardPhotoMatching'
import { useTimelineCardNoteMatching } from '@/composables/useTimelineCardNoteMatching'
import { useLongPressContextMenu } from '@/composables/useLongPressContextMenu'
import { useExclusiveContextMenu } from '@/composables/useExclusiveContextMenu'
import { useNotesStore } from '@/stores/notes'
import DataGapHelpSection from './DataGapHelpSection.vue';
import TimelinePhotoPreviewTrigger from './TimelinePhotoPreviewTrigger.vue'
import TimelineNotePreviewTrigger from './TimelineNotePreviewTrigger.vue'
import NoteEditorDialog from './NoteEditorDialog.vue'

const { t } = useI18n()
const notesStore = useNotesStore()

const props = defineProps({
  dataGapItem: {
    type: Object,
    required: true
  },
  currentDate: {
    type: String,
    required: true
  },
  notes: {
    type: Array,
    default: () => []
  },
  immichPhotos: {
    type: Array,
    default: () => []
  },
  immichPhotoAuthToken: {
    type: String,
    default: null
  },
  allowNoteCreation: {
    type: Boolean,
    default: true
  }
});

const emit = defineEmits(['click', 'convert-to-stay', 'photo-show-on-map', 'note-saved']);

const timezone = useTimezone();

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

const { matchingPhotos } = useTimelineCardPhotoMatching({
  itemRef: computed(() => props.dataGapItem),
  immichPhotosRef: computed(() => props.immichPhotos),
  durationField: 'durationSeconds',
  currentDateRef: computed(() => props.currentDate),
  clampToCurrentDay: true
})

const { matchingNotes } = useTimelineCardNoteMatching({
  itemRef: computed(() => props.dataGapItem),
  notesRef: computed(() => props.notes),
  durationField: 'durationSeconds',
  currentDateRef: computed(() => props.currentDate),
  clampToCurrentDay: true
})

const canManageMatchingNotes = computed(() => {
  return props.allowNoteCreation && matchingNotes.value.some((note) => (
    note?.source === 'GEOPULSE' && note?.editable !== false && note?.id != null
  ))
})

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

const openNotesViewer = () => {
  notePreviewTrigger.value?.openNotes()
}

const contextMenuItems = computed(() => {
  const items = []

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

  return items
})

// Computed properties
const gapDurationSeconds = computed(() => {
  const startTime = timezone.fromUtc(props.dataGapItem.startTime)
  const endTime = timezone.fromUtc(props.dataGapItem.endTime)
  return endTime.diff(startTime, 'second')
})

// Methods
const getTimestampText = () => {
  return timezone.getOvernightTimestampText(props.dataGapItem, props.currentDate)
}

const getGapDuration = () => {
  const startTime = timezone.fromUtc(props.dataGapItem.startTime)
  const endTime = timezone.fromUtc(props.dataGapItem.endTime)
  const durationSeconds = endTime.diff(startTime, 'second')
  return formatDurationSmart(durationSeconds)
}

const getOnThisDayText = () => {
  return timezone.getOvernightOnThisDayText(props.dataGapItem, props.currentDate)
}

const handleClick = (event) => {
  if (shouldSuppressClick(event)) return
  emit('click', props.dataGapItem);
};

const handleConvertToStay = () => {
  emit('convert-to-stay', props.dataGapItem);
};

const handlePhotoShowOnMap = (photo) => {
  emit('photo-show-on-map', photo)
}

const handleNoteSaved = (note) => {
  emit('note-saved', note)
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
  
  .overnight-data-gap-content {
    margin-top: var(--gp-spacing-xs);
  }
  
  .duration-detail,
  .end-time-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }
}

.timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.timeline-card--overnight-data-gap {
  background-color: var(--gp-timeline-orange-light);
  border-left: 4px solid var(--gp-warning-dark);
}

.timeline-timestamp {
  color: var(--gp-warning-dark);
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
  font-weight: 600;
}

.gap-label {
  color: var(--gp-warning);
  font-weight: 700;
}

.overnight-data-gap-content {
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-primary);
}

.duration-detail,
.end-time-detail {
  margin: var(--gp-spacing-xs) 0;
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.3;
}

.duration-detail .duration-value,
.end-time-detail .time-value {
  font-weight: 700;
  color: var(--gp-warning-dark);
}

.convert-gap-btn {
  margin-top: var(--gp-spacing-xs);
  border: none;
  background: transparent;
  color: var(--gp-warning-dark);
  font-weight: 700;
  font-size: 0.8rem;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}

/* Dark mode adjustments */
.p-dark .timeline-card {
  border-color: var(--gp-border-medium);
}

.p-dark .timeline-card--overnight-data-gap {
  background-color: var(--gp-timeline-orange);
  border-left: 4px solid var(--gp-warning-dark);
}

.p-dark .timeline-timestamp,
.p-dark .duration-detail .duration-value,
.p-dark .end-time-detail .time-value {
  color: var(--gp-warning);
}

.p-dark .gap-label {
  color: var(--gp-warning);
}

.p-dark .timeline-subtitle,
.p-dark .overnight-data-gap-content,
.p-dark .duration-detail,
.p-dark .end-time-detail {
  color: var(--gp-text-primary);
}

.p-dark .timeline-card:hover {
  box-shadow: var(--gp-shadow-medium);
}
</style>
