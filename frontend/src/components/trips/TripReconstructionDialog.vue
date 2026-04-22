<template>
  <Dialog
    v-model:visible="internalVisible"
    modal
    :header="dialogHeader"
    class="gp-dialog-xl trip-reconstruction-dialog"
    @hide="handleClose"
  >
    <div class="reconstruction-layout">
      <div class="segments-panel">
        <Message severity="info" :closable="false">
          {{ reconstructionHelpMessage }}
        </Message>

        <div class="segments-toolbar">
          <Button
            icon="pi pi-home"
            label="Add Stay"
            outlined
            @click="addSegment('STAY')"
          />
          <Button
            icon="pi pi-directions-alt"
            label="Add Trip"
            outlined
            @click="addSegment('TRIP')"
          />
        </div>

        <div v-if="segments.length === 0" class="segments-empty">
          No segments yet.
        </div>

        <div v-else class="segments-list">
          <div
            v-for="(segment, index) in segments"
            :key="segment.id"
            class="segment-card"
            :class="{ 'segment-card--active': activeSegmentId === segment.id }"
            @click="setActiveSegment(segment.id)"
          >
            <div class="segment-header">
              <div class="segment-title-row">
                <Tag
                  :value="segment.segmentType"
                  :severity="segment.segmentType === 'TRIP' ? 'info' : 'warn'"
                />
                <strong>Segment {{ index + 1 }}</strong>
              </div>
              <div class="segment-actions">
                <Button
                  icon="pi pi-angle-up"
                  text
                  rounded
                  size="small"
                  :disabled="index === 0"
                  @click.stop="moveSegment(index, -1)"
                />
                <Button
                  icon="pi pi-angle-down"
                  text
                  rounded
                  size="small"
                  :disabled="index === segments.length - 1"
                  @click.stop="moveSegment(index, 1)"
                />
                <Button
                  icon="pi pi-trash"
                  text
                  rounded
                  severity="danger"
                  size="small"
                  @click.stop="removeSegment(index)"
                />
              </div>
            </div>

            <div class="segment-grid">
              <div class="field-row">
                <label>Type</label>
                <Select
                  :model-value="segment.segmentType"
                  :options="segmentTypeOptions"
                  optionLabel="label"
                  optionValue="value"
                  class="w-full"
                  @update:model-value="updateSegmentType(index, $event)"
                />
              </div>

              <div class="field-row">
                <label>Start Time *</label>
                <DatePicker
                  :model-value="segment.startTime"
                  showTime
                  hourFormat="24"
                  :manualInput="false"
                  :dateFormat="timezone.getPrimeVueDatePickerFormat()"
                  class="w-full"
                  @update:model-value="updateSegmentField(index, 'startTime', $event)"
                />
              </div>

              <div class="field-row">
                <label>End Time *</label>
                <DatePicker
                  :model-value="segment.endTime"
                  showTime
                  hourFormat="24"
                  :manualInput="false"
                  :dateFormat="timezone.getPrimeVueDatePickerFormat()"
                  class="w-full"
                  @update:model-value="updateSegmentField(index, 'endTime', $event)"
                />
              </div>

              <template v-if="segment.segmentType === 'STAY'">
                <div class="field-row field-row--wide">
                  <label>Location Name</label>
                  <div class="resolved-location">
                    <div class="resolved-location-main">
                      <InputText
                        :model-value="segment.locationName"
                        class="w-full"
                        placeholder="Location will be resolved from map point"
                        @update:model-value="updateSegmentField(index, 'locationName', $event)"
                      />
                      <Tag
                        v-if="segment.locationSourceType"
                        :value="locationSourceLabel(segment)"
                        severity="secondary"
                      />
                    </div>
                    <small class="field-hint">Edited name is applied to the linked location source on Commit.</small>
                  </div>
                </div>

                <div class="field-row">
                  <label>Latitude *</label>
                  <InputNumber
                    :model-value="segment.latitude"
                    class="w-full"
                    :min="-90"
                    :max="90"
                    :minFractionDigits="6"
                    :maxFractionDigits="6"
                    @update:model-value="updateSegmentField(index, 'latitude', $event)"
                  />
                </div>

                <div class="field-row">
                  <label>Longitude *</label>
                  <InputNumber
                    :model-value="segment.longitude"
                    class="w-full"
                    :min="-180"
                    :max="180"
                    :minFractionDigits="6"
                    :maxFractionDigits="6"
                    @update:model-value="updateSegmentField(index, 'longitude', $event)"
                  />
                </div>
              </template>

              <template v-else>
                <div class="field-row">
                  <label>Movement Type</label>
                  <Select
                    :model-value="segment.movementType"
                    :options="movementTypeOptions"
                    optionLabel="label"
                    optionValue="value"
                    class="w-full"
                    @update:model-value="updateSegmentField(index, 'movementType', $event)"
                  />
                </div>

                <div class="field-row field-row--wide">
                  <label>Waypoints ({{ segment.waypoints.length }})</label>
                  <small class="field-hint">Click map to add points. Drag markers to adjust.</small>
                  <div class="waypoints-list">
                    <div
                      v-for="(waypoint, waypointIndex) in segment.waypoints"
                      :key="waypoint.id"
                      class="waypoint-row"
                    >
                      <Tag
                        :value="waypointLabel(waypointIndex, segment.waypoints.length)"
                        :severity="waypointTagSeverity(waypointIndex, segment.waypoints.length)"
                      />
                      <span class="waypoint-coords">
                        {{ waypoint.latitude.toFixed(5) }}, {{ waypoint.longitude.toFixed(5) }}
                      </span>
                      <div class="waypoint-actions">
                        <Button
                          icon="pi pi-angle-up"
                          text
                          rounded
                          size="small"
                          :disabled="waypointIndex === 0"
                          @click.stop="moveWaypoint(index, waypointIndex, -1)"
                        />
                        <Button
                          icon="pi pi-angle-down"
                          text
                          rounded
                          size="small"
                          :disabled="waypointIndex === segment.waypoints.length - 1"
                          @click.stop="moveWaypoint(index, waypointIndex, 1)"
                        />
                        <Button
                          icon="pi pi-trash"
                          text
                          rounded
                          severity="danger"
                          size="small"
                          @click.stop="removeWaypoint(index, waypointIndex)"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>

      <div class="map-panel">
        <MapContainer
          :map-id="`trip-reconstruction-map-${mapId}`"
          :center="mapCenter"
          :zoom="mapZoom"
          :show-controls="false"
          height="600px"
          width="100%"
          @map-ready="handleMapReady"
          @map-click="handleMapClick"
        />

        <div class="map-hint">
          <span v-if="activeSegment?.segmentType === 'TRIP'">
            Click map to append waypoint for this trip segment. Existing stays/trips are shown as context.
          </span>
          <span v-else-if="activeSegment?.segmentType === 'STAY'">
            Click map to place the stay location.
          </span>
          <span v-else>
            Select a segment to edit it on the map.
          </span>
        </div>

        <div v-if="previewResult" class="preview-box">
          <div class="preview-header">
            <Tag value="Validation Summary" severity="contrast" />
            <span class="preview-range">
              {{ formatDateTime(previewResult.startTime) }} → {{ formatDateTime(previewResult.endTime) }}
            </span>
          </div>

          <div class="preview-metrics">
            <div class="preview-metric">
              <span class="preview-metric-label">Segments</span>
              <strong>{{ previewSummary.stays }} stays · {{ previewSummary.trips }} trips</strong>
            </div>
            <div class="preview-metric">
              <span class="preview-metric-label">Generated GPS points</span>
              <strong>{{ previewResult.estimatedPoints }}</strong>
            </div>
            <div class="preview-metric">
              <span class="preview-metric-label">Covered</span>
              <strong>{{ formatDurationMinutes(previewSummary.coveredMinutes) }}</strong>
            </div>
            <div class="preview-metric">
              <span class="preview-metric-label">Uncovered intervals</span>
              <strong>{{ previewSummary.gapCount }} ({{ formatDurationMinutes(previewSummary.gapMinutes) }})</strong>
            </div>
          </div>

          <div v-if="Array.isArray(previewWarnings) && previewWarnings.length > 0" class="preview-warnings">
            <div
              v-for="(warning, warningIndex) in previewWarnings"
              :key="`preview-warning-${warningIndex}`"
              class="preview-warning-item"
            >
              <i class="pi pi-exclamation-triangle"></i>
              <span>{{ warning }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="footer-actions">
        <Button
          label="Cancel"
          icon="pi pi-times"
          outlined
          @click="handleClose"
        />
        <Button
          label="Validate"
          icon="pi pi-check-circle"
          outlined
          :loading="isPreviewLoading"
          @click="previewReconstruction"
        />
        <Button
          label="Commit & Regenerate"
          icon="pi pi-check"
          :loading="isCommitLoading"
          @click="commitReconstruction"
        />
      </div>
    </template>
  </Dialog>

</template>

<script setup>
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import DatePicker from 'primevue/datepicker'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import { MapContainer } from '@/components/maps'
import { useTimezone } from '@/composables/useTimezone'
import { useTripsStore } from '@/stores/trips'
import { useFavoritesStore } from '@/stores/favorites'
import { useGeocodingStore } from '@/stores/geocoding'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import L from 'leaflet'
import maplibregl from 'maplibre-gl'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  mode: {
    type: String,
    default: 'trip'
  },
  tripId: {
    type: Number,
    default: null
  },
  trip: {
    type: Object,
    default: null
  },
  contextStartTime: {
    type: String,
    default: null
  },
  contextEndTime: {
    type: String,
    default: null
  },
  fallbackCenter: {
    type: Array,
    default: () => [37.7749, -122.4194]
  }
})

