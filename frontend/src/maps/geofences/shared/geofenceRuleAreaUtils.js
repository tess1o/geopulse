import { normalizeBoundsToLeafletCorners, toFiniteCoordinate } from '@/maps/shared/coordinateUtils'

export const hasRuleArea = (rule) => {
  return ['northEastLat', 'northEastLon', 'southWestLat', 'southWestLon'].every((key) => {
    const value = toFiniteCoordinate(rule?.[key])
    return value !== null
  })
}

export const toRuleAreaBounds = (rule) => {
  if (!hasRuleArea(rule)) {
    return null
  }

  return normalizeBoundsToLeafletCorners({
    southWestLat: rule.southWestLat,
    southWestLon: rule.southWestLon,
    northEastLat: rule.northEastLat,
    northEastLon: rule.northEastLon
  })
}

export const buildRenderableRuleAreas = ({
  rules = [],
  editingRuleId = null,
  editingAreaExists = false
} = {}) => {
  if (!Array.isArray(rules)) {
    return []
  }

  return rules
    .map((rule) => {
      if (
        editingRuleId !== null
        && Number(rule?.id) === Number(editingRuleId)
        && editingAreaExists
      ) {
        return null
      }

      const bounds = toRuleAreaBounds(rule)
      if (!bounds) {
        return null
      }

      return {
        rule,
        bounds
      }
    })
    .filter(Boolean)
}

export const buildAreaLikeFromBoundsApi = (boundsApi) => {
  const corners = normalizeBoundsToLeafletCorners(boundsApi)
  if (!corners) {
    return null
  }

  const [southWest, northEast] = corners

  return {
    southWestLat: southWest[0],
    southWestLon: southWest[1],
    northEastLat: northEast[0],
    northEastLon: northEast[1]
  }
}
