/**
 * Synchronous, pure screen-pixel grouping shared by Timeline's own clustering
 * and the cross-type (Stay/Trip vs Note vs Photo) collision pass. Depends only
 * on `map.project()`, never on a MapLibre source, so it has no loading state.
 */

// Markers are ~26-34px wide, so centers closer than this visibly overlap.
export const CROSS_TYPE_COLLISION_RADIUS_PX = 32

// Screen-space half-size of a rendered marker (a stay's weather badge included),
// used to test it against a chip.
const ENTITY_RADIUS_PX = 18
const CLUSTER_ENTITY_RADIUS_PX = 22

// Mirrors the .gp-cross-type-marker chip CSS (padding 3px 8px 3px 3px, 22px
// icon + 4px gap + count per segment, 6px between segments).
const CHIP_HEIGHT_PX = 28
const CHIP_PADDING_X_PX = 11
const CHIP_SEGMENT_BASE_PX = 26
const CHIP_DIGIT_PX = 7
const CHIP_SEGMENT_GAP_PX = 6

export const CROSS_TYPE_ORDER = ['timeline', 'notes', 'photos']

/**
 * Greedy pixel-distance grouping. Returns arrays of the original items, in
 * input order; each item lands in exactly one group.
 */
export const groupByPixelDistance = (items, getPoint, radiusPx) => {
  const projected = items.map((item) => ({ item, point: getPoint(item) }))
  const used = new Array(projected.length).fill(false)
  const groups = []

  for (let i = 0; i < projected.length; i += 1) {
    if (used[i]) {
      continue
    }
    used[i] = true

    const members = [projected[i].item]
    for (let j = i + 1; j < projected.length; j += 1) {
      if (used[j]) {
        continue
      }

      const dx = projected[i].point.x - projected[j].point.x
      const dy = projected[i].point.y - projected[j].point.y
      if (Math.sqrt((dx * dx) + (dy * dy)) <= radiusPx) {
        used[j] = true
        members.push(projected[j].item)
      }
    }

    groups.push(members)
  }

  return groups
}

/** Number of underlying items (stays/trips, notes, photos) a member stands for. */
export const getMemberCount = ({ type, group }) => {
  if (type === 'timeline') return Math.max(group?.items?.length || 0, 1)
  if (type === 'notes') return Math.max(group?.notes?.length || 0, 1)
  return Math.max(Number(group?.count) || group?.photos?.length || 0, 1)
}

export const countMembersByType = (members) => members.reduce((counts, member) => {
  counts[member.type] = (counts[member.type] || 0) + getMemberCount(member)
  return counts
}, {})

export const estimateChipSize = (countsByType) => {
  const counts = Object.values(countsByType)
  const width = CHIP_PADDING_X_PX
    + counts.reduce((sum, count) => sum + CHIP_SEGMENT_BASE_PX + (String(count).length * CHIP_DIGIT_PX), 0)
    + (CHIP_SEGMENT_GAP_PX * Math.max(counts.length - 1, 0))
  return { width, height: CHIP_HEIGHT_PX }
}

const createEmptyExclusions = () => Object.fromEntries(CROSS_TYPE_ORDER.map((type) => [type, new Set()]))

export const circleIntersectsRect = (center, radius, rect) => {
  const dx = center.x - Math.max(rect.left, Math.min(center.x, rect.right))
  const dy = center.y - Math.max(rect.top, Math.min(center.y, rect.bottom))
  return (dx * dx) + (dy * dy) <= radius * radius
}

const rectsIntersect = (a, b) => (
  a.left <= b.right && b.left <= a.right && a.top <= b.bottom && b.top <= a.bottom
)

// The highlighted timeline marker (desktop highlight size) when it is the focus.
const FOCUSED_TIMELINE_MARKER_SIZE_PX = 44
// Gap between a focus marker and a chip moved beside it.
const FOCUS_CHIP_GAP_PX = 6

const toSquareRect = (point, size, offsetX = 0) => ({
  left: point.x + offsetX - (size / 2),
  right: point.x + offsetX + (size / 2),
  top: point.y - (size / 2),
  bottom: point.y + (size / 2)
})

const getEntityRadius = (entity) => (entity.isCluster ? CLUSTER_ENTITY_RADIUS_PX : ENTITY_RADIUS_PX)

/**
 * `sources` maps type -> { groups, entities }, where `entities` is what that
 * type currently renders ([{ indices, latitude, longitude, isCluster, isFocused? }],
 * each index pointing into `groups`). Clusters take part like standalone
 * markers, so nothing a type renders can hide under another type's marker.
 *
 * Entities of different types within `radiusPx` become one combo chip; the
 * chip then absorbs anything its (much wider) footprint still covers, until
 * nothing overlaps it.
 *
 * Focus: while a timeline item is highlighted, its marker (`isFocused`) and
 * any `obstacles` (the highlighted trip's start/end markers:
 * [{ latitude, longitude, size, offsetX? }]) are never merged. Whatever would
 * hide under them is gathered into one chip that is moved beside the focus
 * marker (`offset`, in pixels) - like a label on it - instead of under it.
 */