const emit = defineEmits(['close', 'committed'])

const timezone = useTimezone()
const toast = useToast()
const tripsStore = useTripsStore()
const favoritesStore = useFavoritesStore()
const geocodingStore = useGeocodingStore()

const internalVisible = ref(props.visible)
const mapId = ref(Math.random().toString(36).slice(2, 10))
const mapInstance = ref(null)
const mapAdapter = ref(null)
const segments = ref([])
const activeSegmentId = ref(null)
const segmentIdCounter = ref(1)
const waypointIdCounter = ref(1)
const isPreviewLoading = ref(false)
const isCommitLoading = ref(false)
const previewResult = ref(null)
const previewWarnings = ref([])
const stayNameRequestToken = ref(0)
const suppressNextActiveSegmentReframe = ref(false)
const mapCenter = ref([...props.fallbackCenter])
const mapZoom = ref(13)

const segmentTypeOptions = [
  { label: 'Stay', value: 'STAY' },
  { label: 'Trip', value: 'TRIP' }
]

const movementTypeOptions = [
  { label: 'Walk', value: 'WALK' },
  { label: 'Running', value: 'RUNNING' },
  { label: 'Bicycle', value: 'BICYCLE' },
  { label: 'Car', value: 'CAR' },
  { label: 'Train', value: 'TRAIN' },
  { label: 'Flight', value: 'FLIGHT' },
  { label: 'Unknown', value: 'UNKNOWN' }
]

const activeSegmentIndex = computed(() => segments.value.findIndex((segment) => segment.id === activeSegmentId.value))
const activeSegment = computed(() => {
  const index = activeSegmentIndex.value
  if (index < 0) return null
  return segments.value[index]
})
const dialogHeader = computed(() => (props.mode === 'trip' ? 'Add Missing Trip Data' : 'Add Missing Timeline Data'))
const reconstructionHelpMessage = computed(() => (
  props.mode === 'trip'
    ? 'Add stays and trips to generate missing GPS points for this trip. Existing GPS points are preserved; this does not replace existing timeline data. For a trip segment, add multiple waypoints on the map: first point is start, last point is end.'
    : 'Add stays and trips to generate missing GPS points for any dates and times you choose. Existing GPS points are preserved; this does not replace existing timeline data. For a trip segment, add multiple waypoints on the map: first point is start, last point is end.'
))

const hasValidCoordinates = (lat, lon) => {
  return Number.isFinite(lat) && Number.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180
}

const normalizePositiveId = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? Math.trunc(parsed) : null
}

const clearStayLocationResolution = (segment) => {
  if (!segment || segment.segmentType !== 'STAY') return
  segment.locationName = ''
  segment.locationSourceType = null
  segment.locationFavoriteId = null
  segment.locationFavoriteType = null
  segment.locationGeocodingId = null
  segment.locationResolvedName = null
  segment.locationNameEdited = false
}

const locationSourceLabel = (segment) => {
  if (!segment?.locationSourceType) {
    return 'Unknown'
  }

  switch (segment.locationSourceType) {
    case 'favorite-point':
      return 'Favorite point'
    case 'favorite-area':
      return 'Favorite area'
    case 'geocoding':
      return 'Reverse geocoding'
    case 'coordinates':
      return 'Coordinates'
    default:
      return segment.locationSourceType
  }
}

const getFavoriteById = (favoriteId) => {
  const points = favoritesStore.favoritePlaces?.points || []
  const areas = favoritesStore.favoritePlaces?.areas || []
  return [...points, ...areas].find((favorite) => Number(favorite.id) === Number(favoriteId)) || null
}

const applyFavoriteNameToSegments = (favoriteId, nextName) => {
  const normalizedId = normalizePositiveId(favoriteId)
  const normalizedName = typeof nextName === 'string' ? nextName.trim() : ''
  if (normalizedId === null || normalizedName.length === 0) return

  segments.value.forEach((segment) => {
    if (segment.segmentType !== 'STAY') return
    if (normalizePositiveId(segment.locationFavoriteId) !== normalizedId) return
    segment.locationName = normalizedName
    segment.locationResolvedName = normalizedName
    segment.locationNameEdited = false
  })
}

const applyGeocodingNameToSegments = (geocodingId, nextName) => {
  const normalizedId = normalizePositiveId(geocodingId)
  const normalizedName = typeof nextName === 'string' ? nextName.trim() : ''
  if (normalizedId === null || normalizedName.length === 0) return

  segments.value.forEach((segment) => {
    if (segment.segmentType !== 'STAY') return
    if (normalizePositiveId(segment.locationGeocodingId) !== normalizedId) return
    segment.locationName = normalizedName
    segment.locationResolvedName = normalizedName
    segment.locationNameEdited = false
  })
}

const normalizeLocationName = (value) => {
  if (typeof value !== 'string') return ''
  return value.trim()
}

