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

<style scoped src="./timeline-card.css"></style>

<style scoped>
/* Mobile optimizations */
@media (max-width: 768px) {
  .overnight-data-gap-content {
    margin-top: var(--gp-spacing-xs);
  }

  .duration-detail,
  .end-time-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }
}

.timeline-card--overnight-data-gap {
  background-color: var(--gp-timeline-card-gap);
  border-left: 4px solid var(--gp-warning-strong);
}

.timeline-timestamp {
  color: var(--gp-warning-text);
}

.timeline-subtitle {
  font-weight: 600;
}

.gap-label {
  color: var(--gp-warning-text);
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
