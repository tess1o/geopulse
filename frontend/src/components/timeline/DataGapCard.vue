<template>
  <Card
    class="timeline-card timeline-card--data-gap"
    v-bind="longPressBindings"
    @click="handleClick"
    @contextmenu="showContextMenu"
  >
    <template #title>
      <div class="timeline-title-row">
        <p class="timeline-timestamp">
          🕐 {{ formattedStartTime }}
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
        ❓ {{ t('timeline.dataGap.subtitle') }}
      </div>
    </template>

    <template #content>
      <div class="data-gap-content">
        <p class="gap-detail">
          📅 {{ t('timeline.dataGap.endTimeLabel') }}
          <span class="detail-value">{{ formattedEndTime }}</span>
        </p>
        <p class="gap-detail">
          ⏱️ {{ t('timeline.dataGap.durationLabel') }}
          <span class="detail-value">{{ formatDuration(dataGapItem.durationSeconds) }}</span>
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
        <DataGapHelpSection :duration-seconds="dataGapItem.durationSeconds" />
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
import { formatDuration } from '@/utils/calculationsHelpers'
import { useTimezone } from '@/composables/useTimezone'
import { useTimelineCardPhotoMatching } from '@/composables/useTimelineCardPhotoMatching'
import { useTimelineCardNoteMatching } from '@/composables/useTimelineCardNoteMatching'
import { useLongPressContextMenu } from '@/composables/useLongPressContextMenu'
import { useExclusiveContextMenu } from '@/composables/useExclusiveContextMenu'
import { useNotesStore } from '@/stores/notes'
import DataGapHelpSection from './DataGapHelpSection.vue'
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
})

const emit = defineEmits(['click', 'convert-to-stay', 'photo-show-on-map', 'note-saved'])

const timezone = useTimezone()

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
  durationField: 'durationSeconds'
})

const { matchingNotes } = useTimelineCardNoteMatching({
  itemRef: computed(() => props.dataGapItem),
  notesRef: computed(() => props.notes),
  durationField: 'durationSeconds'
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

const handleClick = (event) => {
  if (shouldSuppressClick(event)) return
  emit('click', props.dataGapItem)
}

const handleConvertToStay = () => {
  emit('convert-to-stay', props.dataGapItem)
}

const handlePhotoShowOnMap = (photo) => {
  emit('photo-show-on-map', photo)
}

const handleNoteSaved = (note) => {
  emit('note-saved', note)
}

const formattedStartTime = computed(() => {
  if (!props.dataGapItem.startTime) return '';
  return `${timezone.formatDateDisplay(props.dataGapItem.startTime)} ${timezone.formatTime(props.dataGapItem.startTime)}`
})

const formattedEndTime = computed(() => {
  if (!props.dataGapItem.endTime) return '';
  return `${timezone.formatDateDisplay(props.dataGapItem.endTime)} ${timezone.formatTime(props.dataGapItem.endTime)}`
})
</script>

<style scoped>
.timeline-card {
  margin-top: var(--gp-spacing-md);
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
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
  
  .data-gap-content {
    margin-top: var(--gp-spacing-xs);
  }
  
  .gap-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }
}

.timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.timeline-card--data-gap {
  background-color: var(--gp-timeline-orange-light);
  border-left: 4px solid var(--gp-warning);
}

.timeline-timestamp {
  color: var(--gp-warning);
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
  color: var(--gp-warning);
  font-size: 0.9rem;
  font-weight: 700;
  line-height: 1.3;
}

.data-gap-content {
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-primary);
}

.gap-detail {
  margin: var(--gp-spacing-xs) 0;
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.3;
}

.gap-detail .detail-value {
  font-weight: 700;
  color: var(--gp-warning);
}

.convert-gap-btn {
  margin-top: var(--gp-spacing-xs);
  border: none;
  background: transparent;
  color: var(--gp-warning);
  font-weight: 700;
  font-size: 0.8rem;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}
</style>