const registerRenameOperation = (operationMap, sourceId, nextName, sourceLabel) => {
  const existingName = operationMap.get(sourceId)
  if (existingName && existingName !== nextName) {
    throw new Error(`Conflicting names for ${sourceLabel} ${sourceId}. Use one name per source.`)
  }
  operationMap.set(sourceId, nextName)
}

const collectSourceRenameOperations = () => {
  const favoriteRenames = new Map()
  const geocodingRenames = new Map()

  segments.value.forEach((segment) => {
    if (segment?.segmentType !== 'STAY') return

    const nextName = normalizeLocationName(segment.locationName)
    if (!nextName || !segment.locationNameEdited) {
      return
    }

    const resolvedName = normalizeLocationName(segment.locationResolvedName)
    if (resolvedName && resolvedName === nextName) {
      return
    }

    const favoriteId = normalizePositiveId(segment.locationFavoriteId)
    if (favoriteId !== null) {
      registerRenameOperation(favoriteRenames, favoriteId, nextName, 'favorite')
      return
    }

    const geocodingId = normalizePositiveId(segment.locationGeocodingId)
    if (geocodingId !== null) {
      registerRenameOperation(geocodingRenames, geocodingId, nextName, 'geocoding')
    }
  })

  return { favoriteRenames, geocodingRenames }
}

const syncStayLocationNamesToSources = async () => {
  const { favoriteRenames, geocodingRenames } = collectSourceRenameOperations()
  if (favoriteRenames.size === 0 && geocodingRenames.size === 0) {
    return
  }

  let favoritesReloaded = false
  for (const [favoriteId, nextName] of favoriteRenames.entries()) {
    let favorite = getFavoriteById(favoriteId)
    if (!favorite && !favoritesReloaded) {
      await favoritesStore.fetchFavoritePlaces()
      favoritesReloaded = true
      favorite = getFavoriteById(favoriteId)
    }
    if (!favorite) {
      throw new Error(`Favorite ${favoriteId} not found for rename.`)
    }

    const bounds = favorite.type === 'AREA'
      ? {
        northEastLat: favorite.northEastLat,
        northEastLon: favorite.northEastLon,
        southWestLat: favorite.southWestLat,
        southWestLon: favorite.southWestLon
      }
      : null

    await favoritesStore.editFavorite(
      favorite.id,
      nextName,
      favorite.city,
      favorite.country,
      bounds
    )
    applyFavoriteNameToSegments(favoriteId, nextName)
  }

  for (const [geocodingId, nextName] of geocodingRenames.entries()) {
    const geocoding = await geocodingStore.getGeocodingResult(geocodingId)
    if (!geocoding?.id) {
      throw new Error(`Geocoding ${geocodingId} not found for rename.`)
    }

    const updated = await geocodingStore.updateGeocodingResult(geocodingId, {
      displayName: nextName,
      city: geocoding.city ?? null,
      country: geocoding.country ?? null
    })
    applyGeocodingNameToSegments(geocodingId, updated?.displayName || nextName)
  }
}

const getSegmentEndpoint = (segment, endpoint = 'end') => {
  if (!segment) return null

  if (segment.segmentType === 'STAY' && hasValidCoordinates(segment.latitude, segment.longitude)) {
    return { latitude: segment.latitude, longitude: segment.longitude }
  }

  if (segment.segmentType === 'TRIP' && Array.isArray(segment.waypoints) && segment.waypoints.length > 0) {
    const waypoint = endpoint === 'start'
      ? segment.waypoints[0]
      : segment.waypoints[segment.waypoints.length - 1]
    if (waypoint && hasValidCoordinates(waypoint.latitude, waypoint.longitude)) {
      return { latitude: waypoint.latitude, longitude: waypoint.longitude }
    }
  }

  return null
}

const resolveSegmentReferenceCoordinate = (segmentIndex) => {
  const current = segments.value[segmentIndex]
  const currentAnchor = getSegmentEndpoint(current, 'start')
  if (currentAnchor) return currentAnchor

  for (let index = segmentIndex - 1; index >= 0; index -= 1) {
    const previousAnchor = getSegmentEndpoint(segments.value[index], 'end')
    if (previousAnchor) return previousAnchor
  }

  for (let index = segmentIndex + 1; index < segments.value.length; index += 1) {
    const nextAnchor = getSegmentEndpoint(segments.value[index], 'start')
    if (nextAnchor) return nextAnchor
  }

  return null
}

const previewSummary = computed(() => {
  const sorted = [...segments.value]
    .filter((segment) => segment?.startTime instanceof Date && segment?.endTime instanceof Date)
    .sort((left, right) => left.startTime.getTime() - right.startTime.getTime())

  let coveredMinutes = 0
  let gapMinutes = 0
  let gapCount = 0
  let previousEnd = null

  sorted.forEach((segment) => {
    const segmentDurationMinutes = Math.max(0, Math.round((segment.endTime.getTime() - segment.startTime.getTime()) / 60000))
    coveredMinutes += segmentDurationMinutes

    if (previousEnd instanceof Date && segment.startTime > previousEnd) {
      gapCount += 1
      gapMinutes += Math.round((segment.startTime.getTime() - previousEnd.getTime()) / 60000)
    }

    if (!(previousEnd instanceof Date) || segment.endTime > previousEnd) {
      previousEnd = segment.endTime
    }
  })

  return {
    stays: segments.value.filter((segment) => segment.segmentType === 'STAY').length,
    trips: segments.value.filter((segment) => segment.segmentType === 'TRIP').length,
    coveredMinutes,
    gapMinutes,
    gapCount
  }
})

const createRasterWaypointIcon = (index, total) => {
  const markerVariant = index === 0 ? 'start' : (index === total - 1 ? 'end' : 'mid')
  return L.divIcon({
    className: 'trip-reconstruction-waypoint-icon-wrapper',
    html: `<div class="trip-reconstruction-waypoint-icon trip-reconstruction-waypoint-icon--${markerVariant}">${index + 1}</div>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14]
  })
}

const createRasterStayIcon = () => {
  return L.divIcon({
    className: 'trip-reconstruction-waypoint-icon-wrapper',
    html: '<div class="trip-reconstruction-waypoint-icon trip-reconstruction-waypoint-icon--stay"><i class="pi pi-home"></i></div>',
    iconSize: [30, 30],
    iconAnchor: [15, 15]
  })
}

const createRasterContextIcon = (point) => {
  return L.divIcon({
    className: 'trip-reconstruction-waypoint-icon-wrapper',
    html: `<div class="trip-reconstruction-context-icon">${point.label}</div>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11]
  })
}

const normalizeMapLibrePadding = (paddingValue) => {
  if (Array.isArray(paddingValue) && paddingValue.length >= 2) {
    const horizontal = Number(paddingValue[0])
    const vertical = Number(paddingValue[1])
    if (Number.isFinite(horizontal) && Number.isFinite(vertical)) {
      return {
        left: horizontal,
        right: horizontal,
        top: vertical,
        bottom: vertical
      }
    }
  }

  return paddingValue
}

const setMapView = (center, zoom, options = {}) => {
  if (!mapInstance.value) return
  if (!Array.isArray(center) || center.length < 2) return
  mapInstance.value.setView(center, zoom, options)
}

