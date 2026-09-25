<template>
  <AppLayout variant="default">
    <PageContainer
      variant="fullwidth"
    >
      <template #header>
        <div class="workspace-page-header">
          <div class="workspace-page-title-wrap">
            <h1 class="workspace-page-title">{{ pageTitle }}</h1>
            <p class="workspace-page-subtitle">{{ pageSubtitle }}</p>
            <p class="workspace-page-subtitle workspace-page-subtitle--compact">{{ pageSubtitleCompact }}</p>
          </div>
        </div>
      </template>
      <template #actions>
        <div class="workspace-header-actions">
          <!-- Left to right: the range you are looking at, the control that resets it, then the
               trip-level actions. Source order is the visual order on every viewport. -->
          <Button
            v-if="isUnplannedTrip && isOwner"
            icon="pi pi-calendar-plus"
            label="Set Trip Dates"
            class="gp-btn-primary"
            @click="openTripScheduling"
          />
          <!-- Available to everyone, owner included. It used to render only for
               non-owners, so an owner could not re-scope their own trip, and a
               non-owner on an unplanned trip got a picker with no bounds. -->
          <DatePicker
            v-if="!isUnplannedTrip"
            v-model="selectedDateRange"
            selectionMode="range"
            :manualInput="false"
            :dateFormat="timezone.getPrimeVueDatePickerFormat()"
            :minDate="tripMinDate"
            :maxDate="tripMaxDate"
            @update:model-value="handleDateRangeChange"
            class="workspace-date-picker"
          />
          <Button
            v-if="!isUnplannedTrip"
            icon="pi pi-history"
            text
            rounded
            aria-label="Reset to full trip range"
            @click="resetToTripRange"
            v-tooltip.bottom="'Reset to full trip range'"
          />
          <Button
            v-if="canReconstructTrip"
            icon="pi pi-map"
            outlined
            label="Add Missing Trip Data"
            @click="openReconstructionDialog"
          />
          <Button
            v-if="isOwner"
            icon="pi pi-users"
            outlined
            label="Collaborators"
            @click="openCollaboratorsDialog"
          />
        </div>
      </template>

      <div v-if="isInitialLoading" class="workspace-loading">
        <ProgressSpinner />
      </div>

      <Message v-else-if="pageError" severity="error" :closable="false">
        {{ pageError }}
      </Message>

      <template v-else>
        <Message v-if="isUnplannedTrip" severity="info" :closable="false" class="unplanned-trip-banner">
          This trip is unplanned. Add planned stops now, then set trip dates to enable timeline, path, and analytics.
        </Message>
        <Message v-if="showAccessModeBanner" severity="warn" :closable="false" class="trip-access-banner">
          {{ accessModeBannerText }}
        </Message>

        <!-- Always visible. These metrics used to live inside the Overview tab, so they
             disappeared on the Plan tab and were absent entirely for future trips. -->
        <TripSummaryBar
          :completion-rate="summaryCompletion"
          :visited-count="summaryVisitedCount"
          :total-count="summaryPlanTotal"
          :must-visited="mustVisitedCount"
          :must-total="mustPlanTotal"
          :distance-label="formatDistance(tripSummary?.totalDistanceMeters || 0)"
          :duration-label="formatDuration(tripSummary?.totalTripDurationSeconds || 0)"
        />

        <!-- One layout, in every trip state. Nothing here appears or disappears based on
             trip status; only the rail's contents change. -->
        <BaseCard class="workspace-card">
          <TimelineSplitLayout
            ref="timelineSplitLayoutRef"
            class="workspace-timeline-split"
            collapsed-label="Stops"
            expanded-label="Stops"
            handle-mobile-only
            @layout-resize="triggerWorkspaceMapResize"
          >
            <template #map>
              <div v-if="workspaceLoading" class="pane-loading">
                <ProgressSpinner />
              </div>
              <TimelineMap
                v-else
                ref="timelineMapRef"
                class="workspace-timeline-map"
                :style="workspaceTimelineMapStyle"
                :pathData="hasPathData ? workspacePath : null"
                :timelineData="workspaceTimeline"
                :weather-samples="weatherSamples"
                :plannedItemsData="tripPlanMapItems"
                :showFavoritesByDefault="false"
                :showImmichByDefault="true"
                :showPlanToVisitAction="canEditPlanItems"
                :showFavoritesContextActions="false"
                :showHeatmapControl="false"
                :enableFavoriteContextMenu="canEditPlanItems"
                :showCurrentLocation="false"
                :read-only="demoReadOnly"
                @timeline-marker-click="handleWorkspaceTimelineMarkerClick"
                @highlighted-path-click="handleWorkspaceHighlightedPathClick"
                @plan-to-visit="handlePlanToVisit"
                @plan-item-edit="handlePlanItemEditFromMap"
                @plan-item-delete="handlePlanItemDeleteFromMap"
              />
            </template>

            <template #side>
              <TripStopsRail
                :stops="sortedTripPlanItems"
                :loading="workspaceLoading"
                :can-edit="canEditPlanItems"
                :has-actual-data="hasTimelineData || hasPathData"
                :lens="railLens"
                :selected-id="focusedPlanItemId"
                @update:lens="railLens = $event"
                @add-stop="handleAddStopFromRail"
                @focus-stop="focusPlannedItemOnMap"
                @edit-stop="openEditPlanItemDialog"
                @delete-stop="confirmDeletePlanItem"
                @mark-visited="handleMarkVisitedFromRail"
              >
                <template #actual>
                  <div class="trip-actual-lens">
                    <TimelineContainer
                      ref="timelineContainerRef"
                      :timeline-data="workspaceTimeline"
                      :timelineNoData="!workspaceTimeline.length"
                      :timelineDataLoading="workspaceLoading"
                      :dateRange="activeDateRangeArray"
                      :loadImmichPhotos="false"
                      :weather-samples="weatherSamples"
                      :showTimelineLabels="false"
                      @timeline-item-click="handleWorkspaceTimelineItemClick"
                      @rename-stay="handleWorkspaceRenameStay"
                      @timeline-refresh-requested="handleWorkspaceTimelineRefreshRequested"
                      @reset-data-gap-override="handleWorkspaceResetDataGapOverride"
                      @reset-trip-split-override="handleWorkspaceResetTripSplitOverride"
                    />

                    <ImmichLatestPhotosSection
                      v-if="showTripPhotosSection"
                      title="Trip Photos"
                      :search-params="tripImmichSearchParams"
                      :use-store-photos="true"
                      empty-message="No Immich photos found for this trip range."
                      @show-on-map="handleTripPhotoShowOnMap"
                    />
                  </div>
                </template>
              </TripStopsRail>
            </template>
          </TimelineSplitLayout>
        </BaseCard>

      </template>
    </PageContainer>

    <Dialog
      v-model:visible="showPlanItemDialog"
      modal
      :header="editingPlanItemId ? 'Edit Plan Item' : 'Add Plan Item'"
      class="gp-dialog-xl plan-item-dialog"
      @hide="resetPlanItemForm"
    >
      <div class="plan-item-dialog-layout">
        <div class="plan-item-dialog-form">
          <div v-if="isResolvingPlanSuggestion">
            <Message severity="info" :closable="false">Resolving place details...</Message>
          </div>
          <div v-if="planItemMapSourceHint">
            <Message severity="info" :closable="false">{{ planItemMapSourceHint }}</Message>
          </div>

          <div>
            <label for="planTitle" class="field-label">Title *</label>
            <InputText
              id="planTitle"
              v-model="planItemForm.title"
              class="w-full"
              placeholder="e.g., Sagrada Familia"
              :class="{ 'p-invalid': planItemErrors.title }"
            />
            <small v-if="planItemErrors.title" class="p-error">{{ planItemErrors.title }}</small>
          </div>

          <div>
            <label for="planNotes" class="field-label">Notes</label>
            <Textarea
              id="planNotes"
              v-model="planItemForm.notes"
              rows="3"
              class="w-full"
              placeholder="Optional context..."
            />
          </div>

          <div class="plan-item-dialog-row">
            <div>
              <label for="planDate" class="field-label">Planned Day</label>
              <DatePicker
                id="planDate"
                v-model="planItemForm.plannedDay"
                :manualInput="false"
                :dateFormat="timezone.getPrimeVueDatePickerFormat()"
                class="w-full"
              />
            </div>
            <div>
              <label for="planPriority" class="field-label">Priority</label>
              <Select
                id="planPriority"
                v-model="planItemForm.priority"
                :options="priorityOptions"
                optionLabel="label"
                optionValue="value"
                class="w-full"
              />
            </div>
          </div>

          <div class="plan-item-dialog-row">
            <div>
              <label for="planOrder" class="field-label">Order</label>
              <InputNumber
                id="planOrder"
                v-model="planItemForm.orderIndex"
                :min="0"
                :maxFractionDigits="0"
                class="w-full"
              />
            </div>
            <div>
              <label class="field-label">Coordinates</label>
              <div class="plan-item-coordinate-pill">
                <span v-if="hasPlanItemCoordinates">
                  {{ Number(planItemForm.latitude).toFixed(5) }}, {{ Number(planItemForm.longitude).toFixed(5) }}
                </span>
                <span v-else>Not selected yet</span>
              </div>
            </div>

            <PlanItemPoiImages
              :latitude="planItemForm.latitude"
              :longitude="planItemForm.longitude"
              @select="handlePoiSuggestionSelect"
            />
          </div>
        </div>

        <div class="plan-item-dialog-map-pane">
          <div>
            <label for="planLocationSearch" class="field-label">Search place</label>
            <TripPlanLocationSearchInput
              input-id="planLocationSearch"
              ref="planLocationSearchRef"
              v-model="planItemLocationSearchQuery"
              :suggestions="planItemLocationSearchSuggestions"
              placeholder="Search saved places or providers..."
              :loading="isPlanItemLocationSearchLoading"
              :error="planItemLocationSearchError"
              class="plan-item-location-search"
              @complete="handlePlanItemLocationSearchComplete"
              @select="handlePlanItemLocationSearchSelect"
            />
          </div>

          <div class="plan-item-dialog-map-wrap">
            <MapContainer
              ref="planItemDialogMapRef"
              :map-id="`trip-plan-item-dialog-map-${planItemDialogMapId}`"
              :center="planItemDialogMapCenter"
              :zoom="planItemDialogMapZoom"
              :show-controls="false"
              :enable-fullscreen="false"
              height="420px"
              width="100%"
              @map-ready="handlePlanItemDialogMapReady"
              @map-click="handlePlanItemDialogMapClick"
            />
          </div>

          <small class="plan-item-dialog-map-hint">
            Search by place name or click the map to pin this stop.
          </small>
        </div>
      </div>

      <template #footer>
        <Button label="Cancel" icon="pi pi-times" outlined @click="showPlanItemDialog = false" />
        <Button
          :label="editingPlanItemId ? 'Update Item' : 'Create Item'"
          icon="pi pi-check"
          :loading="isSubmittingPlanItem"
          @click="submitPlanItem"
        />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="showCollaboratorsDialog"
      modal
      header="Trip Collaborators"
      class="gp-dialog-md"
      @show="loadCollaboratorsData"
    >
      <div class="collaborators-content">
        <Message severity="info" :closable="false">
          Only invited friends can access this trip. Choose Viewer or Editor per friend.
        </Message>

        <div class="collaborator-add-row">
          <Select
            v-model="newCollaboratorFriendId"
            :options="availableFriendOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Select friend"
            class="w-full"
            filter
          />
          <Select
            v-model="newCollaboratorRole"
            :options="collaboratorRoleOptions"
            optionLabel="label"
            optionValue="value"
            class="collaborator-role-select"
          />
          <Button
            icon="pi pi-plus"
            label="Add"
            :loading="isSavingCollaborator"
            :disabled="!newCollaboratorFriendId"
            @click="addCollaborator"
          />
        </div>

        <div v-if="collaboratorsLoading" class="collaborators-loading">
          <ProgressSpinner />
        </div>

        <div v-else-if="collaborators.length === 0" class="collaborators-empty">
          No collaborators yet.
        </div>

        <div v-else class="collaborators-list">
          <div
            v-for="collaborator in collaborators"
            :key="collaborator.userId"
            class="collaborator-row"
          >
            <div class="collaborator-main">
              <strong>{{ collaborator.fullName || collaborator.email }}</strong>
              <small>{{ collaborator.email }}</small>
            </div>
            <Select
              :model-value="collaborator.accessRole"
              :options="collaboratorRoleOptions"
              optionLabel="label"
              optionValue="value"
              class="collaborator-role-select"
              @update:model-value="updateCollaboratorRole(collaborator, $event)"
            />
            <Button
              icon="pi pi-trash"
              text
              rounded
              severity="danger"
              :loading="isSavingCollaborator"
              @click="removeCollaborator(collaborator)"
            />
          </div>
        </div>
      </div>
    </Dialog>

    <TripReconstructionDialog
      :visible="showReconstructionDialog"
      mode="trip"
      :trip-id="tripId"
      :trip="currentTrip"
      :fallback-center="workspaceFallbackCenter"
      :read-only="demoReadOnly"
      @close="showReconstructionDialog = false"
      @committed="handleReconstructionCommitted"
    />

    <TimelineLocationEditDialogs
      :favorite-visible="showFavoriteDialog"
      :selected-favorite="selectedFavorite"
      :geocoding-visible="showGeocodingEditDialog"
      :edit-geocoding-data="editGeocodingData"
      v-model:regeneration-visible="locationRegenerationVisible"
      :regeneration-type="locationRegenerationType"
      :current-job-id="locationCurrentJobId"
      :job-progress="locationJobProgress"
      @save-favorite="handleWorkspaceFavoriteDialogSave"
      @close-favorite="closeWorkspaceFavoriteEditor"
      @save-geocoding="handleWorkspaceSaveGeocoding"
      @close-geocoding="closeWorkspaceGeocodingDialog"
    />

    <Dialog
      v-model:visible="showTimelineGenerationDialog"
      modal
      header="Timeline Generation"
      class="gp-dialog-md timeline-generation-dialog"
      :closable="timelineJobCanClose"
      :dismissableMask="timelineJobCanClose"
      @hide="handleTimelineGenerationDialogHide"
    >
      <div class="timeline-generation-content">
        <div class="timeline-generation-status-row">
          <Tag :value="timelineJobStatusLabel" :severity="timelineJobStatusSeverity" />
          <small v-if="trackedTimelineJobId" class="timeline-generation-job-id">
            Job: {{ trackedTimelineJobId }}
          </small>
        </div>

        <p class="timeline-generation-step">
          {{ timelineJobCurrentStep }}
        </p>

        <ProgressBar
          :value="timelineJobProgressValue"
          :showValue="false"
        />

        <div class="timeline-generation-meta">
          <span>{{ timelineJobProgressValue }}%</span>
          <span>{{ timelineJobDurationLabel }}</span>
        </div>

        <Message
          v-if="timelineJobProgress?.errorMessage || timelineJobError"
          severity="error"
          :closable="false"
        >
          {{ timelineJobProgress?.errorMessage || timelineJobError }}
        </Message>
        <Message
          v-else-if="isRefreshingAfterTimelineJob"
          severity="info"
          :closable="false"
        >
          Refreshing workspace data...
        </Message>
      </div>

      <template #footer>
        <Button
          v-if="timelineJobStatus === 'FAILED'"
          label="Retry Status"
          icon="pi pi-refresh"
          outlined
          :disabled="!trackedTimelineJobId"
          @click="retryTimelineJobProgress"
        />
        <Button
          label="Close"
          icon="pi pi-times"
          :disabled="!timelineJobCanClose"
          @click="showTimelineGenerationDialog = false"
        />
      </template>
    </Dialog>

    <ConfirmDialog group="trip-workspace-plan-item" />
  </AppLayout>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useTimezone } from '@/composables/useTimezone'
