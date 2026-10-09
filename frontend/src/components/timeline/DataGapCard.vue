<template>
  <Card
    class="timeline-card timeline-card--data-gap"
    v-bind="longPressBindings"
    @click="handleClick"
    @contextmenu="showContextMenu"
  >
    <template #title>
      <div class="timeline-title-row">
        <p class="timeline-timestamp" :title="timezone.getLocationTimezoneHint(dataGapItem.startLocationTimezone) || undefined">
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
  forceZoneLabel: {
    type: Boolean,
    default: false
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
  return timezone.formatDateTimeDisplayAt(props.dataGapItem.startTime, props.dataGapItem.startLocationTimezone, {
    forceLabel: props.forceZoneLabel
  })
})

const formattedEndTime = computed(() => {
  if (!props.dataGapItem.endTime) return '';
  return timezone.formatDateTimeDisplayAt(props.dataGapItem.endTime, props.dataGapItem.endLocationTimezone, {
    forceLabel: props.forceZoneLabel
  })
})
</script>

<style scoped src="./timeline-card.css"></style>

<style scoped>
/* Mobile optimizations */
@media (max-width: 768px) {
  .data-gap-content {
    margin-top: var(--gp-spacing-xs);
  }

  .gap-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }
}

.timeline-card--data-gap {
  background-color: var(--gp-timeline-card-gap);
  border-left: 4px solid var(--gp-warning);
}

.timeline-timestamp {
  color: var(--gp-warning-text);
}

.timeline-subtitle {
  color: var(--gp-warning-text);
  font-weight: 700;
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
  color: var(--gp-warning-text);
}

.convert-gap-btn {
  margin-top: var(--gp-spacing-xs);
  border: none;
  background: transparent;
  color: var(--gp-warning-text);
  font-weight: 700;
  font-size: 0.8rem;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}
</style>