const fitMapToBounds = (bounds, options = {}) => {
  if (!mapInstance.value || !Array.isArray(bounds) || bounds.length < 2) {
    return
  }

  const mode = resolveMapEngineModeFromInstance(mapInstance.value, MAP_RENDER_MODES.RASTER)
  if (mode === MAP_RENDER_MODES.VECTOR) {
    mapInstance.value.fitBounds(
      bounds.map(([latitude, longitude]) => [longitude, latitude]),
      {
        ...options,
        padding: normalizeMapLibrePadding(options.padding)
      }
    )
    return
  }

  mapInstance.value.fitBounds(bounds, options)
}

const createRasterAdapter = (map) => {
  let waypointMarkers = []
  let stayMarker = null
  let polyline = null
  let contextMarkers = []
  let contextTripPolylines = []

  const clear = () => {
    waypointMarkers.forEach((marker) => map.removeLayer(marker))
    waypointMarkers = []

    if (stayMarker) {
      map.removeLayer(stayMarker)
      stayMarker = null
    }

    if (polyline) {
      map.removeLayer(polyline)
      polyline = null
    }

    contextTripPolylines.forEach((line) => map.removeLayer(line))
    contextTripPolylines = []

    contextMarkers.forEach((marker) => map.removeLayer(marker))
    contextMarkers = []
  }

  const renderContext = (contextPoints) => {
    contextMarkers = contextPoints.map((point) => {
      const marker = L.marker([point.latitude, point.longitude], {
        icon: createRasterContextIcon(point),
        interactive: false,
        keyboard: false
      }).addTo(map)
      return marker
    })
  }

  const renderContextTripLines = (contextTripLines) => {
    contextTripPolylines = contextTripLines.map((linePoints) => (
      L.polyline(linePoints, {
        color: '#0ea5e9',
        weight: 3,
        opacity: 0.8,
        dashArray: '3 3',
        interactive: false
      }).addTo(map)
    ))
  }

  const renderStay = (segment, segmentIndex) => {
    if (!hasValidCoordinates(segment.latitude, segment.longitude)) {
      return
    }

    stayMarker = L.marker([segment.latitude, segment.longitude], {
      icon: createRasterStayIcon(),
      draggable: true
    }).addTo(map)

    stayMarker.on('dragend', (event) => {
      const latLng = event.target.getLatLng()
      updateStayLocation(segmentIndex, latLng.lat, latLng.lng)
    })
  }

  const renderTrip = (segment, segmentIndex) => {
    const points = segment.waypoints
      .filter((waypoint) => hasValidCoordinates(waypoint.latitude, waypoint.longitude))
      .map((waypoint) => [waypoint.latitude, waypoint.longitude])

    if (points.length >= 2) {
      polyline = L.polyline(points, {
        color: '#1d4ed8',
        weight: 4,
        opacity: 0.85
      }).addTo(map)
    }

    waypointMarkers = segment.waypoints.map((waypoint, index) => {
      const marker = L.marker([waypoint.latitude, waypoint.longitude], {
        icon: createRasterWaypointIcon(index, segment.waypoints.length),
        draggable: true
      }).addTo(map)

      marker.on('dragend', (event) => {
        const latLng = event.target.getLatLng()
        updateWaypoint(segmentIndex, index, latLng.lat, latLng.lng)
      })

      marker.on('contextmenu', () => {
        removeWaypoint(segmentIndex, index)
      })

      return marker
    })
  }

  return {
    render({ activeSegment, activeSegmentIndex, contextPoints, contextTripLines }) {
      clear()
      if (!activeSegment) return

      renderContext(contextPoints)
      renderContextTripLines(contextTripLines)

      if (activeSegment.segmentType === 'STAY') {
        renderStay(activeSegment, activeSegmentIndex)
      } else {
        renderTrip(activeSegment, activeSegmentIndex)
      }
    },
    cleanup: clear
  }
}

const createVectorWaypointElement = (index, total) => {
  const markerVariant = index === 0 ? 'start' : (index === total - 1 ? 'end' : 'mid')
  const root = document.createElement('div')
  root.className = `trip-reconstruction-waypoint-marker trip-reconstruction-waypoint-marker--${markerVariant}`
  root.textContent = String(index + 1)
  return root
}

const createVectorStayElement = () => {
  const root = document.createElement('div')
  root.className = 'trip-reconstruction-waypoint-marker trip-reconstruction-waypoint-marker--stay'
  root.textContent = 'S'
  return root
}

const createVectorContextElement = (point) => {
  const root = document.createElement('div')
  root.className = 'trip-reconstruction-context-marker'
  root.textContent = point.label
  return root
}