import { useTimelineRegeneration } from '@/composables/useTimelineRegeneration'
import { useTimelineItemSelection } from '@/composables/useTimelineItemSelection'
import { useTimelineLocationEditing } from '@/composables/useTimelineLocationEditing'
import { formatDistance, formatDuration } from '@/utils/calculationsHelpers'
import { getTimelineGeographicBounds, getWeatherQueryRange, padWeatherBounds } from '@/utils/timelineWeatherQuery'
import { useTripsStore } from '@/stores/trips'
import { useAuthStore } from '@/stores/auth'
import { useImmichStore } from '@/stores/immich'
import { useTimelineStore } from '@/stores/timeline'
import { useFriendsStore } from '@/stores/friends'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import TimelineMap from '@/components/maps/TimelineMap.vue'
import MapContainer from '@/components/maps/MapContainer.vue'
import TimelineContainer from '@/components/timeline/TimelineContainer.vue'
import TimelineLocationEditDialogs from '@/components/timeline/TimelineLocationEditDialogs.vue'
import TimelineSplitLayout from '@/components/timeline/TimelineSplitLayout.vue'
import ImmichLatestPhotosSection from '@/components/location-analytics/ImmichLatestPhotosSection.vue'
import TripStopsRail from '@/components/trips/workspace/TripStopsRail.vue'
import PlanItemPoiImages from '@/components/trips/workspace/PlanItemPoiImages.vue'
import TripSummaryBar from '@/components/trips/workspace/TripSummaryBar.vue'
import TripReconstructionDialog from '@/components/trips/TripReconstructionDialog.vue'
import L from 'leaflet'
import maplibregl from 'maplibre-gl'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import ProgressBar from 'primevue/progressbar'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import ConfirmDialog from 'primevue/confirmdialog'
import TripPlanLocationSearchInput from '@/components/trips/TripPlanLocationSearchInput.vue'
import {
  getTripPlanSuggestionCoordinates,
  useTripPlanLocationSearch
} from '@/composables/useTripPlanLocationSearch'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const timezone = useTimezone()
const authStore = useAuthStore()
const tripsStore = useTripsStore()
const immichStore = useImmichStore()
const timelineStore = useTimelineStore()
const friendsStore = useFriendsStore()

const {
  currentTrip,
  tripSummary,
  tripPlanItems,
  workspaceTimeline,
  workspacePath,
  visitSuggestions
} = storeToRefs(tripsStore)
const { demoReadOnly } = storeToRefs(authStore)
const { weatherSamples } = storeToRefs(timelineStore)

const isInitialLoading = ref(true)
const workspaceLoading = ref(false)
const pageError = ref(null)
const selectedDateRange = ref(null)
const activeRange = ref({ start: null, end: null })
const timelineMapRef = ref(null)
const timelineSplitLayoutRef = ref(null)
const timelineContainerRef = ref(null)
const planItemDialogMapRef = ref(null)
const planLocationSearchRef = ref(null)
const activeWorkspaceTab = ref('overview')

const {
  clearTimelineSelection: clearWorkspaceTimelineSelection,
  handleTimelineItemClick: handleWorkspaceTimelineItemClick,
  handleTimelineMarkerClick: handleWorkspaceTimelineMarkerClick,
  handleHighlightedPathClick: handleWorkspaceHighlightedPathClick
} = useTimelineItemSelection({
  collapseForMobileSelection: () => {
    timelineSplitLayoutRef.value?.collapseForMobileSelection?.()
  }
})

const {
  showFavoriteDialog,
  selectedFavorite,
  closeFavoriteEditor: closeWorkspaceFavoriteEditor,
  handleFavoriteDialogSave: handleWorkspaceFavoriteDialogSave,
  timelineRegenerationVisible: locationRegenerationVisible,
  timelineRegenerationType: locationRegenerationType,
  currentJobId: locationCurrentJobId,
  jobProgress: locationJobProgress,
  showGeocodingEditDialog,
  editGeocodingData,
  closeGeocodingDialog: closeWorkspaceGeocodingDialog,
  handleSaveGeocoding: handleWorkspaceSaveGeocoding,
  handleRenameStay: handleWorkspaceRenameStay
} = useTimelineLocationEditing({
  onFavoriteSaved: (data) => {
    tripsStore.applyWorkspaceStayFavoriteUpdate(data)
  },
  onGeocodingSaved: (oldGeocodingId, updated) => {
    tripsStore.applyWorkspaceStayGeocodingUpdate(oldGeocodingId, updated)
  }
})

const showPlanItemDialog = ref(false)
const editingPlanItemId = ref(null)
const isSubmittingPlanItem = ref(false)
const planItemErrors = ref({})
const isResolvingPlanSuggestion = ref(false)
const planItemLocationSource = ref(null)
const planItemSuggestionRequestToken = ref(0)
const planItemDialogMapId = ref(Math.random().toString(36).slice(2, 10))
const planItemDialogMapCenter = ref([37.7749, -122.4194])
const planItemDialogMapZoom = ref(13)
const planItemDialogMapInstance = ref(null)
const planItemDialogMapAdapter = ref(null)
const showCollaboratorsDialog = ref(false)
const showReconstructionDialog = ref(false)
const showTimelineGenerationDialog = ref(false)
const isRefreshingAfterTimelineJob = ref(false)

const {
  query: planItemLocationSearchQuery,
  suggestions: planItemLocationSearchSuggestions,
  isLoading: isPlanItemLocationSearchLoading,
  error: planItemLocationSearchError,
  search: handlePlanItemLocationSearchComplete,
  reset: resetPlanItemLocationSearchState
} = useTripPlanLocationSearch({
  getBias: () => resolvePlanItemLocationSearchBias(),
  fallbackLabel: 'Planned place'
})
const collaboratorsLoading = ref(false)
const isSavingCollaborator = ref(false)
const collaborators = ref([])
const availableFriends = ref([])
const newCollaboratorFriendId = ref(null)
const newCollaboratorRole = ref('VIEW')
const {
  currentJobId: trackedTimelineJobId,
  jobProgress: timelineJobProgress,
  jobError: timelineJobError,
  trackExistingTimelineJob,
  refreshCurrentJobProgress,
  clearTrackedTimelineJob
} = useTimelineRegeneration()

