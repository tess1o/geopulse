import { computed, nextTick, ref } from 'vue'
import {
  buildContextPoints,
  buildContextTripLines,
  computePreviewSummary,
  getSegmentEndpoint,
  hasValidCoordinates,
  resolveSegmentReferenceCoordinate,
  waypointLabel,
  waypointTagSeverity
} from '@/maps/tripReconstruction/shared/tripReconstructionMapData'

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

const toUtcIso = (timezone, dateValue) => {
  const dateTime = timezone.createDateTimeFromPicker(dateValue)
  if (!dateTime || !dateTime.isValid()) {
    return null
  }
  return dateTime.utc().toISOString()
}

export function useTripReconstructionSegments({
  timezone,
  segmentsRef = null,
  getContextRangeDates,
  clearStayLocationResolution,
  resolveStayLocationName,
  onRenderRequest
}) {
  const segments = segmentsRef || ref([])
  const activeSegmentId = ref(null)
  const segmentIdCounter = ref(1)
  const waypointIdCounter = ref(1)
  const suppressNextActiveSegmentReframe = ref(false)

  const activeSegmentIndex = computed(() => {
    return segments.value.findIndex((segment) => segment.id === activeSegmentId.value)
  })

  const activeSegment = computed(() => {
    const index = activeSegmentIndex.value
    if (index < 0) {
      return null
    }
    return segments.value[index]
  })

  const previewSummary = computed(() => computePreviewSummary(segments.value))

  const queueRender = (reframe = false) => {
    if (typeof onRenderRequest !== 'function') {
      return
    }
    nextTick(() => {
      onRenderRequest({ reframe })
    })
  }

  const resolveReferenceForIndex = (segmentIndex) => {
    return resolveSegmentReferenceCoordinate(segments.value, segmentIndex)
  }

  const createSegment = (segmentType) => {
    const contextRange = getContextRangeDates()
    const previousSegment = segments.value.length > 0 ? segments.value[segments.value.length - 1] : null
    const previousEnd = previousSegment?.endTime instanceof Date ? previousSegment.endTime : null

    const now = timezone.now().toDate()
    const fallbackStart = contextRange.start || now
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
    suppressNextActiveSegmentReframe.value = false
  }

  const setActiveSegment = (segmentId) => {
    activeSegmentId.value = segmentId
  }

  const updateSegmentField = (index, field, value) => {
    const segment = segments.value[index]
    if (!segment) {
      return
    }

    segment[field] = value

    if (field === 'startTime' && value instanceof Date && !Number.isNaN(value.getTime())) {
      segment.endTime = new Date(value.getTime() + 30 * 60 * 1000)
    }

    if ((field === 'latitude' || field === 'longitude') && segment.segmentType === 'STAY') {
      clearStayLocationResolution?.(segment)
      if (hasValidCoordinates(segment.latitude, segment.longitude)) {
        resolveStayLocationName?.(index, segment.latitude, segment.longitude)
      }
    }

    if (field === 'locationName' && segment.segmentType === 'STAY') {
      segment.locationNameEdited = true
    }

    queueRender(false)
  }

  const updateSegmentType = (index, nextType) => {
    const segment = segments.value[index]
    if (!segment || !nextType || segment.segmentType === nextType) {
      return
    }

    segment.segmentType = nextType

    if (nextType === 'STAY') {
      segment.waypoints = []
      clearStayLocationResolution?.(segment)

      const reference = resolveReferenceForIndex(index)
      if (reference) {
        segment.latitude = reference.latitude
        segment.longitude = reference.longitude
        resolveStayLocationName?.(index, segment.latitude, segment.longitude)
      }
    } else {
      segment.latitude = null
      segment.longitude = null
      clearStayLocationResolution?.(segment)
      segment.movementType = segment.movementType || 'CAR'

      const reference = resolveReferenceForIndex(index)
      segment.waypoints = reference
        ? [{
          id: waypointIdCounter.value++,
          latitude: Number(reference.latitude.toFixed(6)),
          longitude: Number(reference.longitude.toFixed(6))
        }]
        : []
    }

    queueRender(true)
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
      resolveStayLocationName?.(segmentIndex, segment.latitude, segment.longitude)
    }

    queueRender(false)
  }

  const removeSegment = (index) => {
    if (index < 0 || index >= segments.value.length) {
      return
    }

    const [removed] = segments.value.splice(index, 1)
    if (!removed) {
      return
    }

    if (segments.value.length === 0) {
      resetDialogState()
      return
    }

    if (activeSegmentId.value === removed.id) {
      const nextIndex = Math.min(index, segments.value.length - 1)
      activeSegmentId.value = segments.value[nextIndex].id
    }

    queueRender(true)
  }

  const moveSegment = (index, offset) => {
    const nextIndex = index + offset
    if (index < 0 || nextIndex < 0 || nextIndex >= segments.value.length) {
      return
    }

    const cloned = [...segments.value]
    const [segment] = cloned.splice(index, 1)
    cloned.splice(nextIndex, 0, segment)
    segments.value = cloned

    queueRender(false)
  }

  const addWaypoint = (segmentIndex, latitude, longitude) => {
    const segment = segments.value[segmentIndex]
    if (!segment || segment.segmentType !== 'TRIP') {
      return
    }

    if (!hasValidCoordinates(latitude, longitude)) {
      return
    }

    segment.waypoints.push({
      id: waypointIdCounter.value++,
      latitude: Number(latitude.toFixed(6)),
      longitude: Number(longitude.toFixed(6))
    })

    queueRender(false)
  }

  const updateWaypoint = (segmentIndex, waypointIndex, latitude, longitude) => {
    const segment = segments.value[segmentIndex]
    if (!segment || segment.segmentType !== 'TRIP') {
      return
    }

    if (!segment.waypoints[waypointIndex] || !hasValidCoordinates(latitude, longitude)) {
      return
    }

    segment.waypoints[waypointIndex] = {
      ...segment.waypoints[waypointIndex],
      latitude: Number(latitude.toFixed(6)),
      longitude: Number(longitude.toFixed(6))
    }

    queueRender(false)
  }

  const removeWaypoint = (segmentIndex, waypointIndex) => {
    const segment = segments.value[segmentIndex]
    if (!segment || segment.segmentType !== 'TRIP') {
      return
    }

    segment.waypoints.splice(waypointIndex, 1)
    queueRender(false)
  }

  const moveWaypoint = (segmentIndex, waypointIndex, offset) => {
    const segment = segments.value[segmentIndex]
    if (!segment || segment.segmentType !== 'TRIP') {
      return
    }

    const targetIndex = waypointIndex + offset
    if (targetIndex < 0 || targetIndex >= segment.waypoints.length) {
      return
    }

    const updated = [...segment.waypoints]
    const [waypoint] = updated.splice(waypointIndex, 1)
    updated.splice(targetIndex, 0, waypoint)
    segment.waypoints = updated

    queueRender(false)
  }

  const updateStayLocation = (segmentIndex, latitude, longitude, options = {}) => {
    const segment = segments.value[segmentIndex]
    if (!segment || segment.segmentType !== 'STAY') {
      return
    }

    if (!hasValidCoordinates(latitude, longitude)) {
      return
    }

    segment.latitude = Number(latitude.toFixed(6))
    segment.longitude = Number(longitude.toFixed(6))

    queueRender(options.reframe === true)

    if (options.resolveName !== false) {
      resolveStayLocationName?.(segmentIndex, segment.latitude, segment.longitude)
    }
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

  const toApiPayload = (tripId) => {
    const parsedTripId = Number(tripId)
    const resolvedTripId = Number.isFinite(parsedTripId) && parsedTripId > 0
      ? parsedTripId
      : null

    return {
      tripId: resolvedTripId,
      segments: segments.value.map((segment) => ({
        segmentType: segment.segmentType,
        startTime: toUtcIso(timezone, segment.startTime),
        endTime: toUtcIso(timezone, segment.endTime),
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

  const buildValidatedPayload = (tripId) => {
    const validationError = validateSegments()
    if (validationError) {
      return { payload: null, error: validationError }
    }

    const payload = toApiPayload(tripId)
    if (payload.segments.some((segment) => !segment.startTime || !segment.endTime)) {
      return { payload: null, error: 'All segment dates must be valid.' }
    }

    return { payload, error: null }
  }

  const consumeActiveSegmentReframeFlag = () => {
    const shouldReframe = !suppressNextActiveSegmentReframe.value
    suppressNextActiveSegmentReframe.value = false
    return shouldReframe
  }

  return {
    segments,
    activeSegmentId,
    activeSegmentIndex,
    activeSegment,
    segmentTypeOptions,
    movementTypeOptions,
    previewSummary,
    waypointLabel,
    waypointTagSeverity,
    addSegment,
    removeSegment,
    moveSegment,
    setActiveSegment,
    updateSegmentField,
    updateSegmentType,
    addWaypoint,
    updateWaypoint,
    removeWaypoint,
    moveWaypoint,
    updateStayLocation,
    resetDialogState,
    validateSegments,
    buildValidatedPayload,
    consumeActiveSegmentReframeFlag,
    getContextPoints: () => buildContextPoints(segments.value, activeSegmentIndex.value),
    getContextTripLines: () => buildContextTripLines(segments.value, activeSegmentIndex.value)
  }
}