const createVectorAdapter = (map) => {
  let waypointMarkers = []
  let stayMarker = null
  let contextMarkers = []
  const sourceId = `trip-reconstruction-source-${mapId.value}`
  const layerId = `trip-reconstruction-line-${mapId.value}`
  const contextSourceId = `trip-reconstruction-context-source-${mapId.value}`
  const contextLayerId = `trip-reconstruction-context-line-${mapId.value}`

  const isMapUsable = () => {
    if (!map || map._removed === true) {
      return false
    }

    if (typeof map.getStyle !== 'function') {
      return false
    }

    try {
      return Boolean(map.getStyle())
    } catch (error) {
      return false
    }
  }

  const clearPolyline = () => {
    if (!isMapUsable()) {
      return
    }

    try {
      if (map.getLayer(contextLayerId)) {
        map.removeLayer(contextLayerId)
      }
      if (map.getSource(contextSourceId)) {
        map.removeSource(contextSourceId)
      }
      if (map.getLayer(layerId)) {
        map.removeLayer(layerId)
      }
      if (map.getSource(sourceId)) {
        map.removeSource(sourceId)
      }
    } catch (error) {
      // Map may be in teardown between unmount hooks. Safe to ignore.
    }
  }

  const clear = () => {
    waypointMarkers.forEach((marker) => {
      try {
        marker.remove()
      } catch (error) {
        // Ignore marker cleanup errors during teardown.
      }
    })
    waypointMarkers = []

    if (stayMarker) {
      try {
        stayMarker.remove()
      } catch (error) {
        // Ignore marker cleanup errors during teardown.
      }
      stayMarker = null
    }

    contextMarkers.forEach((marker) => {
      try {
        marker.remove()
      } catch (error) {
        // Ignore marker cleanup errors during teardown.
      }
    })
    contextMarkers = []

    clearPolyline()
  }

  const renderContext = (contextPoints) => {
    if (!isMapUsable()) {
      return
    }

    contextMarkers = contextPoints.map((point) => {
      return new maplibregl.Marker({
        element: createVectorContextElement(point),
        anchor: 'center'
      })
        .setLngLat([point.longitude, point.latitude])
        .addTo(map)
    })
  }

  const renderPolyline = (segment) => {
    if (!isMapUsable()) {
      return
    }

    const coordinates = segment.waypoints
      .filter((waypoint) => hasValidCoordinates(waypoint.latitude, waypoint.longitude))
      .map((waypoint) => [waypoint.longitude, waypoint.latitude])

    if (coordinates.length < 2) {
      return
    }

    map.addSource(sourceId, {
      type: 'geojson',
      data: {
        type: 'Feature',
        geometry: {
          type: 'LineString',
          coordinates
        },
        properties: {}
      }
    })

    map.addLayer({
      id: layerId,
      type: 'line',
      source: sourceId,
      paint: {
        'line-color': '#1d4ed8',
        'line-width': 4,
        'line-opacity': 0.85
      }
    })
  }

  const renderContextTripLines = (contextTripLines) => {
    if (!isMapUsable()) {
      return
    }

    const features = contextTripLines
      .map((linePoints) => ({
        type: 'Feature',
        geometry: {
          type: 'LineString',
          coordinates: linePoints.map(([latitude, longitude]) => [longitude, latitude])
        },
        properties: {}
      }))
      .filter((feature) => feature.geometry.coordinates.length >= 2)

    if (features.length === 0) {
      return
    }

    map.addSource(contextSourceId, {
      type: 'geojson',
      data: {
        type: 'FeatureCollection',
        features
      }
    })

    map.addLayer({
      id: contextLayerId,
      type: 'line',
      source: contextSourceId,
      paint: {
        'line-color': '#0ea5e9',
        'line-width': 3,
        'line-opacity': 0.8,
        'line-dasharray': [1, 1.4]
      }
    })
  }

  const renderStay = (segment, segmentIndex) => {
    if (!isMapUsable()) {
      return
    }

    if (!hasValidCoordinates(segment.latitude, segment.longitude)) {
      return
    }

    stayMarker = new maplibregl.Marker({
      element: createVectorStayElement(),
      anchor: 'center',
      draggable: true
    })
      .setLngLat([segment.longitude, segment.latitude])
      .addTo(map)

    stayMarker.on('dragend', () => {
      const lngLat = stayMarker.getLngLat()
      updateStayLocation(segmentIndex, lngLat.lat, lngLat.lng)
    })
  }

  const renderTrip = (segment, segmentIndex) => {
    if (!isMapUsable()) {
      return
    }

    renderPolyline(segment)

    waypointMarkers = segment.waypoints.map((waypoint, index) => {
      const marker = new maplibregl.Marker({
        element: createVectorWaypointElement(index, segment.waypoints.length),
        anchor: 'center',
        draggable: true
      })
        .setLngLat([waypoint.longitude, waypoint.latitude])
        .addTo(map)

      marker.on('dragend', () => {
        const lngLat = marker.getLngLat()
        updateWaypoint(segmentIndex, index, lngLat.lat, lngLat.lng)
      })

      marker.getElement().addEventListener('contextmenu', (event) => {
        event.preventDefault()
        removeWaypoint(segmentIndex, index)
      })

      return marker
    })
  }

  return {
    render({ activeSegment, activeSegmentIndex, contextPoints, contextTripLines }) {
      clear()
      if (!activeSegment) return

      renderContext(contextPoints)
      renderContextTripLines(contextTripLines)

      if (activeSegment.segmentType === 'STAY') {
        renderStay(activeSegment, activeSegmentIndex)
      } else {
        renderTrip(activeSegment, activeSegmentIndex)
      }
    },
    cleanup: clear
  }
}

const createMapAdapter = (map) => {
  const mode = resolveMapEngineModeFromInstance(map, MAP_RENDER_MODES.RASTER)
  if (mode === MAP_RENDER_MODES.VECTOR) {
    return createVectorAdapter(map)
  }
  return createRasterAdapter(map)
}

const buildContextPoints = () => {
  const activeIndex = activeSegmentIndex.value
  if (activeIndex < 0) {
    return []
  }

  const points = []
  segments.value.forEach((segment, index) => {
    if (index === activeIndex) {
      return
    }

    if (segment.segmentType === 'STAY' && hasValidCoordinates(segment.latitude, segment.longitude)) {
      points.push({
        id: `ctx-stay-${segment.id}`,
        label: `S${index + 1}`,
        latitude: segment.latitude,
        longitude: segment.longitude
      })
      return
    }

    if (segment.segmentType === 'TRIP' && Array.isArray(segment.waypoints) && segment.waypoints.length > 0) {
      const start = segment.waypoints[0]
      const end = segment.waypoints[segment.waypoints.length - 1]
      if (start && hasValidCoordinates(start.latitude, start.longitude)) {
        points.push({
          id: `ctx-trip-start-${segment.id}`,
          label: `T${index + 1}S`,
          latitude: start.latitude,
          longitude: start.longitude
        })
      }
      if (
        end
        && hasValidCoordinates(end.latitude, end.longitude)
        && (end.latitude !== start?.latitude || end.longitude !== start?.longitude)
      ) {
        points.push({
          id: `ctx-trip-end-${segment.id}`,
          label: `T${index + 1}E`,
          latitude: end.latitude,
          longitude: end.longitude
        })
      }
    }
  })

  return points
}

const buildContextTripLines = () => {
  const activeIndex = activeSegmentIndex.value
  if (activeIndex < 0) {
    return []
  }

  return segments.value
    .map((segment, index) => {
      if (index === activeIndex || segment.segmentType !== 'TRIP' || !Array.isArray(segment.waypoints)) {
        return []
      }

      return segment.waypoints
        .filter((waypoint) => hasValidCoordinates(waypoint.latitude, waypoint.longitude))
        .map((waypoint) => [waypoint.latitude, waypoint.longitude])
    })
    .filter((points) => points.length >= 2)
}

const fitMapToActiveSegment = () => {
  if (!mapInstance.value || !activeSegment.value) {
    return
  }

  const segment = activeSegment.value
  if (segment.segmentType === 'STAY' && hasValidCoordinates(segment.latitude, segment.longitude)) {
    setMapView([segment.latitude, segment.longitude], 14, { animate: true })
    return
  }

  if (segment.segmentType === 'TRIP' && segment.waypoints.length > 0) {
    const bounds = segment.waypoints
      .filter((waypoint) => hasValidCoordinates(waypoint.latitude, waypoint.longitude))
      .map((waypoint) => [waypoint.latitude, waypoint.longitude])

    if (bounds.length === 1) {
      setMapView(bounds[0], 12, { animate: true })
      return
    }

    if (bounds.length > 1) {
      fitMapToBounds(bounds, { padding: [30, 30], animate: true })
    }
    return
  }

  const reference = resolveSegmentReferenceCoordinate(activeSegmentIndex.value)
  if (reference) {
    setMapView([reference.latitude, reference.longitude], 13, { animate: true })
  }
}

const renderMapOverlay = (options = {}) => {
  if (!mapAdapter.value) return
  const shouldReframe = options.reframe !== false
  mapAdapter.value.render({
    activeSegment: activeSegment.value,
    activeSegmentIndex: activeSegmentIndex.value,
    contextPoints: buildContextPoints(),
    contextTripLines: buildContextTripLines()
  })
  if (shouldReframe) {
    fitMapToActiveSegment()
  }
}

const getContextRangeDates = () => {
  if (props.mode === 'trip') {
    if (!props.trip?.startTime || !props.trip?.endTime) {
      return { start: null, end: null }
    }

    return {
      start: timezone.fromUtc(props.trip.startTime).toDate(),
      end: timezone.fromUtc(props.trip.endTime).toDate()
    }
  }

  if (!props.contextStartTime || !props.contextEndTime) {
    return { start: null, end: null }
  }

  return {
    start: timezone.fromUtc(props.contextStartTime).toDate(),
    end: timezone.fromUtc(props.contextEndTime).toDate()
  }
}

