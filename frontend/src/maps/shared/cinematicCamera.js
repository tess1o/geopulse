import { interpolateReplayLongitude, normalizeReplayLongitude, unwrapReplayLongitudes } from './tripReplayMath'

// Camera choreography for the cinematic Rewind. Every view is a pure function of (scene, scene progress), so the
// film clock alone drives the camera: pausing freezes it and seeking lands on the exact frame.

const clamp = (value, min, max) => Math.min(max, Math.max(min, value))
const lerp = (from, to, t) => from + ((to - from) * t)
const lerpBearing = (from, to, t) => from + (((((to - from) % 360) + 540) % 360) - 180) * t

export const easeInOutCubic = (t) => (t < 0.5 ? 4 * t * t * t : 1 - ((-2 * t + 2) ** 3) / 2)
const easeOutCubic = (t) => 1 - ((1 - t) ** 3)

// A trip scene flies in (1.5 s), chases the moving marker while the route draws (3.5 s by default), pulls back
// to reveal the whole route (0.8 s) and rests there for the caption and photos (1.2 s by default). The story
// lengthens the chase and the rest for trips with photos.
const TRIP_FLIGHT_MS = 1500
const TRIP_REVEAL_MS = 800
export const TRIP_FOLLOW_MS = 3500
export const TRIP_HOLD_MS = 1200

/** Phase boundaries as fractions of a trip scene, plus its total length. */
export const buildTripPhases = ({ followMs = TRIP_FOLLOW_MS, holdMs = TRIP_HOLD_MS } = {}) => {
  const durationMs = TRIP_FLIGHT_MS + followMs + TRIP_REVEAL_MS + holdMs
  return Object.freeze({
    durationMs,
    flightEnd: TRIP_FLIGHT_MS / durationMs,
    followEnd: (TRIP_FLIGHT_MS + followMs) / durationMs,
    revealEnd: (TRIP_FLIGHT_MS + followMs + TRIP_REVEAL_MS) / durationMs
  })
}
export const TRIP_PHASES = buildTripPhases()
const FOLLOW_PITCH = 52
const REVEAL_PITCH = 28
const FOLLOW_DRIFT = 6 // degrees the chase camera turns while following, so it never feels static
const FOLLOW_ZOOM_IN = 0.6 // chase camera zoom above the full-route view; small enough to keep surroundings
const MAX_TRIP_BEARING = 60 // the camera turns towards the travel direction, but never further than this from north
const HOLD_PUSH_IN = 0.2
const PLACE_ZOOM = 10.5
const MAX_TRIP_ZOOM = 12.5

// The route draws with a gentle start and finish while the camera follows it.
export const tripRouteProgress = (sceneProgress, phases = TRIP_PHASES) => {
  const linear = clamp(
    (sceneProgress - phases.flightEnd) / (phases.followEnd - phases.flightEnd),
    0,
    1
  )
  return (1 - Math.cos(Math.PI * linear)) / 2
}

// Zoom at which the globe's diameter is `fill` × the viewport's shorter side. MapLibre draws the globe with a
// radius of worldSize / (2π·cos(latitude)) so the centre keeps its Mercator scale, hence the cosine.
export const globeZoomForViewport = ({ width, height } = {}, latitude = 0, fill = 0.9) => {
  const side = Math.max(1, Math.min(Number(width) || 0, Number(height) || 0))
  const cosine = Math.max(0.35, Math.cos((clamp(latitude, -85, 85) * Math.PI) / 180))
  return Math.log2((fill * side * Math.PI * cosine) / 512)
}

const initialBearing = ([fromLongitude, fromLatitude], [toLongitude, toLatitude]) => {
  const toRadians = Math.PI / 180
  const deltaLongitude = (toLongitude - fromLongitude) * toRadians
  const y = Math.sin(deltaLongitude) * Math.cos(toLatitude * toRadians)
  const x = Math.cos(fromLatitude * toRadians) * Math.sin(toLatitude * toRadians)
    - Math.sin(fromLatitude * toRadians) * Math.cos(toLatitude * toRadians) * Math.cos(deltaLongitude)
  return ((Math.atan2(y, x) / toRadians) + 360) % 360
}

const boundsOf = (coordinates) => {
  const unwrapped = unwrapReplayLongitudes(coordinates)
  if (unwrapped.length === 0) return null
  const longitudes = unwrapped.map(([longitude]) => longitude)
  const latitudes = unwrapped.map(([, latitude]) => latitude)
  return [[Math.min(...longitudes), Math.min(...latitudes)], [Math.max(...longitudes), Math.max(...latitudes)]]
}

const view = (center, zoom, pitch = 0, bearing = 0) => ({
  center: [normalizeReplayLongitude(center[0]), center[1]],
  zoom,
  pitch,
  bearing
})