const planItemForm = ref({
  title: '',
  notes: '',
  latitude: null,
  longitude: null,
  plannedDay: null,
  priority: 'OPTIONAL',
  orderIndex: 0
})

const priorityOptions = [
  { label: 'Optional', value: 'OPTIONAL' },
  { label: 'Must', value: 'MUST' }
]

const collaboratorRoleOptions = [
  { label: 'Viewer', value: 'VIEW' },
  { label: 'Editor', value: 'EDIT' }
]

const tripId = computed(() => Number(route.params.tripId))
const isOwner = computed(() => Boolean(currentTrip.value?.isOwner) || String(currentTrip.value?.accessRole || '').toUpperCase() === 'OWNER')
const accessRole = computed(() => String(currentTrip.value?.accessRole || (isOwner.value ? 'OWNER' : 'VIEW')).toUpperCase())
const canEditPlanItems = computed(() => isOwner.value || accessRole.value === 'EDIT')
const canReconstructTrip = computed(() => isOwner.value && !isUnplannedTrip.value)
const showAccessModeBanner = computed(() => !isOwner.value)
const accessModeBannerText = computed(() => {
  if (accessRole.value === 'EDIT') {
    return 'Editor access: you can update planned stops and visit states, but only the owner can change trip metadata.'
  }
  return 'Viewer access: this trip is read-only for you.'
})

const pageTitle = computed(() => currentTrip.value?.name || 'Trip Planner')
const pageSubtitle = computed(() => {
  if (!currentTrip.value) return 'Workspace'

  if (isUnplannedTrip.value) {
    return 'Unplanned • Set trip dates later to unlock timeline and analytics'
  }

  const status = String(currentTrip.value.status || '').toLowerCase()
  const statusLabel = status ? status.charAt(0).toUpperCase() + status.slice(1) : 'Unknown'
  return `${statusLabel} • ${formatDateTime(currentTrip.value.startTime)} - ${formatDateTime(currentTrip.value.endTime)}`
})

/**
 * Mobile-only subtitle. The date picker in the action row already shows the trip range, so
 * repeating "01/10/2026 00:00 - 04/10/2026 23:59" here costs two lines and adds nothing.
 * What the picker cannot show is where the trip stands and how long it runs.
 */
const pageSubtitleCompact = computed(() => {
  if (!currentTrip.value) return 'Workspace'
  if (isUnplannedTrip.value) return 'Unplanned • Dates not set'

  const status = String(currentTrip.value.status || '').toLowerCase()
  const statusLabel = status ? status.charAt(0).toUpperCase() + status.slice(1) : 'Unknown'

  const start = currentTrip.value.startTime ? timezone.fromUtc(currentTrip.value.startTime) : null
  const end = currentTrip.value.endTime ? timezone.fromUtc(currentTrip.value.endTime) : null
  const days = start && end
    ? Math.max(1, end.startOf('day').diff(start.startOf('day'), 'day') + 1)
    : null

  return days ? `${statusLabel} • ${days} day${days === 1 ? '' : 's'}` : statusLabel
})

const summaryPlanTotal = computed(() => tripSummary.value?.planItemsTotal || 0)
const summaryVisitedCount = computed(() => tripSummary.value?.planItemsVisited || 0)
// Numeric, not pre-formatted: TripSummaryBar renders the unit so the bar stays the
// single place that decides how a metric is displayed.
const summaryCompletion = computed(() => Math.round(tripSummary.value?.planCompletionRate || 0))
const mustPlanTotal = computed(() => (tripPlanItems.value || []).filter((item) => item?.priority === 'MUST').length)
const mustVisitedCount = computed(() => (tripPlanItems.value || []).filter((item) => item?.priority === 'MUST' && item?.isVisited).length)
const mustCompletionLabel = computed(() => {
  if (mustPlanTotal.value === 0) return '0%'
  return `${Math.round((mustVisitedCount.value / mustPlanTotal.value) * 100)}%`
})

const hasPathData = computed(() => Array.isArray(workspacePath.value?.points) && workspacePath.value.points.length > 0)
const hasTimelineData = computed(() => Array.isArray(workspaceTimeline.value) && workspaceTimeline.value.length > 0)
const workspaceTimelineMapStyle = computed(() => ({
  height: '100%',
  minHeight: '0'
}))
const isUnplannedTrip = computed(() => {
  const status = String(currentTrip.value?.status || '').toUpperCase()
  return status === 'UNPLANNED' || (!currentTrip.value?.startTime && !currentTrip.value?.endTime)
})
const isFutureTrip = computed(() => {
  if (isUnplannedTrip.value) return false
  if (!currentTrip.value?.startTime) return false
  return timezone.fromUtc(currentTrip.value.startTime).isAfter(timezone.now())
})
const isPlanningMode = computed(() => isFutureTrip.value && !hasPathData.value && !hasTimelineData.value)
const isPlanningWorkspace = computed(() => isUnplannedTrip.value || isPlanningMode.value)
const isActiveTrip = computed(() => String(currentTrip.value?.status || '').toUpperCase() === 'ACTIVE')
const showOverviewSection = computed(() => activeWorkspaceTab.value === 'overview' && !isFutureTrip.value && !isUnplannedTrip.value)
const showPlanSection = computed(() => activeWorkspaceTab.value === 'plan')
const showDiscoverSection = computed(() => activeWorkspaceTab.value === 'discover')
const showPlanningPanelMode = computed(() => isPlanningWorkspace.value || (showPlanSection.value && isActiveTrip.value))
const workspaceTabs = computed(() => {
  // Discovery is offered for trips that have not happened yet - the case where the user
  // has never been there and needs ideas. It is the same trip type that has no Overview.
  const discover = { key: 'discover', label: 'Discover', icon: 'pi pi-compass' }

  if (isUnplannedTrip.value) {
    return [{ key: 'plan', label: 'Plan', icon: 'pi pi-list-check' }, discover]
  }

  const tabs = []
  if (!isFutureTrip.value) {
    tabs.push({ key: 'overview', label: 'Overview', icon: 'pi pi-chart-line' })
  }
  tabs.push({ key: 'plan', label: 'Plan', icon: 'pi pi-list-check' })
  tabs.push(discover)
  return tabs
})
const comparisonCardTitle = computed(() => ((isPlanningWorkspace.value || isActiveTrip.value) ? 'Planned Stops' : 'Plan vs Actual'))
const planningPanelTitle = computed(() => {
  if (isUnplannedTrip.value) return 'Unplanned trip planning mode'
  return isPlanningMode.value ? 'Future trip planning mode' : 'Active trip planning mode'
})
const planningPanelPrimaryText = computed(() => (
  isUnplannedTrip.value
    ? 'This trip has no schedule yet. Build your place plan first, then set trip dates when you are ready.'
    : (isPlanningMode.value
      ? 'This trip has no actual timeline data yet.'
      : 'This trip is in progress. Keep adding planned stops while actual visits are matched automatically.')
))
const planningPanelHintText = computed(() => {
  if (!canEditPlanItems.value) {
    return 'You have read-only access to this trip plan.'
  }
  return 'Use Add Place to search and add planned stops quickly, or right-click the map as a shortcut.'
})
const sortPlanItems = (items) => {
  const safeItems = Array.isArray(items) ? items : []
  const priorityRank = (priority) => (String(priority || '').toUpperCase() === 'MUST' ? 0 : 1)
  return [...safeItems].sort((a, b) => {
    const priorityDiff = priorityRank(a?.priority) - priorityRank(b?.priority)
    if (priorityDiff !== 0) return priorityDiff
    const orderA = a?.orderIndex ?? 0
    const orderB = b?.orderIndex ?? 0
    if (orderA !== orderB) return orderA - orderB
    return Number(a?.id || 0) - Number(b?.id || 0)
  })
}
const sortedTripPlanItems = computed(() => sortPlanItems(tripPlanItems.value))
const hasPlanItems = computed(() => sortedTripPlanItems.value.length > 0)

const parsePlanItemCoordinate = (value, min, max) => {
  if (value === null || value === undefined || value === '') {
    return null
  }

  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return null
  }

  if (Number.isFinite(min) && parsed < min) {
    return null
  }

  if (Number.isFinite(max) && parsed > max) {
    return null
  }

  return parsed
}

const hasPlanItemCoordinates = computed(() => {
  const latitude = parsePlanItemCoordinate(planItemForm.value.latitude, -90, 90)
  const longitude = parsePlanItemCoordinate(planItemForm.value.longitude, -180, 180)
  return latitude !== null && longitude !== null
})
const planItemMapSourceHint = computed(() => {
  if (!planItemLocationSource.value) return ''

  const latitude = Number(planItemLocationSource.value.latitude)
  const longitude = Number(planItemLocationSource.value.longitude)
  const source = planItemLocationSource.value.source
  const sourceLabel = source === 'context-menu'
    ? 'Pinned from map (context menu)'
    : source === 'dialog-map'
      ? 'Pinned from map (dialog)'
      : 'Pinned from map'

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return `${sourceLabel}.`
  }

  return `${sourceLabel}: ${latitude.toFixed(5)}, ${longitude.toFixed(5)}`
})
const planningPanelItems = computed(() => {
  return sortPlanItems(tripPlanItems.value)
    .filter((item) => typeof item.latitude === 'number' && typeof item.longitude === 'number')
    .slice(0, 8)
})
const tripPlanMapItems = computed(() => {
  return (tripPlanItems.value || [])
    .filter((item) => typeof item.latitude === 'number' && typeof item.longitude === 'number')
    .map((item) => ({
      id: `trip-plan-${item.id}`,
      name: item.title || 'Planned place',
      type: 'trip-plan',
      planItemId: item.id,
      priority: item.priority || 'OPTIONAL',
      latitude: item.latitude,
      longitude: item.longitude,
      // Carried through for the marker's hover card, which otherwise had nothing to
      // show beyond a name and always reported "no day" / "not visited".
      plannedDay: item.plannedDay || null,
      notes: item.notes || '',
      isVisited: Boolean(item.isVisited),
      visitConfidence: item.visitConfidence ?? null,
      manualOverrideState: item.manualOverrideState || null
    }))
})
const workspaceFallbackCenter = computed(() => {
  const firstPathPoint = workspacePath.value?.points?.[0]
  if (
    firstPathPoint
    && typeof firstPathPoint.latitude === 'number'
    && typeof firstPathPoint.longitude === 'number'
  ) {
    return [firstPathPoint.latitude, firstPathPoint.longitude]
  }

  const firstPlanPoint = (tripPlanItems.value || []).find((item) => (
    typeof item.latitude === 'number' && typeof item.longitude === 'number'
  ))
  if (firstPlanPoint) {
    return [firstPlanPoint.latitude, firstPlanPoint.longitude]
  }

  return [37.7749, -122.4194]
})
const tripMinDate = computed(() => {
  if (isUnplannedTrip.value) return null
  if (!currentTrip.value?.startTime || !currentTrip.value?.endTime) return null
  return timezone.convertUtcRangeToCalendarDates(currentTrip.value.startTime, currentTrip.value.endTime)[0]
})