const createSegment = (segmentType) => {
  const tripRange = getContextRangeDates()
  const previousSegment = segments.value.length > 0 ? segments.value[segments.value.length - 1] : null
  const previousEnd = previousSegment?.endTime instanceof Date ? previousSegment.endTime : null

  const now = timezone.now().toDate()
  const fallbackStart = tripRange.start || now
  const defaultStart = previousEnd ? new Date(previousEnd.getTime()) : fallbackStart
  const resolvedStart = new Date(defaultStart.getTime())
  let resolvedEnd = new Date(resolvedStart.getTime() + 30 * 60 * 1000)
  if (!(resolvedEnd > resolvedStart)) {
    resolvedEnd = new Date(resolvedStart.getTime() + 5 * 60 * 1000)
  }

  let initialWaypoints = []
  let initialLatitude = null
  let initialLongitude = null

  if (segmentType === 'STAY' && previousSegment?.segmentType === 'TRIP') {
    const previousTripEnd = getSegmentEndpoint(previousSegment, 'end')
    if (previousTripEnd) {
      initialLatitude = Number(previousTripEnd.latitude.toFixed(6))
      initialLongitude = Number(previousTripEnd.longitude.toFixed(6))
    }
  }

  if (segmentType === 'TRIP') {
    const previousAnchor = getSegmentEndpoint(previousSegment, 'end')
    if (previousAnchor) {
      initialWaypoints = [{
        id: waypointIdCounter.value++,
        latitude: Number(previousAnchor.latitude.toFixed(6)),
        longitude: Number(previousAnchor.longitude.toFixed(6))
      }]
    }
  }

  return {
    id: segmentIdCounter.value++,
    segmentType,
    startTime: resolvedStart,
    endTime: resolvedEnd,
    locationName: '',
    locationSourceType: null,
    locationFavoriteId: null,
    locationFavoriteType: null,
    locationGeocodingId: null,
    locationResolvedName: null,
    locationNameEdited: false,
    latitude: initialLatitude,
    longitude: initialLongitude,
    movementType: 'CAR',
    waypoints: initialWaypoints
  }
}

const resetDialogState = () => {
  const initialSegment = createSegment('STAY')
  segments.value = [initialSegment]
  activeSegmentId.value = initialSegment.id
  previewResult.value = null
  previewWarnings.value = []
}

const setActiveSegment = (segmentId) => {
  activeSegmentId.value = segmentId
}

const updateSegmentField = (index, field, value) => {
  const segment = segments.value[index]
  if (!segment) return
  segment[field] = value

  if (field === 'startTime' && value instanceof Date && !Number.isNaN(value.getTime())) {
    const autoEnd = new Date(value.getTime() + 30 * 60 * 1000)
    segment.endTime = autoEnd
  }

  if ((field === 'latitude' || field === 'longitude') && segment.segmentType === 'STAY') {
    clearStayLocationResolution(segment)
    if (hasValidCoordinates(segment.latitude, segment.longitude)) {
      resolveStayLocationName(index, segment.latitude, segment.longitude)
    }
  }

  if (field === 'locationName' && segment.segmentType === 'STAY') {
    segment.locationNameEdited = true
  }

  nextTick(() => renderMapOverlay({ reframe: false }))
}

const updateSegmentType = (index, nextType) => {
  const segment = segments.value[index]
  if (!segment || !nextType || segment.segmentType === nextType) {
    return
  }

  segment.segmentType = nextType
  if (nextType === 'STAY') {
    segment.waypoints = []
    clearStayLocationResolution(segment)
    const reference = resolveSegmentReferenceCoordinate(index)
    if (reference) {
      segment.latitude = reference.latitude
      segment.longitude = reference.longitude
      resolveStayLocationName(index, segment.latitude, segment.longitude)
    }
  } else {
    segment.latitude = null
    segment.longitude = null
    clearStayLocationResolution(segment)
    segment.movementType = segment.movementType || 'CAR'
    const reference = resolveSegmentReferenceCoordinate(index)
    segment.waypoints = reference
      ? [{
        id: waypointIdCounter.value++,
        latitude: Number(reference.latitude.toFixed(6)),
        longitude: Number(reference.longitude.toFixed(6))
      }]
      : []
  }
  nextTick(() => renderMapOverlay({ reframe: true }))
}

const addSegment = (segmentType) => {
  const segment = createSegment(segmentType)
  segments.value.push(segment)
  const segmentIndex = segments.value.length - 1
  suppressNextActiveSegmentReframe.value = true
  activeSegmentId.value = segment.id

  if (
    segment.segmentType === 'STAY'
    && hasValidCoordinates(segment.latitude, segment.longitude)
    && (!segment.locationName || segment.locationName.trim().length === 0)
  ) {
    resolveStayLocationName(segmentIndex, segment.latitude, segment.longitude)
  }

  nextTick(() => renderMapOverlay({ reframe: false }))
}

const removeSegment = (index) => {
  if (index < 0 || index >= segments.value.length) return
  const [removed] = segments.value.splice(index, 1)
  if (!removed) return

  if (segments.value.length === 0) {
    resetDialogState()
    return
  }

  if (activeSegmentId.value === removed.id) {
    const nextIndex = Math.min(index, segments.value.length - 1)
    activeSegmentId.value = segments.value[nextIndex].id
  }
  nextTick(() => renderMapOverlay({ reframe: true }))
}

const moveSegment = (index, offset) => {
  const nextIndex = index + offset
  if (index < 0 || nextIndex < 0 || nextIndex >= segments.value.length) return
  const cloned = [...segments.value]
  const [segment] = cloned.splice(index, 1)
  cloned.splice(nextIndex, 0, segment)
  segments.value = cloned
  nextTick(() => renderMapOverlay({ reframe: false }))
}

const addWaypoint = (segmentIndex, latitude, longitude) => {
  const segment = segments.value[segmentIndex]
  if (!segment || segment.segmentType !== 'TRIP') return
  if (!hasValidCoordinates(latitude, longitude)) return

  segment.waypoints.push({
    id: waypointIdCounter.value++,
    latitude: Number(latitude.toFixed(6)),
    longitude: Number(longitude.toFixed(6))
  })
  nextTick(() => renderMapOverlay({ reframe: false }))
}

const updateWaypoint = (segmentIndex, waypointIndex, latitude, longitude) => {
  const segment = segments.value[segmentIndex]
  if (!segment || segment.segmentType !== 'TRIP') return
  if (!segment.waypoints[waypointIndex]) return
  if (!hasValidCoordinates(latitude, longitude)) return

  segment.waypoints[waypointIndex] = {
    ...segment.waypoints[waypointIndex],
    latitude: Number(latitude.toFixed(6)),
    longitude: Number(longitude.toFixed(6))
  }
  nextTick(() => renderMapOverlay({ reframe: false }))
}

const removeWaypoint = (segmentIndex, waypointIndex) => {
  const segment = segments.value[segmentIndex]
  if (!segment || segment.segmentType !== 'TRIP') return
  segment.waypoints.splice(waypointIndex, 1)
  nextTick(() => renderMapOverlay({ reframe: false }))
}