export const computeCrossTypeCollisions = ({
  sources = {},
  obstacles = [],
  mapInstance,
  radiusPx = CROSS_TYPE_COLLISION_RADIUS_PX
}) => {
  const excluded = createEmptyExclusions()
  if (!mapInstance) {
    return { collisions: [], excluded, focusActive: false, occupied: { circles: [], rects: [] } }
  }

  const projected = CROSS_TYPE_ORDER.flatMap((type) => (sources[type]?.entities || []).map((entity) => ({
    ...entity,
    type,
    point: mapInstance.project([entity.longitude, entity.latitude])
  })))

  const focusRects = [
    ...obstacles.map((obstacle) => toSquareRect(
      mapInstance.project([obstacle.longitude, obstacle.latitude]),
      obstacle.size,
      obstacle.offsetX || 0
    )),
    ...projected
      .filter((entity) => entity.isFocused)
      .map((entity) => toSquareRect(entity.point, FOCUSED_TIMELINE_MARKER_SIZE_PX))
  ]
  const focusActive = focusRects.length > 0
  const all = projected.filter((entity) => !entity.isFocused)
  const toOccupiedCircle = (entity) => ({ x: entity.point.x, y: entity.point.y, radius: getEntityRadius(entity) })

  if (!focusActive && new Set(all.map((entity) => entity.type)).size < 2) {
    return { collisions: [], excluded, focusActive, occupied: { circles: all.map(toOccupiedCircle), rects: [] } }
  }

  const toMembers = (entity) => entity.indices.map((index) => ({
    type: entity.type,
    index,
    group: sources[entity.type].groups[index]
  }))

  const buildChip = (entities) => {
    const latitude = entities.reduce((sum, entity) => sum + entity.latitude, 0) / entities.length
    const longitude = entities.reduce((sum, entity) => sum + entity.longitude, 0) / entities.length
    const center = mapInstance.project([longitude, latitude])
    const { width, height } = estimateChipSize(countMembersByType(entities.flatMap(toMembers)))
    return {
      entities,
      latitude,
      longitude,
      center,
      width,
      rect: {
        left: center.x - (width / 2),
        right: center.x + (width / 2),
        top: center.y - (height / 2),
        bottom: center.y + (height / 2)
      }
    }
  }

  let chips = []
  let free = []
  groupByPixelDistance(all, (entity) => entity.point, radiusPx).forEach((entities) => {
    if (new Set(entities.map((entity) => entity.type)).size < 2) {
      free.push(...entities)
    } else {
      chips.push(buildChip(entities))
    }
  })

  // Each pass absorbs at least one entity or merges two chips, so this ends.
  let changed = chips.length > 0
  while (changed) {
    changed = false

    for (let i = 0; i < chips.length && !changed; i += 1) {
      const covered = free.filter((entity) => circleIntersectsRect(entity.point, getEntityRadius(entity), chips[i].rect))
      if (covered.length > 0) {
        free = free.filter((entity) => !covered.includes(entity))
        chips[i] = buildChip([...chips[i].entities, ...covered])
        changed = true
        break
      }

      for (let j = i + 1; j < chips.length; j += 1) {
        if (rectsIntersect(chips[i].rect, chips[j].rect)) {
          const merged = buildChip([...chips[i].entities, ...chips[j].entities])
          chips = chips.filter((_, index) => index !== i && index !== j).concat(merged)
          changed = true
          break
        }
      }
    }
  }

  // Move whatever a focus marker covers into one chip placed to its right,
  // stepping past any other focus marker (e.g. a trip's end next to its start).
  const displaced = []
  focusRects.forEach((focus) => {
    const coveredFree = free.filter((entity) => circleIntersectsRect(entity.point, getEntityRadius(entity), focus))
    const coveredChips = chips.filter((chip) => rectsIntersect(chip.rect, focus))
    if (coveredFree.length === 0 && coveredChips.length === 0) {
      return
    }

    free = free.filter((entity) => !coveredFree.includes(entity))
    chips = chips.filter((chip) => !coveredChips.includes(chip))
    const chip = buildChip([...coveredChips.flatMap((item) => item.entities), ...coveredFree])

    const targetY = (focus.top + focus.bottom) / 2
    const halfHeight = (chip.rect.bottom - chip.rect.top) / 2
    let targetX = focus.right + FOCUS_CHIP_GAP_PX + (chip.width / 2)
    for (let step = 0; step < focusRects.length; step += 1) {
      const placed = {
        left: targetX - (chip.width / 2),
        right: targetX + (chip.width / 2),
        top: targetY - halfHeight,
        bottom: targetY + halfHeight
      }
      const blocking = focusRects.find((other) => rectsIntersect(placed, other))
      if (!blocking) {
        break
      }
      targetX = blocking.right + FOCUS_CHIP_GAP_PX + (chip.width / 2)
    }

    displaced.push({
      ...chip,
      offset: [targetX - chip.center.x, targetY - chip.center.y],
      rect: {
        left: targetX - (chip.width / 2),
        right: targetX + (chip.width / 2),
        top: targetY - halfHeight,
        bottom: targetY + halfHeight
      }
    })
  })

  const collisions = [...chips, ...displaced].map((chip) => {
    const members = chip.entities.flatMap(toMembers)
    members.forEach((member) => excluded[member.type].add(member.index))
    return {
      latitude: chip.latitude,
      longitude: chip.longitude,
      offset: chip.offset || null,
      members,
      // A same-type cluster can stand for many items: the chip zooms in instead of listing them.
      hasCluster: chip.entities.some((entity) => entity.isCluster)
    }
  })

  // Screen space the final markers take, so lower-priority markers (weather) can stay clear of it.
  const occupied = {
    circles: free.map(toOccupiedCircle),
    rects: [...chips, ...displaced].map((chip) => chip.rect).concat(focusRects)
  }

  return { collisions, excluded, focusActive, occupied }
}