const tripMaxDate = computed(() => {
  if (isUnplannedTrip.value) return null
  if (!currentTrip.value?.startTime || !currentTrip.value?.endTime) return null
  return timezone.convertUtcRangeToCalendarDates(currentTrip.value.startTime, currentTrip.value.endTime)[1]
})

const activeDateRangeArray = computed(() => {
  if (!activeRange.value.start || !activeRange.value.end) {
    return []
  }
  return [activeRange.value.start, activeRange.value.end]
})

const tripImmichSearchParams = computed(() => {
  if (isFutureTrip.value || isUnplannedTrip.value || !immichStore.isConfigured) return null
  const start = activeRange.value.start || currentTrip.value?.startTime
  const end = activeRange.value.end || currentTrip.value?.endTime
  if (!start || !end) return null
  return {
    startDate: start,
    endDate: end
  }
})
const showTripPhotosSection = computed(() => (
  showOverviewSection.value &&
  !isUnplannedTrip.value &&
  Boolean(tripImmichSearchParams.value)
))
const availableFriendOptions = computed(() => {
  const selectedIds = new Set((collaborators.value || []).map((item) => String(item.userId)))
  return (availableFriends.value || [])
    .filter((friend) => !selectedIds.has(String(friend.friendId)))
    .map((friend) => ({
      value: friend.friendId,
      label: friend.fullName || friend.email || String(friend.friendId)
    }))
})
const timelineJobStatus = computed(() => String(timelineJobProgress.value?.status || '').toUpperCase())
const timelineJobCanClose = computed(() => {
  if (timelineJobError.value) return true
  if (!trackedTimelineJobId.value) return true
  return timelineJobStatus.value === 'COMPLETED' || timelineJobStatus.value === 'FAILED'
})
const timelineJobStatusLabel = computed(() => {
  switch (timelineJobStatus.value) {
    case 'QUEUED':
      return 'Queued'
    case 'RUNNING':
      return 'Running'
    case 'COMPLETED':
      return 'Completed'
    case 'FAILED':
      return 'Failed'
    default:
      return 'Starting'
  }
})
const timelineJobStatusSeverity = computed(() => {
  switch (timelineJobStatus.value) {
    case 'QUEUED':
      return 'info'
    case 'RUNNING':
      return 'warn'
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
    default:
      return 'secondary'
  }
})
const timelineJobCurrentStep = computed(() => (
  timelineJobProgress.value?.currentStep
  || (timelineJobStatus.value === 'FAILED' ? 'Timeline generation failed.' : 'Preparing timeline generation...')
))
const timelineJobProgressValue = computed(() => {
  const raw = Number(timelineJobProgress.value?.progressPercentage)
  if (!Number.isFinite(raw)) return 0
  return Math.min(100, Math.max(0, Math.round(raw)))
})
const timelineJobDurationLabel = computed(() => {
  const durationMs = Number(timelineJobProgress.value?.durationMs)
  if (!Number.isFinite(durationMs) || durationMs < 0) {
    return 'Duration: —'
  }
  return `Duration: ${formatJobDuration(durationMs)}`
})

const formatDateTime = (value) => {
  if (!value) return '—'
  return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value)}`
}

const formatJobDuration = (durationMs) => {
  const totalSeconds = Math.floor(durationMs / 1000)
  if (totalSeconds < 60) {
    return `${totalSeconds}s`
  }
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (minutes < 60) {
    return seconds > 0 ? `${minutes}m ${seconds}s` : `${minutes}m`
  }
  const hours = Math.floor(minutes / 60)
  const minutesRemainder = minutes % 60
  return minutesRemainder > 0 ? `${hours}h ${minutesRemainder}m` : `${hours}h`
}

const formatPlannedDay = (plannedDay) => {
  if (!plannedDay) return '—'
  const parsed = timezone.parseUrlDate(plannedDay, false)
  return parsed ? timezone.formatDateDisplay(parsed) : plannedDay
}

const getPrioritySeverity = (priority) => {
  return String(priority || '').toUpperCase() === 'MUST' ? 'danger' : 'warn'
}

const ensurePlanEditAccess = (message = 'You have read-only access to this trip plan.') => {
  if (canEditPlanItems.value) return true
  toast.add({
    severity: 'warn',
    summary: 'Read-Only Access',
    detail: message,
    life: 3500
  })
  return false
}

const getPhotoCoordinates = (photo) => {
  if (!photo) return null
  if (typeof photo.latitude === 'number' && typeof photo.longitude === 'number') {
    return { latitude: photo.latitude, longitude: photo.longitude }
  }
  const exif = photo.exifInfo || photo.exif || {}
  if (typeof exif.latitude === 'number' && typeof exif.longitude === 'number') {
    return { latitude: exif.latitude, longitude: exif.longitude }
  }
  return null
}

const ensureActiveWorkspaceTab = () => {
  const availableTabKeys = workspaceTabs.value.map((tab) => tab.key)
  if (!availableTabKeys.includes(activeWorkspaceTab.value)) {
    activeWorkspaceTab.value = isFutureTrip.value ? 'plan' : (availableTabKeys[0] || 'plan')
  }
}

const selectWorkspaceTab = (tabKey) => {
  activeWorkspaceTab.value = tabKey
}

const openTripScheduling = () => {
  if (!isOwner.value) return
  if (!tripId.value) return
  router.push({
    path: '/app/trips',
    query: {
      action: 'edit',
      tripId: String(tripId.value)
    }
  })
}

const openCollaboratorsDialog = () => {
  if (!isOwner.value) return
  showCollaboratorsDialog.value = true
}

const openReconstructionDialog = () => {
  if (!canReconstructTrip.value) return
  showReconstructionDialog.value = true
}

const loadCollaboratorsData = async () => {
  if (!isOwner.value) return
  collaboratorsLoading.value = true
  try {
    const [loadedCollaborators, loadedFriends] = await Promise.all([
      tripsStore.fetchTripCollaborators(tripId.value),
      friendsStore.fetchFriends()
    ])
    collaborators.value = Array.isArray(loadedCollaborators) ? loadedCollaborators : []
    availableFriends.value = Array.isArray(loadedFriends) ? loadedFriends : []
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Load Collaborators',
      detail: formatApiErrorDetail(error, 'Could not load collaborators.'),
      life: 5000
    })
  } finally {
    collaboratorsLoading.value = false
  }
}

const addCollaborator = async () => {
  if (!newCollaboratorFriendId.value) return
  isSavingCollaborator.value = true
  try {
    await tripsStore.setTripCollaborator(tripId.value, newCollaboratorFriendId.value, newCollaboratorRole.value)
    newCollaboratorFriendId.value = null
    newCollaboratorRole.value = 'VIEW'
    await loadCollaboratorsData()
    toast.add({
      severity: 'success',
      summary: 'Collaborator Added',
      detail: 'Trip collaborator updated successfully.',
      life: 2500
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Add Collaborator',
      detail: formatApiErrorDetail(error, 'Request failed.'),
      life: 5000
    })
  } finally {
    isSavingCollaborator.value = false
  }
}

const updateCollaboratorRole = async (collaborator, nextRole) => {
  if (!collaborator?.userId || !nextRole) return
  const previousRole = collaborator.accessRole
  collaborator.accessRole = nextRole
  isSavingCollaborator.value = true
  try {
    await tripsStore.setTripCollaborator(tripId.value, collaborator.userId, nextRole)
    toast.add({
      severity: 'success',
      summary: 'Role Updated',
      detail: 'Collaborator role updated.',
      life: 2200
    })
  } catch (error) {
    collaborator.accessRole = previousRole
    toast.add({
      severity: 'error',
      summary: 'Failed to Update Role',
      detail: formatApiErrorDetail(error, 'Request failed.'),
      life: 5000
    })
  } finally {
    isSavingCollaborator.value = false
  }
}

const removeCollaborator = async (collaborator) => {
  if (!collaborator?.userId) return
  isSavingCollaborator.value = true
  try {
    await tripsStore.removeTripCollaborator(tripId.value, collaborator.userId)
    collaborators.value = collaborators.value.filter((item) => String(item.userId) !== String(collaborator.userId))
    toast.add({
      severity: 'success',
      summary: 'Collaborator Removed',
      detail: 'Access revoked.',
      life: 2200
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Remove Collaborator',
      detail: formatApiErrorDetail(error, 'Request failed.'),
      life: 5000
    })
  } finally {
    isSavingCollaborator.value = false
  }
}

const clampRangeToTrip = (startTime, endTime, trip = currentTrip.value) => {
  if (!trip?.startTime || !trip?.endTime) {
    return { start: startTime, end: endTime }
  }

  let start = startTime || trip.startTime
  let end = endTime || trip.endTime

  const startMoment = timezone.fromUtc(start)
  const endMoment = timezone.fromUtc(end)
  const tripStart = timezone.fromUtc(trip.startTime)
  const tripEnd = timezone.fromUtc(trip.endTime)

  if (startMoment.isBefore(tripStart)) {
    start = trip.startTime
  }
  if (endMoment.isAfter(tripEnd)) {
    end = trip.endTime
  }

  if (!timezone.fromUtc(end).isAfter(timezone.fromUtc(start))) {
    start = trip.startTime
    end = trip.endTime
  }

  return { start, end }
}

const syncCalendarRange = (range) => {
  if (!range?.start || !range?.end) {
    activeRange.value = { start: null, end: null }
    selectedDateRange.value = null
    return
  }

  activeRange.value = { ...range }
  selectedDateRange.value = timezone.convertUtcRangeToCalendarDates(range.start, range.end)
}

const updateRouteQuery = async (range) => {
  await router.replace({
    path: route.path,
    query: {
      ...route.query,
      start: timezone.formatUrlDate(range.start),
      end: timezone.formatUrlDate(range.end)
    }
  })
}

const fetchWorkspaceRange = async (range, syncQuery = true) => {
  workspaceLoading.value = true
  clearWorkspaceTimelineSelection()
  try {
    await Promise.all([
      tripsStore.fetchWorkspaceTimeline(tripId.value, range.start, range.end),
      tripsStore.fetchWorkspacePath(tripId.value, range.start, range.end)
    ])
    await fetchWorkspaceWeatherData(range)
    if (syncQuery) {
      await updateRouteQuery(range)
    }
  } finally {
    workspaceLoading.value = false
  }
}

const fetchWorkspaceWeatherData = async (range) => {
  if (!range?.start || !range?.end || !Array.isArray(workspaceTimeline.value) || workspaceTimeline.value.length === 0) {
    timelineStore.clearWeatherSamples()
    return
  }

  const weatherRange = getWeatherQueryRange(range.start, range.end, workspaceTimeline.value)
  const apiBounds = padWeatherBounds(getTimelineGeographicBounds(workspaceTimeline.value))

  try {
    await timelineStore.fetchWeatherSamples(weatherRange.startTime, weatherRange.endTime, apiBounds)
  } catch (error) {
    console.warn('Trip workspace weather samples unavailable:', error)
    timelineStore.clearWeatherSamples()
  }
}

const triggerWorkspaceMapResize = () => {
  nextTick(() => {
    const invalidateMap = () => {
      timelineMapRef.value?.invalidateSize?.()
    }

    invalidateMap()
    setTimeout(invalidateMap, 260)
  })
}

const handleWorkspaceTimelineRefreshRequested = async () => {
  if (isUnplannedTrip.value || !activeRange.value?.start || !activeRange.value?.end) {
    return
  }

  await fetchWorkspaceRange(activeRange.value, false)
  await refreshVisitComparisons()
}

const handleWorkspaceResetDataGapOverride = (stayItem) => {
  const overrideId = stayItem?.dataGapOverrideId
  if (!overrideId) {
    return
  }

  confirm.require({
    header: 'Reset Manual Stay Override',
    message: 'Reset this manual Data Gap override back to automatic timeline detection? This will regenerate timeline segments.',
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        await timelineStore.resetDataGapStayOverride(overrideId)
        await handleWorkspaceTimelineRefreshRequested()
        toast.add({
          severity: 'success',
          summary: 'Override Reset',
          detail: 'Manual Data Gap override was reset to automatic behavior.',
          life: 3000
        })
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: 'Reset Failed',
          detail: formatApiErrorDetail(error, 'Failed to reset manual override'),
          life: 5000
        })
      }
    }
  })
}

const handleWorkspaceResetTripSplitOverride = (stayItem) => {
  const overrideId = stayItem?.tripSplitOverrideId
  if (!overrideId) {
    return
  }

  confirm.require({
    header: 'Undo Manual Trip Split',
    message: 'Undo this manual trip split and regenerate timeline segments?',
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        await timelineStore.resetTripStaySplitOverride(overrideId)
        await handleWorkspaceTimelineRefreshRequested()
        toast.add({
          severity: 'success',
          summary: 'Split Reset',
          detail: 'Manual trip split was reset to automatic behavior.',
          life: 3000
        })
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: 'Reset Failed',
          detail: formatApiErrorDetail(error, 'Failed to reset manual trip split'),
          life: 5000
        })
      }
    }
  })
}

const resolveInitialRange = () => {
  const trip = currentTrip.value
  if (!trip) {
    return { start: null, end: null }
  }

  const queryStart = timezone.parseUrlDate(route.query.start, false)
  const queryEnd = timezone.parseUrlDate(route.query.end, true)
  return clampRangeToTrip(queryStart || trip.startTime, queryEnd || trip.endTime, trip)
}

const refreshVisitComparisons = async () => {
  try {
    if (isUnplannedTrip.value) {
      visitSuggestions.value = []
      await Promise.all([
        tripsStore.fetchTripSummary(tripId.value),
        tripsStore.fetchTripPlanItems(tripId.value)
      ])
      return
    }

    await tripsStore.fetchVisitSuggestions(tripId.value)
    await Promise.all([
      tripsStore.fetchTripSummary(tripId.value),
      tripsStore.fetchTripPlanItems(tripId.value)
    ])
  } catch (error) {
    // Non-blocking: comparison hints should not break workspace load.
  }
}

const handleTripPhotoShowOnMap = (photo) => {
  const coords = getPhotoCoordinates(photo)
  if (!photo || !coords) {
    return
  }

  if (activeWorkspaceTab.value !== 'overview' && !isFutureTrip.value) {
    activeWorkspaceTab.value = 'overview'
  }

  const setView = timelineMapRef.value?.setView
  if (typeof setView !== 'function') {
    return
  }

  setView([coords.latitude, coords.longitude], 16, { animate: true })
}

const loadWorkspace = async () => {
  isInitialLoading.value = true
  pageError.value = null

  try {
    await Promise.all([
      tripsStore.fetchTrip(tripId.value),
      tripsStore.fetchTripSummary(tripId.value),
      tripsStore.fetchTripPlanItems(tripId.value)
    ])

    if (isUnplannedTrip.value) {
      workspaceTimeline.value = []
      workspacePath.value = {
        points: [],
        segments: [],
        pointCount: 0
      }
      timelineStore.clearWeatherSamples()
      visitSuggestions.value = []
      syncCalendarRange({ start: null, end: null })
      return
    }

    const initialRange = resolveInitialRange()
    if (!initialRange.start || !initialRange.end) {
      throw new Error('Trip date range is invalid')
    }

    syncCalendarRange(initialRange)
    await fetchWorkspaceRange(initialRange, true)
    await refreshVisitComparisons()
  } catch (error) {
    pageError.value = formatApiErrorDetail(error, 'Failed to load trip planner')
  } finally {
    isInitialLoading.value = false
  }
}

const handleDateRangeChange = async (value) => {
  if (isUnplannedTrip.value) {
    return
  }

  if (!value || !Array.isArray(value) || value.length < 2 || !value[0] || !value[1]) {
    return
  }

  const { start, end } = timezone.createDateRangeFromPicker(value[0], value[1])
  const clamped = clampRangeToTrip(start, end)
  syncCalendarRange(clamped)
  await fetchWorkspaceRange(clamped, true)
}

const resetToTripRange = async () => {
  if (isUnplannedTrip.value) return
  if (!currentTrip.value) return
  const range = clampRangeToTrip(currentTrip.value.startTime, currentTrip.value.endTime)
  syncCalendarRange(range)
  await fetchWorkspaceRange(range, true)
}

const applyPlanSuggestionToForm = async (suggestion) => {
  if (!suggestion) {
    return
  }

  const resolvedTitle = suggestion.title || planItemForm.value.title || 'Planned place'
  planItemForm.value.title = resolvedTitle
  planItemForm.value.latitude = typeof suggestion.latitude === 'number' ? suggestion.latitude : planItemForm.value.latitude
  planItemForm.value.longitude = typeof suggestion.longitude === 'number' ? suggestion.longitude : planItemForm.value.longitude
}

const PLAN_ITEM_DIALOG_MARKER_HTML = `
  <span class="plan-item-dialog-marker-pin" aria-hidden="true">
    <svg viewBox="0 0 30 40" xmlns="http://www.w3.org/2000/svg">
      <path d="M15 1.5C9.2 1.5 4.5 6.2 4.5 12c0 8.1 10.5 19.6 10.5 19.6S25.5 20.1 25.5 12c0-5.8-4.7-10.5-10.5-10.5z" fill="#f43f5e" stroke="#0f172a" stroke-width="1.4" />
      <circle cx="15" cy="12" r="4.2" fill="#fef08a" stroke="#0f172a" stroke-width="1.2" />
    </svg>
  </span>