const moveWaypoint = (segmentIndex, waypointIndex, offset) => {
  const segment = segments.value[segmentIndex]
  if (!segment || segment.segmentType !== 'TRIP') return

  const targetIndex = waypointIndex + offset
  if (targetIndex < 0 || targetIndex >= segment.waypoints.length) return

  const updated = [...segment.waypoints]
  const [waypoint] = updated.splice(waypointIndex, 1)
  updated.splice(targetIndex, 0, waypoint)
  segment.waypoints = updated
  nextTick(() => renderMapOverlay({ reframe: false }))
}

const resolveStayLocationName = async (segmentIndex, latitude, longitude) => {
  const segment = segments.value[segmentIndex]
  if (!segment || segment.segmentType !== 'STAY') return
  if (!hasValidCoordinates(latitude, longitude)) return

  const requestToken = ++stayNameRequestToken.value
  try {
    const suggestion = await tripsStore.getPlanSuggestion(latitude, longitude)
    if (requestToken !== stayNameRequestToken.value) {
      return
    }

    const title = suggestion?.title?.trim()
    if (!title) {
      return
    }

    const latestSegment = segments.value[segmentIndex]
    if (!latestSegment || latestSegment.segmentType !== 'STAY') {
      return
    }

    const samePoint = Math.abs(latestSegment.latitude - latitude) < 0.00001
      && Math.abs(latestSegment.longitude - longitude) < 0.00001
    if (!samePoint) {
      return
    }

    if (!latestSegment.locationNameEdited) {
      latestSegment.locationName = title
    }
    latestSegment.locationSourceType = suggestion?.sourceType || 'coordinates'
    latestSegment.locationFavoriteId = normalizePositiveId(suggestion?.favoriteId)
    latestSegment.locationFavoriteType = suggestion?.favoriteType || null
    latestSegment.locationGeocodingId = normalizePositiveId(suggestion?.geocodingId)
    latestSegment.locationResolvedName = title
  } catch (error) {
    // Non-blocking. The segment can still be committed with coordinates.
  }
}

const updateStayLocation = (segmentIndex, latitude, longitude, options = {}) => {
  const segment = segments.value[segmentIndex]
  if (!segment || segment.segmentType !== 'STAY') return
  if (!hasValidCoordinates(latitude, longitude)) return

  segment.latitude = Number(latitude.toFixed(6))
  segment.longitude = Number(longitude.toFixed(6))
  nextTick(() => renderMapOverlay({ reframe: options.reframe === true }))

  if (options.resolveName !== false) {
    resolveStayLocationName(segmentIndex, segment.latitude, segment.longitude)
  }
}

const handleMapReady = (map) => {
  mapInstance.value = map
  mapAdapter.value?.cleanup?.()
  mapAdapter.value = createMapAdapter(map)
  renderMapOverlay({ reframe: true })
}

const handleMapClick = (event) => {
  if (!activeSegment.value || !event?.latlng) {
    return
  }

  const { lat, lng } = event.latlng
  const segmentIndex = activeSegmentIndex.value

  if (activeSegment.value.segmentType === 'STAY') {
    updateStayLocation(segmentIndex, lat, lng, { reframe: false, resolveName: true })
    return
  }

  addWaypoint(segmentIndex, lat, lng)
}

const waypointLabel = (index, total) => {
  if (index === 0) return 'Start'
  if (index === total - 1) return 'End'
  return `W${index + 1}`
}

const waypointTagSeverity = (index, total) => {
  if (index === 0) return 'success'
  if (index === total - 1) return 'danger'
  return 'info'
}

const validateSegments = () => {
  if (!Array.isArray(segments.value) || segments.value.length === 0) {
    return 'Add at least one segment.'
  }

  for (let index = 0; index < segments.value.length; index += 1) {
    const segment = segments.value[index]
    const position = index + 1

    if (!(segment.startTime instanceof Date) || Number.isNaN(segment.startTime.getTime())) {
      return `Segment ${position}: start time is required.`
    }
    if (!(segment.endTime instanceof Date) || Number.isNaN(segment.endTime.getTime())) {
      return `Segment ${position}: end time is required.`
    }
    if (segment.endTime <= segment.startTime) {
      return `Segment ${position}: end time must be after start time.`
    }

    if (segment.segmentType === 'STAY') {
      if (!hasValidCoordinates(segment.latitude, segment.longitude)) {
        return `Segment ${position}: stay requires valid coordinates.`
      }
    } else if (segment.segmentType === 'TRIP') {
      if (!Array.isArray(segment.waypoints) || segment.waypoints.length < 2) {
        return `Segment ${position}: trip requires at least 2 waypoints.`
      }
    } else {
      return `Segment ${position}: unsupported segment type.`
    }
  }

  return null
}

const toUtcIso = (dateValue) => {
  const dateTime = timezone.createDateTimeFromPicker(dateValue)
  if (!dateTime || !dateTime.isValid()) {
    return null
  }
  return dateTime.utc().toISOString()
}

const toApiPayload = () => {
  const parsedTripId = Number(props.tripId)
  const resolvedTripId = Number.isFinite(parsedTripId) && parsedTripId > 0
    ? parsedTripId
    : null

  return {
    tripId: resolvedTripId,
    segments: segments.value.map((segment) => ({
      segmentType: segment.segmentType,
      startTime: toUtcIso(segment.startTime),
      endTime: toUtcIso(segment.endTime),
      locationName: segment.segmentType === 'STAY'
        ? (segment.locationName?.trim() || null)
        : null,
      latitude: segment.segmentType === 'STAY' ? segment.latitude : null,
      longitude: segment.segmentType === 'STAY' ? segment.longitude : null,
      movementType: segment.segmentType === 'TRIP' ? segment.movementType : null,
      waypoints: segment.segmentType === 'TRIP'
        ? segment.waypoints.map((waypoint) => ({
          latitude: waypoint.latitude,
          longitude: waypoint.longitude
        }))
        : []
    }))
  }
}

const previewReconstruction = async () => {
  const validationError = validateSegments()
  if (validationError) {
    toast.add({
      severity: 'warn',
      summary: 'Validation Error',
      detail: validationError,
      life: 3500
    })
    return
  }

  const payload = toApiPayload()
  if (payload.segments.some((segment) => !segment.startTime || !segment.endTime)) {
    toast.add({
      severity: 'warn',
      summary: 'Validation Error',
      detail: 'All segment dates must be valid.',
      life: 3500
    })
    return
  }

  isPreviewLoading.value = true
  try {
    previewResult.value = await tripsStore.previewReconstruction(payload)
    const backendWarnings = Array.isArray(previewResult.value?.warnings)
      ? previewResult.value.warnings
      : []
    const derivedWarnings = []
    if (previewSummary.value.gapCount > 0) {
      derivedWarnings.push(`Detected ${previewSummary.value.gapCount} uncovered interval(s) totaling ${formatDurationMinutes(previewSummary.value.gapMinutes)}.`)
    }
    previewWarnings.value = [...backendWarnings, ...derivedWarnings]
    toast.add({
      severity: 'success',
      summary: 'Validation Ready',
      detail: `Generated points: ${previewResult.value?.estimatedPoints || 0}. Uncovered intervals: ${previewSummary.value.gapCount}.`,
      life: 2800
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Validation Failed',
      detail: error.response?.data?.message || error.message || 'Request failed.',
      life: 5000
    })
  } finally {
    isPreviewLoading.value = false
  }
}