const interpolateView = (from, to, t) => ({
  center: [interpolateReplayLongitude(from.center[0], to.center[0], t), lerp(from.center[1], to.center[1], t)],
  zoom: lerp(from.zoom, to.zoom, t),
  pitch: lerp(from.pitch, to.pitch, t),
  bearing: lerpBearing(from.bearing, to.bearing, t)
})

// A flyTo-like arc: the camera rises to `peakZoom` halfway when the two views are far apart.
const flightView = (from, to, peakZoom, t) => {
  const eased = easeInOutCubic(t)
  const base = interpolateView(from, to, eased)
  const lift = Math.max(0, Math.min(from.zoom, to.zoom) - peakZoom)
  return { ...base, zoom: base.zoom - (lift * Math.sin(Math.PI * t)) }
}

// Web-Mercator y of a latitude: 0 at the north edge of the world square, 1 at the south edge.
const mercatorY = (latitude) => {
  const phi = (clamp(latitude, -85, 85) * Math.PI) / 180
  return (1 - (Math.log(Math.tan((Math.PI / 4) + (phi / 2))) / Math.PI)) / 2
}
const latitudeOfMercatorY = (y) => (Math.atan(Math.sinh(Math.PI * (1 - (2 * y)))) * 180) / Math.PI

/**
 * The top-down view framing `bounds` inside `viewport` minus `padding`, as MapLibre's Mercator fitBounds does.
 * Computed here rather than with map.cameraForBounds, which on a globe can give up and return undefined. The
 * globe keeps Mercator scale at its centre, so the zoom holds for both projections.
 */
export const fitCinematicBounds = (bounds, viewport, padding = 0) => {
  if (!bounds) return null
  const [[west, south], [east, north]] = bounds
  const width = Math.max(1, (Number(viewport?.width) || 0) - (2 * padding))
  const height = Math.max(1, (Number(viewport?.height) || 0) - (2 * padding))
  const top = mercatorY(north)
  const bottom = mercatorY(south)
  const spanX = Math.max((east - west) / 360, 1e-7)
  const spanY = Math.max(bottom - top, 1e-7)
  return {
    center: [(west + east) / 2, latitudeOfMercatorY((top + bottom) / 2)],
    zoom: Math.log2(Math.min(width / spanX, height / spanY) / 512)
  }
}

const tripCoordinates = (trip) => unwrapReplayLongitudes((Array.isArray(trip?.path) ? trip.path : [])
  .map((point) => [Number(point?.longitude), Number(point?.latitude)]))

const sceneCoordinates = (scenes) => scenes.flatMap((scene) => {
  if (scene.type === 'trip') return tripCoordinates(scene.trip)
  if (scene.type === 'place') return [[scene.place.coordinates[1], scene.place.coordinates[0]]]
  return []
})

/** [lng, lat] at the middle of everything the film shows, where the camera opens and returns to. */
export const resolveCinematicHome = (scenes = []) => {
  const fitted = fitCinematicBounds(boundsOf(sceneCoordinates(scenes)), { width: 1, height: 1 })
  // Without coordinates the player shows its empty state, so this centre is never filmed.
  return fitted ? [normalizeReplayLongitude(fitted.center[0]), fitted.center[1]] : [0, 0]
}

/**
 * Precomputes the key views of every scene. Every position comes from the film's own routes and places.
 *
 * @param {object} options
 * @param {Array} options.scenes - timed scenes from buildCinematicStory
 * @param {{width:number,height:number}} options.viewport - map container size in pixels
 */