`

const resolvePlanItemCoordinates = () => {
  const latitude = parsePlanItemCoordinate(planItemForm.value.latitude, -90, 90)
  const longitude = parsePlanItemCoordinate(planItemForm.value.longitude, -180, 180)
  if (latitude === null || longitude === null) {
    return null
  }

  return { lat: latitude, lon: longitude }
}

const resolvePlanItemDialogMapCenter = () => {
  const coordinates = resolvePlanItemCoordinates()
  if (coordinates) {
    return [coordinates.lat, coordinates.lon]
  }

  const workspaceMapInstance = timelineMapRef.value?.map?.value || timelineMapRef.value?.map
  const workspaceCenter = workspaceMapInstance?.getCenter?.()
  if (Number.isFinite(workspaceCenter?.lat) && Number.isFinite(workspaceCenter?.lng)) {
    return [workspaceCenter.lat, workspaceCenter.lng]
  }

  return Array.isArray(workspaceFallbackCenter.value) ? [...workspaceFallbackCenter.value] : [37.7749, -122.4194]
}

const setPlanItemDialogMapCenter = ({ zoom = null, centerFromSelection = false } = {}) => {
  if (centerFromSelection) {
    const coordinates = resolvePlanItemCoordinates()
    if (coordinates) {
      planItemDialogMapCenter.value = [coordinates.lat, coordinates.lon]
    } else {
      planItemDialogMapCenter.value = resolvePlanItemDialogMapCenter()
    }
  } else {
    planItemDialogMapCenter.value = resolvePlanItemDialogMapCenter()
  }

  if (Number.isFinite(zoom)) {
    planItemDialogMapZoom.value = zoom
  } else if (!hasPlanItemCoordinates.value) {
    planItemDialogMapZoom.value = 13
  }
}

const createRasterPlanItemDialogMapAdapter = (map) => {
  let marker = null

  const clear = () => {
    if (marker) {
      map.removeLayer(marker)
      marker = null
    }
  }

  const render = (coords) => {
    clear()
    if (!coords) return

    const markerIcon = L.divIcon({
      className: 'plan-item-dialog-marker-icon',
      html: PLAN_ITEM_DIALOG_MARKER_HTML,
      iconSize: [30, 40],
      iconAnchor: [15, 38]
    })

    marker = L.marker([coords.lat, coords.lon], { icon: markerIcon }).addTo(map)
  }

  return {
    render,
    cleanup: clear
  }
}

const createVectorPlanItemDialogMarkerElement = () => {
  const markerElement = document.createElement('div')
  markerElement.className = 'plan-item-dialog-marker-icon plan-item-dialog-marker-icon--vector'
  markerElement.innerHTML = PLAN_ITEM_DIALOG_MARKER_HTML
  return markerElement
}

const createVectorPlanItemDialogMapAdapter = (map) => {
  let marker = null

  const clear = () => {
    if (marker) {
      marker.remove()
      marker = null
    }
  }

  const render = (coords) => {
    clear()
    if (!coords) return

    marker = new maplibregl.Marker({
      element: createVectorPlanItemDialogMarkerElement(),
      anchor: 'bottom'
    })
      .setLngLat([coords.lon, coords.lat])
      .addTo(map)
  }

  return {
    render,
    cleanup: clear
  }
}

const createPlanItemDialogMapAdapter = (map) => {
  const mapMode = resolveMapEngineModeFromInstance(map, MAP_RENDER_MODES.RASTER)
  if (mapMode === MAP_RENDER_MODES.VECTOR) {
    return createVectorPlanItemDialogMapAdapter(map)
  }
  return createRasterPlanItemDialogMapAdapter(map)
}

const syncPlanItemDialogMapLocation = ({ recenter = false, zoom = null } = {}) => {
  if (recenter) {
    setPlanItemDialogMapCenter({ centerFromSelection: true, zoom })
  }

  if (!planItemDialogMapAdapter.value) {
    return
  }

  const coordinates = resolvePlanItemCoordinates()
  planItemDialogMapAdapter.value.render(coordinates)

  if (!planItemDialogMapInstance.value || !coordinates || !recenter) {
    return
  }

  const nextZoom = Number.isFinite(zoom) ? zoom : planItemDialogMapInstance.value.getZoom?.() || 13
  planItemDialogMapInstance.value.setView([coordinates.lat, coordinates.lon], nextZoom, { animate: true })
}

const handlePlanItemDialogMapReady = (map) => {
  planItemDialogMapInstance.value = map
  planItemDialogMapAdapter.value?.cleanup?.()
  planItemDialogMapAdapter.value = createPlanItemDialogMapAdapter(map)
  syncPlanItemDialogMapLocation({ recenter: true, zoom: hasPlanItemCoordinates.value ? 16 : planItemDialogMapZoom.value })
}

const cleanupPlanItemDialogMap = () => {
  planItemDialogMapAdapter.value?.cleanup?.()
  planItemDialogMapAdapter.value = null
  planItemDialogMapInstance.value = null
}

const resolvePlanSuggestionForCoordinates = async (latitude, longitude, {
  source = 'dialog-map',
  showUnavailableWarning = true
} = {}) => {
  const requestToken = ++planItemSuggestionRequestToken.value
  isResolvingPlanSuggestion.value = true
  planItemLocationSource.value = {
    source,
    latitude,
    longitude
  }

  try {
    const suggestion = await tripsStore.getPlanSuggestion(latitude, longitude)
    if (requestToken !== planItemSuggestionRequestToken.value) {
      return
    }
    if (suggestion) {
      await applyPlanSuggestionToForm(suggestion)
      syncPlanItemDialogMapLocation({ recenter: true, zoom: 16 })
    }
  } catch (error) {
    if (requestToken !== planItemSuggestionRequestToken.value || !showUnavailableWarning) {
      return
    }
    toast.add({
      severity: 'warn',
      summary: 'Plan suggestion unavailable',
      detail: formatApiErrorDetail(error, 'You can still edit title and save.'),
      life: 3000
    })
  } finally {
    if (requestToken === planItemSuggestionRequestToken.value) {
      isResolvingPlanSuggestion.value = false
    }
  }
}

const handlePlanItemDialogMapClick = async (event) => {
  const latitude = Number(event?.latlng?.lat)
  const longitude = Number(event?.latlng?.lng)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return
  }

  planItemForm.value.latitude = latitude
  planItemForm.value.longitude = longitude
  syncPlanItemDialogMapLocation({ recenter: true, zoom: 16 })
  await resolvePlanSuggestionForCoordinates(latitude, longitude, { source: 'dialog-map' })
}

const resolvePlanItemLocationSearchBias = () => {
  const coordinates = resolvePlanItemCoordinates()
  if (coordinates) {
    return { lat: coordinates.lat, lon: coordinates.lon }
  }

  const planItemMapCenter = planItemDialogMapInstance.value?.getCenter?.()
  if (Number.isFinite(planItemMapCenter?.lat) && Number.isFinite(planItemMapCenter?.lng)) {
    return { lat: planItemMapCenter.lat, lon: planItemMapCenter.lng }
  }

  const mapInstance = timelineMapRef.value?.map?.value || timelineMapRef.value?.map
  const mapCenter = mapInstance?.getCenter?.()
  if (Number.isFinite(mapCenter?.lat) && Number.isFinite(mapCenter?.lng)) {
    return { lat: mapCenter.lat, lon: mapCenter.lng }
  }

  if (Array.isArray(workspaceFallbackCenter.value) && workspaceFallbackCenter.value.length >= 2) {
    const [fallbackLat, fallbackLon] = workspaceFallbackCenter.value
    if (Number.isFinite(fallbackLat) && Number.isFinite(fallbackLon)) {
      return { lat: fallbackLat, lon: fallbackLon }
    }
  }

  return null
}

const handlePlanItemLocationSearchSelect = (suggestion) => {
  const coordinates = getTripPlanSuggestionCoordinates(suggestion)
  if (!coordinates) {
    return
  }

  planItemForm.value.latitude = coordinates.latitude
  planItemForm.value.longitude = coordinates.longitude

  const selectedTitle = suggestion?.title?.trim() || suggestion?.displayName?.trim()
  if (selectedTitle) {
    planItemForm.value.title = selectedTitle
  }

  planItemLocationSource.value = null
  resetPlanItemLocationSearchState()
  syncPlanItemDialogMapLocation({ recenter: true, zoom: 16 })
}

const openPlanItemDialogFromCoordinates = async (lat, lon, source = 'map') => {
  resetPlanItemForm()
  planItemForm.value.latitude = lat
  planItemForm.value.longitude = lon
  setPlanItemDialogMapCenter({ centerFromSelection: true, zoom: 16 })
  showPlanItemDialog.value = true
  syncPlanItemDialogMapLocation({ recenter: true, zoom: 16 })
  await resolvePlanSuggestionForCoordinates(lat, lon, { source, showUnavailableWarning: true })
  await focusPlanLocationSearch()
}

const handlePlanToVisit = async (event) => {
  if (!ensurePlanEditAccess()) return
  const lat = event?.latlng?.lat
  const lon = event?.latlng?.lng
  if (typeof lat !== 'number' || typeof lon !== 'number') {
    return
  }
  await openPlanItemDialogFromCoordinates(lat, lon, 'context-menu')
}

// Which stop the map is currently focused on, so the rail can highlight it.
const focusedPlanItemId = ref(null)

// The rail's lens. 'plan' = the stops you intended; 'actual' = what happened.
const railLens = ref('plan')

const focusPlannedItemOnMap = (item) => {
  focusedPlanItemId.value = item?.id ?? null
  if (!item || typeof item.latitude !== 'number' || typeof item.longitude !== 'number') {
    return
  }

  const setView = timelineMapRef.value?.setView
  if (typeof setView !== 'function') {
    return
  }

  setView([item.latitude, item.longitude], 16, { animate: true })
}

const resolvePlanItemIdFromContext = (contextPayload) => {
  const contextItem = contextPayload?.item || contextPayload?.favorite || contextPayload
  if (!contextItem) return null

  if (contextItem.planItemId !== undefined && contextItem.planItemId !== null) {
    return Number(contextItem.planItemId)
  }

  const rawId = String(contextItem.id || '')
  if (rawId.startsWith('trip-plan-')) {
    const parsed = Number(rawId.replace('trip-plan-', ''))
    return Number.isFinite(parsed) ? parsed : null
  }

  return null
}

const findPlanItemById = (planItemId) => {
  if (planItemId === null || planItemId === undefined) return null
  return (tripPlanItems.value || []).find((item) => Number(item.id) === Number(planItemId)) || null
}

const handlePlanItemEditFromMap = (contextPayload) => {
  if (!ensurePlanEditAccess()) return
  const planItemId = resolvePlanItemIdFromContext(contextPayload)
  const planItem = findPlanItemById(planItemId)
  if (!planItem) {
    return
  }
  openEditPlanItemDialog(planItem)
}

const handlePlanItemDeleteFromMap = (contextPayload) => {
  if (!ensurePlanEditAccess()) return
  const planItemId = resolvePlanItemIdFromContext(contextPayload)
  const planItem = findPlanItemById(planItemId)
  if (!planItem) {
    return
  }
  confirmDeletePlanItem(planItem)
}

const handlePlanItemOverrideFromTable = (payload) => {
  if (!ensurePlanEditAccess()) return
  const item = payload?.item
  const action = payload?.action
  if (!item || !action) {
    return
  }
  applyVisitOverride(item, action)
}

const focusPlanLocationSearch = async () => {
  await nextTick()
  const autocompleteInput = planLocationSearchRef.value?.$el?.querySelector?.('input')
  if (autocompleteInput && typeof autocompleteInput.focus === 'function') {
    autocompleteInput.focus()
  }
}

const openCreatePlanItemDialog = async () => {
  if (!ensurePlanEditAccess()) return
  resetPlanItemForm()
  setPlanItemDialogMapCenter({ centerFromSelection: false, zoom: 13 })
  showPlanItemDialog.value = true
  syncPlanItemDialogMapLocation({ recenter: true, zoom: 13 })
  await focusPlanLocationSearch()
}

const resetPlanItemForm = () => {
  planItemForm.value = {
    title: '',
    notes: '',
    latitude: null,
    longitude: null,
    plannedDay: null,
    priority: 'OPTIONAL',
    orderIndex: 0
  }
  planItemErrors.value = {}
  editingPlanItemId.value = null
  planItemLocationSource.value = null
  planItemSuggestionRequestToken.value += 1
  isResolvingPlanSuggestion.value = false
  resetPlanItemLocationSearchState()
}

const parsePlannedDayToDate = (plannedDay) => {
  if (!plannedDay) return null
  return new Date(`${plannedDay}T12:00:00`)
}

const formatCalendarDate = (dateValue) => {
  if (!dateValue) return null
  const year = dateValue.getFullYear()
  const month = String(dateValue.getMonth() + 1).padStart(2, '0')
  const day = String(dateValue.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const openEditPlanItemDialog = (item) => {
  if (!ensurePlanEditAccess()) return
  resetPlanItemForm()
  editingPlanItemId.value = item.id
  planItemForm.value = {
    title: item.title || '',
    notes: item.notes || '',
    latitude: item.latitude ?? null,
    longitude: item.longitude ?? null,
    plannedDay: parsePlannedDayToDate(item.plannedDay),
    priority: item.priority || 'OPTIONAL',
    orderIndex: item.orderIndex ?? 0
  }

  setPlanItemDialogMapCenter({ centerFromSelection: true, zoom: hasPlanItemCoordinates.value ? 16 : 13 })
  showPlanItemDialog.value = true
  syncPlanItemDialogMapLocation({ recenter: true, zoom: hasPlanItemCoordinates.value ? 16 : 13 })
}

const validatePlanItem = () => {
  planItemErrors.value = {}

  if (!planItemForm.value.title || !planItemForm.value.title.trim()) {
    planItemErrors.value.title = 'Title is required'
  }

  return Object.keys(planItemErrors.value).length === 0
}

/**
 * Names the stop from a suggested POI. Only fills fields the user has not already set, so
 * clicking a photo can never overwrite typing they have done.
 */
const handlePoiSuggestionSelect = (poi) => {
  if (!poi) return

  // The title is set unconditionally: the dialog pre-fills it from reverse geocoding, so
  // a fill-if-empty rule meant clicking a photo appeared to do nothing. An explicit click
  // is an explicit choice - it outranks the automatic suggestion.
  planItemForm.value.title = poi.name

  // Notes are only filled when blank, so a click cannot discard something the user wrote.
  if (!planItemForm.value.notes?.trim() && poi.description) {
    planItemForm.value.notes = poi.description
  }

  if (Number.isFinite(poi.latitude) && Number.isFinite(poi.longitude)) {
    planItemForm.value.latitude = poi.latitude
    planItemForm.value.longitude = poi.longitude
    // Move the dialog's map too, otherwise the pin stays behind while the coordinates
    // underneath it change.
    syncPlanItemDialogMapLocation({ recenter: true, zoom: 15 })
  }
}

/**
 * Adds a stop from the rail's add panel, whether it came from a name search or from
 * discovery. Deliberately one click: the full plan-item dialog asks for day, priority and
 * order, which is too much ceremony for "add this museum". It is refined afterwards in the
 * rail, where the stop appears immediately with its day group.
 */
const handleAddStopFromRail = async (place) => {
  if (!place?.title || !ensurePlanEditAccess()) return

  try {
    await tripsStore.createTripPlanItem(tripId.value, {
      title: place.title,
      notes: place.description || null,
      latitude: place.latitude ?? null,
      longitude: place.longitude ?? null,
      plannedDay: null,
      priority: 'OPTIONAL',
      orderIndex: (tripPlanItems.value || []).length
    })
    toast.add({
      severity: 'success',
      summary: 'Added to plan',
      detail: `"${place.title}" was added to your stops`,
      life: 2500
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Could not add place',
      detail: error?.userMessage || error?.message || 'Failed to add this place',
      life: 3000
    })
  }
}

/** Mark a stop visited from the rail, through the same override path used everywhere else. */
const handleMarkVisitedFromRail = (stop) => {
  if (!stop || !ensurePlanEditAccess()) return
  applyVisitOverride(stop, 'CONFIRM_VISITED')
}

const submitPlanItem = async () => {
  if (!ensurePlanEditAccess()) return
  if (!validatePlanItem()) return

  isSubmittingPlanItem.value = true
  try {
    const payload = {
      title: planItemForm.value.title.trim(),
      notes: planItemForm.value.notes?.trim() || null,
      latitude: planItemForm.value.latitude ?? null,
      longitude: planItemForm.value.longitude ?? null,
      plannedDay: formatCalendarDate(planItemForm.value.plannedDay),
      priority: planItemForm.value.priority || 'OPTIONAL',
      orderIndex: planItemForm.value.orderIndex ?? 0
    }

    if (editingPlanItemId.value) {
      await tripsStore.updateTripPlanItem(tripId.value, editingPlanItemId.value, payload)
      toast.add({
        severity: 'success',
        summary: 'Plan Item Updated',
        detail: 'Trip plan item updated successfully',
        life: 2500
      })
    } else {
      await tripsStore.createTripPlanItem(tripId.value, payload)
      toast.add({
        severity: 'success',
        summary: 'Plan Item Added',
        detail: 'Trip plan item added successfully',
        life: 2500
      })
    }

    await Promise.all([
      tripsStore.fetchTripSummary(tripId.value),
      refreshVisitComparisons()
    ])
    showPlanItemDialog.value = false
    resetPlanItemForm()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Save Plan Item',
      detail: formatApiErrorDetail(error, 'Request failed'),
      life: 5000
    })
  } finally {
    isSubmittingPlanItem.value = false
  }
}

const confirmDeletePlanItem = (item) => {
  if (!ensurePlanEditAccess()) return
  confirm.require({
    group: 'trip-workspace-plan-item',
    message: `Delete plan item "${item.title}"?`,
    header: 'Delete Plan Item',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await tripsStore.deleteTripPlanItem(tripId.value, item.id)
        await Promise.all([
          tripsStore.fetchTripSummary(tripId.value),
          refreshVisitComparisons()
        ])
        toast.add({
          severity: 'success',
          summary: 'Plan Item Deleted',
          detail: 'Trip plan item removed',
          life: 2500
        })
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: 'Failed to Delete Plan Item',
          detail: formatApiErrorDetail(error, 'Delete failed'),
          life: 5000
        })
      }
    }
  })
}

const applyVisitOverride = async (item, action) => {
  if (!ensurePlanEditAccess()) return
  try {
    await tripsStore.applyVisitOverride(
      tripId.value,
      item.id,
      action,
      action === 'CONFIRM_VISITED' ? timezone.now().utc().toISOString() : null
    )
    await Promise.all([
      tripsStore.fetchTripSummary(tripId.value),
      refreshVisitComparisons()
    ])
    toast.add({
      severity: 'success',
      summary: 'Visit Status Updated',
      detail: 'Plan item visit status has been updated',
      life: 2500
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Update Visit Status',
      detail: formatApiErrorDetail(error, 'Update failed'),
      life: 5000
    })
  }
}

const openTimelineGenerationDialogForJob = async (jobId) => {
  if (!jobId) return

  isRefreshingAfterTimelineJob.value = false
  showTimelineGenerationDialog.value = true

  await trackExistingTimelineJob(String(jobId), {
    modalType: 'reconstruction',
    showModal: false,
    autoCloseOnCompleted: false,
    autoCloseOnFailed: false,
    autoCloseOnTrackingError: false,
    onCompleted: async () => {
      isRefreshingAfterTimelineJob.value = true

      try {
        await Promise.all([
          tripsStore.fetchTrip(tripId.value),
          tripsStore.fetchTripSummary(tripId.value),
          tripsStore.fetchTripPlanItems(tripId.value)
        ])

        if (!isUnplannedTrip.value && activeRange.value?.start && activeRange.value?.end) {
          await fetchWorkspaceRange(activeRange.value, false)
        }

        await refreshVisitComparisons()

        toast.add({
          severity: 'success',
          summary: 'Timeline Generation Complete',
          detail: 'Trip workspace was refreshed with regenerated data.',
          life: 3200
        })
      } catch (error) {
        toast.add({
          severity: 'warn',
          summary: 'Refresh Incomplete',
          detail: formatApiErrorDetail(error, 'Timeline completed, but workspace refresh failed.'),
          life: 5000
        })
      } finally {
        isRefreshingAfterTimelineJob.value = false
      }
    },
    onFailed: (progress) => {
      toast.add({
        severity: 'error',
        summary: 'Timeline Generation Failed',
        detail: progress?.errorMessage || timelineJobError.value || 'Job failed.',
        life: 5000
      })
    },
    onTrackingError: (error) => {
      toast.add({
        severity: 'error',
        summary: 'Timeline Job Tracking Failed',
        detail: error,
        life: 5000
      })
    }
  })
}

const retryTimelineJobProgress = async () => {
  if (!trackedTimelineJobId.value) return
  await refreshCurrentJobProgress()
}

const handleTimelineGenerationDialogHide = () => {
  if (!timelineJobCanClose.value) {
    showTimelineGenerationDialog.value = true
    return
  }
  clearTrackedTimelineJob()
  isRefreshingAfterTimelineJob.value = false
}

const handleReconstructionCommitted = async (result) => {
  showReconstructionDialog.value = false
  const jobId = result?.jobId

  if (jobId) {
    await openTimelineGenerationDialogForJob(jobId)
    return
  }

  // No job was started (e.g. all points were duplicates or an active job already exists).
  if (!isUnplannedTrip.value && activeRange.value?.start && activeRange.value?.end) {
    fetchWorkspaceRange(activeRange.value, false)
  }
}

onMounted(async () => {
  await loadWorkspace()
  ensureActiveWorkspaceTab()
})

onUnmounted(() => {
  clearWorkspaceTimelineSelection()
  cleanupPlanItemDialogMap()
})

watch(workspaceTabs, () => {
  ensureActiveWorkspaceTab()
}, { deep: true })

watch(showPlanItemDialog, async (nextVisible) => {
  if (!nextVisible) {
    cleanupPlanItemDialogMap()
    return
  }

  await nextTick()
  planItemDialogMapRef.value?.invalidateSize?.()
  syncPlanItemDialogMapLocation({
    recenter: true,
    zoom: hasPlanItemCoordinates.value ? 16 : planItemDialogMapZoom.value
  })
})
</script>

<style scoped>
.workspace-loading {
  display: flex;
  justify-content: center;
  padding: var(--gp-spacing-xl);
}

.workspace-page-header {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
}

.workspace-page-title-wrap {
  min-width: 0;
}

.workspace-page-title {
  margin: 0;
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  line-height: 1.2;
}

.workspace-page-subtitle {
  margin: var(--gp-spacing-xs) 0 0;
  font-size: 1rem;
  color: var(--gp-text-secondary);
  line-height: 1.4;
}

/* Desktop shows the full datetime subtitle; the compact status line takes over at <=768px. */
.workspace-page-subtitle--compact {
  display: none;
}

.unplanned-trip-banner {
  margin-bottom: var(--gp-spacing-sm);
}

.trip-access-banner {
  margin-bottom: var(--gp-spacing-sm);
}

.workspace-card {
  margin-bottom: var(--gp-spacing-md);
  /* Grows into the space the summary strip leaves. Only takes effect once the page frame has
     a definite height - see the mobile block at the end of this sheet. */
  flex: 1 1 auto;
}

.workspace-header-actions {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex-wrap: wrap;
}

.workspace-date-picker {
  min-width: 260px;
}

.collaborators-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.collaborator-add-row {
  display: grid;
  grid-template-columns: 1fr 10rem auto;
  gap: var(--gp-spacing-sm);
  align-items: center;
}

.collaborator-role-select {
  min-width: 8rem;
}

.collaborators-loading {
  display: flex;
  justify-content: center;
  padding: var(--gp-spacing-md);
}

.collaborators-empty {
  color: var(--gp-text-secondary);
  text-align: center;
  padding: var(--gp-spacing-md);
}

.collaborators-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.collaborator-row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: var(--gp-spacing-sm);
  align-items: center;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  padding: var(--gp-spacing-sm);
}

.collaborator-main {
  display: flex;
  flex-direction: column;
}

.collaborator-main small {
  color: var(--gp-text-secondary);
}

.workspace-card .workspace-timeline-split {
  --workspace-map-height: clamp(550px, 70vh, 760px);
  --timeline-split-side-width: clamp(360px, 22vw, 460px);
  /* The component ships `flex: 1`, i.e. flex-basis: 0%, and this element is the only child of
     the card's auto-height flex column. In a flex column the base size is what decides the
     main size, so with `0%` the `height` below is ignored and only `min-height` holds the card
     up. `auto` puts the declared height back in charge; the min-height stays as the backstop. */
  flex: 1 1 auto;
  height: calc(var(--workspace-map-height) + 0.5rem);
  min-height: calc(550px + 0.5rem);
}

.workspace-timeline-split :deep(.timeline-split-main) {
  height: 100%;
  min-height: 0;
}

.workspace-timeline-split :deep(.timeline-split-map-pane) {
  max-height: none;
}

.workspace-timeline-map {
  flex: 1 1 auto;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.workspace-timeline-split :deep(.map-container-wrapper),
.workspace-timeline-split :deep(.base-map) {
  height: 100%;
}

/* Desktop only. The rail inside the pane is absolutely positioned, so the pane has to be
   the containing block and carry a definite height - otherwise the rail grows with its
   content and the whole page scrolls. On mobile that same declaration fights the
   component's bottom sheet, so it is undone by the media block at the end of this sheet. */
.workspace-timeline-split :deep(.timeline-split-side-pane:not(.timeline-sheet--compact)) {
  max-height: none;
  background: var(--gp-surface-white);
  position: relative;
  min-height: 0;
  overflow: hidden;
}

.p-dark .workspace-timeline-split :deep(.timeline-split-side-pane:not(.timeline-sheet--compact)) {
  background: var(--gp-surface-dark);
}

.pane-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--gp-text-secondary);
  gap: var(--gp-spacing-sm);
}

.field-label {
  display: block;
  margin-bottom: var(--gp-spacing-xs);
  color: var(--gp-text-secondary);
  font-weight: 500;
}

.plan-item-dialog-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.95fr) minmax(0, 1.35fr);
  gap: var(--gp-spacing-md);
}

.plan-item-dialog-form {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.plan-item-dialog-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--gp-spacing-sm);
}

.plan-item-coordinate-pill {
  min-height: 2.5rem;
  border-radius: var(--gp-radius-small);
  border: 1px solid var(--gp-border-light);
  background: var(--gp-surface-light);
  padding: 0.55rem 0.7rem;
  color: var(--gp-text-secondary);
  font-size: 0.83rem;
  display: flex;
  align-items: center;
}

.plan-item-dialog-map-pane {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.plan-item-dialog-map-wrap {
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
  background: var(--gp-surface-light);
}

.plan-item-dialog-map-hint {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
}

.plan-item-location-search {
  width: 100%;
}

.timeline-generation-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.timeline-generation-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
}

.timeline-generation-job-id {
  color: var(--gp-text-secondary);
}

.timeline-generation-step {
  margin: 0;
  color: var(--gp-text-primary);
  font-weight: 500;
}

.timeline-generation-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
}

@media (max-width: 1279px) {
  .workspace-card .workspace-timeline-split {
    --timeline-split-side-width: clamp(340px, 30vw, 420px);
  }
}

@media (min-width: 1280px) and (max-width: 1599px) {
  .workspace-card .workspace-timeline-split {
    --timeline-split-side-width: clamp(360px, 24vw, 440px);
  }
}

@media (min-width: 1600px) {
  .workspace-card .workspace-timeline-split {
    --workspace-map-height: clamp(580px, 75vh, 860px);
    --timeline-split-side-width: clamp(380px, 20vw, 460px);
  }
}

@media (max-width: 1024px) {
  .workspace-card .workspace-timeline-split {
    --workspace-map-height: clamp(500px, 70vh, 640px);
    height: calc(var(--workspace-map-height) + 0.5rem);
    min-height: calc(500px + 0.5rem);
  }

  .plan-item-dialog-layout {
    grid-template-columns: 1fr;
  }

}

@media (max-width: 768px) {
  .workspace-card .workspace-timeline-split {
    /* Budget measured from the top of the viewport, after the chrome below was compacted:
         60 navbar + 8 padding + 43 title/subtitle + 12 gap + 44 action row
       + 12 header margin + 60 summary strip = ~239px, plus the card's own padding and the
       0.5rem the split adds. env() is subtracted because 100dvh includes the safe areas
       and both the navbar height and .gp-app-layout's bottom padding already claim them. */
    --workspace-map-height: clamp(
      260px,
      calc(100dvh - 288px - env(safe-area-inset-top) - env(safe-area-inset-bottom)),
      560px
    );
    height: calc(var(--workspace-map-height) + 0.5rem);
    /* The height above is this element's flex base size (flex-basis is `auto` here), so it is
       what the card is sized by, and the mobile frame normally stretches it past this value.
       260px is a floor for the frame being shorter than the clamp, not the card's height: at 0
       the card collapsed to its own padding (18px) and took the map and the sheet with it,
       because on mobile both panes are out of flow and contribute no height. */
    min-height: 260px;
  }

  .workspace-page-title {
    font-size: 1.25rem;
    line-height: 1.25;
  }

  /* The picker in the action row already shows the range, so the full "Upcoming •
     01/10/2026 00:00 - 04/10/2026 23:59" line is replaced by status + trip length.
     Swapped in CSS rather than behind a JS flag so it cannot disagree with the media
     query that actually positions the sheet. */
  .workspace-page-subtitle:not(.workspace-page-subtitle--compact) {
    display: none;
  }

  .workspace-page-subtitle--compact {
    display: block;
    margin-top: 2px;
    font-size: 0.8125rem;
    line-height: 1.25;
  }

  /* The dense map is the point of the card; 16px of card padding on each side was chrome. */
  .workspace-card :deep(.gp-card-content) {
    padding: var(--gp-spacing-sm);
  }

  /* ~60px of roll-up numbers that are only read, never acted on, and on a future trip are all
     zeros. The map is the page on a phone, and per-stop visited state already lives in the
     Stops list. Desktop keeps the strip. Same trade-off as the landscape block below, which
     still covers wide-but-short windows this query does not match. */
  .trip-summary-bar {
    display: none;
  }

  /* One 44px row: the picker flexes, the rest are icon-only square buttons. This replaces
     a wrap that produced three rows (Add Missing / picker / reset + Collaborators). */
  .workspace-header-actions {
    width: 100%;
    flex-wrap: nowrap;
    align-items: center;
    gap: var(--gp-spacing-xs);
  }

  .workspace-date-picker {
    /* The picker is the primary control and leads the row in the template, so it needs no
       `order` override - it used to sit between two icon buttons and was pulled to the front
       from here. */
    flex: 1 1 auto;
    /* Load-bearing: the base rule sets min-width: 260px, which would force a second row. */
    min-width: 0;
    width: auto;
    max-width: none;
  }

  .workspace-date-picker :deep(.p-datepicker-input) {
    min-height: 2.75rem;
    width: 100%;
    min-width: 0;
    text-overflow: ellipsis;
  }

  /* Icon-only. The label is hidden visually only - PrimeVue derives aria-label from the
     `label` prop, so the accessible name survives. Because `label` is still set PrimeVue
     never adds `p-button-icon-only`, hence the explicit 44px sizing. */
  .workspace-header-actions :deep(.p-button-label) {
    display: none;
  }

  .workspace-header-actions :deep(.p-button) {
    flex: 0 0 2.75rem;
    width: 2.75rem;
    min-width: 2.75rem;
    height: 2.75rem;
    padding: 0;
  }

  .collaborator-add-row {
    grid-template-columns: 1fr;
  }

  .collaborator-row {
    grid-template-columns: 1fr;
  }

  .plan-item-dialog-row {
    grid-template-columns: 1fr;
  }
}

/* Mobile sheet mode. The rule further up this file (which desktop needs, because the rail
   inside the pane is absolutely positioned and would otherwise anchor to
   .timeline-split-main) also matched here and turned the pane back into an in-flow flex
   child, so it landed at the top of the card with the map painted behind it. Identical
   selector, later in the sheet -> this wins; media queries add no specificity.
   Deliberately the same query the component uses, so landscape phones
   (max-height: 520px + coarse pointer) are covered - a max-width-only block is not. */
@media (max-width: 768px), (max-height: 520px) and (pointer: coarse) {
  .workspace-timeline-split :deep(.timeline-split-side-pane:not(.timeline-sheet--compact)) {
    position: absolute;
    top: auto;
    bottom: 0;
    /* The desktop rule above sets `max-height: none` at (0,4,0), which beats the
       component's mobile `max-height: calc(100% - 24px)` at (0,2,0). Restore it, or the
       sheet can grow past the card. */
    max-height: calc(100% - 24px);
  }
}

/* Landscape phones and short windows. The component is in sheet mode here too, so the card
   has to shrink with it. The max-width guard keeps a wide-but-short desktop window out. */
@media (max-height: 520px) and (max-width: 1024px) {
  .workspace-page-subtitle,
  .workspace-page-subtitle--compact {
    display: none;
  }

  .workspace-page-title {
    font-size: 1rem;
  }

  .workspace-card .workspace-timeline-split {
    --workspace-map-height: clamp(
      180px,
      calc(100dvh - 184px - env(safe-area-inset-top) - env(safe-area-inset-bottom)),
      340px
    );
    height: calc(var(--workspace-map-height) + 0.5rem);
    /* Base size again, not a target: the mobile frame stretches this to fill the screen. */
    min-height: 180px;
  }

  /* Landscape phones, and wide-but-short windows where the portrait mobile block above does
     not match. Same trade-off there: metrics are unavailable until the user rotates back or
     widens the window. */
  .trip-summary-bar {
    display: none;
  }
}

/* Mobile: give the frame a definite height so the card can take whatever the navbar, page
   padding, title, action row and summary strip leave over. Deliberately last in this sheet:
   the clamp blocks above become the flex base size rather than the final height, so the card
   keeps a sane minimum but is no longer capped - the 560px ceiling is what left the bottom of
   the screen empty while the map stayed short.

   Scoped to this page by the header class rather than applied to AppLayout/PageContainer
   globally: a `.gp-app-layout` rule alone would tie on specificity with the
   `min-height: 100vh` AppLayout itself sets, and lose or win by stylesheet order. */
@media (max-width: 768px), (max-height: 520px) and (pointer: coarse) {
  .gp-app-layout:has(.workspace-page-header) {
    height: 100dvh;
    min-height: 0;
  }

  /* Each of these is a flex item with the default `min-height: auto`, which would refuse to
     shrink and push the overflow out of the frame instead of giving the card a smaller share. */
  .gp-app-layout:has(.workspace-page-header) :deep(.gp-app-main),
  .gp-app-layout:has(.workspace-page-header) :deep(.gp-page-container),
  .gp-app-layout:has(.workspace-page-header) :deep(.gp-page-content) {
    min-height: 0;
  }

  /* The card has no height of its own to give the split - it is a block wrapping a flex
     column - so it has to hand its resolved height down explicitly. */
  .workspace-card :deep(.gp-card-content) {
    height: 100%;
  }

  /* 16px of page background under a card that is meant to reach the bottom of the screen. */
  .workspace-card {
    margin-bottom: 0;
  }
}
</style>

<style>
.plan-item-dialog {
  width: 95vw !important;
  max-width: 1480px !important;
}

.plan-item-dialog-marker-icon {
  background: transparent;
  border: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.plan-item-dialog-marker-pin {
  display: inline-flex;
  width: 30px;
  height: 40px;
  align-items: flex-end;
  justify-content: center;
}

.plan-item-dialog-marker-pin svg {
  width: 30px;
  height: 40px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.35));
}

.plan-item-dialog-marker-icon--vector .plan-item-dialog-marker-pin svg {
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.45));
}
</style>