const commitReconstruction = async () => {
  const validationError = validateSegments()
  if (validationError) {
    toast.add({
      severity: 'warn',
      summary: 'Validation Error',
      detail: validationError,
      life: 3500
    })
    return
  }

  const payload = toApiPayload()
  if (payload.segments.some((segment) => !segment.startTime || !segment.endTime)) {
    toast.add({
      severity: 'warn',
      summary: 'Validation Error',
      detail: 'All segment dates must be valid.',
      life: 3500
    })
    return
  }

  isCommitLoading.value = true
  try {
    await syncStayLocationNamesToSources()
    const result = await tripsStore.commitReconstruction(payload)

    if (result?.regenerationWarning) {
      toast.add({
        severity: 'warn',
        summary: 'Points Saved',
        detail: result.regenerationWarning,
        life: 6000
      })
    } else {
      toast.add({
        severity: 'success',
        summary: 'Missing Data Saved',
        detail: `Inserted ${result?.insertedPoints || 0} GPS points.`,
        life: 3200
      })
    }

    emit('committed', result)
    internalVisible.value = false
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Commit Failed',
      detail: error.response?.data?.message || error.message || 'Request failed.',
      life: 5000
    })
  } finally {
    isCommitLoading.value = false
  }
}

const formatDateTime = (value) => {
  if (!value) return '—'
  return timezone.formatDateTimeDisplay(value)
}

const formatDurationMinutes = (minutes) => {
  const safeMinutes = Number.isFinite(minutes) ? Math.max(0, minutes) : 0
  if (safeMinutes < 60) {
    return `${safeMinutes} min`
  }

  const hours = Math.floor(safeMinutes / 60)
  const remainder = safeMinutes % 60
  if (remainder === 0) {
    return `${hours} h`
  }
  return `${hours} h ${remainder} min`
}

const handleClose = () => {
  internalVisible.value = false
  previewWarnings.value = []
  emit('close')
}

watch(() => props.visible, (nextVisible) => {
  internalVisible.value = nextVisible
  if (nextVisible) {
    mapCenter.value = Array.isArray(props.fallbackCenter) ? [...props.fallbackCenter] : [37.7749, -122.4194]
    mapZoom.value = 13
    resetDialogState()
    nextTick(() => {
      renderMapOverlay()
    })
  }
})

watch(internalVisible, (nextVisible) => {
  if (!nextVisible) {
    emit('close')
  }
})

watch(activeSegmentId, () => {
  const shouldReframe = !suppressNextActiveSegmentReframe.value
  suppressNextActiveSegmentReframe.value = false
  nextTick(() => renderMapOverlay({ reframe: shouldReframe }))
})

onUnmounted(() => {
  mapAdapter.value?.cleanup?.()
  mapAdapter.value = null
})
</script>

<style scoped>
.reconstruction-layout {
  display: grid;
  grid-template-columns: minmax(340px, 0.85fr) minmax(0, 1.75fr);
  gap: var(--gp-spacing-md);
  min-height: 0;
}

.segments-panel {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  min-height: 0;
}

.segments-toolbar {
  display: flex;
  gap: var(--gp-spacing-xs);
  flex-wrap: wrap;
}

.segments-empty {
  border: 1px dashed var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  color: var(--gp-text-secondary);
  padding: var(--gp-spacing-md);
  text-align: center;
}

.segments-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  max-height: 510px;
  overflow-y: auto;
  padding-right: 0.15rem;
}

.segment-card {
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  padding: var(--gp-spacing-sm);
  cursor: pointer;
  background: var(--gp-surface-light);
}

.segment-card--active {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--gp-primary) 40%, transparent);
}

.segment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--gp-spacing-xs);
  margin-bottom: var(--gp-spacing-sm);
}

.segment-title-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.segment-actions {
  display: flex;
  align-items: center;
  gap: 0.05rem;
}

.segment-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--gp-spacing-sm);
}

.field-row {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.field-row label {
  font-size: 0.82rem;
  color: var(--gp-text-secondary);
}

.field-row--wide {
  grid-column: 1 / -1;
}

.field-hint {
  color: var(--gp-text-secondary);
}

.resolved-location {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-white);
  padding: 0.5rem;
}

.resolved-location-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-xs);
  flex-wrap: wrap;
}

.resolved-location-main :deep(.p-inputtext) {
  flex: 1;
  min-width: 0;
}

.waypoints-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  max-height: 170px;
  overflow-y: auto;
  padding-right: 0.1rem;
}

.waypoint-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--gp-spacing-xs);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-white);
  padding: 0.2rem 0.3rem;
}

.waypoint-coords {
  font-size: 0.8rem;
  color: var(--gp-text-primary);
}

.waypoint-actions {
  display: flex;
  align-items: center;
  gap: 0;
}

.map-panel {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.map-hint {
  font-size: 0.84rem;
  color: var(--gp-text-secondary);
}

.preview-box {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  padding: var(--gp-spacing-sm);
  background: var(--gp-surface-light);
  color: var(--gp-text-primary);
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
  flex-wrap: wrap;
}

.preview-range {
  color: var(--gp-text-secondary);
  font-size: 0.82rem;
}

.preview-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--gp-spacing-xs);
}

.preview-metric {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-white);
  padding: 0.42rem 0.5rem;
}

.preview-metric-label {
  color: var(--gp-text-secondary);
  font-size: 0.74rem;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.preview-warnings {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  margin-top: 0.15rem;
}

.preview-warning-item {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--gp-text-secondary);
  font-size: 0.82rem;
}

.footer-actions {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--gp-spacing-xs);
  flex-wrap: wrap;
}

@media (max-width: 1080px) {
  .reconstruction-layout {
    grid-template-columns: 1fr;
  }

  .segments-list {
    max-height: 360px;
  }
}

@media (max-width: 720px) {
  .segment-grid {
    grid-template-columns: 1fr;
  }

  .preview-metrics {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
.trip-reconstruction-dialog {
  width: 96vw !important;
  max-width: 1600px !important;
}

.trip-reconstruction-waypoint-icon-wrapper,
.trip-reconstruction-waypoint-marker {
  box-sizing: border-box;
}

.trip-reconstruction-waypoint-icon {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: 2px solid #ffffff;
  color: #ffffff;
  font-size: 0.75rem;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.25);
}

.trip-reconstruction-context-icon,
.trip-reconstruction-context-marker {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  border: 2px solid #ffffff;
  color: #ffffff;
  font-size: 0.58rem;
  font-weight: 700;
  letter-spacing: 0.01em;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #6b7280;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.22);
  pointer-events: none;
  user-select: none;
}

.trip-reconstruction-waypoint-icon--start,
.trip-reconstruction-waypoint-marker--start {
  background: #16a34a;
}

.trip-reconstruction-waypoint-icon--end,
.trip-reconstruction-waypoint-marker--end {
  background: #dc2626;
}

.trip-reconstruction-waypoint-icon--mid,
.trip-reconstruction-waypoint-marker--mid {
  background: #2563eb;
}

.trip-reconstruction-waypoint-icon--stay,
.trip-reconstruction-waypoint-marker--stay {
  background: #d97706;
}

.trip-reconstruction-waypoint-marker {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  border: 2px solid #ffffff;
  color: #ffffff;
  font-size: 0.75rem;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.28);
  cursor: grab;
  user-select: none;
}
</style>