export const buildCinematicCameraPlan = ({ scenes = [], viewport }) => {
  const padding = Math.round(clamp(Math.min(viewport?.width || 0, viewport?.height || 0) * 0.16, 40, 160))
  const fit = (coordinates) => fitCinematicBounds(boundsOf(coordinates), viewport, padding)
  const heroZoom = (latitude) => globeZoomForViewport(viewport, latitude)

  const overviewFit = fit(sceneCoordinates(scenes))
  const homeCenter = resolveCinematicHome(scenes)
  const homeLatitude = clamp(homeCenter[1], -60, 60)
  // Never pull back past a screen-filling globe: that is the "seen from the moon" view.
  const overview = view(
    homeCenter,
    clamp(overviewFit?.zoom ?? heroZoom(homeLatitude), heroZoom(homeLatitude), 6.5),
    18,
    0
  )
  // The globe spins in from the west and out to the east, always at the latitude of home.
  const introStart = view([homeCenter[0] - 45, homeLatitude], heroZoom(homeLatitude) - 0.35)
  const introEnd = view([homeCenter[0] - 10, homeLatitude], heroZoom(homeLatitude))
  const closingEnd = view([homeCenter[0] + 20, homeLatitude], heroZoom(homeLatitude) - 0.25)

  const plan = []
  let previousEnd = introStart
  scenes.forEach((scene) => {
    let entry
    if (scene.type === 'intro') {
      entry = { type: 'intro', from: introStart, to: introEnd }
    } else if (scene.type === 'overview') {
      entry = { type: 'overview', from: previousEnd, to: overview }
    } else if (scene.type === 'trip') {
      const coordinates = tripCoordinates(scene.trip)
      const start = coordinates[0]
      const end = coordinates.at(-1)
      const tripFit = fit(coordinates)
      const revealZoom = clamp(tripFit?.zoom ?? 8, 3, MAX_TRIP_ZOOM)
      const followZoom = clamp(revealZoom + FOLLOW_ZOOM_IN, 4, MAX_TRIP_ZOOM + 1)
      const bearing = start && end && (start[0] !== end[0] || start[1] !== end[1])
        ? initialBearing(start, end)
        : previousEnd.bearing
      // Lean towards the travel direction, but stay near north-up so the viewer keeps their bearings.
      const cameraBearing = clamp(((bearing + 540) % 360) - 180, -MAX_TRIP_BEARING, MAX_TRIP_BEARING)
      const followStart = view(start || homeCenter, followZoom, FOLLOW_PITCH, cameraBearing)
      const reveal = view(tripFit?.center || end || homeCenter, revealZoom, REVEAL_PITCH, cameraBearing + FOLLOW_DRIFT)
      const flightFit = fit([previousEnd.center, followStart.center])
      entry = {
        type: 'trip',
        phases: scene.phases || TRIP_PHASES,
        from: previousEnd,
        followStart,
        followZoom,
        bearing: cameraBearing,
        peakZoom: Math.min(flightFit?.zoom ?? followZoom, previousEnd.zoom, followZoom) - 0.4,
        reveal,
        to: { ...reveal, zoom: reveal.zoom + HOLD_PUSH_IN, bearing: reveal.bearing + 3 }
      }
    } else if (scene.type === 'place') {
      const center = [scene.place.coordinates[1], scene.place.coordinates[0]]
      const arrival = view(center, PLACE_ZOOM, 48, previousEnd.bearing)
      const flightFit = fit([previousEnd.center, center])
      entry = {
        type: 'place',
        from: previousEnd,
        arrival,
        peakZoom: Math.min(flightFit?.zoom ?? PLACE_ZOOM, previousEnd.zoom) - 0.4,
        to: { ...arrival, bearing: arrival.bearing + 30 }
      }
    } else {
      entry = { type: 'closing', from: previousEnd, to: closingEnd }
    }
    plan.push(entry)
    previousEnd = entry.to
  })
  return plan
}

/**
 * The camera at `progress` (0–1) through a planned scene. `cursor` is the trip marker position
 * ({longitude, latitude}) for trip scenes. With reduced motion the camera holds each scene's final view.
 */
export const resolveCinematicCamera = (entry, progress, { cursor = null, reducedMotion = false } = {}) => {
  if (!entry) return null
  if (reducedMotion) {
    return { ...entry.to, pitch: 0 }
  }
  const p = clamp(Number(progress) || 0, 0, 1)

  if (entry.type === 'intro') {
    return interpolateView(entry.from, entry.to, easeOutCubic(p))
  }
  if (entry.type === 'overview' || entry.type === 'closing') {
    return flightView(entry.from, entry.to, Math.min(entry.from.zoom, entry.to.zoom), p)
  }
  if (entry.type === 'place') {
    if (p < 0.4) return flightView(entry.from, entry.arrival, entry.peakZoom, p / 0.4)
    return interpolateView(entry.arrival, entry.to, (p - 0.4) / 0.6)
  }

  // Trip: fly in, chase the marker, reveal the route, rest on it.
  const phases = entry.phases
  if (p < phases.flightEnd) {
    return flightView(entry.from, entry.followStart, entry.peakZoom, p / phases.flightEnd)
  }
  const followCenter = cursor && Number.isFinite(cursor.longitude) && Number.isFinite(cursor.latitude)
    ? [cursor.longitude, cursor.latitude]
    : entry.followStart.center
  const follow = view(followCenter, entry.followZoom, FOLLOW_PITCH, entry.bearing + (FOLLOW_DRIFT * tripRouteProgress(p, phases)))
  if (p < phases.followEnd) return follow
  if (p < phases.revealEnd) {
    return interpolateView(follow, entry.reveal, easeInOutCubic((p - phases.followEnd) / (phases.revealEnd - phases.followEnd)))
  }
  return interpolateView(entry.reveal, entry.to, (p - phases.revealEnd) / (1 - phases.revealEnd))
}
